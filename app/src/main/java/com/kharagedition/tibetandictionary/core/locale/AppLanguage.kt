package com.kharagedition.tibetandictionary.core.locale

/**
 * The two UI languages the app ships. [tag] is the BCP-47 tag used for per-app locales
 * and resource qualifiers (`values/` = en, `values-bo/` = bo).
 */
enum class AppLanguage(val tag: String) {
    EN("en"),
    BO("bo"),
    ;

    /** True when numbers that represent counts are rendered with Tibetan digits (༠-༩). */
    val usesTibetanDigits: Boolean get() = this == BO

    companion object {
        /** The shipped language whose tag starts [tag]; unknown or missing tags fall back to [EN]. */
        fun fromTag(tag: String?): AppLanguage = shipped(tag) ?: EN

        /**
         * Language to persist so stored prefs match the UI: the per-app locale [appLocaleTag] when it is a
         * shipped language different from [stored], else null (nothing to change).
         */
        fun syncTarget(appLocaleTag: String?, stored: AppLanguage): AppLanguage? =
            shipped(appLocaleTag)?.takeIf { it != stored }

        private fun shipped(tag: String?): AppLanguage? =
            entries.firstOrNull { tag != null && tag.startsWith(it.tag, ignoreCase = true) }
    }
}
