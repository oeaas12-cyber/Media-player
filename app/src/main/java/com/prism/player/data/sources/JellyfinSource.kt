package com.prism.player.data.sources

import android.net.Uri
import androidx.media3.common.MimeTypes
import com.prism.player.data.ExternalSubtitle
import com.prism.player.data.HttpException
import com.prism.player.data.MediaItem
import com.prism.player.data.MediaKind
import com.prism.player.data.MediaSource
import com.prism.player.data.Net
import com.prism.player.data.Playback
import com.prism.player.data.PlaybackEvent
import com.prism.player.data.SourceConfig
import com.prism.player.data.SourceType
import com.prism.player.data.arr
import com.prism.player.data.b
import com.prism.player.data.d
import com.prism.player.data.i
import com.prism.player.data.l
import com.prism.player.data.o
import com.prism.player.data.obj
import com.prism.player.data.s
import com.prism.player.data.strings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.UUID

/** Jellyfin (and Emby-compatible) servers through their public REST API. */
class JellyfinSource(override val config: SourceConfig, private val deviceId: String) : MediaSource {
    private val base = config.url.trimEnd('/')
    private val headers get() = authHeaders(deviceId, config.token)
    private val uid get() = config.userId

    private val mutex = Mutex()
    private var moviesCache: List<MediaItem>? = null
    private var showsCache: List<MediaItem>? = null

    override suspend fun refresh() = mutex.withLock { moviesCache = null; showsCache = null }

    private suspend fun items(query: String): List<MediaItem> =
        Net.getJson("$base/Users/$uid/Items?$query&Fields=$FIELDS", headers).obj()?.arr("Items").orEmpty().mapNotNull { toItem(it) }

    override suspend fun movies() = mutex.withLock {
        moviesCache ?: items("IncludeItemTypes=Movie&Recursive=true&SortBy=SortName").also { moviesCache = it }
    }

    override suspend fun shows() = mutex.withLock {
        showsCache ?: items("IncludeItemTypes=Series&Recursive=true&SortBy=SortName").also { showsCache = it }
    }

    override suspend fun children(item: MediaItem): List<MediaItem> = when (item.kind) {
        MediaKind.SHOW -> Net.getJson("$base/Shows/${item.nativeId}/Seasons?userId=$uid&Fields=$FIELDS", headers)
            .obj()?.arr("Items").orEmpty().mapNotNull { toItem(it) }
        MediaKind.SEASON -> Net.getJson(
            "$base/Shows/${item.parentId}/Episodes?seasonId=${item.nativeId}&userId=$uid&Fields=$FIELDS", headers
        ).obj()?.arr("Items").orEmpty().mapNotNull { toItem(it) }
        else -> emptyList()
    }

    override suspend fun item(nativeId: String): MediaItem? =
        Net.getJson("$base/Users/$uid/Items/$nativeId", headers).obj()?.let { toItem(it) }

    override suspend fun continueWatching(): List<MediaItem> =
        Net.getJson("$base/Users/$uid/Items/Resume?Limit=24&MediaTypes=Video&Fields=$FIELDS", headers)
            .obj()?.arr("Items").orEmpty().mapNotNull { toItem(it) }

    override suspend fun playback(item: MediaItem): Playback {
        val info = runCatching {
            Net.getJson("$base/Items/${item.nativeId}/PlaybackInfo?UserId=$uid", headers).obj()
        }.getOrNull()
        val source = info?.arr("MediaSources")?.firstOrNull()
        val msId = source?.s("Id") ?: item.nativeId
        val token = Uri.encode(config.token)
        val subs = source?.arr("MediaStreams").orEmpty()
            .filter { it.s("Type") == "Subtitle" && it.b("IsExternal") == true }
            .mapNotNull { st ->
                val index = st.i("Index") ?: return@mapNotNull null
                val ass = st.s("Codec")?.lowercase() in setOf("ass", "ssa")
                val fmt = if (ass) "ass" else "vtt"
                ExternalSubtitle(
                    uri = "$base/Videos/${item.nativeId}/$msId/Subtitles/$index/Stream.$fmt?api_key=$token",
                    mimeType = if (ass) MimeTypes.TEXT_SSA else MimeTypes.TEXT_VTT,
                    language = st.s("Language"),
                    label = st.s("DisplayTitle") ?: st.s("Title"),
                )
            }
        return Playback(
            uri = "$base/Videos/${item.nativeId}/stream?static=true&MediaSourceId=$msId&api_key=$token",
            headers = headers,
            subtitles = subs,
        )
    }

