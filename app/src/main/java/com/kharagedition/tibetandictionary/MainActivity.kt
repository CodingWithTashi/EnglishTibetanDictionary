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
import com.kharagedition.tibetandictionary.core.init.nightMode
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
 * or the app. Every `app://` link, at cold start or delivered to the running activity, goes through
 * [NavigationEventBus] (see [takeAppLink]).
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
        takeAppLink(intent, follow = savedInstanceState == null)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            when (val s = state) {
                AppRootState.Loading -> Box(Modifier.fillMaxSize().background(colorResource(R.color.window_background)))
                is AppRootState.Installing -> DictTheme { InstallingScreen(s, onRetry = viewModel::retryInstall) }
                is AppRootState.Ready -> {
                    // DictionaryApplication applied the stored mode before this activity existed, so at start-up this
                    // sets the mode in force and does nothing; a later change in Profile recreates the activity.
                    LaunchedEffect(s.appearance) { AppCompatDelegate.setDefaultNightMode(s.appearance.nightMode()) }
                    AppRoot(s, viewModel::retrySwitch, viewModel::keepCurrentDictionary)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeAppLink(intent, follow = true)
    }

    /**
     * Sends the `app://` link in [intent] (if any) to the nav host through [NavigationEventBus], which waits until
     * onboarding is done, and removes it from the activity's intent so NavController never handles it itself (that
     * would stack Entry over Onboarding, or restart the task for NEW_TASK). A link replayed from Recents, or seen
     * again after recreation ([follow] false), is removed without being followed.
     */
    private fun takeAppLink(intent: Intent, follow: Boolean) {
        val link = DeepLinks.parse(intent) ?: return
        val fromRecents = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (follow && !fromRecents) navigationEvents.emit(link)
        setIntent(Intent(intent).setData(null))
    }
}
