package com.kharagedition.tibetandictionary.core.text

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.locale.AppLanguage
import org.junit.Test

class TibetanNumeralsTest {

    @Test
    fun convertsDigitString() {
        assertThat(TibetanNumerals.toTibetan("42")).isEqualTo("༤༢")
    }

    @Test
    fun convertsEveryDigit() {
        assertThat(TibetanNumerals.toTibetan("0123456789")).isEqualTo("༠༡༢༣༤༥༦༧༨༩")
    }

    @Test
    fun convertsInts() {
        assertThat(TibetanNumerals.toTibetan(0)).isEqualTo("༠")
        assertThat(TibetanNumerals.toTibetan(108)).isEqualTo("༡༠༨")
        assertThat(TibetanNumerals.toTibetan(-42)).isEqualTo("-༤༢")
    }

    @Test
    fun passesNonDigitsThrough() {
        assertThat(TibetanNumerals.toTibetan("3.0 · 1:30")).isEqualTo("༣.༠ · ༡:༣༠")
    }

    @Test
    fun formatCountGroupsThousandsAndUsesTibetanDigitsInBo() {
        assertThat(TibetanNumerals.formatCount(180412, AppLanguage.EN)).isEqualTo("180,412")
        assertThat(TibetanNumerals.formatCount(180412, AppLanguage.BO)).isEqualTo("༡༨༠,༤༡༢")
        assertThat(TibetanNumerals.formatCount(42, AppLanguage.EN)).isEqualTo("42")
        assertThat(TibetanNumerals.formatCount(42, AppLanguage.BO)).isEqualTo("༤༢")
    }
}
