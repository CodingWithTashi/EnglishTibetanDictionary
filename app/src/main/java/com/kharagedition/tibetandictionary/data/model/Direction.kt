package com.kharagedition.tibetandictionary.data.model

/**
 * One dictionary direction (`bo-en`, `bo-bo`, `en-bo`, …). [position] 0–2 is a fixed switcher slot, ≥3 goes to
 * "More". Directions with no entries are never exposed.
 */
data class Direction(
    val id: String,
    val sourceLang: String,
    val targetLang: String,
    val sourceScript: Script,
    val labelEn: String,
    val labelBo: String,
    val shortLabel: String,
    val position: Int,
    val entryCount: Int,
    val features: DirectionFeatures,
)

/** What a direction's data contains; each flag is a build-time count > 0. UI shows a slot only when its flag is set. */
data class DirectionFeatures(
    val hasWylie: Boolean,
    val hasAudio: Boolean,
    val hasPartOfSpeech: Boolean,
    val hasLabels: Boolean,
    val hasExamples: Boolean,
    val hasRelated: Boolean,
    val hasWordOfTheDay: Boolean,
    val hasTruncated: Boolean,
)
