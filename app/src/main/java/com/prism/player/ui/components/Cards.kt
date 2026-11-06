package com.prism.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prism.player.data.MediaItem
import com.prism.player.data.Progress
import com.prism.player.data.Shelf
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.Palette

@Composable
fun PosterCard(
    item: MediaItem,
    width: Dp,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
    progress: Progress? = null,
    subtitle: String? = null,
    onFocused: (MediaItem) -> Unit = {},
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused(item) }
    val shape = RoundedCornerShape(14.dp)
    val image = if (landscape) item.backdropUrl ?: item.posterUrl else item.posterUrl
    Column(modifier.width(width)) {
        Box(
            Modifier
                .glassClickable(interaction, focusScale = 1.08f, onClick = onClick)
                .fillMaxWidth()
                .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF26304D), Color(0xFF141A2B))))
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape,
                ),
        ) {
            Text(
                item.title,
                color = Palette.TextSecondary,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (landscape) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
                    )
                )
                Text(
                    item.seriesTitle?.let { s -> "$s · ${item.episodeLabel ?: ""}".trimEnd(' ', '·') } ?: item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 14.dp),
                )
            }
            val watched = progress?.finished == true || (progress == null && item.played)
            if (watched) {
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Watched",
                    tint = Palette.Accent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            val fraction = progress?.takeIf { it.inProgress }?.fraction
                ?: item.resumeMs?.let { r -> item.runtimeMs?.takeIf { it > 0 }?.let { (r.toFloat() / it).coerceIn(0f, 1f) } }
            if (fraction != null && fraction > 0f) ProgressLine(fraction, Modifier.align(Alignment.BottomCenter))
        }
        if (!landscape) {
            Spacer(Modifier.height(8.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) Color.White else Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = subtitle ?: item.year?.toString()
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.labelMedium, color = Palette.TextTertiary, maxLines = 1)
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                item.title, style = MaterialTheme.typography.labelMedium,
                color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.25f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(Palette.Accent, Palette.AccentAlt)))
        )
    }
}

@Composable
fun ShelfRow(
    shelf: Shelf,
    progress: Map<String, Progress>,
    onFocused: (MediaItem) -> Unit,
    onClick: (MediaItem) -> Unit,
) {
    val tv = LocalIsTv.current
    val width = when {
        shelf.landscape -> if (tv) 280.dp else 220.dp
        else -> if (tv) 150.dp else 112.dp
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            shelf.title,
            style = MaterialTheme.typography.titleLarge,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(start = if (tv) 48.dp else 20.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = if (tv) 48.dp else 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (tv) 22.dp else 14.dp),
        ) {
            items(shelf.items, key = { it.id }) { item ->
                PosterCard(
                    item = item,
                    width = width,
                    landscape = shelf.landscape,
                    progress = progress[item.id],
                    onFocused = onFocused,
                    onClick = { onClick(item) },
                )
            }
        }
    }
}
