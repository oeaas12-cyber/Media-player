package com.prism.player

import android.content.Context
import com.prism.player.data.LibraryRepository
import com.prism.player.data.ProgressStore
import com.prism.player.data.SettingsRepository
import com.prism.player.data.Tmdb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(context)
    val progress = ProgressStore(context)
    val tmdb = Tmdb { settings.current().tmdbKey }
    val library = LibraryRepository(context, settings, tmdb, progress)

    /** True once a profile was chosen (or auto-chosen) in this process; stops re-asking on every screen. */
    @Volatile var profileChosenThisLaunch = false

    /** Makes [id] the active profile and loads its watch history. */
    suspend fun switchProfile(id: String) {
        settings.setActiveProfile(id)
        progress.switchProfile(id)
        profileChosenThisLaunch = true
    }
}
