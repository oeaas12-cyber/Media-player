package com.prism.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.prism.player.data.HomeData
import com.prism.player.data.LibraryRepository
import com.prism.player.data.MediaItem
import com.prism.player.data.MediaKind
import com.prism.player.ui.appContainer
import com.prism.player.ui.components.GlassButton
import com.prism.player.ui.components.LoadingState
import com.prism.player.ui.components.MessageState
import com.prism.player.ui.components.ShelfRow
import com.prism.player.ui.components.Tab
import com.prism.player.ui.components.TopLevelScaffold
import com.prism.player.ui.components.formatDuration
import com.prism.player.ui.components.glass
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.State

@OptIn(kotlinx.coroutines.FlowPreview::class)
class HomeViewModel(private val repo: LibraryRepository) : ViewModel() {
    private val _state = mutableStateOf<UiState<HomeData>>(UiState.Loading)
    val state: State<UiState<HomeData>> = _state

    init {
        viewModelScope.launch {
            combine(repo.sourcesChanged, repo.progress.all) { a, _ -> a }
                .debounce(300)
                .collectLatest { load(silent = _state.value is UiState.Ready) }
        }
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.value = UiState.Loading
            _state.value = runCatching { UiState.Ready(repo.home()) }
                .getOrElse { UiState.Error(it.message ?: "Something went wrong") }
        }
    }
}

@Composable
fun HomeScreen(onTab: (Tab) -> Unit, onOpen: (MediaItem) -> Unit, onPlay: (MediaItem) -> Unit) {
    val container = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(container.library) }
    val state by vm.state
    val progress by container.progress.all.collectAsState()
    var backdrop by remember { mutableStateOf<String?>(null) }
    val tv = LocalIsTv.current
    val scope = rememberCoroutineScope()

    TopLevelScaffold(Tab.HOME, backdrop, onTab) {
        when (val s = state) {
            UiState.Loading -> LoadingState()
            is UiState.Error -> MessageState("Couldn't load", s.message, "Try again") { vm.load() }
            is UiState.Ready -> {
                val data = s.data
                if (!data.hasSources) {
                    MessageState(
                        "Welcome to Prism",
                        "Add a Jellyfin or Plex server, a network share or a WebDAV folder to start building your library.",
                        "Add a source",
                        onAction = { onTab(Tab.SETTINGS) },
                        secondaryAction = "Try the demo",
                        onSecondaryAction = {
                            scope.launch {
                                container.settings.update { s2 ->
                                    if (s2.sources.any { it.id == "demo" }) s2 else s2.copy(sources = s2.sources + demoSource())
                                }
                            }
                        },
                    )
                } else if (data.shelves.isEmpty() && data.hero.isEmpty()) {
                    MessageState(
                        "Nothing here yet",
                        (data.errors.joinToString("\n").ifBlank { "Your sources don't contain any videos yet." }),
                        "Reload",
                    ) { vm.load() }
                } else {
                    LaunchedEffect(data.hero) { if (backdrop == null) backdrop = data.hero.firstOrNull()?.backdropUrl }
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(if (tv) 28.dp else 20.dp),
                    ) {
                        if (data.hero.isNotEmpty()) {
                            item(key = "hero") {
                                Hero(data.hero, onPlay = onPlay, onOpen = onOpen, onShown = { backdrop = it.backdropUrl })
                            }
                        }
                        items(data.shelves, key = { it.title }) { shelf ->
                            ShelfRow(
                                shelf, progress,
                                onFocused = { backdrop = it.backdropUrl ?: backdrop },
                                onClick = { if (shelf.landscape && it.isPlayable) onPlay(it) else onOpen(it) },
                            )
                        }
                        if (data.errors.isNotEmpty()) {
                            item(key = "errors") {
                                Text(
                                    "Some sources could not be reached:\n" + data.errors.joinToString("\n"),
                                    color = Palette.Danger,
                                    modifier = Modifier.padding(horizontal = 24.dp).glass().padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onOpen: (MediaItem) -> Unit,
    onShown: (MediaItem) -> Unit,
) {
    val tv = LocalIsTv.current
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(items) {
        while (true) {
            delay(9_000)
            index = (index + 1) % items.size
        }
    }
    val item = items[index % items.size]
    LaunchedEffect(item) { onShown(item) }
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .padding(horizontal = if (tv) 48.dp else 16.dp)
            .fillMaxWidth()
            .height(if (tv) 300.dp else 230.dp)
            .glass(shape)
    ) {
        AnimatedContent(item, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "hero") { current ->
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = current.backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(shape),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.35f), Color.Transparent))
                    )
                )
                Column(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (tv) 0.55f else 0.9f)
                        .padding(if (tv) 36.dp else 18.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        if (current.kind == MediaKind.SHOW) "TV SHOW" else "MOVIE",
                        style = MaterialTheme.typography.labelMedium, color = Palette.Accent,
                    )
                    Text(
                        current.title,
                        style = if (tv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        current.year?.toString(),
                        current.runtimeMs?.let { formatDuration(it) },
                        current.rating?.let { "★ %.1f".format(it) },
                        current.genres.take(2).joinToString(", ").ifBlank { null },
                    ).joinToString("  ·  ")
                    if (meta.isNotBlank()) Text(meta, color = Palette.TextSecondary, style = MaterialTheme.typography.labelLarge)
                    if (tv && current.overview != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(current.overview, color = Palette.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(if (tv) 18.dp else 10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (current.isPlayable) GlassButton("Play", Icons.Filled.PlayArrow, primary = true) { onPlay(current) }
                        GlassButton("Details", Icons.Filled.Info) { onOpen(current) }
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.indices.forEach { i ->
                Box(
                    Modifier
                        .size(width = if (i == index % items.size) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (i == index % items.size) 0.95f else 0.4f))
                )
            }
        }
    }
}
