package com.kharagedition.tibetandictionary.ui.entry

import com.kharagedition.tibetandictionary.core.text.DOT_SEPARATOR
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.core.text.isTibetanText
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.Example
import com.kharagedition.tibetandictionary.data.model.RelatedEntry
import com.kharagedition.tibetandictionary.data.model.Sense
import com.kharagedition.tibetandictionary.ui.components.LABEL_KEY_SEPARATOR
import com.kharagedition.tibetandictionary.ui.model.ExampleUi
import com.kharagedition.tibetandictionary.ui.model.RelatedUi
import com.kharagedition.tibetandictionary.ui.model.SenseUi

/** Parts of speech written out in full, so they take no abbreviation dot. */
private val UNABBREVIATED_POS = setOf("prefix", "suffix")

/**
 * This entry ready to draw, in [direction]. [targetDirections] maps every see-also target id found in the data to
 * its direction id; a target missing from it is opened as a search, and its badge shows a direction from
 * [directions] (the current one when unknown).
 *
 * Sub-senses become separate cards; a sense's number, part of speech, label and note go on its first card only,
 * and its later cards keep the number column's indent. Numbers show only when more than one sense remains, and
 * parts of speech only when the entry has several. Labels every sense shares become the entry's [EntryUi.label];
 * a sense shows only the labels it adds. Every example of a gloss is kept. Glosses that are blank with no example
 * are dropped, so a redirect-only entry has no empty card.
 */
internal fun Entry.toUi(
    direction: Direction,
    directions: Map<String, Direction>,
    targetDirections: Map<Long, String>,
): EntryUi {
    val visibleSenses = senses
        .map { sense -> sense.copy(items = sense.items.filter { it.gloss.isNotBlank() || it.examples.isNotEmpty() }) }
        .filter { it.items.isNotEmpty() }
    val numbered = visibleSenses.size > 1
    val partsOfSpeech = summary.partOfSpeech.filter { it.isNotBlank() }
    val perSensePos = partsOfSpeech.size > 1
    val sharedLabels = visibleSenses.sharedLabels()
    return EntryUi(
        id = summary.id,
        direction = direction,
        headword = summary.headword,
        headwordIsTibetan = isTibetanText(summary.headword),
        wylie = summary.wylie?.takeIf { it.isNotBlank() },
        phonetic = summary.phonetic?.takeIf { it.isNotBlank() },
        partsOfSpeech = partsOfSpeech.map(::abbreviatePartOfSpeech),
        label = sharedLabels.joinLabels(),
        senses = visibleSenses.flatMap { sense ->
            val ownLabels = sense.labels.filter { it.isNotBlank() && it !in sharedLabels }.distinct().joinLabels()
            val note = sense.note?.let(::displayNote)
            sense.items.mapIndexed { index, item ->
                val first = index == 0
                SenseUi(
                    number = sense.number.takeIf { numbered && first },
                    partOfSpeech = sense.partOfSpeech
                        ?.takeIf { perSensePos && first && it.isNotBlank() }
                        ?.let(::abbreviatePartOfSpeech),
                    text = item.gloss,
                    examples = item.examples.map { it.toUi() },
                    label = ownLabels.takeIf { first },
                    note = note.takeIf { first },
                    reserveNumberColumn = numbered && !first,
                )
            }
        },
        related = related.map { it.toUi(direction, directions, targetDirections) },
        hasAudio = summary.audioFile != null,
        audioFile = summary.audioFile,
        isTruncated = summary.isTruncated || senses.any { sense -> sense.items.any { it.isTruncated } },
    )
}

/** "n" → "n.", "vt" → "vt."; dotted, written-out and Tibetan values are kept as they are. */
internal fun abbreviatePartOfSpeech(pos: String): String {
    val value = pos.trim()
    val keep = value.isEmpty() || value.endsWith('.') || value in UNABBREVIATED_POS || TibetanScript.hasTibetan(value)
    return if (keep) value else "$value."
}

/** The label keys every sense carries, in the first sense's order; empty when they share none. */
private fun List<Sense>.sharedLabels(): List<String> {
    val first = firstOrNull() ?: return emptyList()
    return first.labels.filter { label -> label.isNotBlank() && all { label in it.labels } }.distinct()
}

/** Label keys as the data joins them ("adm,law"); null when there are none. */
private fun List<String>.joinLabels(): String? = takeIf { it.isNotEmpty() }?.joinToString(LABEL_KEY_SEPARATOR)

/**
 * A sense note as shown: its "; "-joined parts without the source's markers, which have no letter or digit (the
 * en-bo table's bare "*"); null when nothing is left.
 */
internal fun displayNote(note: String): String? =
    note.split(';')
        .map { it.trim() }
        .filter { part -> part.any(Char::isLetterOrDigit) }
        .joinToString("; ")
        .ifEmpty { null }

private fun Example.toUi() = ExampleUi(text = text, translation = translation, textIsTibetan = isTibetanText(text))

private fun RelatedEntry.toUi(
    current: Direction,
    directions: Map<String, Direction>,
    targetDirections: Map<Long, String>,
): RelatedUi {
    val targetDirectionId = targetEntryId?.let(targetDirections::get)
    return RelatedUi(
        entryId = targetEntryId?.takeIf { targetDirectionId != null },
        direction = targetDirectionId?.let(directions::get) ?: current,
        headword = headword,
        isTibetan = isTibetanText(headword),
    )
}

/** The headword with its Wylie in brackets when there is one: "བཀྲ་ཤིས་ (bkra shis)". */
internal fun EntryUi.headwordWithWylie(): String = if (wylie != null) "$headword ($wylie)" else headword

/** Plain-text share: "{headword} ({wylie}) — {sense} · {sense}". */
internal fun EntryUi.shareText(): String {
    val glosses = senses.map { it.text }.filter { it.isNotBlank() }
    if (glosses.isEmpty()) return headwordWithWylie()
    return "${headwordWithWylie()} — ${glosses.joinToString(DOT_SEPARATOR)}"
}

/** A correction e-mail to the developer naming this entry. */
internal fun EntryUi.correctionEmail(address: String): EntryEffect.OpenEmail = EntryEffect.OpenEmail(
    address = address,
    subject = "Correction: $headword (#$id)",
    body = "${headwordWithWylie()}$DOT_SEPARATOR${direction.id} #$id\n\n",
)
