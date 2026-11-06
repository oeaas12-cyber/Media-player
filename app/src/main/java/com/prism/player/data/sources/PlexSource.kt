package com.prism.player.data.sources

import android.net.Uri
import androidx.media3.common.MimeTypes
import com.prism.player.data.ExternalSubtitle
import com.prism.player.data.MediaItem
import com.prism.player.data.MediaKind
import com.prism.player.data.MediaSource
import com.prism.player.data.Net
import com.prism.player.data.Playback
import com.prism.player.data.PlaybackEvent
import com.prism.player.data.SourceConfig
import com.prism.player.data.arr
import com.prism.player.data.b
import com.prism.player.data.d
import com.prism.player.data.i
import com.prism.player.data.l
import com.prism.player.data.o
import com.prism.player.data.obj
import com.prism.player.data.objects
import com.prism.player.data.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Plex Media Server through its HTTP API. Sign-in uses the official plex.tv/link PIN flow. */
class PlexSource(override val config: SourceConfig, private val deviceId: String) : MediaSource {
    private val base = config.url.trimEnd('/')
    private val token = config.token
    private val headers get() = PlexAuth.headers(deviceId) + ("X-Plex-Token" to token)

    private val mutex = Mutex()
    private var moviesCache: List<MediaItem>? = null
    private var showsCache: List<MediaItem>? = null

    override suspend fun refresh() = mutex.withLock { moviesCache = null; showsCache = null }

    private suspend fun container(path: String): JsonObject? =
        Net.getJson(base + path, headers).obj()?.o("MediaContainer")

    private suspend fun sectionItems(type: String): List<MediaItem> {
        val sections = container("/library/sections")?.arr("Directory").orEmpty().filter { it.s("type") == type }
        return sections.flatMap { sec ->
            container("/library/sections/${sec.s("key")}/all")?.arr("Metadata").orEmpty().mapNotNull { toItem(it) }
        }.sortedBy { it.title.lowercase() }
    }

    override suspend fun movies() = mutex.withLock { moviesCache ?: sectionItems("movie").also { moviesCache = it } }
    override suspend fun shows() = mutex.withLock { showsCache ?: sectionItems("show").also { showsCache = it } }

    override suspend fun children(item: MediaItem): List<MediaItem> =
        container("/library/metadata/${item.nativeId}/children")?.arr("Metadata").orEmpty().mapNotNull { toItem(it) }

    override suspend fun item(nativeId: String): MediaItem? =
        container("/library/metadata/$nativeId")?.arr("Metadata")?.firstOrNull()?.let { toItem(it) }

    override suspend fun continueWatching(): List<MediaItem> =
        container("/library/onDeck")?.arr("Metadata").orEmpty().mapNotNull { toItem(it) }

    override suspend fun playback(item: MediaItem): Playback {
        val meta = container("/library/metadata/${item.nativeId}")?.arr("Metadata")?.firstOrNull()
            ?: error("Item not found on server")
        val part = meta.arr("Media").firstOrNull()?.arr("Part")?.firstOrNull() ?: error("No playable file")
        val t = Uri.encode(token)
        val subs = part.arr("Stream").filter { it.i("streamType") == 3 && it.s("key") != null }.map { st ->
            val codec = st.s("codec")?.lowercase()
            ExternalSubtitle(
                uri = "$base${st.s("key")}?X-Plex-Token=$t",
                mimeType = when (codec) {
                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                    "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                    else -> MimeTypes.APPLICATION_SUBRIP
                },
                language = st.s("languageCode") ?: st.s("languageTag"),
                label = st.s("displayTitle"),
            )
        }
        return Playback("$base${part.s("key")}?X-Plex-Token=$t", headers, subs)
    }

    override suspend fun report(item: MediaItem, event: PlaybackEvent, positionMs: Long, durationMs: Long) {
        val state = if (event == PlaybackEvent.STOP) "stopped" else "playing"
        Net.get(
            "$base/:/timeline?ratingKey=${item.nativeId}&key=${Uri.encode("/library/metadata/${item.nativeId}")}" +
                "&state=$state&time=$positionMs&duration=$durationMs",
            headers,
        )
        if (event == PlaybackEvent.STOP && durationMs > 0 && positionMs >= durationMs * 0.92) {
            Net.get("$base/:/scrobble?identifier=com.plexapp.plugins.library&key=${item.nativeId}", headers)
        }
    }