    override suspend fun report(item: MediaItem, event: PlaybackEvent, positionMs: Long, durationMs: Long) {
        val path = when (event) {
            PlaybackEvent.START -> "/Sessions/Playing"
            PlaybackEvent.PROGRESS -> "/Sessions/Playing/Progress"
            PlaybackEvent.STOP -> "/Sessions/Playing/Stopped"
        }
        val body = buildJsonObject {
            put("ItemId", item.nativeId)
            put("PositionTicks", positionMs * 10_000)
            put("CanSeek", true)
            put("IsPaused", false)
        }.toString().toRequestBody("application/json".toMediaType())
        Net.send(Request.Builder().url(base + path).post(body).apply { headers.forEach { (k, v) -> header(k, v) } }.build())
    }

    private fun toItem(o: JsonObject): MediaItem? {
        val id = o.s("Id") ?: return null
        val kind = when (o.s("Type")) {
            "Movie" -> MediaKind.MOVIE
            "Series" -> MediaKind.SHOW
            "Season" -> MediaKind.SEASON
            "Episode" -> MediaKind.EPISODE
            else -> return null
        }
        val primaryTag = o.o("ImageTags")?.s("Primary")
        val poster = when {
            primaryTag != null -> "$base/Items/$id/Images/Primary?fillHeight=600&quality=90&tag=$primaryTag"
            o.s("SeriesId") != null -> "$base/Items/${o.s("SeriesId")}/Images/Primary?fillHeight=600&quality=90"
            else -> null
        }
        val backdrop = when {
            o.strings("BackdropImageTags").isNotEmpty() -> "$base/Items/$id/Images/Backdrop/0?maxWidth=1920&quality=85"
            o.s("ParentBackdropItemId") != null -> "$base/Items/${o.s("ParentBackdropItemId")}/Images/Backdrop/0?maxWidth=1920&quality=85"
            else -> null
        }
        val user = o.o("UserData")
        return MediaItem(
            id = MediaItem.makeId(config.id, id),
            sourceId = config.id,
            nativeId = id,
            kind = kind,
            title = o.s("Name").orEmpty(),
            overview = o.s("Overview"),
            year = o.i("ProductionYear"),
            posterUrl = poster,
            backdropUrl = backdrop,
            rating = o.d("CommunityRating"),
            runtimeMs = o.l("RunTimeTicks")?.div(10_000),
            genres = o.strings("Genres"),
            seasonNumber = if (kind == MediaKind.SEASON) o.i("IndexNumber") else o.i("ParentIndexNumber"),
            episodeNumber = if (kind == MediaKind.EPISODE) o.i("IndexNumber") else null,
            seriesTitle = o.s("SeriesName"),
            parentId = when (kind) {
                MediaKind.SEASON -> o.s("SeriesId")
                MediaKind.EPISODE -> o.s("SeasonId")
                else -> null
            },
            addedAt = o.s("DateCreated")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            resumeMs = user?.l("PlaybackPositionTicks")?.div(10_000)?.takeIf { it > 0 },
            played = user?.b("Played") == true,
        )
    }

