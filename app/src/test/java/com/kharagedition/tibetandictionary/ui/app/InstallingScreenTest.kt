package com.kharagedition.tibetandictionary.ui.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.install.SwitchState
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstallingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var retries = 0

    private fun show(state: AppRootState.Installing, bundledEntryCount: Int? = null) {
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                if (bundledEntryCount == null) {
                    InstallingScreen(state, onRetry = { retries++ })
                } else {
                    InstallingScreen(state, onRetry = { retries++ }, bundledEntryCount = bundledEntryCount)
                }
            }
        }
    }

    @Test
    fun `while copying it shows the app name and a generic status`() {
        // 0: the bundled asset's count is unknown too (the real resource now carries the data build's count).
        show(AppRootState.Installing(DictionaryPack.TIBETAN, progress = 0.6f, totalEntries = null), bundledEntryCount = 0)

        compose.onNodeWithText("བོད་ཡིག་ཚིག་མཛོད།").assertExists()
        compose.onNodeWithText("Tibetan Dictionary").assertExists()
        compose.onNodeWithText("Preparing the dictionary · offline").assertExists()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `a known entry count is shown in the status`() {
        show(AppRootState.Installing(DictionaryPack.TIBETAN, progress = 0.6f, totalEntries = 180_412))

        compose.onNodeWithText("Preparing 180,412 entries · offline").assertExists()
    }

    @Test
    fun `before the installer knows the count it shows the bundled asset's count`() {
        show(AppRootState.Installing(DictionaryPack.TIBETAN, progress = 0.2f, totalEntries = null), bundledEntryCount = 180_412)

        compose.onNodeWithText("Preparing 180,412 entries · offline").assertExists()
        compose.onNodeWithText("Preparing the dictionary · offline").assertDoesNotExist()
    }

    @Test
    fun `the installer's count wins over the bundled asset's count`() {
        show(AppRootState.Installing(DictionaryPack.TIBETAN, progress = 0.2f, totalEntries = 180_500), bundledEntryCount = 180_412)

        compose.onNodeWithText("Preparing 180,500 entries · offline").assertExists()
    }

    @Test
    fun `a failure offers to try again`() {
        show(AppRootState.Installing(DictionaryPack.TIBETAN, progress = null, totalEntries = null, failure = IOException("disk full")))

        compose.onNodeWithText("Couldn't prepare the dictionary.").assertExists()
        compose.onNodeWithText("Make sure there is free storage space, then try again.").assertExists()
        compose.onNodeWithText("Preparing the dictionary · offline").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()

        assertThat(retries).isEqualTo(1)
    }

    @Test
    fun `a cancelled preparation is worded as an interruption`() {
        show(AppRootState.Installing(DictionaryPack.TIBETAN, progress = null, totalEntries = null, failure = CancellationException("stopped")))

        compose.onNodeWithText("Couldn't prepare the dictionary.").assertExists()
        compose.onNodeWithText("Preparation was interrupted.").assertExists()
        compose.onNodeWithText("Make sure there is free storage space, then try again.").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()

        assertThat(retries).isEqualTo(1)
    }

    private fun showOverlay(switching: SwitchState) {
        compose.setContent {
            DictTheme(darkTheme = false, language = AppLanguage.EN) {
                SwitchingOverlay(switching, onRetry = { retries++ }, onKeepCurrent = { keeps++ })
            }
        }
    }

    private var keeps = 0

    @Test
    fun `a Dzongkha install shows the Dzongkha count`() {
        show(AppRootState.Installing(DictionaryPack.DZONGKHA, progress = 0.2f, totalEntries = 12_345))

        compose.onNodeWithText("Preparing 12,345 entries · offline").assertExists()
    }

    @Test
    fun `an unknown Dzongkha count reads as the generic status`() {
        show(AppRootState.Installing(DictionaryPack.DZONGKHA, progress = 0.2f, totalEntries = null))

        compose.onNodeWithText("Preparing the dictionary · offline").assertExists()
    }

    @Test
    fun `overlay shows keep current only when failed`() {
        showOverlay(SwitchState.InProgress(DictionaryPack.DZONGKHA, 0.4f))

        compose.onNodeWithTag("switching_overlay").assertExists()
        compose.onNodeWithText("Keep current dictionary").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `a failed overlay offers try again and keep current`() {
        showOverlay(SwitchState.Failed(DictionaryPack.DZONGKHA, IOException("disk full")))

        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Keep current dictionary").performClick()

        assertThat(retries).isEqualTo(1)
        assertThat(keeps).isEqualTo(1)
    }
}
