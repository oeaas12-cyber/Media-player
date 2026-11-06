package com.prism.player

import android.app.UiModeManager
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.prism.player.ui.AppNavigation
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.PrismTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uiMode = getSystemService(UI_MODE_SERVICE) as UiModeManager
        val isTv = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        setContent {
            PrismTheme {
                CompositionLocalProvider(LocalIsTv provides isTv) {
                    AppNavigation()
                }
            }
        }
    }
}
