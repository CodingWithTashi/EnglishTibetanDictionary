package com.kharagedition.tibetandictionary.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.kharagedition.tibetandictionary.data.model.DictionaryPack

/**
 * The dictionary pack the app is showing, provided by the app root. Pack-specific copy and samples (the greeting, the
 * reading preview) default to it; a screen that shows another pack, such as onboarding's choice, passes that one.
 */
val LocalActivePack = staticCompositionLocalOf { DictionaryPack.TIBETAN }

/**
 * The entry the reading preview draws for a pack: a headword with its Wylie, numbered English glosses and a line in
 * the pack's own language, taken from that pack's data so the preview reads as the dictionary does. [shortGloss] is
 * the one-word gloss beside the headword in Profile's reading row.
 */
@Immutable
data class PackSample(
    val headword: String,
    val wylie: String,
    val glosses: List<String>,
    val definition: String,
    val shortGloss: String,
)

/** The sample entry of this pack; see [PackSample]. */
val DictionaryPack.sample: PackSample
    get() = when (this) {
        DictionaryPack.TIBETAN -> TibetanSample
        DictionaryPack.DZONGKHA -> DzongkhaSample
    }

private val TibetanSample = PackSample(
    headword = "བཀྲ་ཤིས།",
    wylie = "bkra shis",
    glosses = listOf("auspicious, fortunate", "good luck, blessing"),
    definition = "བདེ་ལེགས་དང་ལེགས་པའི་མཚན་མ།",
    shortGloss = "auspicious",
)

// DDC dz-en and dz-dz entries for བཀྲ་ཤིས་ (the first sentence of the Dzongkha definition).
private val DzongkhaSample = PackSample(
    headword = "བཀྲ་ཤིས།",
    wylie = "bkra shis",
    glosses = listOf("auspicious, good, prosperity"),
    definition = "ལེགས་ཤོམ། དགེ་བ་ཕུན་སུམ་ཚོགས་མི་ཅིག་ལུ་སླབཔ་ཨིན།",
    shortGloss = "auspicious",
)
