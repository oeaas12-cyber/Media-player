package com.prism.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.prism.player.data.Net
import kotlinx.coroutines.launch

class PrismApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.settings.ensureDeviceId()
            container.settings.ensureProfile()
            val active = container.settings.current().activeProfile?.id ?: "default"
            container.progress.switchProfile(active)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Net.client)
        .crossfade(true)
        .build()
}
