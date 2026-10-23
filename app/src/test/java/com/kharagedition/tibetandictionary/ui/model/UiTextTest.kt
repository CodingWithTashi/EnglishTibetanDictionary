package com.kharagedition.tibetandictionary.ui.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class UiTextTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `a count is formatted in Western digits in English`() {
        assertThat(UiText(R.string.signed_in_left, counts = listOf(7)).resolve(resources))
            .isEqualTo("Signed in · 7 translations left today")
    }

    @Test
    @Config(qualifiers = "bo")
    fun `a count is formatted in Tibetan digits in Tibetan`() {
        assertThat(UiText(R.string.signed_in_left, counts = listOf(7)).resolve(resources)).contains("༧")
    }

    @Test
    fun `a text without args resolves as is`() {
        assertThat(UiText(R.string.signed_in).resolve(resources)).isEqualTo("Signed in")
    }
}
