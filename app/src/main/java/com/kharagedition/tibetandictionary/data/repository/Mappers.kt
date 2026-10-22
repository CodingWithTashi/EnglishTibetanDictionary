package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.local.entity.DirectionEntity
import com.kharagedition.tibetandictionary.data.local.entity.EntryEntity
import com.kharagedition.tibetandictionary.data.local.entity.ExampleEntity
import com.kharagedition.tibetandictionary.data.local.entity.MetaEntity
import com.kharagedition.tibetandictionary.data.local.entity.RelatedEntryEntity
import com.kharagedition.tibetandictionary.data.local.relation.EntryWithDetails
import com.kharagedition.tibetandictionary.data.local.relation.SenseWithExamples
import com.kharagedition.tibetandictionary.data.model.Attribution
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.DirectionFeatures
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Example
import com.kharagedition.tibetandictionary.data.model.RelatedEntry
import com.kharagedition.tibetandictionary.data.model.Relation
import com.kharagedition.tibetandictionary.data.model.Script
import com.kharagedition.tibetandictionary.data.model.Sense
import com.kharagedition.tibetandictionary.data.model.SenseItem
import java.time.Instant
import java.time.OffsetDateTime

/** `meta` keys the repositories read (dictionary.db; written by the build pipeline). */
internal object MetaKeys {
    const val DATA_VERSION = "data_version"
    const val BUILT_AT = "built_at"
    const val SOURCE_NAME = "source_name"
    const val SOURCE_AUTHOR = "source_author"
    const val SOURCE_URL = "source_url"
    const val SOURCE_LICENSE = "source_license"
    const val ATTRIBUTION_TEXT = "attribution_text"
    const val WOTD_SEED = "wotd_seed"
    const val WOTD_COUNT = "wotd_count"
    const val WOTD_DIRECTION_ID = "wotd_direction_id"
}

/** The [Script] of a `directions.source_script` code (`tibt`, `latn`, `hani`); null for any other code. */
internal fun scriptOf(sourceScript: String): Script? = when (sourceScript) {
    "tibt" -> Script.TIBETAN
    "latn" -> Script.LATIN
    "hani" -> Script.HAN
    else -> null
}

/** The domain direction; each feature flag is its build-time count > 0. Null when the script code is unknown. */
internal fun DirectionEntity.toDomain(): Direction? {
    val script = scriptOf(sourceScript) ?: return null
    return Direction(
        id = id,
        sourceLang = sourceLang,
        targetLang = targetLang,
        sourceScript = script,
        labelEn = labelEn,
        labelBo = labelBo,
        shortLabel = shortLabel,
        position = position,
        entryCount = entryCount,
        features = DirectionFeatures(
            hasWylie = wylieCount > 0,
            hasAudio = audioCount > 0,
            hasPartOfSpeech = posCount > 0,
            hasLabels = labelCount > 0,
            hasExamples = exampleCount > 0,
            hasRelated = relatedCount > 0,
            hasWordOfTheDay = wotdCount > 0,
            hasTruncated = truncatedCount > 0,
        ),
    )
}

internal fun List<MetaEntity>.toMetaMap(): Map<String, String?> = associate { it.key to it.value }

/**
 * Capabilities from the visible direction rows and `meta`: directions with no entries or an unknown script are
 * dropped; [DictionaryCapabilities.wotdDirectionId] is null unless `wotd_count` is a positive number.
 */
internal fun capabilitiesOf(
    pack: DictionaryPack,
    directions: List<DirectionEntity>,
    meta: Map<String, String?>,
): DictionaryCapabilities =
    DictionaryCapabilities(
        pack = pack,
        directions = directions.filter { it.entryCount > 0 }.mapNotNull { it.toDomain() },
        dataVersion = meta[MetaKeys.DATA_VERSION].orEmpty(),
        builtAt = parseInstant(meta[MetaKeys.BUILT_AT]),
        attribution = Attribution(
            sourceName = meta[MetaKeys.SOURCE_NAME].orEmpty(),
            author = meta[MetaKeys.SOURCE_AUTHOR].orEmpty(),
            url = meta[MetaKeys.SOURCE_URL].orEmpty(),
            license = meta[MetaKeys.SOURCE_LICENSE].orEmpty(),
            text = meta[MetaKeys.ATTRIBUTION_TEXT].orEmpty(),
        ),
        wotdDirectionId = WotdParams.from(meta)?.directionId,
    )

