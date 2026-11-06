package com.prism.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

@Serializable
data class Progress(
    val item: MediaItem,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val finished: Boolean = false,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val inProgress: Boolean get() = !finished && positionMs > 30_000
}

/** Local watch history: resume points and watched flags, shared by every source. */
class ProgressStore(context: Context) {
    private val file = File(context.filesDir, "progress.json")
    private val mutex = Mutex()
    private val serializer = MapSerializer(String.serializer(), Progress.serializer())
    private val _all = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val all: StateFlow<Map<String, Progress>> = _all
    private var loaded = false

    suspend fun load() = mutex.withLock { ensureLoaded() }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _all.value = withContext(Dispatchers.IO) {
            runCatching { Net.json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())
        }
        loaded = true
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        runCatching { file.writeText(Net.json.encodeToString(serializer, _all.value)) }
    }

    fun get(id: String): Progress? = _all.value[id]

    suspend fun save(item: MediaItem, positionMs: Long, durationMs: Long) = mutex.withLock {
        ensureLoaded()
        val finished = durationMs > 0 && positionMs >= durationMs * 0.92
        _all.value = _all.value + (item.id to Progress(item, if (finished) 0 else positionMs, durationMs, System.currentTimeMillis(), finished))
        persist()
    }

    suspend fun setWatched(item: MediaItem, watched: Boolean) = mutex.withLock {
        ensureLoaded()
        _all.value = if (watched) {
            _all.value + (item.id to Progress(item, 0, item.runtimeMs ?: 0, System.currentTimeMillis(), true))
        } else {
            _all.value - item.id
        }
        persist()
    }

    fun continueWatching(): List<Progress> =
        _all.value.values.filter { it.inProgress }.sortedByDescending { it.updatedAt }
}