    companion object {
        private const val FIELDS = "Overview,Genres,DateCreated"
        private const val CLIENT = "Prism Player"
        private const val VERSION = "0.1.0"

        fun authHeaders(deviceId: String, token: String?): Map<String, String> {
            val auth = buildString {
                append("MediaBrowser Client=\"$CLIENT\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"$VERSION\"")
                if (!token.isNullOrBlank()) append(", Token=\"$token\"")
            }
            return buildMap {
                put("Authorization", auth)
                put("X-Emby-Authorization", auth)
                if (!token.isNullOrBlank()) put("X-Emby-Token", token)
            }
        }

        /**
         * Signs in with username/password and returns a ready-to-save source.
         * Works with Jellyfin and Emby: tries the server root and the /emby path, both
         * header styles, and both password field names before giving up.
         */
        suspend fun login(url: String, username: String, password: String, deviceId: String): SourceConfig {
            val root = normaliseUrl(url).removeSuffix("/emby")
            val bases = listOf("" to root, "/emby" to "$root/emby")
            val styles = listOf("Emby header" to embyHeaders(deviceId), "Jellyfin header" to authHeaders(deviceId, null))
            val pwJson = buildJsonObject { put("Username", username); put("Pw", password) }.toString()
                .toRequestBody("application/json".toMediaType())
            val pwForm = FormBody.Builder().add("Username", username).add("Pw", password).build()
            val legacyJson = buildJsonObject { put("Username", username); put("Pw", password); put("Password", password) }
                .toString().toRequestBody("application/json".toMediaType())
            val bodies = listOf("JSON" to pwJson, "form" to pwForm, "JSON+Password" to legacyJson)

            val attempts = mutableListOf<String>()
            for ((suffix, base) in bases) {
                var pathMissing = false
                for ((styleName, headers) in styles) {
                    if (pathMissing) break
                    for ((bodyName, body) in bodies) {
                        val label = "${suffix.ifEmpty { "/" }} · $styleName · $bodyName"
                        val res = try {
                            Net.json.parseToJsonElement(
                                Net.send(
                                    Request.Builder().url("$base/Users/AuthenticateByName")
                                        .post(body)
                                        .header("Accept", "application/json")
                                        .header("User-Agent", "$CLIENT/$VERSION (Android)")
                                        .apply { headers.forEach { (k, v) -> header(k, v) } }
                                        .build()
                                )
                            ).obj()
                        } catch (e: HttpException) {
                            val msg = e.body.trim().take(120).takeIf { it.isNotBlank() && !it.startsWith("<") }
                            attempts += "$label → HTTP ${e.code}${msg?.let { ": $it" } ?: ""}"
                            // A real "wrong credentials" / "locked" answer: stop immediately.
                            // Retrying the same password only counts as more failed logins on the server.
                            if (e.code == 401 || e.code == 403) {
                                throw IOException(
                                    (msg ?: "The server refused the sign-in (HTTP ${e.code}).") +
                                        if (e.code == 401) "\n\nThe server says the username or password doesn't match. " +
                                            "Check them on the server's web page in a browser. Too many failed tries can lock the account for a while."
                                        else ""
                                )
                            }
                            if (e.code == 404 || e.code == 405) { pathMissing = true; break }
                            continue
                        } catch (e: Exception) {
                            attempts += "$label → ${e.message ?: e.javaClass.simpleName}"
                            continue
                        }
                        val token = res?.s("AccessToken")
                        val userId = res?.o("User")?.s("Id")
                        if (token == null || userId == null) {
                            attempts += "$label → unexpected response"
                            continue
                        }
                        val info = runCatching { Net.getJson("$base/System/Info/Public").obj() }.getOrNull()
                        return SourceConfig(
                            id = UUID.randomUUID().toString(),
                            type = SourceType.JELLYFIN,
                            name = info?.s("ServerName") ?: Uri.parse(base).host ?: "Media server",
                            url = base,
                            username = username,
                            token = token,
                            userId = userId,
                        )
                    }
                }
            }
            val headline = "Couldn't sign in."
            throw IOException(headline + "\n\nWhat the server answered:\n" + attempts.joinToString("\n") { "• $it" })
        }

        /** Emby-style client header (the format Emby's own apps send). */
        fun embyHeaders(deviceId: String): Map<String, String> = mapOf(
            "X-Emby-Authorization" to
                "Emby UserId=\"\", Client=\"$CLIENT\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"$VERSION\"",
            "X-Emby-Client" to CLIENT,
            "X-Emby-Device-Name" to "Android",
            "X-Emby-Device-Id" to deviceId,
            "X-Emby-Client-Version" to VERSION,
        )

        fun normaliseUrl(url: String): String {
            val u = url.trim().trimEnd('/')
            return if (u.startsWith("http://") || u.startsWith("https://")) u else "http://$u"
        }
    }
}
