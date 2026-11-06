package com.prism.player.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.prism.player.BuildConfig
import com.prism.player.data.Net
import com.prism.player.data.l
import com.prism.player.data.arr
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
 * Checks GitHub Releases for a newer APK, downloads it and hands it to the system
 * installer. Updates only install when the new APK is signed with the same key.
 */
object UpdateManager {
    data class ReleaseInfo(val version: String, val code: Int, val notes: String, val apkUrl: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: ReleaseInfo) : State
        data class Downloading(val info: ReleaseInfo, val progress: Float) : State
        data class Installing(val info: ReleaseInfo) : State
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
        if (_state.value is State.Downloading || _state.value is State.Installing) return
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

    suspend fun downloadAndInstall(context: Context, info: ReleaseInfo) {
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            val opened = runCatching {
                app.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (opened) {
                _state.value = State.Failed("Allow Prism Player to install apps, then press Update again.", info)
                return
            }
            // Some TVs have no such settings screen; the installer will ask instead.
        }
        try {
            val file = download(app, info)
            _state.value = State.Installing(info)
            install(app, file)
        } catch (e: Exception) {
            _state.value = State.Failed("Update failed: ${e.message ?: e.javaClass.simpleName}", info)
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

    private suspend fun install(context: Context, file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallReceiver::class.java).setPackage(context.packageName), flags,
            )
            session.commit(callback.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val info = (_state.value as? State.Installing)?.info
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> State.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> State.Failed("Update cancelled.", info)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                State.Failed(
                    "This version can't be updated in place because it was signed with a different key. " +
                        "Uninstall Prism Player once, then install the new version from the Releases page. Updates after that work from inside the app.",
                    info,
                )
            else -> State.Failed("Update failed: ${message ?: "status $status"}", info)
        }
    }
}
