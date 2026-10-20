package com.kharagedition.tibetandictionary.data.model

/** A full dictionary entry. An entry may have no senses and only [related] links (a redirect). */
data class Entry(
    val summary: EntrySummary,
    val senses: List<Sense>,
    val related: List<RelatedEntry>,
)

/** A numbered sense (1-based [number]) with its sub-senses in [items]. */
data class Sense(
    val number: Int,
    val partOfSpeech: String?,
    val labels: List<String>,
    val note: String?,
    val sourceTags: List<String>,
    val items: List<SenseItem>,
)

/** One gloss of a sense; [subNumber] is set for sub-senses (ཀཿ ཁཿ … or 1> 2>). */
data class SenseItem(
    val subNumber: Int?,
    val gloss: String,
    val glossLang: String,
    val isTruncated: Boolean,
    val examples: List<Example>,
)

data class Example(
    val text: String,
    val translation: String?,
    val audioFile: String?,
)

/** A see-also or synonym link; [targetEntryId] is null when the target is not in the data (tap runs a search). */
data class RelatedEntry(
    val targetEntryId: Long?,
    val headword: String,
    val wylie: String?,
    val relation: Relation,
)

enum class Relation { SEE_ALSO, SYNONYM }
