package com.kharagedition.tibetandictionary.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.DirectionFeatures
import com.kharagedition.tibetandictionary.data.model.Script
import com.kharagedition.tibetandictionary.data.prefs.LineSpacing
import com.kharagedition.tibetandictionary.domain.DirectionSlots
import com.kharagedition.tibetandictionary.ui.model.ExampleUi
import com.kharagedition.tibetandictionary.ui.model.FolderUi
import com.kharagedition.tibetandictionary.ui.model.RelatedUi
import com.kharagedition.tibetandictionary.ui.model.SearchResultUi
import com.kharagedition.tibetandictionary.ui.model.SenseUi
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi
import com.kharagedition.tibetandictionary.ui.preview.DictPreview
import com.kharagedition.tibetandictionary.ui.theme.DictTheme

/** Light and dark previews in the Tibetan UI language (combine with `@PreviewLightDark` for the English pair). */
@Preview(name = "bo · light", locale = "bo")
@Preview(name = "bo · dark", locale = "bo", uiMode = Configuration.UI_MODE_NIGHT_YES)
internal annotation class BoPreview

/**
 * Review Focus 5: system font scale 2.0; the preview body adds the largest app text size (×1.3) and the widest
 * Tibetan line spacing (2.3) through [ExtremeScale].
 */
@Preview(name = "Extreme scale · font 2.0 · text ×1.3 · spacing 2.3", fontScale = 2f, widthDp = 360)
internal annotation class ExtremeScalePreview

/**
 * Preview frame for components: [DictPreview] in the preview's night mode and locale (en/bo), with screen
 * padding and block gaps between the stacked samples.
 */
@Composable
internal fun ComponentPreview(
    textScale: Float = 1f,
    lineSpacing: Float = LineSpacing.DEFAULT,
    edgeToEdge: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val language = AppLanguage.fromTag(ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language)
    DictPreview(dark = isSystemInDarkTheme(), language = language, textScale = textScale, lineSpacing = lineSpacing) {
        Column(
            if (edgeToEdge) Modifier else Modifier.padding(DictTheme.dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(if (edgeToEdge) 0.dp else DictTheme.dimens.blockGap),
            content = content,
        )
    }
}

/** [ComponentPreview] at the extreme accessibility settings of Review Focus 5. */
@Composable
internal fun ExtremeScale(edgeToEdge: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    ComponentPreview(EXTREME_TEXT_SCALE, LineSpacing.MAX, edgeToEdge, content)
}

private const val EXTREME_TEXT_SCALE = 1.3f

/** Realistic sample content for component previews. */
internal object PreviewSamples {
    private val features = DirectionFeatures(
        hasWylie = true,
        hasAudio = true,
        hasPartOfSpeech = true,
        hasLabels = true,
        hasExamples = true,
        hasRelated = true,
        hasWordOfTheDay = true,
        hasTruncated = false,
    )

    private fun direction(id: String, source: String, target: String, script: Script, position: Int) = Direction(
        id = id,
        sourceLang = source,
        targetLang = target,
        sourceScript = script,
        labelEn = id,
        labelBo = id,
        shortLabel = id,
        position = position,
        entryCount = 50_000,
        features = features,
    )

    val boEn = direction("bo-en", "bo", "en", Script.TIBETAN, 0)
    val boBo = direction("bo-bo", "bo", "bo", Script.TIBETAN, 1)
    val enBo = direction("en-bo", "en", "bo", Script.LATIN, 2)
    val zhBo = direction("zh-bo", "zh", "bo", Script.HAN, 3)
    val directions = listOf(boEn, boBo, enBo)

    val threeSlots = DirectionSlots(fixed = listOf(boEn, boBo, enBo), overflow = emptyList())
    val fourSlots = DirectionSlots(fixed = listOf(boEn, enBo), overflow = listOf(boBo, zhBo))

    val tashi = SearchResultUi(
        entryId = 1,
        direction = boEn,
        headword = "བཀྲ་ཤིས།",
        headwordIsTibetan = true,
        wylie = "bkra shis",
        matchRange = 0..3,
        headwordMatchRange = null,
        gloss = "1. auspicious, fortunate 2. good luck, blessing",
        showBadge = true,
    )
    val tashiDelek = tashi.copy(
        entryId = 2,
        headword = "བཀྲ་ཤིས་བདེ་ལེགས།",
        wylie = "bkra shis bde legs",
        gloss = "greeting: good luck, may all be auspicious",
        showBadge = false,
    )
    val compassion = SearchResultUi(
        entryId = 3,
        direction = enBo,
        headword = "compassion",
        headwordIsTibetan = false,
        wylie = null,
        matchRange = null,
        headwordMatchRange = 0..3,
        gloss = "སྙིང་རྗེ། ཐུགས་རྗེ།",
        showBadge = true,
    )

    val senses = listOf(
        SenseUi(1, null, "auspicious, fortunate, favourable", null),
        SenseUi(2, null, "good luck, good fortune, blessing", ExampleUi("བཀྲ་ཤིས་བདེ་ལེགས།", null, true)),
        SenseUi(3, "n.", "a common personal name, \"Tashi\"", null),
    )
    val tibetanSense = SenseUi(null, null, "དགེ་ལེགས་དང་ལྡན་པ། ལེགས་པའི་ལྟས།", null)

    val related = listOf(
        RelatedUi(10, boBo, "བདེ་ལེགས་དང་ལེགས་པའི་མཚན་མ།", true),
        RelatedUi(null, enBo, "auspicious", false),
    )

    val wotd = WordOfTheDayUi(
        entryId = 1,
        date = "16 Oct",
        headword = "བཀྲ་ཤིས།",
        wylie = "bkra shis",
        gloss = "auspicious; good fortune, blessing",
        hasAudio = true,
        audioFile = "bkra_shis.mp3",
        isSaved = false,
    )

    val folders = listOf(FolderUi(1, "Dharma", 14), FolderUi(2, "Daily", 11), FolderUi(3, "Grammar", 6))
}
