package com.prism.player

import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.Coil
import coil.ImageLoader
import com.github.takahirom.roborazzi.captureRoboImage
import com.prism.player.data.MediaItem
import com.prism.player.data.MediaKind
import com.prism.player.player.Controls
import com.prism.player.player.OptionPanel
import com.prism.player.ui.components.Tab
import com.prism.player.ui.screens.DetailScreen
import com.prism.player.ui.screens.HomeScreen
import com.prism.player.ui.screens.LibraryScreen
import com.prism.player.ui.screens.SettingsScreen
import com.prism.player.ui.screens.demoSource
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.PrismTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Renders the real screens with the demo library and writes PNGs to docs/screenshots. */
abstract class ScreenshotBase(private val tv: Boolean, private val prefix: String) {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<PrismApp>()
    private var screen by mutableStateOf<@Composable () -> Unit>({})

    private fun settle(rounds: Int = 20) {
        repeat(rounds) {
            Thread.sleep(40)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            compose.mainClock.advanceTimeBy(250)
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        screen = content
        settle()
        compose.onRoot().captureRoboImage("../docs/screenshots/$prefix-$name.png")
    }

    private fun seed() = runBlocking {
        val c = app.container
        c.settings.update { it.copy(sources = listOf(demoSource())) }
        val sintel = c.library.item("demo|movie:sintel")!!
        c.progress.save(sintel, 6 * 60_000L, 15 * 60_000L)
        val ep1 = c.library.item("demo|episode:showcase:1")!!
        val ep2 = c.library.item("demo|episode:showcase:2")!!
        c.progress.setWatched(ep1, true)
        c.progress.save(ep2, 6_000L, 15_000L)
    }

    @Test
    fun screens() {
        Coil.setImageLoader(ImageLoader.Builder(app).dispatcher(Dispatchers.Unconfined).crossfade(false).build())
        seed()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PrismTheme { CompositionLocalProvider(LocalIsTv provides tv) { screen() } }
        }

        shot("home") { HomeScreen(onTab = {}, onOpen = {}, onPlay = {}) }
        shot("movies") { LibraryScreen(MediaKind.MOVIE, onTab = {}, onOpen = {}) }
        shot("movie-detail") { DetailScreen("demo|movie:sintel", onBack = {}, onOpen = {}, onPlay = { _, _ -> }) }
        shot("show-detail") { DetailScreen("demo|show:showcase", onBack = {}, onOpen = {}, onPlay = { _, _ -> }) }
        if (tv) {
            shot("player") { PlayerMock(panel = false) }
            shot("player-subtitles") { PlayerMock(panel = true) }
            shot("settings") { SettingsScreen(onTab = { _: Tab -> }, onAddSource = {}) }
        }
    }

    /** Player overlay drawn over a still frame (video decoding isn't available in unit tests). */
    @Composable
    private fun PlayerMock(panel: Boolean) {
        val item = MediaItem(
            id = "demo|movie:sintel", sourceId = "demo", nativeId = "movie:sintel", kind = MediaKind.MOVIE,
            title = "Sintel", year = 2010, runtimeMs = 888_000,
        )
        val play = remember { FocusRequester() }
        Box(Modifier.fillMaxSize()) {
            Image(
                painterResource(R.drawable.demo_sintel_backdrop), null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            if (!panel) {
                Controls(
                    item = item, isPlaying = true, position = 372_000, duration = 888_000, buffered = 520_000,
                    nextEpisode = null, skipBackLabel = 10, skipForwardLabel = 30,
                    playFocus = play, seekFocus = remember { FocusRequester() },
                    onBack = {}, onTogglePlay = {}, onSeekBy = {}, onSeekTo = {}, onPanel = {}, onNext = {},
                )
                LaunchedEffect(Unit) { runCatching { play.requestFocus() } }
            } else {
                Box(Modifier.align(Alignment.CenterEnd)) {
                    OptionPanel(
                        "Subtitles",
                        listOf(
                            "Off" to false,
                            "English · SRT" to true,
                            "Arabic · SRT" to false,
                            "French · ASS" to false,
                            "Spanish · WebVTT" to false,
                        ),
                        onPick = {}, onClose = {},
                    )
                }
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class TvScreenshots : ScreenshotBase(tv = true, prefix = "tv")

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-port-xxhdpi")
class PhoneScreenshots : ScreenshotBase(tv = false, prefix = "phone")
