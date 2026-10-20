package com.kharagedition.tibetandictionary.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.text.DOT_SEPARATOR

/** How the data joins several label keys of one sense (`senses.label`, "adm,law"); the UI models keep that form. */
const val LABEL_KEY_SEPARATOR = ","

/**
 * Keys written as words by the pipeline (the Tibetan tables' labels and the bo-en "Def."/"Div." prefixes), with
 * their abbreviated English aliases: shown through a string resource, so bo reads the source's own Tibetan label.
 */
@StringRes
private fun usageLabelRes(key: String): Int? = when (key) {
    "poetic", "poet" -> R.string.usage_label_poetic
    "archaic" -> R.string.usage_label_archaic
    "dialect", "dial" -> R.string.usage_label_dialect
    "sanskrit", "skt" -> R.string.usage_label_sanskrit
    "chinese" -> R.string.usage_label_chinese
    "mongolian" -> R.string.usage_label_mongolian
    "definition" -> R.string.usage_label_definition
    "division" -> R.string.usage_label_division
    else -> null
}

/** English-source keys that are names or whole words, written as they are rather than with an abbreviation dot. */
private val WRITTEN_LABELS = mapOf(
    "us" to "US",
    "tv" to "TV",
    "brit" to "Brit.",
    "fr" to "Fr.",
    "gk" to "Gk.",
    "lat" to "Lat.",
    "scot" to "Scot.",
    "intl" to "int'l",
)
private val WHOLE_WORD_LABELS = setOf(
    "billiards", "boxing", "cricket", "falconry", "golf", "law", "logic", "mining", "optics", "printing", "sports",
    "tennis",
)

/** One label key as shown: a string resource, a fixed spelling, or the source abbreviation with a dot ("astrol."). */
private fun usageLabel(key: String, resources: Resources): String =
    usageLabelRes(key)?.let(resources::getString)
        ?: WRITTEN_LABELS[key]
        ?: if (key in WHOLE_WORD_LABELS || key.endsWith('.')) key else "$key."

/**
 * The display labels of comma-joined usage-label [keys] ("adm,law" → ["adm.", "law"]), in order and without
 * repeats: the one place that turns the data's canonical label keys into badge and caption text.
 */
fun usageLabels(keys: String, resources: Resources): List<String> =
    keys.split(LABEL_KEY_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { usageLabel(it, resources) }
        .distinct()

/** [usageLabels] in the current app language. */
@Composable
fun usageLabels(keys: String): List<String> = usageLabels(keys, LocalResources.current)

/** The [usageLabels] of [keys] as one caption ("adm,law" → "adm. · law"). */
fun usageLabelText(keys: String, resources: Resources): String = usageLabels(keys, resources).joinToString(DOT_SEPARATOR)

/** [usageLabelText] in the current app language. */
@Composable
fun usageLabelText(keys: String): String = usageLabelText(keys, LocalResources.current)
