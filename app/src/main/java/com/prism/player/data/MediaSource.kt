package com.prism.player.data

interface MediaSource {
    val config: SourceConfig

    suspend fun movies(): List<MediaItem>
    suspend fun shows(): List<MediaItem>

    /** Seasons of a show, or episodes of a season. */
    suspend fun children(item: MediaItem): List<MediaItem>
    suspend fun item(nativeId: String): MediaItem?
    suspend fun playback(item: MediaItem): Playback

    suspend fun continueWatching(): List<MediaItem> = emptyList()

    /** Sends playback state back to the server so progress syncs with other apps. */
    suspend fun report(item: MediaItem, event: PlaybackEvent, positionMs: Long, durationMs: Long) {}

    suspend fun refresh() {}
}
