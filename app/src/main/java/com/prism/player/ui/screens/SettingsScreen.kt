package com.prism.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.prism.player.data.AppSettings
import com.prism.player.data.PlayerPrefs
import com.prism.player.data.SourceConfig
import com.prism.player.data.SourceType
import com.prism.player.ui.appContainer
import com.prism.player.ui.components.Avatar
import com.prism.player.ui.components.GlassButton
import com.prism.player.ui.components.LocalOpenProfiles
import com.prism.player.ui.components.GlassChip
import com.prism.player.ui.components.GlassIconButton
import com.prism.player.ui.components.Tab
import com.prism.player.ui.components.TopLevelScaffold
import com.prism.player.ui.components.glass
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.Palette
import com.prism.player.update.UpdateCard
import com.prism.player.update.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onTab: (Tab) -> Unit, onAddSource: (SourceType) -> Unit, onEditProfile: (String) -> Unit = {}) {
    val container = appContainer()
    val settings by container.settings.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val tv = LocalIsTv.current
    var status by remember { mutableStateOf<String?>(null) }
    fun updatePlayer(t: (PlayerPrefs) -> PlayerPrefs) = scope.launch {
        container.settings.update { it.copy(player = t(it.player)) }
    }

    TopLevelScaffold(Tab.SETTINGS, null, onTab) {
        LazyColumn(
            contentPadding = PaddingValues(start = if (tv) 96.dp else 16.dp, end = if (tv) 96.dp else 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SectionTitle("Profiles") }
            item {
                val openProfiles = LocalOpenProfiles.current
                Column(Modifier.glass().padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Each profile has its own Continue Watching and watched history. Kids profiles only show family titles.",
                        color = Palette.TextSecondary,
                    )
                    settings.profiles.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(p.name, p.avatar, 40.dp)
                            Column(Modifier.weight(1f)) {
                                Text(p.name + if (p.id == settings.activeProfileId) "  ·  current" else "", color = Palette.TextPrimary)
                                if (p.isKids) Text("Kids", color = Palette.Accent, style = MaterialTheme.typography.labelMedium)
                            }
                            GlassButton("Edit") { onEditProfile(p.id) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Add profile", Icons.Filled.Add) { onEditProfile("new") }
                        if (settings.profiles.size > 1) GlassButton("Switch profile", onClick = openProfiles)
                    }
                }
            }

            item { SectionTitle("Sources") }
            if (settings.sources.isEmpty()) {
                item { Text("No sources yet. Add one below.", color = Palette.TextSecondary) }
            }
            items(settings.sources, key = { it.id }) { src ->
                SourceRow(
                    src,
                    onRefresh = {
                        scope.launch {
                            status = "Refreshing ${src.name}…"
                            container.library.refresh(src.id)
                            runCatching { container.library.movies(); container.library.shows() }
                            status = "${src.name} refreshed"
                        }
                    },
                    onRemove = {
                        scope.launch { container.settings.update { s -> s.copy(sources = s.sources.filterNot { it.id == src.id }) } }
                    },
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(SourceType.entries) { t -> GlassButton(t.label, Icons.Filled.Add) { onAddSource(t) } }
                }
            }
            status?.let { item { Text(it, color = Palette.Accent) } }

            item { SectionTitle("Metadata") }
            item {
                var key by remember(settings.tmdbKey) { mutableStateOf(settings.tmdbKey) }
                Column(Modifier.glass().padding(20.dp).fillMaxWidth()) {
                    Text(
                        "Artwork and descriptions for SMB / WebDAV files come from The Movie Database. " +
                            "Paste a free TMDB API key or read access token (themoviedb.org → Settings → API).",
                        color = Palette.TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = key, onValueChange = { key = it.trim() }, singleLine = true,
                            label = { Text("TMDB API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton("Save", primary = true) {
                            scope.launch {
                                container.settings.update { it.copy(tmdbKey = key) }
                                status = "TMDB key saved. Refresh file sources to fetch artwork."
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Playback") }
            item {
                val p = settings.player
                Column(Modifier.glass().padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ChoiceRow("Resume where you left off", listOf("On" to true, "Off" to false), p.autoResume) { v -> updatePlayer { it.copy(autoResume = v) } }
                    ChoiceRow("Play next episode automatically", listOf("On" to true, "Off" to false), p.autoPlayNext) { v -> updatePlayer { it.copy(autoPlayNext = v) } }
                    ChoiceRow("Subtitles", listOf("Off by default" to false, "On by default" to true), p.subtitlesByDefault) { v -> updatePlayer { it.copy(subtitlesByDefault = v) } }
                    ChoiceRow("Preferred audio language", LANGS, p.preferredAudioLanguage) { v -> updatePlayer { it.copy(preferredAudioLanguage = v) } }
                    ChoiceRow("Preferred subtitle language", LANGS, p.preferredSubtitleLanguage) { v -> updatePlayer { it.copy(preferredSubtitleLanguage = v) } }
                    ChoiceRow("Subtitle size", listOf("Small" to 0.8f, "Normal" to 1f, "Large" to 1.3f, "Huge" to 1.7f), p.subtitleScale) { v -> updatePlayer { it.copy(subtitleScale = v) } }
                    ChoiceRow("Skip back", listOf("5s" to 5, "10s" to 10, "15s" to 15, "30s" to 30), p.skipBackSeconds) { v -> updatePlayer { it.copy(skipBackSeconds = v) } }
                    ChoiceRow("Skip forward", listOf("10s" to 10, "15s" to 15, "30s" to 30, "60s" to 60), p.skipForwardSeconds) { v -> updatePlayer { it.copy(skipForwardSeconds = v) } }
                }
            }
            item { SectionTitle("Updates") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Version ${UpdateManager.currentVersion}", color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                        GlassButton("Check for updates", Icons.Filled.Refresh) {
                            scope.launch { UpdateManager.check(force = true) }
                        }
                    }
                    UpdateCard(showUpToDate = true)
                }
            }
            item {
                Text(
                    "Prism Player ${UpdateManager.currentVersion} · Plays media from servers and shares you own.",
                    color = Palette.TextTertiary, modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

private val LANGS = listOf(
    "Device default" to "", "English" to "en", "Arabic" to "ar", "French" to "fr", "Spanish" to "es",
    "German" to "de", "Italian" to "it", "Portuguese" to "pt", "Turkish" to "tr", "Hindi" to "hi",
    "Japanese" to "ja", "Korean" to "ko", "Chinese" to "zh",
)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(title, color = Palette.TextSecondary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { (label, value) -> GlassChip(label, value == selected) { onSelect(value) } }
        }
    }
}

@Composable
private fun SourceRow(src: SourceConfig, onRefresh: () -> Unit, onRemove: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().glass().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(src.name, color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text("${src.type.label} · ${src.url}", color = Palette.TextTertiary, maxLines = 1)
        }
        GlassIconButton(Icons.Filled.Refresh, "Refresh", onClick = onRefresh)
        if (confirm) {
            GlassButton("Remove?", primary = true, modifier = Modifier.widthIn(min = 100.dp)) { onRemove() }
        } else {
            GlassIconButton(Icons.Filled.Delete, "Remove") { confirm = true }
        }
    }
}
