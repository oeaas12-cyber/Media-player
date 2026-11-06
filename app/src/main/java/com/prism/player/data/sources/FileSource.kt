package com.prism.player.data.sources

import android.content.Context
import com.prism.player.data.ExternalSubtitle
import com.prism.player.data.FileNameParser
import com.prism.player.data.MediaItem
import com.prism.player.data.MediaKind
import com.prism.player.data.MediaSource
import com.prism.player.data.Net
import com.prism.player.data.Playback
import com.prism.player.data.SourceConfig
import com.prism.player.data.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

data class RemoteFile(val name: String, val path: String, val isDir: Boolean, val modified: Long = 0)

@Serializable
data class ScanIndex(
    val movies: List<MediaItem> = emptyList(),
    val shows: List<MediaItem> = emptyList(),
    val seasons: Map<String, List<MediaItem>> = emptyMap(),
    val episodes: Map<String, List<MediaItem>> = emptyMap(),
    val subtitles: Map<String, List<String>> = emptyMap(),
)

/**
 * Base for folder-based sources. Walks the share, recognises movies and episodes from the
 * file names and folders, and fetches artwork and descriptions from TMDB when a key is set.
 * The result is cached on disk until the user refreshes the source.
 */
abstract class FileSource(
    final override val config: SourceConfig,
    private val context: Context,
    private val tmdb: Tmdb,
) : MediaSource {

    /** Lists a folder. [path] is relative to the source root; folders end with "/". */
    abstract suspend fun list(path: String): List<RemoteFile>
    abstract fun streamUri(path: String): String
    open val streamHeaders: Map<String, String> = emptyMap()
    open val smbConfig: SourceConfig? = null

    private val mutex = Mutex()
    private var index: ScanIndex? = null
    private val cacheFile get() = File(context.filesDir, "scan_${config.id}.json")

    private suspend fun index(): ScanIndex = mutex.withLock {
        index ?: (loadCache() ?: scan().also { save(it) }).also { index = it }
    }

    private suspend fun loadCache(): ScanIndex? = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) null
        else runCatching { Net.json.decodeFromString(ScanIndex.serializer(), cacheFile.readText()) }.getOrNull()
    }

    private suspend fun save(i: ScanIndex) = withContext(Dispatchers.IO) {
        runCatching { cacheFile.writeText(Net.json.encodeToString(ScanIndex.serializer(), i)) }
    }

    override suspend fun refresh() = mutex.withLock {
        index = null
        withContext(Dispatchers.IO) { cacheFile.delete() }
        Unit
    }

    override suspend fun movies() = index().movies
    override suspend fun shows() = index().shows

    override suspend fun children(item: MediaItem): List<MediaItem> = when (item.kind) {
        MediaKind.SHOW -> index().seasons[item.nativeId].orEmpty()
        MediaKind.SEASON -> index().episodes[item.nativeId].orEmpty()
        else -> emptyList()
    }

    override suspend fun item(nativeId: String): MediaItem? {
        val i = index()
        return (i.movies.asSequence() + i.shows + i.seasons.values.flatten() + i.episodes.values.flatten())
            .firstOrNull { it.nativeId == nativeId }
    }

    override suspend fun playback(item: MediaItem): Playback {
        val subs = index().subtitles[item.nativeId].orEmpty().mapNotNull { p ->
            val name = p.substringAfterLast('/')
            val mime = FileNameParser.SUBTITLE_MIME[name.substringAfterLast('.').lowercase()] ?: return@mapNotNull null
            ExternalSubtitle(streamUri(p), mime, FileNameParser.subtitleLanguage(name), name)
        }
        return Playback(streamUri(item.nativeId), streamHeaders, subs, smbConfig)
    }

    private class Found(val file: RemoteFile, val folders: List<String>)

    private suspend fun scan(): ScanIndex = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Found>()
        val subsByDir = mutableMapOf<String, MutableList<RemoteFile>>()

        suspend fun walk(path: String, folders: List<String>, depth: Int) {
            if (depth > 8 || videos.size > 20_000) return
            val entries = try {
                list(path)
            } catch (e: Exception) {
                if (depth == 0) throw e else return
            }
            for (e in entries) {
                if (e.name.startsWith(".") || e.name.startsWith("@") || e.name.equals("sample", true)) continue
                if (e.isDir) {
                    walk(e.path, listOf(e.name) + folders, depth + 1)
                } else {
                    val ext = e.name.substringAfterLast('.', "").lowercase()
                    when {
                        ext in FileNameParser.VIDEO_EXT && !e.name.contains("sample", true) -> videos += Found(e, folders)
                        ext in FileNameParser.SUBTITLE_MIME -> subsByDir.getOrPut(path) { mutableListOf() } += e
                    }
                }
            }
        }
        walk("", emptyList(), 0)

        val subtitles = mutableMapOf<String, List<String>>()
        for (v in videos) {
            val dir = v.file.path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            val base = v.file.name.substringBeforeLast('.')
            val matches = subsByDir[dir].orEmpty().filter { it.name.startsWith(base) }
            if (matches.isNotEmpty()) subtitles[v.file.path] = matches.map { it.path }
        }

        val limiter = Semaphore(6)
        val movieFiles = mutableListOf<Pair<FileNameParser.Parsed, Found>>()
        val episodeFiles = linkedMapOf<String, MutableList<Pair<FileNameParser.Parsed, Found>>>()
        for (v in videos) {
            val p = FileNameParser.parse(v.file.name, v.folders)
            if (p.season != null && p.episode != null) {
                episodeFiles.getOrPut(p.title.lowercase()) { mutableListOf() } += p to v
            } else {
                movieFiles += p to v
            }
        }

        coroutineScope {
            val movies = movieFiles.map { (p, v) ->
                async {
                    val m = limiter.withPermit { runCatching { tmdb.searchMovie(p.title, p.year) }.getOrNull() }
                    MediaItem(
                        id = MediaItem.makeId(config.id, v.file.path),
                        sourceId = config.id,
                        nativeId = v.file.path,
                        kind = MediaKind.MOVIE,
                        title = m?.title?.takeIf { it.isNotBlank() } ?: p.title,
                        overview = m?.overview,
                        year = m?.year ?: p.year,
                        posterUrl = m?.posterUrl,
                        backdropUrl = m?.backdropUrl,
                        rating = m?.rating,
                        genres = m?.genres.orEmpty(),
                        addedAt = v.file.modified.takeIf { it > 0 },
                    )
                }
            }.awaitAll().sortedBy { it.title.lowercase() }

            val seasons = mutableMapOf<String, List<MediaItem>>()
            val episodes = mutableMapOf<String, List<MediaItem>>()
            val shows = episodeFiles.values.map { files ->
                async {
                    val name = files.first().first.title
                    val showNative = "show:${name.lowercase()}"
                    val m = limiter.withPermit { runCatching { tmdb.searchShow(name) }.getOrNull() }
                    val showTitle = m?.title?.takeIf { it.isNotBlank() } ?: name
                    val bySeason = files.groupBy { it.first.season!! }.toSortedMap()
                    val seasonItems = bySeason.map { (sn, eps) ->
                        val seasonNative = "season:${name.lowercase()}:$sn"
                        val epItems = eps.sortedBy { it.first.episode }.map { (p, v) ->
                            val te = m?.let { limiter.withPermit { runCatching { tmdb.episode(it.id, sn, p.episode!!) }.getOrNull() } }
                            MediaItem(
                                id = MediaItem.makeId(config.id, v.file.path),
                                sourceId = config.id,
                                nativeId = v.file.path,
                                kind = MediaKind.EPISODE,
                                title = te?.name?.takeIf { it.isNotBlank() } ?: "Episode ${p.episode}",
                                overview = te?.overview,
                                posterUrl = te?.stillUrl ?: m?.backdropUrl,
                                backdropUrl = m?.backdropUrl,
                                runtimeMs = te?.runtimeMs,
                                seasonNumber = sn,
                                episodeNumber = p.episode,
                                seriesTitle = showTitle,
                                parentId = seasonNative,
                                addedAt = v.file.modified.takeIf { it > 0 },
                            )
                        }
                        synchronized(episodes) { episodes[seasonNative] = epItems }
                        MediaItem(
                            id = MediaItem.makeId(config.id, seasonNative),
                            sourceId = config.id,
                            nativeId = seasonNative,
                            kind = MediaKind.SEASON,
                            title = if (sn == 0) "Specials" else "Season $sn",
                            posterUrl = m?.posterUrl,
                            backdropUrl = m?.backdropUrl,
                            seasonNumber = sn,
                            seriesTitle = showTitle,
                            parentId = showNative,
                        )
                    }
                    synchronized(seasons) { seasons[showNative] = seasonItems }
                    MediaItem(
                        id = MediaItem.makeId(config.id, showNative),
                        sourceId = config.id,
                        nativeId = showNative,
                        kind = MediaKind.SHOW,
                        title = showTitle,
                        overview = m?.overview,
                        year = m?.year,
                        posterUrl = m?.posterUrl,
                        backdropUrl = m?.backdropUrl,
                        rating = m?.rating,
                        genres = m?.genres.orEmpty(),
                        addedAt = files.maxOf { it.second.file.modified }.takeIf { it > 0 },
                    )
                }
            }.awaitAll().sortedBy { it.title.lowercase() }

            ScanIndex(movies, shows, seasons, episodes, subtitles)
        }
    }
}
