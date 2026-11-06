@file:OptIn(UnstableApi::class)

package com.prism.player.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.prism.player.data.MediaItem
import com.prism.player.data.MediaKind
import com.prism.player.data.Playback
import com.prism.player.data.PlaybackEvent
import com.prism.player.data.PlayerPrefs
import com.prism.player.data.sources.SmbContexts
import com.prism.player.ui.appContainer
import com.prism.player.ui.components.GlassButton
import com.prism.player.ui.components.GlassIconButton
import com.prism.player.ui.components.MessageState
import com.prism.player.ui.components.formatClock
import com.prism.player.ui.components.glass
import com.prism.player.ui.components.glassClickable
import com.prism.player.ui.screens.UiState
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.media3.common.MediaItem as ExoMediaItem

private class Loaded(val item: MediaItem, val playback: Playback, val prefs: PlayerPrefs, val startMs: Long)

private enum class Panel(val title: String) { AUDIO("Audio"), SUBTITLES("Subtitles"), SPEED("Playback speed"), ASPECT("Picture") }

@Composable
fun PlayerScreen(itemId: String, startMs: Long, onBack: () -> Unit, onPlayNext: (MediaItem) -> Unit) {
    val container = appContainer()
    val ctx = LocalContext.current
    val tv = LocalIsTv.current

    DisposableEffect(Unit) {
        val activity = ctx.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        val previous = activity?.requestedOrientation
        if (!tv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (!tv && previous != null) activity?.requestedOrientation = previous
        }
    }

    val state by produceState<UiState<Loaded>>(UiState.Loading, itemId) {
        value = runCatching {
            val item = container.library.item(itemId) ?: error("This title is no longer available.")
            val playback = container.library.playback(item)
            val prefs = container.settings.current().player
            val start = when {
                startMs >= 0 -> startMs
                prefs.autoResume -> container.progress.get(item.id)?.takeIf { it.inProgress }?.positionMs ?: item.resumeMs ?: 0
                else -> 0
            }
            UiState.Ready(Loaded(item, playback, prefs, start))
        }.getOrElse { UiState.Error(it.message ?: "Couldn't start playback") }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            UiState.Loading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            is UiState.Error -> {
                BackHandler(onBack = onBack)
                MessageState("Can't play this", s.message, "Back", onBack)
            }
            is UiState.Ready -> PlayerContent(s.data, onBack, onPlayNext)
        }
    }
}

