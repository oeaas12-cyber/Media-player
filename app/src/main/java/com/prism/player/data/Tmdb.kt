package com.prism.player.data

import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

data class TmdbMatch(
    val id: Int,
    val title: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val rating: Double?,
    val genres: List<String>,
)

data class TmdbEpisode(val name: String?, val overview: String?, val stillUrl: String?, val runtimeMs: Long?)

/** Metadata lookups for file-based sources (SMB / WebDAV) using The Movie Database. */
class Tmdb(private val keyProvider: suspend () -> String) {
    private val searchCache = ConcurrentHashMap<String, Result<TmdbMatch?>>()
    private val seasonCache = ConcurrentHashMap<String, Map<Int, TmdbEpisode>>()

    suspend fun hasKey() = keyProvider().isNotBlank()

    private suspend fun call(path: String, params: Map<String, String>): JsonObject? {
        val key = keyProvider().trim()
        if (key.isBlank()) return null
        val url = "https://api.themoviedb.org/3$path".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            if (key.length <= 40) addQueryParameter("api_key", key)
        }.build().toString()
        val headers = if (key.length > 40) mapOf("Authorization" to "Bearer $key") else emptyMap()
        return Net.getJson(url, headers).obj()
    }

    suspend fun searchMovie(title: String, year: Int?): TmdbMatch? = cached("m|$title|$year") {
        val params = buildMap { put("query", title); year?.let { put("year", it.toString()) } }
        val first = call("/search/movie", params)?.arr("results")?.firstOrNull()
            ?: if (year != null) call("/search/movie", mapOf("query" to title))?.arr("results")?.firstOrNull() else null
        first?.toMatch("title", "release_date")
    }

    suspend fun searchShow(title: String): TmdbMatch? = cached("t|$title") {
        call("/search/tv", mapOf("query" to title))?.arr("results")?.firstOrNull()?.toMatch("name", "first_air_date")
    }

    suspend fun episode(showId: Int, season: Int, episode: Int): TmdbEpisode? {
        val key = "$showId|$season"
        val map = seasonCache[key] ?: runCatching {
            call("/tv/$showId/season/$season", emptyMap())?.arr("episodes").orEmpty().associate { e ->
                (e.i("episode_number") ?: -1) to TmdbEpisode(
                    name = e.s("name"),
                    overview = e.s("overview")?.takeIf { it.isNotBlank() },
                    stillUrl = e.s("still_path")?.let { "$IMG/w780$it" },
                    runtimeMs = e.l("runtime")?.let { it * 60_000 },
                )
            }
        }.getOrDefault(emptyMap()).also { seasonCache[key] = it }
        return map[episode]
    }

    private suspend fun cached(key: String, block: suspend () -> TmdbMatch?): TmdbMatch? {
        searchCache[key]?.let { return it.getOrNull() }
        val r = runCatching { block() }
        if (r.isSuccess) searchCache[key] = r
        return r.getOrNull()
    }

    private fun JsonObject.toMatch(titleKey: String, dateKey: String) = TmdbMatch(
        id = i("id") ?: 0,
        title = s(titleKey).orEmpty(),
        overview = s("overview")?.takeIf { it.isNotBlank() },
        posterUrl = s("poster_path")?.let { "$IMG/w500$it" },
        backdropUrl = s("backdrop_path")?.let { "$IMG/w1280$it" },
        year = s(dateKey)?.take(4)?.toIntOrNull(),
        rating = d("vote_average")?.takeIf { it > 0 },
        genres = (this["genre_ids"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { GENRES[it.toString().toIntOrNull()] }.orEmpty(),
    )

    companion object {
        private const val IMG = "https://image.tmdb.org/t/p"
        private val GENRES = mapOf(
            28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
            99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
            27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction",
            10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western",
            10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News", 10764 to "Reality",
            10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk", 10768 to "War & Politics",
        )
    }
}
