package com.kharagedition.tibetandictionary.data.model

/** The list-row view of an entry: headword, transliteration and a short gloss. */
data class EntrySummary(
    val id: Long,
    val directionId: String,
    val headword: String,
    val wylie: String?,
    val glossPreview: String,
    val partOfSpeech: List<String>,
    val phonetic: String?,
    val audioFile: String?,
    val isTruncated: Boolean,
    val senseCount: Int,
) {
    val hasAudio: Boolean get() = audioFile != null
}
