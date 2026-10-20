package com.kharagedition.tibetandictionary.ui.entry

import com.kharagedition.tibetandictionary.core.text.DOT_SEPARATOR
import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.core.text.isTibetanText
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.Example
import com.kharagedition.tibetandictionary.data.model.RelatedEntry
import com.kharagedition.tibetandictionary.data.model.Sense
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
 * Sub-senses become separate cards; a sense's number and part of speech go on its first card only, and its later
 * cards keep the number column's indent. Numbers show only when more than one sense remains, and parts of speech
 * only when the entry has several. Glosses that are blank with no example are dropped, so a redirect-only entry has
 * no empty card.
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
    return EntryUi(
        id = summary.id,
        direction = direction,
        headword = summary.headword,
        headwordIsTibetan = isTibetanText(summary.headword),
        wylie = summary.wylie?.takeIf { it.isNotBlank() },
        phonetic = summary.phonetic?.takeIf { it.isNotBlank() },
        partsOfSpeech = partsOfSpeech.map(::abbreviatePartOfSpeech),
        label = visibleSenses.sharedLabel(),
        senses = visibleSenses.flatMap { sense ->
            sense.items.mapIndexed { index, item ->
                SenseUi(
                    number = sense.number.takeIf { numbered && index == 0 },
                    partOfSpeech = sense.partOfSpeech
                        ?.takeIf { perSensePos && index == 0 && it.isNotBlank() }
                        ?.let(::abbreviatePartOfSpeech),
                    text = item.gloss,
                    example = item.examples.firstOrNull()?.toUi(),
                    reserveNumberColumn = numbered && index > 0,
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

/** The labels every sense carries, in the first sense's order; null when they share none. */
private fun List<Sense>.sharedLabel(): String? {
    val first = firstOrNull() ?: return null
    val shared = first.labels.filter { label -> label.isNotBlank() && all { label in it.labels } }.distinct()
    return shared.takeIf { it.isNotEmpty() }?.joinToString(DOT_SEPARATOR)
}

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
