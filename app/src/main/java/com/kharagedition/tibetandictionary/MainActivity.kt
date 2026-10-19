package com.kharagedition.tibetandictionary

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetandictionary.data.model.Appearance
import com.kharagedition.tibetandictionary.navigation.DeepLinks
import com.kharagedition.tibetandictionary.navigation.NavigationEventBus
import com.kharagedition.tibetandictionary.ui.app.AppRoot
import com.kharagedition.tibetandictionary.ui.app.AppRootState
import com.kharagedition.tibetandictionary.ui.app.AppRootViewModel
import com.kharagedition.tibetandictionary.ui.app.InstallingScreen
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single activity. Keeps the system splash up while [AppRootState.Loading], then shows the installing screen
 * or the app. A cold-start `app://` link is handled by the nav graph's deep links; one delivered to the running
 * activity goes through [NavigationEventBus].
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: AppRootViewModel by viewModels()

    @Inject lateinit var navigationEvents: NavigationEventBus

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.uiState.value is AppRootState.Loading }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            when (val s = state) {
                AppRootState.Loading -> Box(Modifier.fillMaxSize().background(colorResource(R.color.window_background)))
                is AppRootState.Installing -> DictTheme { InstallingScreen(s, onRetry = viewModel::retryInstall) }
                is AppRootState.Ready -> {
                    LaunchedEffect(s.appearance) { AppCompatDelegate.setDefaultNightMode(s.appearance.nightMode()) }
                    AppRoot(s)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLinks.parse(intent)?.let(navigationEvents::emit)
    }
}

private fun Appearance.nightMode(): Int = when (this) {
    Appearance.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    Appearance.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    Appearance.DARK -> AppCompatDelegate.MODE_NIGHT_YES
}
