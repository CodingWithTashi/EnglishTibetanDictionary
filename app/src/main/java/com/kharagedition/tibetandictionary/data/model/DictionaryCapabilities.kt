package com.kharagedition.tibetandictionary.data.model

import java.time.Instant

/**
 * What the installed dictionary data offers; data-dependent UI derives its visibility from this. [pack] is the pack
 * the rest was read from, so a screen showing the pack's name beside its numbers never mixes two packs.
 */
data class DictionaryCapabilities(
    val pack: DictionaryPack,
    val directions: List<Direction>,
    val dataVersion: String,
    val builtAt: Instant?,
    val attribution: Attribution,
    val wotdDirectionId: String?,
) {
    val totalEntries: Int get() = directions.sumOf { it.entryCount }

    /** Directions in the fixed switcher slots (position 0–2), in position order. */
    val primary: List<Direction> get() = directions.filter { it.position <= 2 }.sortedBy { it.position }

    /** Directions behind the switcher's "More" slot (position ≥3), in position order. */
    val overflow: List<Direction> get() = directions.filter { it.position > 2 }.sortedBy { it.position }

    val hasOverflow: Boolean get() = directions.any { it.position > 2 }

    /** True when any direction has Wylie transliterations. */
    val hasWylie: Boolean get() = directions.any { it.features.hasWylie }
}
