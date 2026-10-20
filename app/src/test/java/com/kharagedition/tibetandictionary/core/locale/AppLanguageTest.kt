package com.kharagedition.tibetandictionary.core.locale

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLanguageTest {

    @Test
    fun fromTagMatchesShippedLanguagesByPrefix() {
        assertThat(AppLanguage.fromTag("en")).isEqualTo(AppLanguage.EN)
        assertThat(AppLanguage.fromTag("en-US")).isEqualTo(AppLanguage.EN)
        assertThat(AppLanguage.fromTag("bo")).isEqualTo(AppLanguage.BO)
        assertThat(AppLanguage.fromTag("bo-CN")).isEqualTo(AppLanguage.BO)
        assertThat(AppLanguage.fromTag("BO")).isEqualTo(AppLanguage.BO)
    }

    @Test
    fun fromTagFallsBackToEnglish() {
        assertThat(AppLanguage.fromTag(null)).isEqualTo(AppLanguage.EN)
        assertThat(AppLanguage.fromTag("")).isEqualTo(AppLanguage.EN)
        assertThat(AppLanguage.fromTag("fr-FR")).isEqualTo(AppLanguage.EN)
    }

    @Test
    fun syncTargetIsTheLocaleLanguageWhenItDiffersFromStored() {
        assertThat(AppLanguage.syncTarget("bo-CN", stored = AppLanguage.EN)).isEqualTo(AppLanguage.BO)
        assertThat(AppLanguage.syncTarget("en-GB", stored = AppLanguage.BO)).isEqualTo(AppLanguage.EN)
    }

    @Test
    fun syncTargetIsNullWhenNothingToChange() {
        assertThat(AppLanguage.syncTarget("bo", stored = AppLanguage.BO)).isNull()
        assertThat(AppLanguage.syncTarget(null, stored = AppLanguage.BO)).isNull()
        assertThat(AppLanguage.syncTarget("fr-FR", stored = AppLanguage.BO)).isNull()
    }

    @Test
    fun onlyTibetanUsesTibetanDigits() {
        assertThat(AppLanguage.BO.usesTibetanDigits).isTrue()
        assertThat(AppLanguage.EN.usesTibetanDigits).isFalse()
    }
}
