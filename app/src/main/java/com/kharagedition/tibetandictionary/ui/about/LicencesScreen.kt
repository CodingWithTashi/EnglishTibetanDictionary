package com.kharagedition.tibetandictionary.ui.about

import android.content.res.AssetManager
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.ui.components.BoPreview
import com.kharagedition.tibetandictionary.ui.components.ComponentPreview
import com.kharagedition.tibetandictionary.ui.components.MixedText
import com.kharagedition.tibetandictionary.ui.components.ScreenTitle
import com.kharagedition.tibetandictionary.ui.theme.DictTheme
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Space above a licence's heading, and between its paragraphs. */
private val HeadingGap = 24.dp
private val ParagraphGap = 8.dp

internal const val LICENCES_SCREEN_TAG = "licences_screen"

/** The licence texts bundled in `assets/licenses`, in the order shown, with the heading each is shown under. */
internal enum class BundledLicence(val asset: String, @param:StringRes val title: Int) {
    DATA("licenses/GPL-3.0.txt", R.string.licence_data),
    JOMOLHARI("licenses/OFL-Jomolhari.txt", R.string.licence_font_jomolhari),
    NOTO_SANS("licenses/OFL-NotoSans.txt", R.string.licence_font_noto_sans),
}

/** One licence as shown: its heading ([title]) and its text in [paragraphs]. */
@Immutable
data class LicenceUi(@param:StringRes val title: Int, val paragraphs: List<String>)

/**
 * [text]'s paragraphs (split at blank lines), each reflowed to the screen: hard line breaks become spaces, except
 * after an all-capitals heading line ("PREAMBLE"), and lines with no letter or digit (the "-----" rules) are dropped.
 */
internal fun licenceParagraphs(text: String): List<String> =
    text.replace("\r\n", "\n")
        .split(BLANK_LINE)
        .map { paragraph ->
            val lines = paragraph.lines().map { it.trim() }.filter { line -> line.any { it.isLetterOrDigit() } }
            buildString {
                lines.forEachIndexed { index, line ->
                    if (index > 0) append(if (lines[index - 1].isHeading()) '\n' else ' ')
                    append(line)
                }
            }
        }
        .filter { it.isNotEmpty() }

private val BLANK_LINE = Regex("\\n\\s*\\n")

private fun String.isHeading(): Boolean = any { it.isLetter() } && none { it.isLowerCase() }

/** Reads every [BundledLicence]; one that cannot be read is shown with its heading only. */
internal fun AssetManager.readLicences(): List<LicenceUi> = BundledLicence.entries.map { licence ->
    val text = try {
        open(licence.asset).bufferedReader().use { it.readText() }
    } catch (_: IOException) {
        ""
    }
    LicenceUi(licence.title, licenceParagraphs(text))
}

/** Licences, pushed from About: the full texts of the dictionary data's and the fonts' licences. */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val assets = LocalContext.current.assets
    val licences by produceState<List<LicenceUi>?>(null, assets) {
        value = withContext(Dispatchers.IO) { assets.readLicences() }
    }
    LicencesScreen(licences, onBack)
}

/**
 * The stateless Licences screen: back, the title, then each licence's heading and its text (13sp, muted), in a lazy
 * list since the GPL alone runs to some 200 paragraphs. [licences] is null while the texts load.
 *
 * Test tag: `licences_screen`.
 */
@Composable
fun LicencesScreen(licences: List<LicenceUi>?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DictTheme.colors
    val type = DictTheme.type
    val dimens = DictTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(LICENCES_SCREEN_TAG)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        AboutBackRow(onBack)
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = dimens.screenPadding),
        ) {
            item { ScreenTitle(stringResource(R.string.licences)) }
            licences.orEmpty().forEach { licence ->
                item(key = licence.title) {
                    MixedText(
                        stringResource(licence.title),
                        type.bodySmallMedium,
                        type.tibetanInline16,
                        Modifier
                            .padding(top = HeadingGap)
                            .semantics { heading() },
                        color = colors.ink,
                    )
                }
                items(licence.paragraphs) { paragraph ->
                    Text(paragraph, Modifier.padding(top = ParagraphGap), colors.muted, style = type.secondary)
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@PreviewLightDark
@BoPreview
@Composable
private fun LicencesPreview() {
    val sample = licenceParagraphs(
        """
                    GNU GENERAL PUBLIC LICENSE
                       Version 3, 29 June 2007

 Everyone is permitted to copy and distribute verbatim copies
 of this license document, but changing it is not allowed.
        """,
    )
    ComponentPreview(edgeToEdge = true) {
        LicencesScreen(
            listOf(LicenceUi(R.string.licence_data, sample), LicenceUi(R.string.licence_font_noto_sans, sample)),
            onBack = {},
        )
    }
}