@Composable
private fun PlayerContent(data: Loaded, onBack: () -> Unit, onPlayNext: (MediaItem) -> Unit) {
    val ctx = LocalContext.current
    val container = appContainer()
    val tv = LocalIsTv.current
    val item = data.item
    val prefs = data.prefs
    val scope = container.appScope

    val player = remember(item.id) { buildPlayer(ctx, data) }
    val session = remember(player) {
        MediaSession.Builder(ctx, player).setId("prism-${item.id.hashCode()}-${System.nanoTime()}").build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var position by remember { mutableLongStateOf(data.startMs) }
    var duration by remember { mutableLongStateOf(item.runtimeMs ?: 0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableLongStateOf(0L) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var focusSeekBar by remember { mutableStateOf(false) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }

    val nextEpisode by produceState<MediaItem?>(null, item.id) {
        if (item.kind == MediaKind.EPISODE) value = container.library.nextEpisode(item)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0)
                if (state == Player.STATE_ENDED) ended = true
            }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onPlayerError(e: PlaybackException) {
                error = "${e.errorCodeName}\n${e.cause?.message ?: e.message ?: ""}".trim()
            }
        }
        player.addListener(listener)
        scope.launch { container.library.report(item, PlaybackEvent.START, data.startMs, duration) }
        onDispose {
            val pos = player.currentPosition
            val dur = player.duration.takeIf { it > 0 } ?: item.runtimeMs ?: 0L
            player.removeListener(listener)
            session.release()
            player.release()
            scope.launch { container.library.report(item, PlaybackEvent.STOP, if (ended) dur else pos, dur) }
        }
    }

    // Position ticker + periodic progress sync.
    LaunchedEffect(player) {
        var tick = 0
        while (true) {
            delay(500)
            position = player.currentPosition
            buffered = player.bufferedPosition
            if (player.duration > 0) duration = player.duration
            if (++tick % 20 == 0 && player.isPlaying) {
                val p = position
                val d = duration
                scope.launch { container.library.report(item, PlaybackEvent.PROGRESS, p, d) }
            }
        }
    }

    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        val next = nextEpisode
        if (prefs.autoPlayNext && next != null) onPlayNext(next) else onBack()
    }

    // Auto-hide controls while playing.
    LaunchedEffect(controlsVisible, interaction, isPlaying, panel) {
        if (controlsVisible && isPlaying && panel == null) {
            delay(5_000)
            controlsVisible = false
        }
    }

    // Move focus between the hidden-controls catcher and the visible buttons.
    LaunchedEffect(controlsVisible, panel) {
        delay(60)
        runCatching {
            when {
                panel != null -> Unit
                !controlsVisible -> rootFocus.requestFocus()
                focusSeekBar -> seekFocus.requestFocus()
                else -> playFocus.requestFocus()
            }
        }
        focusSeekBar = false
    }

    fun poke() { interaction = System.currentTimeMillis() }
    fun seekBy(ms: Long) {
        val target = (player.currentPosition + ms).coerceIn(0, (duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        player.seekTo(target)
        position = target
        poke()
    }
    fun togglePlay() {
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
        poke()
    }
    val back = prefs.skipBackSeconds * 1000L
    val forward = prefs.skipForwardSeconds * 1000L

    BackHandler {
        when {
            panel != null -> panel = null
            controlsVisible && tv && isPlaying -> controlsVisible = false
            else -> onBack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.MediaPlayPause, Key.Spacebar -> { togglePlay(); controlsVisible = true; true }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); controlsVisible = true; true }
                    Key.MediaFastForward -> { seekBy(forward); true }
                    Key.MediaRewind -> { seekBy(-back); true }
                    else -> if (controlsVisible) { poke(); false } else when (ev.key) {
                        Key.DirectionLeft -> { seekBy(-back); focusSeekBar = true; controlsVisible = true; true }
                        Key.DirectionRight -> { seekBy(forward); focusSeekBar = true; controlsVisible = true; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> {
                            controlsVisible = true; poke(); true
                        }
                        else -> false
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible; poke() },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) seekBy(-back) else seekBy(forward)
                    },
                )
            },
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    this.player = player
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    subtitleView?.apply {
                        setApplyEmbeddedStyles(true)
                        setApplyEmbeddedFontSizes(false)
                        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * prefs.subtitleScale)
                        setBottomPaddingFraction(0.06f)
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null,
                            )
                        )
                    }
                }
            },
            update = { it.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(56.dp))
        }

        error?.let { msg ->
            MessageState("Playback error", msg, "Back", onBack)
        }

        AnimatedVisibility(controlsVisible && error == null, enter = fadeIn(), exit = fadeOut()) {
            Controls(
                item = item,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                buffered = buffered,
                nextEpisode = nextEpisode,
                skipBackLabel = prefs.skipBackSeconds,
                skipForwardLabel = prefs.skipForwardSeconds,
                playFocus = playFocus,
                seekFocus = seekFocus,
                onBack = onBack,
                onTogglePlay = ::togglePlay,
                onSeekBy = ::seekBy,
                onSeekTo = { player.seekTo(it); position = it; poke() },
                onPanel = { panel = it; poke() },
                onNext = { n -> onPlayNext(n) },
            )
        }

        AnimatedVisibility(
            panel != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val p = panel ?: Panel.AUDIO
            val options: List<Pair<String, Boolean>>
            val onPick: (Int) -> Unit
            when (p) {
                Panel.AUDIO -> {
                    val audio = trackOptions(tracks, C.TRACK_TYPE_AUDIO)
                    options = audio.map { it.label to it.selected }
                    onPick = { i -> selectTrack(player, audio[i], C.TRACK_TYPE_AUDIO) }
                }
                Panel.SUBTITLES -> {
                    val text = trackOptions(tracks, C.TRACK_TYPE_TEXT)
                    val off = text.none { it.selected }
                    options = listOf("Off" to off) + text.map { it.label to it.selected }
                    onPick = { i -> selectTrack(player, if (i == 0) null else text[i - 1], C.TRACK_TYPE_TEXT) }
                }
                Panel.SPEED -> {
                    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                    options = speeds.map { (if (it == 1f) "Normal" else "${it}×") to (it == speed) }
                    onPick = { i -> speed = speeds[i]; player.setPlaybackSpeed(speeds[i]) }
                }
                Panel.ASPECT -> {
                    val modes = listOf(
                        "Fit to screen" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        "Fill screen (crop)" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        "Stretch" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
                    )
                    options = modes.map { it.first to (it.second == resizeMode) }
                    onPick = { i -> resizeMode = modes[i].second }
                }
            }
            OptionPanel(p.title, options, onPick = { onPick(it); poke() }, onClose = { panel = null })
        }
    }
}

