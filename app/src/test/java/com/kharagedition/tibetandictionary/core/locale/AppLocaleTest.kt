package com.kharagedition.tibetandictionary.core.locale

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class AppLocaleTest {

    /** On API 33+ AppCompat keeps the per-app locale through a running activity's LocaleManager. */
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java)
        .also { it.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat) }
        .setup()

    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        activity.close()
    }

    @Test
    fun `no per-app locale reads as null, not the system language`() {
        assertThat(AppLocale.currentTag()).isNull()
        assertThat(AppLocale.current()).isNull()
        assertThat(AppCompatLocaleApplier().currentLanguage()).isNull()
    }

    @Test
    fun `the applied language is read back`() {
        AppCompatLocaleApplier().setLanguage(AppLanguage.BO)

        assertThat(AppLocale.currentTag()).isEqualTo("bo")
        assertThat(AppLocale.current()).isEqualTo(AppLanguage.BO)
        assertThat(AppCompatLocaleApplier().currentLanguage()).isEqualTo(AppLanguage.BO)
    }
}
