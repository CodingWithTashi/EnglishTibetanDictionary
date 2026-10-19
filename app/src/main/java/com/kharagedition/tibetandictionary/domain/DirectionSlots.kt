package com.kharagedition.tibetandictionary.domain

import com.kharagedition.tibetandictionary.data.model.Direction

/**
 * How the visible directions fill the three-slot direction switcher: [fixed] directions get their own segment;
 * when [overflow] is not empty the last segment becomes "More" and lists them. [moreLabel] is the overflow
 * direction currently selected, shown on the "More" segment instead of the word "More".
 *
 * Data only; the slotting rule (`compute`) is added by the Home + Search lane.
 */
data class DirectionSlots(
    val fixed: List<Direction>,
    val overflow: List<Direction>,
    val moreLabel: Direction? = null,
)
