package com.kharagedition.tibetandictionary.domain

import com.kharagedition.tibetandictionary.data.model.Direction

/**
 * How the visible directions fill the three-slot direction switcher: [fixed] directions get their own segment;
 * when [overflow] is not empty the last segment becomes "More" and lists them. [moreLabel] is the overflow
 * direction currently selected, shown on the "More" segment instead of the word "More".
 */
data class DirectionSlots(
    val fixed: List<Direction>,
    val overflow: List<Direction>,
    val moreLabel: Direction? = null,
) {
    companion object {

        /** Segments in the switcher; with more directions than this, the last one becomes "More". */
        private const val SEGMENTS = 3

        /**
         * Slots for [all] directions. The default direction always takes the first segment, so the direction the
         * user looks up most (onboarding step 1, Profile → Default direction) is where the switcher starts.
         * - **Visible**: only directions with entries (`entryCount > 0`), in canonical order (by `position`).
         * - **Up to three**: all of them are [fixed]: the default direction first, then the others in canonical
         *   order (default en-bo → en-bo, bo-en, bo-bo); no overflow.
         * - **More than three**: [fixed] = the default direction, then its reverse (source and target swapped),
         *   or, when it has none, the next direction after it in canonical order (wrapping round); every other
         *   direction goes to [overflow] in canonical order.
         * - **Default**: [defaultDirection], so a stored default that is gone or empty falls back to the first
         *   visible direction, which then takes the first segment.
         * - **More label**: the direction [selectedId] when it is in [overflow], else null.
         * - **None visible**: empty slots.
         */
        fun compute(all: List<Direction>, defaultId: String, selectedId: String?): DirectionSlots {
            val default = defaultDirection(all, defaultId)
                ?: return DirectionSlots(fixed = emptyList(), overflow = emptyList())
            val visible = visible(all)
            if (visible.size <= SEGMENTS) {
                return DirectionSlots(fixed = listOf(default) + (visible - default), overflow = emptyList())
            }

            val index = visible.indexOf(default)
            val partner = visible.firstOrNull { it.isReverseOf(default) } ?: visible[(index + 1) % visible.size]
            val fixed = listOf(default, partner)
            val overflow = visible.filter { it !in fixed }
            return DirectionSlots(fixed, overflow, moreLabel = overflow.firstOrNull { it.id == selectedId })
        }

        /**
         * The direction stored as default ([defaultId]) when it is visible (has entries), else the first visible
         * direction in canonical order (a data release removed or emptied it); null when none is visible.
         */
        fun defaultDirection(all: List<Direction>, defaultId: String): Direction? {
            val visible = visible(all)
            return visible.firstOrNull { it.id == defaultId } ?: visible.firstOrNull()
        }

        private fun visible(all: List<Direction>): List<Direction> =
            all.filter { it.entryCount > 0 }.sortedBy { it.position }

        private fun Direction.isReverseOf(other: Direction): Boolean =
            id != other.id && sourceLang == other.targetLang && targetLang == other.sourceLang
    }
}