@Composable
private fun Controls(
    item: MediaItem,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    buffered: Long,
    nextEpisode: MediaItem?,
    skipBackLabel: Int,
    skipForwardLabel: Int,
    playFocus: FocusRequester,
    seekFocus: FocusRequester,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPanel: (Panel) -> Unit,
    onNext: (MediaItem) -> Unit,
) {
    val tv = LocalIsTv.current
    val pad = if (tv) 48.dp else 20.dp
    Box(Modifier.fillMaxSize()) {
        // Top and bottom scrims so glass reads over bright video.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.65f),
                    0.25f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.8f),
                )
            )
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = pad, vertical = if (tv) 32.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
            Column {
                Text(
                    item.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (tv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                )
                val sub = listOfNotNull(item.seriesTitle, item.episodeLabel, item.year?.toString().takeIf { item.kind == MediaKind.MOVIE })
                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), color = Palette.TextSecondary)
            }
        }

        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (tv) 40.dp else 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                Icons.Filled.FastRewind,
                "Back $skipBackLabel seconds", size = 60.dp,
            ) { onSeekBy(-skipBackLabel * 1000L) }
            GlassIconButton(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (isPlaying) "Pause" else "Play",
                size = 84.dp,
                modifier = Modifier.focusRequester(playFocus),
                onClick = onTogglePlay,
            )
            GlassIconButton(Icons.Filled.FastForward, "Forward $skipForwardLabel seconds", size = 60.dp) {
                onSeekBy(skipForwardLabel * 1000L)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = pad, vertical = if (tv) 32.dp else 16.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            SeekBar(
                position = position, duration = duration, buffered = buffered,
                stepBack = skipBackLabel * 1000L, stepForward = skipForwardLabel * 1000L,
                modifier = Modifier.focusRequester(seekFocus),
                onSeekTo = onSeekTo,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(formatClock(position), color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    if (duration > 0) "-" + formatClock(duration - position) else "",
                    color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassButton("Audio", Icons.Filled.Audiotrack) { onPanel(Panel.AUDIO) }
                GlassButton("Subtitles", Icons.Filled.Subtitles) { onPanel(Panel.SUBTITLES) }
                if (tv) {
                    GlassButton("Speed", Icons.Filled.Speed) { onPanel(Panel.SPEED) }
                    GlassButton("Picture", Icons.Filled.AspectRatio) { onPanel(Panel.ASPECT) }
                } else {
                    GlassIconButton(Icons.Filled.Speed, "Speed", size = 44.dp) { onPanel(Panel.SPEED) }
                    GlassIconButton(Icons.Filled.AspectRatio, "Picture", size = 44.dp) { onPanel(Panel.ASPECT) }
                }
                Spacer(Modifier.weight(1f))
                if (nextEpisode != null) {
                    GlassButton(
                        if (tv) "Next: ${nextEpisode.episodeLabel ?: nextEpisode.title}" else "Next",
                        Icons.Filled.SkipNext,
                    ) { onNext(nextEpisode) }
                }
            }
        }
    }
}

/** Seek bar usable with touch (tap/drag) and D-pad (left/right while focused). */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    stepBack: Long,
    stepForward: Long,
    modifier: Modifier = Modifier,
    onSeekTo: (Long) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var scrub by remember { mutableStateOf<Float?>(null) }
    val fraction = scrub ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || duration <= 0) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { onSeekTo((position - stepBack).coerceAtLeast(0)); true }
                    Key.DirectionRight -> { onSeekTo((position + stepForward).coerceAtMost(duration)); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .pointerInput(duration) {
                detectTapGestures { o -> if (duration > 0) onSeekTo((o.x / size.width * duration).toLong()) }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> scrub = (o.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ -> scrub = (change.position.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { scrub?.let { if (duration > 0) onSeekTo((it * duration).toLong()) }; scrub = null },
                    onDragCancel = { scrub = null },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackHeight = if (focused || scrub != null) 8.dp else 5.dp
        Box(Modifier.fillMaxWidth().height(trackHeight).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.2f)))
        Box(Modifier.fillMaxWidth(bufferedFraction).height(trackHeight).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.3f)))
        Box(
            Modifier.fillMaxWidth(fraction).height(trackHeight).clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(Palette.Accent, Color.White)))
        )
        val thumb = if (focused || scrub != null) 20.dp else 12.dp
        Box(
            Modifier
                .offset(x = (maxWidth - thumb) * fraction)
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun OptionPanel(
    title: String,
    options: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val tv = LocalIsTv.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(title) {
        delay(80)
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(if (tv) 400.dp else 300.dp)
            .padding(16.dp)
            .glass(RoundedCornerShape(28.dp), strength = 1.6f)
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
            GlassButton("Done") { onClose() }
        }
        Spacer(Modifier.height(12.dp))
        if (options.isEmpty()) {
            Text("No options for this video.", color = Palette.TextSecondary)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(options) { i, (label, selected) ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (i == options.indexOfFirst { it.second }.coerceAtLeast(0)) Modifier.focusRequester(firstFocus) else Modifier)
                        .glassClickable(interaction, focusScale = 1.02f) { onPick(i) }
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (focused) Color.White else if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label, modifier = Modifier.weight(1f),
                        color = if (focused) Color(0xFF0B1020) else Color.White,
                    )
                    if (selected) Icon(Icons.Filled.Check, null, tint = if (focused) Color(0xFF0B1020) else Palette.Accent)
                }
            }
        }
    }
}

