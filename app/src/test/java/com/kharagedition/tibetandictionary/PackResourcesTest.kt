package com.kharagedition.tibetandictionary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class PackResourcesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultQualifiersDefaultToTibetan() {
        assertThat(context.getString(R.string.default_dictionary_pack)).isEqualTo("bo")
        assertThat(context.getString(R.string.app_name)).isEqualTo("Tibetan Dictionary")
    }

    @Test
    @Config(qualifiers = "mcc402")
    fun mcc402KeepsEnglishStringsAndDefaultsToDzongkha() {
        assertThat(context.getString(R.string.default_dictionary_pack)).isEqualTo("dz")
        assertThat(context.getString(R.string.app_name)).isEqualTo("Dzongkha Dictionary")
        assertThat(context.getString(R.string.app_name_tibetan)).isEqualTo("རྫོང་ཁའི་ཚིག་མཛོད།")
        assertThat(context.getString(R.string.tab_search)).isEqualTo("Search")
    }

    @Test
    @Config(qualifiers = "bo")
    fun boLocaleStillTibetan() {
        assertThat(context.getString(R.string.tab_search)).isEqualTo("འཚོལ།")
        assertThat(context.getString(R.string.default_dictionary_pack)).isEqualTo("bo")
    }
}
