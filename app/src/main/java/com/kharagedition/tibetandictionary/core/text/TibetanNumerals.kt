package com.kharagedition.tibetandictionary.core.text

import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import java.util.Locale

/** Converts Western digits to Tibetan digits (༠-༩) and formats counts for the app language. */
object TibetanNumerals {
    private const val ZERO = '༠'

    fun toTibetan(n: Int): String = toTibetan(n.toString())

    /** Replaces each digit 0-9 in [digits] with its Tibetan digit; every other character is kept. */
    fun toTibetan(digits: String): String = buildString(digits.length) {
        for (c in digits) append(if (c in '0'..'9') ZERO + (c - '0') else c)
    }

    /** [n] with thousands grouped ("180,412"), in Tibetan digits when [language] uses them ("༡༨༠,༤༡༢"). */
    fun formatCount(n: Int, language: AppLanguage): String {
        val grouped = String.format(Locale.US, "%,d", n)
        return if (language.usesTibetanDigits) toTibetan(grouped) else grouped
    }
}
