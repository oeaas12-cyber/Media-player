package com.prism.player.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.player.ui.appContainer
import com.prism.player.ui.components.GlassButton
import com.prism.player.ui.components.glass
import com.prism.player.ui.theme.Palette
import kotlinx.coroutines.launch

/** Glass card that offers, downloads and installs an update. Renders nothing when there's nothing to show. */
@Composable
fun UpdateCard(modifier: Modifier = Modifier, showUpToDate: Boolean = false, onDismiss: (() -> Unit)? = null) {
    val state by UpdateManager.state.collectAsState()
    val context = LocalContext.current
    val scope = appContainer().appScope
    fun start(info: UpdateManager.ReleaseInfo) = scope.launch { UpdateManager.downloadAndInstall(context, info) }
    val installed = UpdateManager.currentVersion

    val content: (@Composable () -> Unit)? = when (val s = state) {
        is UpdateManager.State.Available -> {
            {
                Title("Update available: ${s.info.version}")
                Text("Installed: $installed  →  New: ${s.info.version}", color = Palette.TextSecondary)
                if (s.info.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(s.info.notes, color = Palette.TextTertiary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Update now", Icons.Filled.SystemUpdate, primary = true) { start(s.info) }
                    if (onDismiss != null) GlassButton("Later", onClick = onDismiss)
                }
            }
        }
        is UpdateManager.State.Downloading -> {
            {
                Title("Downloading ${s.info.version}… ${(s.progress * 100).toInt()}%")
                Spacer(Modifier.height(10.dp))
                Bar(s.progress)
            }
        }
        is UpdateManager.State.Installing -> {
            {
                Title("Ready to install ${s.info.version}")
                Text(
                    "Android's installer should be open now. Tap Install there. " +
                        "If you closed it, open it again below.",
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                GlassButton("Open installer again", Icons.Filled.SystemUpdate, primary = true) {
                    UpdateManager.openInstaller(context, s.file)
                }
            }
        }
        is UpdateManager.State.Failed -> {
            {
                Text(s.message, color = Palette.Danger)
                if (s.info != null) {
                    Spacer(Modifier.height(10.dp))
                    GlassButton("Try again", primary = true) { start(s.info) }
                }
            }
        }
        UpdateManager.State.Checking -> if (showUpToDate) ({ Text("Checking for updates…", color = Palette.TextSecondary) }) else null
        UpdateManager.State.UpToDate -> if (showUpToDate) ({ Text("You're on the latest version ($installed).", color = Palette.TextSecondary) }) else null
        UpdateManager.State.Idle -> null
    }
    if (content != null) {
        Column(modifier.fillMaxWidth().glass().padding(20.dp)) { content() }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Bar(progress: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.2f))) {
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp)
                .background(Brush.horizontalGradient(listOf(Palette.Accent, Palette.AccentAlt)))
        )
    }
}
