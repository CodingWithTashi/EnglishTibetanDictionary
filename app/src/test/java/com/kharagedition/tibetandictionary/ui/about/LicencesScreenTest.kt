package com.kharagedition.tibetandictionary.ui.about

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import java.security.MessageDigest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class LicencesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val databases = DictionaryDatabases { error("not opened in Licences tests") }.apply {
        activate(DictionaryPack.TIBETAN)
    }

    /** Shows the stateful screen and waits for the texts, which load off the main thread. */
    private fun show(onBack: () -> Unit = {}) {
        val viewModel = LicencesViewModel(databases)
        compose.setContent { DictTheme(darkTheme = false) { LicencesScreen(onBack, viewModel) } }
        compose.waitUntil(LOAD_TIMEOUT_MS) { compose.onAllNodes(hasText(GPL_TITLE)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `paragraphs reflow, keep heading lines and drop rules`() {
        val text = "     TITLE LINE\r\n     Version 3\r\n\r\n\r\nfirst line\n  second line\n\n" +
            "-----\nPREAMBLE\nThe goals\nof it.\n-----\n"

        assertThat(licenceParagraphs(text))
            .containsExactly("TITLE LINE\nVersion 3", "first line second line", "PREAMBLE\nThe goals of it.")
            .inOrder()
        assertThat(licenceParagraphs(" \n\n")).isEmpty()
    }

    @Test
    fun `data licence follows the pack`() {
        assertThat(bundledLicences(DictionaryPack.DZONGKHA).first())
            .isEqualTo("licenses/DDC-permission.txt" to R.string.licence_data_dz)
        assertThat(bundledLicences(DictionaryPack.TIBETAN).first())
            .isEqualTo("licenses/GPL-3.0.txt" to R.string.licence_data)
        assertThat(bundledLicences(DictionaryPack.DZONGKHA).drop(1))
            .isEqualTo(bundledLicences(DictionaryPack.TIBETAN).drop(1))
    }

    @Test
    fun `the view model has no pack until one is active, then follows it`() {
        val inactive = DictionaryDatabases { error("not opened in Licences tests") }
        val viewModel = LicencesViewModel(inactive)

        assertThat(viewModel.pack.value).isNull()

        inactive.activate(DictionaryPack.DZONGKHA)
        assertThat(viewModel.pack.value).isEqualTo(DictionaryPack.DZONGKHA)
    }

    @Test
    fun `no licence is shown while no pack is active`() {
        val inactive = DictionaryDatabases { error("not opened in Licences tests") }
        val viewModel = LicencesViewModel(inactive)
        compose.setContent { DictTheme(darkTheme = false) { LicencesScreen({}, viewModel) } }
        compose.waitForIdle()
        compose.onNodeWithText("Licences").assertIsDisplayed()
        compose.onAllNodes(hasText("Dictionary data", substring = true)).assertCountEquals(0)

        inactive.activate(DictionaryPack.TIBETAN)
        compose.waitUntil(LOAD_TIMEOUT_MS) { compose.onAllNodes(hasText(GPL_TITLE)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the GPL text is bundled verbatim`() {
        val bytes = context.assets.open("licenses/GPL-3.0.txt").use { it.readBytes() }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        // gnu.org/licenses/gpl-3.0.txt
        assertThat(sha256).isEqualTo("3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986")
    }

    @Test
    fun `every bundled licence is read, the data's first`() {
        val licences = context.assets.readLicences(DictionaryPack.TIBETAN)

        assertThat(licences.map { it.title })
            .containsExactly(R.string.licence_data, R.string.licence_font_jomolhari, R.string.licence_font_noto_sans)
            .inOrder()
        val (gpl, jomolhari, noto) = licences
        assertThat(gpl.paragraphs.first()).isEqualTo(GPL_TITLE)
        assertThat(gpl.paragraphs).contains("END OF TERMS AND CONDITIONS")
        assertThat(jomolhari.paragraphs.first()).contains("Copyright (c) 2006, Christopher J Fynn")
        assertThat(noto.paragraphs).contains("SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007")
        licences.forEach { licence -> assertThat(licence.paragraphs).isNotEmpty() }
    }

    @Test
    fun `the Dzongkha pack reads the DDC permission as its first licence`() {
        val licences = context.assets.readLicences(DictionaryPack.DZONGKHA)

        assertThat(licences.map { it.title }.first()).isEqualTo(R.string.licence_data_dz)
        assertThat(licences.first().paragraphs.first()).contains("Dzongkha Development Commission")
        licences.forEach { licence -> assertThat(licence.paragraphs).isNotEmpty() }
    }

    @Test
    fun `the screen shows each licence under its heading, and back pops it`() {
        var backs = 0
        show(onBack = { backs++ })

        compose.onNodeWithText("Licences").assertIsDisplayed()
        compose.onNodeWithText("Dictionary data · GNU General Public License, version 3").assertIsDisplayed()
        compose.onNodeWithText(GPL_TITLE).assertIsDisplayed()

        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText("END OF TERMS AND CONDITIONS"))
        list.performScrollToNode(hasText("Jomolhari font · SIL Open Font License 1.0"))
        list.performScrollToNode(hasText("Noto Sans font · SIL Open Font License 1.1"))
        list.performScrollToNode(hasText("SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007"))

        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(backs).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "bo")
    fun `the Tibetan UI titles the screen in Tibetan`() {
        show()

        compose.onNodeWithText("ཆོག་མཆན།").assertIsDisplayed()
        compose.onNodeWithText("ཚིག་མཛོད་ཀྱི་གཞི་གྲངས། · GNU General Public License, version 3").assertIsDisplayed()
    }

    private companion object {
        const val GPL_TITLE = "GNU GENERAL PUBLIC LICENSE\nVersion 3, 29 June 2007"
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
