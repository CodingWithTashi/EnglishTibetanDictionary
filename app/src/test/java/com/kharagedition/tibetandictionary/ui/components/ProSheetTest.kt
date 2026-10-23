package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProSheetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `coming soon is disabled`() {
        compose.setContent { DictTheme(darkTheme = false) { ProSheet(onDismiss = {}) } }

        compose.onNodeWithTag("pro_sheet").assertExists()
        compose.onNodeWithText("Translate without limits").assertExists()
        compose.onNodeWithText("Unlimited").assertExists()
        compose.onNodeWithTag("pro_sheet_coming_soon").assertIsNotEnabled()
    }
}
