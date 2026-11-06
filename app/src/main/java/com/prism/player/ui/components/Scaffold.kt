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

enum class Tab(val label: String, val icon: ImageVector, val route: String) {
    HOME("Home", Icons.Filled.Home, "home"),
    MOVIES("Movies", Icons.Filled.Movie, "movies"),
    SHOWS("TV Shows", Icons.Filled.Tv, "shows"),
    SEARCH("Search", Icons.Filled.Search, "search"),
    SETTINGS("Settings", Icons.Filled.Settings, "settings"),
}

/** Top-level screen: blurred artwork backdrop + floating glass tab bar + content. */
@Composable
fun TopLevelScaffold(
    selected: Tab,
    backdrop: String?,
    onTab: (Tab) -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(backdrop)
        Column(Modifier.fillMaxSize()) {
            GlassTabBar(selected, onTab)
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
fun GlassTabBar(selected: Tab, onTab: (Tab) -> Unit) {
    val tv = LocalIsTv.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = if (tv) 24.dp else 10.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier
                .glass(RoundedCornerShape(50))
                .horizontalScroll(rememberScrollState())
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Tab.entries.forEach { tab -> TabPill(tab, tab == selected, showLabel = tv || tab == selected) { onTab(tab) } }
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
fun MessageState(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
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
                GlassButton(action, primary = true, onClick = onAction)
            }
        }
    }
}

fun formatDuration(ms: Long): String {
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