    private fun img(path: String?): String? = path?.let {
        if (it.startsWith("http")) it else "$base$it?X-Plex-Token=${Uri.encode(token)}"
    }

    private fun toItem(o: JsonObject): MediaItem? {
        val key = o.s("ratingKey") ?: return null
        val kind = when (o.s("type")) {
            "movie" -> MediaKind.MOVIE
            "show" -> MediaKind.SHOW
            "season" -> MediaKind.SEASON
            "episode" -> MediaKind.EPISODE
            else -> return null
        }
        return MediaItem(
            id = MediaItem.makeId(config.id, key),
            sourceId = config.id,
            nativeId = key,
            kind = kind,
            title = o.s("title").orEmpty(),
            overview = o.s("summary")?.takeIf { it.isNotBlank() },
            year = o.i("year"),
            posterUrl = img(if (kind == MediaKind.SEASON) o.s("thumb") ?: o.s("parentThumb") else o.s("thumb")),
            backdropUrl = img(o.s("art") ?: o.s("grandparentArt") ?: o.s("parentArt")),
            rating = o.d("audienceRating") ?: o.d("rating"),
            runtimeMs = o.l("duration"),
            genres = o.arr("Genre").mapNotNull { it.s("tag") },
            seasonNumber = if (kind == MediaKind.SEASON) o.i("index") else o.i("parentIndex"),
            episodeNumber = if (kind == MediaKind.EPISODE) o.i("index") else null,
            seriesTitle = o.s("grandparentTitle") ?: o.s("parentTitle"),
            parentId = o.s("parentRatingKey"),
            addedAt = o.l("addedAt")?.times(1000),
            resumeMs = o.l("viewOffset"),
            played = (o.i("viewCount") ?: 0) > 0,
        )
    }
}

object PlexAuth {
    data class Pin(val id: Long, val code: String)
    data class Connection(val uri: String, val local: Boolean, val relay: Boolean)
    data class Server(val name: String, val accessToken: String, val connections: List<Connection>)

    fun headers(deviceId: String) = mapOf(
        "X-Plex-Product" to "Prism Player",
        "X-Plex-Version" to "0.1.0",
        "X-Plex-Client-Identifier" to deviceId,
        "X-Plex-Platform" to "Android",
        "X-Plex-Device" to "Android",
        "Accept" to "application/json",
    )

    suspend fun createPin(deviceId: String): Pin {
        val req = Request.Builder().url("https://plex.tv/api/v2/pins?strong=false")
            .post(ByteArray(0).toRequestBody())
            .apply { headers(deviceId).forEach { (k, v) -> header(k, v) } }.build()
        val o = Net.json.parseToJsonElement(Net.send(req)).obj() ?: error("Unexpected response from plex.tv")
        return Pin(o.l("id") ?: error("No PIN id"), o.s("code") ?: error("No PIN code"))
    }

    /** Returns the user's token once they have entered the code at plex.tv/link. */
    suspend fun checkPin(pin: Pin, deviceId: String): String? =
        Net.getJson("https://plex.tv/api/v2/pins/${pin.id}", headers(deviceId)).obj()?.s("authToken")?.takeIf { it.isNotBlank() }

    suspend fun servers(userToken: String, deviceId: String): List<Server> =
        Net.getJson(
            "https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1",
            headers(deviceId) + ("X-Plex-Token" to userToken),
        ).objects().filter { it.s("provides")?.contains("server") == true }.map { r ->
            Server(
                name = r.s("name") ?: "Plex",
                accessToken = r.s("accessToken") ?: userToken,
                connections = r.arr("connections").mapNotNull { c ->
                    c.s("uri")?.let { Connection(it, c.b("local") == true, c.b("relay") == true) }
                },
            )
        }

    /** Tries local connections first, then remote, then relay, and returns the first that answers. */
    suspend fun pickConnection(server: Server, deviceId: String): String? = withContext(Dispatchers.IO) {
        val quick = Net.client.newBuilder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build()
        val ordered = server.connections.sortedBy { (if (it.relay) 2 else if (it.local) 0 else 1) }
        ordered.firstOrNull { c ->
            runCatching {
                Net.send(
                    Request.Builder().url("${c.uri}/identity")
                        .apply { (headers(deviceId) + ("X-Plex-Token" to server.accessToken)).forEach { (k, v) -> header(k, v) } }
                        .build(),
                    quick,
                )
            }.isSuccess
        }?.uri
    }
}
