package com.prism.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prism.player.data.MediaItem
import com.prism.player.data.MediaKind
import com.prism.player.data.Progress
import com.prism.player.ui.appContainer
import com.prism.player.ui.components.GlassBackdrop
import com.prism.player.ui.components.GlassButton
import com.prism.player.ui.components.GlassChip
import com.prism.player.ui.components.GlassIconButton
import com.prism.player.ui.components.LoadingState
import com.prism.player.ui.components.MessageState
import com.prism.player.ui.components.ProgressLine
import com.prism.player.ui.components.formatClock
import com.prism.player.ui.components.formatDuration
import com.prism.player.ui.components.glass
import com.prism.player.ui.components.glassClickable
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onPlay: (MediaItem, Long) -> Unit,
) {
    val container = appContainer()
    val repo = container.library
    val progress by container.progress.all.collectAsState()
    val scope = rememberCoroutineScope()
    val tv = LocalIsTv.current
    BackHandler(onBack = onBack)

    val itemState by produceState<UiState<MediaItem>>(UiState.Loading, id) {
        value = runCatching { repo.item(id) }.fold(
            { it?.let { i -> UiState.Ready(i) } ?: UiState.Error("This title is no longer available.") },
            { UiState.Error(it.message ?: "Couldn't load") },
        )
    }

    Box(Modifier.fillMaxSize()) {
        val item = (itemState as? UiState.Ready)?.data
        GlassBackdrop(item?.backdropUrl ?: item?.posterUrl)
        when (val s = itemState) {
            UiState.Loading -> LoadingState("Loading…")
            is UiState.Error -> MessageState("Unavailable", s.message, "Back", onBack)
            is UiState.Ready -> {
                val it = s.data
                // Seasons for shows, episodes for seasons.
                val children by produceState<List<MediaItem>?>(null, it.id) {
                    value = if (it.kind == MediaKind.SHOW || it.kind == MediaKind.SEASON)
                        runCatching { repo.children(it) }.getOrDefault(emptyList()) else emptyList()
                }
                var seasonIndex by rememberSaveable(it.id) { mutableStateOf(0) }
                val seasons = if (it.kind == MediaKind.SHOW) children.orEmpty() else emptyList()
                val selectedSeason = seasons.getOrNull(seasonIndex)
                val episodes by produceState<List<MediaItem>?>(null, selectedSeason?.id, children) {
                    value = when {
                        it.kind == MediaKind.SEASON -> children
                        selectedSeason != null -> runCatching { repo.children(selectedSeason) }.getOrDefault(emptyList())
                        else -> emptyList()
                    }
                }
                LaunchedEffect(seasons) {
                    // Jump to the first season that still has unwatched episodes.
                    if (seasons.isNotEmpty() && seasonIndex == 0) {
                        val idx = seasons.indexOfFirst { s2 -> !(progress[s2.id]?.finished ?: s2.played) }
                        if (idx > 0) seasonIndex = idx
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(
                        start = if (tv) 56.dp else 16.dp, end = if (tv) 56.dp else 16.dp, bottom = 48.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                ) {
                    item(key = "back") {
                        Box(Modifier.padding(top = if (tv) 24.dp else 8.dp)) {
                            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                        }
                    }
                    item(key = "header") {
                        Header(
                            item = it,
                            progress = progress[it.id],
                            episodes = episodes.orEmpty(),
                            episodeProgress = progress,
                            onPlay = onPlay,
                            onToggleWatched = { watched ->
                                scope.launch { container.progress.setWatched(it, watched) }
                            },
                        )
                    }
                    if (seasons.size > 1) {
                        item(key = "seasons") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(seasons.size) { i ->
                                    GlassChip(seasons[i].title, i == seasonIndex) { seasonIndex = i }
                                }
                            }
                        }
                    }
                    val eps = episodes
                    if (it.kind == MediaKind.SHOW || it.kind == MediaKind.SEASON) {
                        if (eps == null) {
                            item(key = "eps-loading") { Text("Loading episodes…", color = Palette.TextSecondary) }
                        } else {
                            items(eps, key = { e -> e.id }) { ep ->
                                EpisodeRow(ep, progress[ep.id]) { p ->
                                    onPlay(ep, p?.takeIf { pr -> pr.inProgress }?.positionMs ?: ep.resumeMs ?: 0)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    item: MediaItem,
    progress: Progress?,
    episodes: List<MediaItem>,
    episodeProgress: Map<String, Progress>,
    onPlay: (MediaItem, Long) -> Unit,
    onToggleWatched: (Boolean) -> Unit,
) {
    val tv = LocalIsTv.current
    val posterWidth = if (tv) 220.dp else 120.dp
    Row(horizontalArrangement = Arrangement.spacedBy(if (tv) 36.dp else 16.dp)) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(posterWidth)
                .aspectRatio(if (item.kind == MediaKind.EPISODE) 16f / 9f else 2f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1A2033))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
        )
        Column(Modifier.weight(1f)) {
            item.seriesTitle?.takeIf { item.kind != MediaKind.SHOW }?.let {
                Text(it.uppercase(), color = Palette.Accent, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                item.title,
                style = if (tv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            val meta = listOfNotNull(
                item.episodeLabel,
                item.year?.toString(),
                item.runtimeMs?.takeIf { it > 0 }?.let { formatDuration(it) },
                item.rating?.let { "★ %.1f".format(it) },
            ).joinToString("  ·  ")
            if (meta.isNotBlank()) Text(meta, color = Palette.TextSecondary, style = MaterialTheme.typography.labelLarge)
            if (item.genres.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.genres.take(4).forEach { g ->
                        Text(
                            g, color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.glass(RoundedCornerShape(50), 0.8f).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            val resume = progress?.takeIf { it.inProgress }?.positionMs ?: item.resumeMs?.takeIf { it > 30_000 }
            val watched = progress?.finished ?: item.played
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.isPlayable) {
                    if (resume != null) {
                        GlassButton("Resume ${formatClock(resume)}", Icons.Filled.PlayArrow, primary = true) { onPlay(item, resume) }
                        GlassButton("From start", Icons.Filled.Replay) { onPlay(item, 0) }
                    } else {
                        GlassButton("Play", Icons.Filled.PlayArrow, primary = true) { onPlay(item, 0) }
                    }
                    GlassButton(
                        if (watched) "Watched" else "Mark watched",
                        if (watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    ) { onToggleWatched(!watched) }
                } else if (episodes.isNotEmpty()) {
                    val next = episodes.firstOrNull { e -> episodeProgress[e.id]?.inProgress == true || e.resumeMs != null }
                        ?: episodes.firstOrNull { e -> !(episodeProgress[e.id]?.finished ?: e.played) }
                        ?: episodes.first()
                    val nextResume = episodeProgress[next.id]?.takeIf { it.inProgress }?.positionMs ?: next.resumeMs ?: 0
                    GlassButton(
                        "Play ${next.episodeLabel ?: next.title}", Icons.Filled.PlayArrow, primary = true,
                    ) { onPlay(next, nextResume) }
                }
            }
            if (progress?.inProgress == true) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(260.dp)) { ProgressLine(progress.fraction) }
            }
            item.overview?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Palette.TextSecondary, style = MaterialTheme.typography.bodyLarge, maxLines = if (tv) 6 else 10, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: MediaItem, progress: Progress?, onClick: (Progress?) -> Unit) {
    val tv = LocalIsTv.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .glassClickable(interaction, focusScale = 1.02f) { onClick(progress) }
            .glass(shape, highlighted = focused)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(if (tv) 240.dp else 140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF26304D), Color(0xFF141A2B))))
        ) {
            AsyncImage(
                model = ep.posterUrl ?: ep.backdropUrl, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            val fraction = progress?.takeIf { it.inProgress }?.fraction
            if (fraction != null) ProgressLine(fraction, Modifier.align(Alignment.BottomCenter))
            if (progress?.finished ?: ep.played) {
                Icon(
                    Icons.Filled.CheckCircle, "Watched", tint = Palette.Accent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(ep.episodeNumber?.let { "Episode $it" }, ep.runtimeMs?.takeIf { it > 0 }?.let { formatDuration(it) })
                    .joinToString("  ·  "),
                color = Palette.TextTertiary, style = MaterialTheme.typography.labelMedium,
            )
            Text(ep.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ep.overview?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = Palette.TextSecondary, maxLines = if (tv) 3 else 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (focused) Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
    }
}
