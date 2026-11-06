package com.prism.player.data

import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind { MOVIE, SHOW, SEASON, EPISODE }

/**
 * A movie, show, season or episode coming from any configured source.
 * [id] is globally unique: "<sourceId>|<nativeId>".
 */
@Serializable
data class MediaItem(
    val id: String,
    val sourceId: String,
    val nativeId: String,
    val kind: MediaKind,
    val title: String,
    val overview: String? = null,
    val year: Int? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: Double? = null,
    val runtimeMs: Long? = null,
    val genres: List<String> = emptyList(),
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val seriesTitle: String? = null,
    /** Native id of the parent (series for a season, season/series for an episode). */
    val parentId: String? = null,
    val addedAt: Long? = null,
    /** Resume position reported by the server (Jellyfin / Plex). */
    val resumeMs: Long? = null,
    val played: Boolean = false,
) {
    val isPlayable: Boolean get() = kind == MediaKind.MOVIE || kind == MediaKind.EPISODE

    val episodeLabel: String?
        get() = if (kind == MediaKind.EPISODE && seasonNumber != null && episodeNumber != null)
            "S$seasonNumber · E$episodeNumber" else null

    companion object {
        fun makeId(sourceId: String, nativeId: String) = "$sourceId|$nativeId"
        fun sourceIdOf(id: String) = id.substringBefore('|')
        fun nativeIdOf(id: String) = id.substringAfter('|')
    }
}

data class ExternalSubtitle(
    val uri: String,
    val mimeType: String,
    val language: String? = null,
    val label: String? = null,
)

data class Playback(
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<ExternalSubtitle> = emptyList(),
    /** Set for smb:// URIs so the player can open them with the right credentials. */
    val smbConfig: SourceConfig? = null,
)

data class Shelf(
    val title: String,
    val items: List<MediaItem>,
    val landscape: Boolean = false,
)

enum class PlaybackEvent { START, PROGRESS, STOP }

@Serializable
enum class SourceType(val label: String) {
    JELLYFIN("Jellyfin / Emby"),
    PLEX("Plex"),
    SMB("Network share (SMB)"),
    WEBDAV("WebDAV"),
    DEMO("Demo library"),
}

@Serializable
data class SourceConfig(
    val id: String,
    val type: SourceType,
    val name: String,
    /** Server base URL (Jellyfin/Plex/WebDAV) or smb://host/share/path/ */
    val url: String,
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val token: String = "",
    val userId: String = "",
)

@Serializable
data class PlayerPrefs(
    val preferredAudioLanguage: String = "",
    val preferredSubtitleLanguage: String = "",
    val subtitlesByDefault: Boolean = false,
    val subtitleScale: Float = 1f,
    val skipBackSeconds: Int = 10,
    val skipForwardSeconds: Int = 30,
    val autoResume: Boolean = true,
    val autoPlayNext: Boolean = true,
)

@Serializable
data class AppSettings(
    val sources: List<SourceConfig> = emptyList(),
    val tmdbKey: String = "",
    val player: PlayerPrefs = PlayerPrefs(),
    val deviceId: String = "",
)
