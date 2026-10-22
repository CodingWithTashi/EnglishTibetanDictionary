package com.kharagedition.tibetandictionary.ui.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VersionLineTest {

    @get:Rule
    val compose = createComposeRule()

    private fun line(packName: String, count: Int?): String {
        var line = ""
        compose.setContent { DictTheme(darkTheme = false) { line = versionLine(packName, "3.0.0", count) } }
        compose.waitForIdle()
        return line
    }

    @Test
    fun `version line names the pack`() {
        assertThat(line("Dzongkha Dictionary", 12_345))
            .isEqualTo("Dzongkha Dictionary 3.0.0 · 12,345 entries · offline")
    }

    @Test
    fun `version line leaves the count out when unknown`() {
        assertThat(line("Tibetan Dictionary", null)).isEqualTo("Tibetan Dictionary 3.0.0 · offline")
    }
}
