package com.prism.player.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prism.player.AppContainer
import com.prism.player.PrismApp
import com.prism.player.data.MediaKind
import com.prism.player.data.SourceType
import com.prism.player.player.PlayerScreen
import com.prism.player.ui.components.LoadingState
import com.prism.player.ui.components.LocalOpenProfiles
import com.prism.player.ui.components.Tab
import com.prism.player.ui.screens.AddSourceScreen
import com.prism.player.ui.screens.DetailScreen
import com.prism.player.ui.screens.HomeScreen
import com.prism.player.ui.screens.LibraryScreen
import com.prism.player.ui.screens.ProfileEditScreen
import com.prism.player.ui.screens.ProfilePickerScreen
import com.prism.player.ui.screens.SearchScreen
import com.prism.player.ui.screens.SettingsScreen
import com.prism.player.update.UpdateManager

object Routes {
    const val GATE = "gate"
    const val PROFILES = "profiles"
    fun detail(id: String) = "detail/${Uri.encode(id)}"
    fun player(id: String, startMs: Long = -1) = "player/${Uri.encode(id)}?start=$startMs"
    fun addSource(type: SourceType) = "add/${type.name}"
    fun editProfile(id: String) = "profile/${Uri.encode(id)}"
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as PrismApp).container

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val container = appContainer()
    LaunchedEffect(Unit) { UpdateManager.check() }
    val onTab: (Tab) -> Unit = { tab -> nav.navigateTab(tab) }
    val openProfiles: () -> Unit = { nav.navigate(Routes.PROFILES) }

    CompositionLocalProvider(LocalOpenProfiles provides openProfiles) {
        NavHost(navController = nav, startDestination = Routes.GATE) {
            // Decides whether to ask "Who's watching?" (several profiles) or go straight to Home.
            composable(Routes.GATE) {
                LaunchedEffect(Unit) {
                    container.settings.ensureProfile()
                    val s = container.settings.current()
                    if (!container.profileChosenThisLaunch && s.profiles.size > 1) {
                        nav.navigate(Routes.PROFILES) { popUpTo(Routes.GATE) { inclusive = true } }
                    } else {
                        container.switchProfile(s.activeProfile?.id ?: "default")
                        nav.navigate(Tab.HOME.route) { popUpTo(Routes.GATE) { inclusive = true } }
                    }
                }
                LoadingState("Starting…")
            }
            composable(Routes.PROFILES) {
                val fromGate = nav.previousBackStackEntry == null
                ProfilePickerScreen(
                    onChosen = {
                        nav.navigate(Tab.HOME.route) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                    },
                    onEdit = { id -> nav.navigate(Routes.editProfile(id)) },
                    onBack = if (fromGate) null else ({ nav.popBackStack() }),
                )
            }
            composable(
                "profile/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                ProfileEditScreen(
                    profileId = entry.arguments?.getString("id") ?: "new",
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Tab.HOME.route) {
                HomeScreen(onTab = onTab, onOpen = { nav.navigate(Routes.detail(it.id)) }, onPlay = { nav.navigate(Routes.player(it.id)) })
            }
            composable(Tab.MOVIES.route) {
                LibraryScreen(MediaKind.MOVIE, onTab = onTab, onOpen = { nav.navigate(Routes.detail(it.id)) })
            }
            composable(Tab.SHOWS.route) {
                LibraryScreen(MediaKind.SHOW, onTab = onTab, onOpen = { nav.navigate(Routes.detail(it.id)) })
            }
            composable(Tab.SEARCH.route) {
                SearchScreen(onTab = onTab, onOpen = { nav.navigate(Routes.detail(it.id)) })
            }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    onTab = onTab,
                    onAddSource = { nav.navigate(Routes.addSource(it)) },
                    onEditProfile = { nav.navigate(Routes.editProfile(it)) },
                )
            }
            composable(
                "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                DetailScreen(
                    id = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate(Routes.detail(it.id)) },
                    onPlay = { item, start -> nav.navigate(Routes.player(item.id, start)) },
                )
            }
            composable(
                "player/{id}?start={start}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("start") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                PlayerScreen(
                    itemId = entry.arguments?.getString("id").orEmpty(),
                    startMs = entry.arguments?.getLong("start") ?: -1L,
                    onBack = { nav.popBackStack() },
                    onPlayNext = { next ->
                        nav.navigate(Routes.player(next.id, 0)) {
                            popUpTo("player/{id}?start={start}") { inclusive = true }
                        }
                    },
                )
            }
            composable("add/{type}") { entry ->
                AddSourceScreen(
                    type = SourceType.valueOf(entry.arguments?.getString("type") ?: SourceType.JELLYFIN.name),
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(Tab.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