private class TrackOption(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

private fun trackOptions(tracks: Tracks, type: Int): List<TrackOption> =
    tracks.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length)
            .filter { type == C.TRACK_TYPE_TEXT || g.isTrackSupported(it) }
            .map { i -> TrackOption(trackLabel(g.getTrackFormat(i), type), g, i, g.isTrackSelected(i)) }
    }

private fun selectTrack(player: Player, option: TrackOption?, type: Int) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
        if (option == null) {
            setTrackTypeDisabled(type, true)
        } else {
            setTrackTypeDisabled(type, false)
            setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
        }
    }.build()
}

private fun trackLabel(f: Format, type: Int): String {
    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { code ->
        Locale.forLanguageTag(code).displayLanguage.takeIf { it.isNotBlank() } ?: code
    }
    val codec = when (f.sampleMimeType) {
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.APPLICATION_SUBRIP -> "SRT"
        MimeTypes.TEXT_SSA -> "ASS"
        MimeTypes.TEXT_VTT -> "WebVTT"
        MimeTypes.APPLICATION_PGS -> "PGS"
        MimeTypes.APPLICATION_VOBSUB -> "VobSub"
        else -> null
    }
    val channels = if (type == C.TRACK_TYPE_AUDIO) when (f.channelCount) {
        Format.NO_VALUE -> null
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${f.channelCount} ch"
    } else null
    val forced = if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) "Forced" else null
    return listOfNotNull(f.label?.takeIf { it.isNotBlank() && it != lang }, lang, codec, channels, forced)
        .distinct().joinToString(" · ").ifBlank { "Track" }
}

private fun buildPlayer(ctx: Context, data: Loaded): ExoPlayer {
    val playback = data.playback
    val prefs = data.prefs
    val smb = playback.smbConfig
    val upstream: DataSource.Factory = if (smb != null) {
        SmbDataSource.Factory(SmbContexts.forConfig(smb))
    } else {
        DefaultDataSource.Factory(
            ctx,
            DefaultHttpDataSource.Factory()
                .setUserAgent("PrismPlayer/0.1 (Android)")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setDefaultRequestProperties(playback.headers),
        )
    }
    val trackSelector = DefaultTrackSelector(ctx).apply {
        setParameters(
            buildUponParameters()
                .apply { if (prefs.preferredAudioLanguage.isNotBlank()) setPreferredAudioLanguage(prefs.preferredAudioLanguage) }
                .apply { if (prefs.preferredSubtitleLanguage.isNotBlank()) setPreferredTextLanguage(prefs.preferredSubtitleLanguage) }
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !prefs.subtitlesByDefault)
        )
    }
    val renderers = DefaultRenderersFactory(ctx)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

    val media = ExoMediaItem.Builder()
        .setUri(playback.uri)
        .setMediaId(data.item.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(data.item.title)
                .setSubtitle(listOfNotNull(data.item.seriesTitle, data.item.episodeLabel).joinToString(" · ").ifBlank { null })
                .build()
        )
        .setSubtitleConfigurations(
            playback.subtitles.map { s ->
                ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(s.uri))
                    .setMimeType(s.mimeType)
                    .setLanguage(s.language)
                    .setLabel(s.label)
                    .build()
            }
        )
        .build()

    return ExoPlayer.Builder(ctx, renderers)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(DefaultMediaSourceFactory(upstream))
        .setSeekBackIncrementMs(prefs.skipBackSeconds * 1000L)
        .setSeekForwardIncrementMs(prefs.skipForwardSeconds * 1000L)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            setMediaItem(media, data.startMs.coerceAtLeast(0))
            prepare()
            playWhenReady = true
        }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
