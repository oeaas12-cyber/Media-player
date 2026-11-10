package com.prism.player.update

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.prism.player.BuildConfig
import com.prism.player.data.Net
import com.prism.player.data.arr
import com.prism.player.data.l
import com.prism.player.data.obj
import com.prism.player.data.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Checks GitHub Releases for a newer APK, downloads it, then hands it to Android's own
 * installer from the foreground. (Launching the installer from a background receiver is
 * silently blocked on Android 10+, which made earlier updates look successful while nothing
 * installed.) Updates only install when the APK is signed with the same key.
 */
object UpdateManager {
    data class ReleaseInfo(val version: String, val code: Int, val notes: String, val apkUrl: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: ReleaseInfo) : State
        data class Downloading(val info: ReleaseInfo, val progress: Float) : State
        /** The APK is downloaded and Android's installer has been opened. */
        data class Installing(val info: ReleaseInfo, val file: File) : State
        data class Failed(val message: String, val info: ReleaseInfo?) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private var lastCheck = 0L

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** "v1.2.3" → 10203; used for both the app's versionCode and release tags. */
    fun versionCode(version: String): Int {
        val parts = version.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
        return (parts[0] * 10_000 + parts[1] * 100 + parts[2]).coerceAtLeast(1)
    }

    suspend fun check(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < 6 * 60 * 60 * 1000L) return
        if (_state.value is State.Downloading) return
        lastCheck = now
        _state.value = State.Checking
        _state.value = runCatching {
            val o = Net.getJson(
                "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest",
                mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "PrismPlayer/${BuildConfig.VERSION_NAME}"),
            ).obj() ?: error("Unexpected response")
            val tag = o.s("tag_name") ?: error("No release found")
            val apk = o.arr("assets").firstOrNull { it.s("name")?.endsWith(".apk") == true }
                ?: error("The latest release has no APK")
            val info = ReleaseInfo(
                version = tag.removePrefix("v"),
                code = versionCode(tag),
                notes = o.s("body").orEmpty().lineSequence()
                    .filterNot { it.startsWith("**Full Changelog") }.joinToString("\n").trim(),
                apkUrl = apk.s("browser_download_url") ?: error("No download link"),
                size = apk.l("size") ?: 0,
            )
            if (info.code > BuildConfig.VERSION_CODE) State.Available(info) else State.UpToDate
        }.getOrElse { State.Failed("Couldn't check for updates: ${it.message}", null) }
    }

    /** Downloads the APK (with progress) and opens Android's installer for it. */
    suspend fun downloadAndInstall(context: Context, info: ReleaseInfo) {
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (opened) {
                _state.value = State.Failed("Allow Prism Player to install apps on that screen, then come back and press Update again.", info)
                return
            }
            // Some TVs have no such settings screen; the installer will ask instead.
        }
        try {
            val file = download(app, info)
            _state.value = State.Installing(info, file)
            openInstaller(context, file)
        } catch (e: Exception) {
            _state.value = State.Failed("Update failed: ${e.message ?: e.javaClass.simpleName}", info)
        }
    }

    /** Re-opens the installer for an already-downloaded APK (if the user dismissed it). */
    fun openInstaller(context: Context, file: File) {
        val app = context.applicationContext
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        val activity = context.findActivity()
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private suspend fun download(context: Context, info: ReleaseInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "PrismPlayer-${info.version}.apk")
        _state.value = State.Downloading(info, 0f)
        Net.client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} while downloading")
            val body = r.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = done.toFloat() / total
                            if (p - lastReport >= 0.01f) {
                                lastReport = p
                                _state.value = State.Downloading(info, p)
                            }
                        }
                    }
                }
            }
        }
        file
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