/** An ISO-8601 instant (`2026-10-19T06:30:00Z`) or offset date-time (`…+02:00`); null when absent or unparsable. */
internal fun parseInstant(value: String?): Instant? {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Instant.parse(text) }
        .recoverCatching { OffsetDateTime.parse(text).toInstant() }
        .getOrNull()
}

internal fun EntryEntity.toSummary(): EntrySummary = EntrySummary(
    id = id,
    directionId = directionId,
    headword = headword,
    wylie = wylie,
    glossPreview = glossPreview,
    partOfSpeech = splitList(partOfSpeech, ','),
    phonetic = phonetic,
    audioFile = audioFile,
    isTruncated = isTruncated,
    senseCount = senseCount,
)

/**
 * The full entry. Rows are grouped into one [Sense] per `sense_no` (ascending); its [Sense.items] are the rows ordered
 * by `sub_no` (a row without one first). Sense-level fields merge the group's rows: the first part of speech, the
 * distinct labels (`,`-joined keys) and source tags (`;`-joined), and the distinct notes joined with "; ". Examples
 * and related links follow `position`. An entry may have no senses (a redirect with related links only).
 */
internal fun EntryWithDetails.toDomain(): Entry = Entry(
    summary = entry.toSummary(),
    senses = senses
        .sortedWith(SENSE_ORDER)
        .groupBy { it.sense.senseNo }
        .map { (number, rows) -> senseOf(number, rows) },
    related = related.sortedWith(compareBy({ it.position }, { it.id })).map { it.toDomain() },
)

/** A synonym (`synonym`), antonym (`antonym`) or see-also (`see_also`, or any unknown relation) link. */
internal fun RelatedEntryEntity.toDomain(): RelatedEntry = RelatedEntry(
    targetEntryId = targetEntryId,
    headword = targetHeadword,
    wylie = targetWylie,
    relation = when (relation) {
        "synonym" -> Relation.SYNONYM
        "antonym" -> Relation.ANTONYM
        else -> Relation.SEE_ALSO
    },
)

private val SENSE_ORDER: Comparator<SenseWithExamples> =
    compareBy<SenseWithExamples> { it.sense.senseNo }
        .thenBy(nullsFirst()) { it.sense.subNo }
        .thenBy { it.sense.id }

private fun senseOf(number: Int, rows: List<SenseWithExamples>): Sense {
    val senses = rows.map { it.sense }
    return Sense(
        number = number,
        partOfSpeech = senses.firstNotNullOfOrNull { it.partOfSpeech?.trim()?.takeIf(String::isNotEmpty) },
        labels = senses.flatMap { splitList(it.label, ',') }.distinct(),
        note = senses.mapNotNull { it.note?.trim()?.takeIf(String::isNotEmpty) }.distinct()
            .joinToString("; ").ifEmpty { null },
        sourceTags = senses.flatMap { splitList(it.sourceTag, ';') }.distinct(),
        items = rows.map { row ->
            SenseItem(
                subNumber = row.sense.subNo,
                gloss = row.sense.gloss,
                glossLang = row.sense.glossLang,
                isTruncated = row.sense.isTruncated,
                examples = row.examples.sortedWith(compareBy({ it.position }, { it.id })).map { it.toDomain() },
            )
        },
    )
}

private fun ExampleEntity.toDomain(): Example = Example(text = text, translation = translation, audioFile = audioFile)

/** [value] split on [separator], trimmed, without empty parts; empty for null. */
private fun splitList(value: String?, separator: Char): List<String> =
    value?.split(separator)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
