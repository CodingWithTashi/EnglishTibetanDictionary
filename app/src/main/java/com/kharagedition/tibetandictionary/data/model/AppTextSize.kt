package com.kharagedition.tibetandictionary.data.model

/** App-wide text size. [scale] multiplies every font size and line height (on top of the system font scale). */
enum class AppTextSize(val scale: Float) {
    SMALL(0.85f),
    DEFAULT(1f),
    LARGE(1.15f),
    LARGEST(1.3f),
    ;

    companion object {
        /** Parses a stored enum name; unknown or missing values fall back to [DEFAULT]. */
        fun fromName(name: String?): AppTextSize = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
