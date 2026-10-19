package com.kharagedition.tibetandictionary.data.model

/** Light/dark theme choice; [SYSTEM] follows the device setting. */
enum class Appearance {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        /** Parses a stored enum name; unknown or missing values fall back to [SYSTEM]. */
        fun fromName(name: String?): Appearance = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
