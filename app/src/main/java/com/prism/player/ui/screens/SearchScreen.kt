package com.prism.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.player.data.MediaItem
import com.prism.player.data.MediaKind
import com.prism.player.ui.appContainer
import com.prism.player.ui.components.PosterCard
import com.prism.player.ui.components.Tab
import com.prism.player.ui.components.TopLevelScaffold
import com.prism.player.ui.components.glass
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(onTab: (Tab) -> Unit, onOpen: (MediaItem) -> Unit) {
    val container = appContainer()
    val progress by container.progress.all.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var backdrop by remember { mutableStateOf<String?>(null) }
    val tv = LocalIsTv.current
    val results by produceState<List<MediaItem>?>(null, query) {
        delay(250)
        value = runCatching { container.library.search(query) }.getOrDefault(emptyList())
    }
    val hPad = if (tv) 48.dp else 16.dp

    TopLevelScaffold(Tab.SEARCH, backdrop, onTab) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (tv) 150.dp else 108.dp),
            contentPadding = PaddingValues(start = hPad, end = hPad, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(if (tv) 24.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "field") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Movies, shows, genres…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedContainerColor = Color.White.copy(alpha = 0.10f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val r = results
            if (query.isNotBlank() && r != null && r.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    Text("No matches for “$query”", color = Palette.TextSecondary, modifier = Modifier.glass())
                }
            }
            items(r.orEmpty(), key = { it.id }) { item ->
                PosterCard(
                    item = item,
                    width = Dp.Unspecified,
                    progress = progress[item.id],
                    subtitle = listOfNotNull(if (item.kind == MediaKind.SHOW) "TV Show" else "Movie", item.year?.toString()).joinToString(" · "),
                    onFocused = { backdrop = it.backdropUrl ?: backdrop },
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}
