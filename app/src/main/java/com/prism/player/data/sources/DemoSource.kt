package com.prism.player.data.sources

import com.prism.player.data.MediaItem
import com.prism.player.data.MediaKind
import com.prism.player.data.MediaSource
import com.prism.player.data.Playback
import com.prism.player.data.SourceConfig

/**
 * Built-in demo library: open-licensed Blender Foundation films (CC BY) and short sample
 * clips hosted on Google's public test-media bucket, so the app can be tried without a server.
 */
class DemoSource(override val config: SourceConfig, private val packageName: String) : MediaSource {
    private fun art(name: String) = "android.resource://$packageName/drawable/$name"
    private fun id(native: String) = MediaItem.makeId(config.id, native)
    private val now = System.currentTimeMillis()

    private fun movie(key: String, file: String, title: String, year: Int, minutes: Int, genres: List<String>, overview: String, ageDays: Int) =
        MediaItem(
            id = id("movie:$key"), sourceId = config.id, nativeId = "movie:$key", kind = MediaKind.MOVIE,
            title = title, overview = overview, year = year,
            posterUrl = art("demo_${key}_poster"), backdropUrl = art("demo_${key}_backdrop"),
            runtimeMs = minutes * 60_000L, genres = genres, addedAt = now - ageDays * 86_400_000L,
        ).also { urls[it.nativeId] = "$BUCKET/$file" }

    private val urls = mutableMapOf<String, String>()

    private val movieList = listOf(
        movie("bbb", "BigBuckBunny.mp4", "Big Buck Bunny", 2008, 10, listOf("Animation", "Comedy", "Family"),
            "A gentle giant rabbit has had enough of three bullying rodents and plans a cheerful, well-engineered revenge.", 1),
        movie("elephants", "ElephantsDream.mp4", "Elephants Dream", 2006, 11, listOf("Animation", "Science Fiction"),
            "Two men explore an endless, shifting machine world, and only one of them believes it is real.", 3),
        movie("sintel", "Sintel.mp4", "Sintel", 2010, 15, listOf("Animation", "Fantasy", "Adventure"),
            "A lone young woman crosses snow, desert and city in search of the baby dragon she once rescued.", 5),
        movie("tears", "TearsOfSteel.mp4", "Tears of Steel", 2012, 12, listOf("Science Fiction", "Action"),
            "In a future Amsterdam, a group of scientists stages a last attempt to save the world from rogue robots.", 2),
    )

    private val show = MediaItem(
        id = id("show:showcase"), sourceId = config.id, nativeId = "show:showcase", kind = MediaKind.SHOW,
        title = "Bigger Screens", year = 2024, genres = listOf("Documentary"),
        overview = "A demo series of short sample clips to try episodes, resume and auto-play next.",
        posterUrl = art("demo_showcase_poster"), backdropUrl = art("demo_showcase_backdrop"),
        addedAt = now - 4 * 86_400_000L,
    )
    private val season = MediaItem(
        id = id("season:showcase:1"), sourceId = config.id, nativeId = "season:showcase:1", kind = MediaKind.SEASON,
        title = "Season 1", seasonNumber = 1, seriesTitle = show.title, parentId = show.nativeId,
        posterUrl = show.posterUrl, backdropUrl = show.backdropUrl,
    )
    private val episodes = listOf(
        "ForBiggerBlazes.mp4" to "Blazes",
        "ForBiggerEscapes.mp4" to "Escapes",
        "ForBiggerFun.mp4" to "Fun",
        "ForBiggerJoyrides.mp4" to "Joyrides",
        "ForBiggerMeltdowns.mp4" to "Meltdowns",
    ).mapIndexed { i, (file, name) ->
        val native = "episode:showcase:${i + 1}"
        urls[native] = "$BUCKET/$file"
        MediaItem(
            id = id(native), sourceId = config.id, nativeId = native, kind = MediaKind.EPISODE,
            title = name, overview = "Sample clip ${i + 1} of the demo series.",
            posterUrl = art("demo_showcase_e${i + 1}"), backdropUrl = show.backdropUrl,
            runtimeMs = 15_000L, seasonNumber = 1, episodeNumber = i + 1,
            seriesTitle = show.title, parentId = season.nativeId, addedAt = show.addedAt,
        )
    }

    override suspend fun movies() = movieList
    override suspend fun shows() = listOf(show)
    override suspend fun children(item: MediaItem) = when (item.nativeId) {
        show.nativeId -> listOf(season)
        season.nativeId -> episodes
        else -> emptyList()
    }

    override suspend fun item(nativeId: String) =
        (movieList + show + season + episodes).firstOrNull { it.nativeId == nativeId }

    override suspend fun playback(item: MediaItem) =
        Playback(urls[item.nativeId] ?: error("No stream for ${item.title}"))

    companion object {
        private const val BUCKET = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample"
    }
}
