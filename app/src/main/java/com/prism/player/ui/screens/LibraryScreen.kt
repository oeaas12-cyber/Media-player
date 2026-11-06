package com.prism.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prism.player.data.LibraryRepository
import com.prism.player.data.MediaItem
import com.prism.player.data.MediaKind
import com.prism.player.ui.appContainer
import com.prism.player.ui.components.GlassChip
import com.prism.player.ui.components.LoadingState
import com.prism.player.ui.components.MessageState
import com.prism.player.ui.components.PosterCard
import com.prism.player.ui.components.Tab
import com.prism.player.ui.components.TopLevelScaffold
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.Palette
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryViewModel(private val repo: LibraryRepository, private val kind: MediaKind) : ViewModel() {
    private val _state = mutableStateOf<UiState<List<MediaItem>>>(UiState.Loading)
    val state: State<UiState<List<MediaItem>>> = _state

    init {
        viewModelScope.launch { repo.sourcesChanged.collectLatest { load() } }
    }

    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = runCatching {
            UiState.Ready(if (kind == MediaKind.MOVIE) repo.movies() else repo.shows())
        }.getOrElse { UiState.Error(it.message ?: "Something went wrong") }
    }
}

enum class SortOrder(val label: String) { TITLE("A–Z"), ADDED("Recently Added"), YEAR("Year"), RATING("Rating") }

@Composable
fun LibraryScreen(kind: MediaKind, onTab: (Tab) -> Unit, onOpen: (MediaItem) -> Unit) {
    val container = appContainer()
    val vm: LibraryViewModel = viewModel(key = kind.name) { LibraryViewModel(container.library, kind) }
    val state by vm.state
    val progress by container.progress.all.collectAsState()
    var backdrop by remember { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortOrder.TITLE) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var watchFilter by rememberSaveable { mutableStateOf(0) } // 0 all, 1 unwatched, 2 watched
    val tv = LocalIsTv.current
    val tab = if (kind == MediaKind.MOVIE) Tab.MOVIES else Tab.SHOWS
    val hPad = if (tv) 48.dp else 16.dp

    TopLevelScaffold(tab, backdrop, onTab) {
        when (val s = state) {
            UiState.Loading -> LoadingState()
            is UiState.Error -> MessageState("Couldn't load", s.message, "Try again") { vm.load() }
            is UiState.Ready -> {
                val all = s.data
                if (all.isEmpty()) {
                    MessageState(
                        if (kind == MediaKind.MOVIE) "No movies yet" else "No TV shows yet",
                        "Add a source in Settings, or refresh an existing one.",
                        "Open Settings",
                    ) { onTab(Tab.SETTINGS) }
                } else {
                val genres = remember(all) {
                    all.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
                }
                val shown = remember(all, sort, genre, watchFilter, progress) {
                    all.asSequence()
                        .filter { genre == null || genre in it.genres }
                        .filter {
                            val watched = progress[it.id]?.finished ?: it.played
                            when (watchFilter) { 1 -> !watched; 2 -> watched; else -> true }
                        }
                        .let { seq ->
                            when (sort) {
                                SortOrder.TITLE -> seq.sortedBy { it.title.lowercase().removePrefix("the ") }
                                SortOrder.ADDED -> seq.sortedByDescending { it.addedAt ?: 0 }
                                SortOrder.YEAR -> seq.sortedByDescending { it.year ?: 0 }
                                SortOrder.RATING -> seq.sortedByDescending { it.rating ?: 0.0 }
                            }
                        }.toList()
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (tv) 150.dp else 108.dp),
                    contentPadding = PaddingValues(start = hPad, end = hPad, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (tv) 24.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(if (tv) 26.dp else 18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(tab.label, style = MaterialTheme.typography.headlineMedium, color = Palette.TextPrimary)
                                Spacer(Modifier.width(12.dp))
                                Text("${shown.size} titles", color = Palette.TextTertiary, modifier = Modifier.padding(bottom = 4.dp))
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 12.dp),
                            ) {
                                items(SortOrder.entries) { o -> GlassChip(o.label, sort == o) { sort = o } }
                                item { Spacer(Modifier.width(12.dp)) }
                                item { GlassChip("All", watchFilter == 0) { watchFilter = 0 } }
                                item { GlassChip("Unwatched", watchFilter == 1) { watchFilter = 1 } }
                                item { GlassChip("Watched", watchFilter == 2) { watchFilter = 2 } }
                            }
                            if (genres.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item { GlassChip("All genres", genre == null) { genre = null } }
                                    items(genres) { g -> GlassChip(g, genre == g) { genre = if (genre == g) null else g } }
                                }
                            }
                        }
                    }
                    items(shown, key = { it.id }) { item ->
                        PosterCard(
                            item = item,
                            width = Dp.Unspecified,
                            progress = progress[item.id],
                            onFocused = { backdrop = it.backdropUrl ?: backdrop },
                            onClick = { onOpen(item) },
                        )
                    }
                }
                }
            }
        }
    }
}
