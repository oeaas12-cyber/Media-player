package com.prism.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.player.data.SourceConfig
import com.prism.player.data.SourceType
import com.prism.player.data.sources.JellyfinSource
import com.prism.player.data.sources.PlexAuth
import com.prism.player.data.sources.SmbContexts
import com.prism.player.data.sources.SmbSource
import com.prism.player.data.sources.WebDavSource
import com.prism.player.ui.appContainer
import com.prism.player.ui.components.GlassBackdrop
import com.prism.player.ui.components.GlassButton
import com.prism.player.ui.components.GlassChip
import com.prism.player.ui.components.GlassIconButton
import com.prism.player.ui.components.glass
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AddSourceScreen(type: SourceType, onDone: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val tv = LocalIsTv.current
    BackHandler(onBack = onDone)

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save(cfg: SourceConfig) = scope.launch {
        container.settings.update { it.copy(sources = it.sources.filterNot { s -> s.id == cfg.id } + cfg) }
        onDone()
    }

    /** Runs a connection attempt with spinner + error handling. */
    fun attempt(block: suspend () -> SourceConfig?) {
        if (busy) return
        scope.launch {
            busy = true; error = null
            runCatching { block() }
                .onSuccess { it?.let(::save) }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(null)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (tv) 120.dp else 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onDone)
                Text("Add ${type.label}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
            Column(
                Modifier.glass().padding(24.dp).widthIn(max = 720.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (type) {
                    SourceType.JELLYFIN -> JellyfinForm(busy) { url, user, pass ->
                        attempt { JellyfinSource.login(url, user, pass, container.settings.current().deviceId) }
                    }
                    SourceType.PLEX -> PlexForm(busy, ::attempt)
                    SourceType.SMB -> SmbForm(busy) { name, host, path, user, pass, domain ->
                        attempt {
                            val cfg = SourceConfig(
                                id = UUID.randomUUID().toString(), type = SourceType.SMB,
                                name = name.ifBlank { "$host/$path".trim('/') },
                                url = SmbContexts.buildUrl(host, path),
                                username = user, password = pass, domain = domain,
                            )
                            SmbSource(cfg, container.appContext, container.tmdb).list("")
                            cfg
                        }
                    }
                    SourceType.DEMO -> {
                        Text(
                            "Adds a small library of open-licensed Blender films and sample clips streamed from the internet, " +
                                "so you can try Prism without a server.",
                            color = Palette.TextSecondary,
                        )
                        GlassButton("Add demo library", primary = true) {
                            attempt { demoSource() }
                        }
                    }
                    SourceType.WEBDAV -> WebDavForm(busy) { name, url, user, pass ->
                        attempt {
                            val cfg = SourceConfig(
                                id = UUID.randomUUID().toString(), type = SourceType.WEBDAV,
                                name = name.ifBlank { url.substringAfter("://").substringBefore('/') },
                                url = JellyfinSource.normaliseUrl(url), username = user, password = pass,
                            )
                            WebDavSource(cfg, container.appContext, container.tmdb).list("")
                            cfg
                        }
                    }
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Text("Connecting…", color = Palette.TextSecondary)
                    }
                }
                error?.let { Text("Couldn't connect: $it", color = Palette.Danger) }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    password: Boolean = false,
    hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (hint != null) { { Text(hint) } } else null,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun JellyfinForm(busy: Boolean, onSubmit: (String, String, String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Text("Sign in to your Jellyfin or Emby server. Use the same address and account as in the server's web page.", color = Palette.TextSecondary)
    Field("Server address", url, { url = it }, hint = "http://192.168.1.10:8096", keyboard = KeyboardType.Uri)
    Field("Username", user, { user = it })
    Field("Password", pass, { pass = it }, password = true)
    GlassButton("Sign in", primary = true) { if (!busy && url.isNotBlank() && user.isNotBlank()) onSubmit(url.trim(), user.trim(), pass) }
}

@Composable
private fun PlexForm(busy: Boolean, attempt: (suspend () -> SourceConfig?) -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var manual by rememberSaveable { mutableStateOf(false) }
    var pin by remember { mutableStateOf<PlexAuth.Pin?>(null) }
    var userToken by remember { mutableStateOf<String?>(null) }
    var servers by remember { mutableStateOf<List<PlexAuth.Server>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassChip("Sign in with code", !manual) { manual = false }
        GlassChip("Server address + token", manual) { manual = true }
    }

    if (manual) {
        var url by rememberSaveable { mutableStateOf("") }
        var token by remember { mutableStateOf("") }
        Field("Server address", url, { url = it }, hint = "http://192.168.1.10:32400", keyboard = KeyboardType.Uri)
        Field("X-Plex-Token", token, { token = it.trim() }, password = true)
        GlassButton("Connect", primary = true) {
            if (busy || url.isBlank() || token.isBlank()) return@GlassButton
            attempt {
                val base = JellyfinSource.normaliseUrl(url)
                val cfg = SourceConfig(UUID.randomUUID().toString(), SourceType.PLEX, "Plex", base, token = token)
                com.prism.player.data.sources.PlexSource(cfg, container.settings.current().deviceId).movies()
                cfg
            }
        }
        return
    }

    LaunchedEffect(pin) {
        val p = pin ?: return@LaunchedEffect
        val deviceId = container.settings.current().deviceId
        repeat(150) {
            delay(2_000)
            val t = runCatching { PlexAuth.checkPin(p, deviceId) }.getOrNull()
            if (t != null) {
                userToken = t
                message = "Signed in. Finding your servers…"
                servers = runCatching { PlexAuth.servers(t, deviceId) }.getOrElse { e -> message = e.message; emptyList() }
                if (servers.isEmpty() && message?.startsWith("Signed") == true) message = "No Plex Media Servers found on this account."
                else if (servers.isNotEmpty()) message = "Choose a server:"
                pin = null
                return@LaunchedEffect
            }
        }
        message = "The code expired. Try again."
        pin = null
    }

    when {
        userToken != null -> {
            message?.let { Text(it, color = Palette.TextSecondary) }
            servers.forEach { server ->
                GlassButton(server.name, primary = false) {
                    attempt {
                        val deviceId = container.settings.current().deviceId
                        val uri = PlexAuth.pickConnection(server, deviceId)
                            ?: error("Couldn't reach ${server.name}. Is it online and on the same network?")
                        SourceConfig(UUID.randomUUID().toString(), SourceType.PLEX, server.name, uri, token = server.accessToken)
                    }
                }
            }
        }
        pin != null -> {
            Text("On your phone or computer, go to", color = Palette.TextSecondary)
            Text("plex.tv/link", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("and enter this code:", color = Palette.TextSecondary)
            Text(
                pin!!.code,
                fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp, color = Palette.Accent,
                modifier = Modifier.glass().padding(horizontal = 28.dp, vertical = 12.dp),
            )
            Text("Waiting for you to approve…", color = Palette.TextTertiary)
        }
        else -> {
            Text(
                "Sign in with your Plex account. You'll get a short code to enter at plex.tv/link.",
                color = Palette.TextSecondary,
            )
            message?.let { Text(it, color = Palette.Danger) }
            GlassButton("Get sign-in code", primary = true) {
                scope.launch {
                    message = null
                    runCatching { PlexAuth.createPin(container.settings.current().deviceId) }
                        .onSuccess { pin = it }
                        .onFailure { message = it.message }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SmbForm(busy: Boolean, onSubmit: (String, String, String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var path by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var domain by rememberSaveable { mutableStateOf("") }
    Text("Connect to a Windows, macOS or NAS share (SMB 2/3).", color = Palette.TextSecondary)
    Field("Name (optional)", name, { name = it }, hint = "Living room NAS")
    Field("Host or IP", host, { host = it }, hint = "192.168.1.20", keyboard = KeyboardType.Uri)
    Field("Share and folder", path, { path = it }, hint = "Media/Movies")
    Field("Username (blank for guest)", user, { user = it })
    Field("Password", pass, { pass = it }, password = true)
    Field("Domain / workgroup (optional)", domain, { domain = it })
    GlassButton("Connect", primary = true) {
        if (!busy && host.isNotBlank() && path.isNotBlank()) onSubmit(name, host, path, user, pass, domain)
    }
}

@Composable
private fun WebDavForm(busy: Boolean, onSubmit: (String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Text("Connect to a WebDAV folder (Nextcloud, NAS, rclone serve…).", color = Palette.TextSecondary)
    Field("Name (optional)", name, { name = it })
    Field("Folder URL", url, { url = it }, hint = "https://cloud.example.com/remote.php/dav/files/me/Movies", keyboard = KeyboardType.Uri)
    Field("Username", user, { user = it })
    Field("Password", pass, { pass = it }, password = true)
    GlassButton("Connect", primary = true) { if (!busy && url.isNotBlank()) onSubmit(name, url, user, pass) }
}

fun demoSource() = SourceConfig(
    id = "demo",
    type = SourceType.DEMO,
    name = "Demo library",
    url = "Blender open movies · sample clips",
)
