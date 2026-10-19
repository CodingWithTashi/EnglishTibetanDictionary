package com.kharagedition.tibetandictionary.ui.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstallingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var retries = 0

    private fun show(state: AppRootState.Installing) {
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                InstallingScreen(state, onRetry = { retries++ })
            }
        }
    }

    @Test
    fun `while copying it shows the app name and a generic status`() {
        show(AppRootState.Installing(progress = 0.6f, totalEntries = null))

        compose.onNodeWithText("བོད་ཡིག་ཚིག་མཛོད།").assertExists()
        compose.onNodeWithText("Tibetan Dictionary").assertExists()
        compose.onNodeWithText("Preparing the dictionary · offline").assertExists()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `a known entry count is shown in the status`() {
        show(AppRootState.Installing(progress = 0.6f, totalEntries = 180_412))

        compose.onNodeWithText("Preparing 180,412 entries · offline").assertExists()
    }

    @Test
    fun `a failure offers to try again`() {
        show(AppRootState.Installing(progress = null, totalEntries = null, failure = IOException("disk full")))

        compose.onNodeWithText("Couldn't prepare the dictionary.").assertExists()
        compose.onNodeWithText("Preparing the dictionary · offline").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()

        assertThat(retries).isEqualTo(1)
    }
}
