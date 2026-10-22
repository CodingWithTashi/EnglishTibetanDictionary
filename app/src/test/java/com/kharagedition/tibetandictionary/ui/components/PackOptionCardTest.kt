package com.kharagedition.tibetandictionary.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.core.text.TibetanNumerals
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.resources
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text widths
class PackOptionCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(language: AppLanguage) {
        compose.setContent {
            DictTheme(darkTheme = false, language = language) {
                Column {
                    DictionaryPack.entries.forEach {
                        PackOptionCard(
                            it,
                            selected = it == DictionaryPack.TIBETAN,
                            onClick = {},
                            modifier = Modifier.testTag("card_${it.id}"),
                        )
                    }
                }
            }
        }
    }

    private fun assertStacked(language: AppLanguage) {
        for (pack in DictionaryPack.entries) {
            val card = compose.onNodeWithTag("card_${pack.id}").getBoundsInRoot()
            val name = compose
                .onNodeWithText(context.getString(pack.resources.name), substring = true, useUnmergedTree = true)
                .getBoundsInRoot()
            val count = TibetanNumerals.formatCount(context.resources.getInteger(pack.resources.entryCount), language)
            val description = compose
                .onNodeWithText(context.getString(pack.resources.description, count), useUnmergedTree = true)
                .getBoundsInRoot()
            assertThat(description.top.value).isAtLeast(name.bottom.value)
            assertThat(card.height).isAtMost(160.dp)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `pack cards stack the description under the name in English`() {
        show(AppLanguage.EN)
        assertStacked(AppLanguage.EN)
    }

    @Test
    @Config(qualifiers = "bo-w360dp-h800dp")
    fun `pack cards stack the description under the name in Tibetan`() {
        show(AppLanguage.BO)
        assertStacked(AppLanguage.BO)
    }
}
