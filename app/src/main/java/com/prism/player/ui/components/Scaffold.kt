package com.prism.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import com.prism.player.data.AppSettings
import com.prism.player.ui.appContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.Palette

/** Opens the "Who's watching?" screen; provided by AppNavigation. */
val LocalOpenProfiles = staticCompositionLocalOf<() -> Unit> { {} }

enum class Tab(val label: String, val icon: ImageVector, val route: String) {
    HOME("Home", Icons.Filled.Home, "home"),
    MOVIES("Movies", Icons.Filled.Movie, "movies"),
    SHOWS("TV Shows", Icons.Filled.Tv, "shows"),
    SEARCH("Search", Icons.Filled.Search, "search"),
    SETTINGS("Settings", Icons.Filled.Settings, "settings"),
}

/**
 * Top-level screen: blurred artwork backdrop, content, and a floating glass tab bar drawn
 * over it (top on TV, bottom on phones). [fullBleed] lets content run edge to edge under the bar.
 */
@Composable
fun TopLevelScaffold(
    selected: Tab,
    backdrop: String?,
    onTab: (Tab) -> Unit,
    fullBleed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tv = LocalIsTv.current
    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(backdrop)
        Box(
            Modifier.fillMaxSize().then(
                when {
                    fullBleed -> Modifier
                    tv -> Modifier.padding(top = 96.dp)
                    else -> Modifier.statusBarsPadding().padding(top = 8.dp)
                }
            )
        ) { content() }
        GlassTabBar(selected, onTab, Modifier.align(if (tv) Alignment.TopCenter else Alignment.BottomCenter))
    }
}

@Composable
fun GlassTabBar(selected: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val tv = LocalIsTv.current
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (tv) Modifier.statusBarsPadding().padding(top = 24.dp, bottom = 8.dp)
                else Modifier.navigationBarsPadding().padding(bottom = 14.dp)
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier
                .glass(RoundedCornerShape(50), strength = 1.3f)
                .background(Color(0xFF0B0E18).copy(alpha = 0.55f))
                .horizontalScroll(rememberScrollState())
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab -> TabPill(tab, tab == selected, showLabel = tv || tab == selected) { onTab(tab) } }
            ProfilePill()
        }
    }
}

/** Current profile's avatar at the end of the tab bar; tap to switch profiles. */
@Composable
private fun ProfilePill() {
    val settings by appContainer().settings.settings.collectAsState(initial = AppSettings())
    val profile = settings.activeProfile ?: return
    val open = LocalOpenProfiles.current
    val tv = LocalIsTv.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .glassClickable(interaction, focusScale = 1.06f, onClick = open)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.Transparent)
            .padding(start = 6.dp, end = if (tv) 14.dp else 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(profile.name, profile.avatar, 30.dp, focused = focused)
        if (tv) {
            Spacer(Modifier.width(8.dp))
            Text(
                profile.name, maxLines = 1,
                color = if (focused) Color(0xFF0B1020) else Palette.TextSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun TabPill(tab: Tab, selected: Boolean, showLabel: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    val fg = when {
        focused -> Color(0xFF0B1020)
        selected -> Color.White
        else -> Palette.TextSecondary
    }
    Row(
        Modifier
            .glassClickable(interaction, focusScale = 1.04f, onClick = onClick)
            .clip(shape)
            .background(
                when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = 0.22f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = fg, modifier = Modifier.size(20.dp))
        if (showLabel) {
            Spacer(Modifier.width(8.dp))
            Text(tab.label, color = fg, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun LoadingState(message: String = "Loading your library…") {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(message, color = Palette.TextSecondary)
    }
}

@Composable
fun MessageState(
    title: String,
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
    secondaryAction: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.glass().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(Palette.Accent, Palette.AccentAlt)))
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Palette.TextSecondary, textAlign = TextAlign.Center)
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(action, primary = true, onClick = onAction)
                    if (secondaryAction != null) GlassButton(secondaryAction, onClick = onSecondaryAction)
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms < 60_000) return "${(ms / 1000).coerceAtLeast(1)}s"
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
