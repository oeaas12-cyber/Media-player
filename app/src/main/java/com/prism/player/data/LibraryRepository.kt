package com.prism.player.data

import android.content.Context
import com.prism.player.data.sources.DemoSource
import com.prism.player.data.sources.JellyfinSource
import com.prism.player.data.sources.PlexSource
import com.prism.player.data.sources.SmbSource
import com.prism.player.data.sources.WebDavSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

data class HomeData(
    val hero: List<MediaItem>,
    val shelves: List<Shelf>,
    val errors: List<String>,
    val hasSources: Boolean,
)

/** Merges every configured source into one library. */
class LibraryRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val tmdb: Tmdb,
    val progress: ProgressStore,
) {
    private val instances = mutableMapOf<SourceConfig, MediaSource>()
    private val cache = ConcurrentHashMap<String, MediaItem>()

    val sourcesChanged: Flow<List<SourceConfig>> = settings.settings.map { it.sources }.distinctUntilChanged()

    suspend fun sources(): List<MediaSource> {
        val s = settings.current()
        return synchronized(instances) {
            instances.keys.retainAll(s.sources.toSet())
            s.sources.map { c -> instances.getOrPut(c) { create(c, s.deviceId) } }
        }
    }

    private fun create(c: SourceConfig, deviceId: String): MediaSource = when (c.type) {
        SourceType.JELLYFIN -> JellyfinSource(c, deviceId)
        SourceType.PLEX -> PlexSource(c, deviceId)
        SourceType.SMB -> SmbSource(c, context, tmdb)
        SourceType.WEBDAV -> WebDavSource(c, context, tmdb)
        SourceType.DEMO -> DemoSource(c, context.packageName)
    }

    private suspend fun sourceOf(item: MediaItem) = sources().firstOrNull { it.config.id == item.sourceId }
        ?: error("The source for \"${item.title}\" was removed")

    private fun remember(items: List<MediaItem>) = items.also { list -> list.forEach { cache[it.id] = it } }

    private suspend fun <T> fromAll(block: suspend (MediaSource) -> List<T>): Pair<List<T>, List<String>> = coroutineScope {
        val results = sources().map { src -> async { src to runCatching { block(src) } } }.awaitAll()
        results.flatMap { it.second.getOrDefault(emptyList()) } to
            results.mapNotNull { (src, r) -> r.exceptionOrNull()?.let { "${src.config.name}: ${it.message ?: it.javaClass.simpleName}" } }
    }

    suspend fun movies(): List<MediaItem> = remember(fromAll { it.movies() }.first).sortedBy { it.title.lowercase() }
    suspend fun shows(): List<MediaItem> = remember(fromAll { it.shows() }.first).sortedBy { it.title.lowercase() }

    suspend fun children(item: MediaItem): List<MediaItem> = remember(sourceOf(item).children(item))

    suspend fun item(id: String): MediaItem? = cache[id] ?: progress.get(id)?.item ?: run {
        val src = sources().firstOrNull { it.config.id == MediaItem.sourceIdOf(id) } ?: return null
        src.item(MediaItem.nativeIdOf(id))?.also { cache[it.id] = it }
    }

    suspend fun playback(item: MediaItem): Playback = sourceOf(item).playback(item)

    suspend fun report(item: MediaItem, event: PlaybackEvent, positionMs: Long, durationMs: Long) {
        if (event != PlaybackEvent.START) progress.save(item, positionMs, durationMs)
        runCatching { sourceOf(item).report(item, event, positionMs, durationMs) }
    }

    suspend fun refresh(sourceId: String? = null) {
        sources().filter { sourceId == null || it.config.id == sourceId }.forEach { it.refresh() }
    }

    /** The episode after [episode], crossing into the next season when needed. */
    suspend fun nextEpisode(episode: MediaItem): MediaItem? = runCatching {
        val season = episode.parentId?.let { item(MediaItem.makeId(episode.sourceId, it)) }
        if (season != null) {
            val eps = children(season)
            val idx = eps.indexOfFirst { it.id == episode.id }
            if (idx >= 0 && idx + 1 < eps.size) return@runCatching eps[idx + 1]
            val show = season.parentId?.let { item(MediaItem.makeId(episode.sourceId, it)) } ?: return@runCatching null
            val seasons = children(show)
            val sIdx = seasons.indexOfFirst { it.id == season.id }
            seasons.drop(sIdx + 1).firstNotNullOfOrNull { children(it).firstOrNull() }
        } else null
    }.getOrNull()

    suspend fun search(query: String): List<MediaItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return (movies() + shows())
            .filter { it.title.lowercase().contains(q) || it.genres.any { g -> g.lowercase() == q } }
            .sortedBy { if (it.title.lowercase().startsWith(q)) 0 else 1 }
    }

    suspend fun home(): HomeData = coroutineScope {
        val cfgs = settings.current().sources
        if (cfgs.isEmpty()) return@coroutineScope HomeData(emptyList(), emptyList(), emptyList(), false)
        val moviesD = async { fromAll { it.movies() } }
        val showsD = async { fromAll { it.shows() } }
        val resumeD = async { fromAll { it.continueWatching() } }
        val (movies, e1) = moviesD.await()
        val (shows, e2) = showsD.await()
        val (serverResume, _) = resumeD.await()
        remember(movies); remember(shows); remember(serverResume)

        val local = progress.continueWatching().map { it.item }
        val continueWatching = (local + serverResume.filter { progress.get(it.id)?.finished != true })
            .distinctBy { it.id }.take(20)

        val shelves = buildList {
            if (continueWatching.isNotEmpty()) add(Shelf("Continue Watching", continueWatching, landscape = true))
            val recentMovies = movies.filter { it.addedAt != null }.sortedByDescending { it.addedAt }.take(20)
            if (recentMovies.isNotEmpty()) add(Shelf("Recently Added Movies", recentMovies))
            val recentShows = shows.filter { it.addedAt != null }.sortedByDescending { it.addedAt }.take(20)
            if (recentShows.isNotEmpty()) add(Shelf("Recently Updated TV Shows", recentShows))
            if (movies.isNotEmpty()) add(Shelf("Movies", movies.take(40)))
            if (shows.isNotEmpty()) add(Shelf("TV Shows", shows.take(40)))
            val topRated = (movies + shows).filter { (it.rating ?: 0.0) >= 7.5 }.sortedByDescending { it.rating }.take(20)
            if (topRated.size >= 4) add(Shelf("Top Rated", topRated))
            movies.flatMap { it.genres }.groupingBy { it }.eachCount().entries
                .filter { it.value >= 4 }.sortedByDescending { it.value }.take(6)
                .forEach { (genre, _) -> add(Shelf(genre, movies.filter { genre in it.genres }.shuffled().take(20))) }
        }
        val hero = (movies + shows).filter { it.backdropUrl != null }
            .sortedByDescending { it.addedAt ?: 0 }.take(6)
        HomeData(hero, shelves, e1 + e2, true)
    }
}
