package com.kharagedition.tibetandictionary.data.prefs

import kotlin.math.roundToInt

/** Single home of the Tibetan line-spacing range; Tibetan line heights scale by `lineSpacing / DEFAULT`. */
object LineSpacing {
    const val MIN = 1.5f
    const val MAX = 2.3f
    const val STEP = 0.1f
    const val DEFAULT = 1.9f

    /** [value] clamped to [MIN]..[MAX] and snapped to the nearest [STEP]; NaN gives [DEFAULT]. */
    fun clamp(value: Float): Float {
        if (value.isNaN()) return DEFAULT
        return (value.coerceIn(MIN, MAX) * STEPS_PER_UNIT).roundToInt() / STEPS_PER_UNIT
    }

    private const val STEPS_PER_UNIT = 10f
}
