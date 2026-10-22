package com.kharagedition.tibetandictionary

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
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
        assertThat(context.getString(R.string.tab_search)).isEqualTo("Search")
        // The names the installing screen and About show follow the pack, not the SIM.
        assertThat(context.getString(R.string.pack_title_bo)).isEqualTo("བོད་ཡིག་ཚིག་མཛོད།")
        assertThat(context.getString(R.string.pack_title_dz)).isEqualTo("རྫོང་ཁའི་ཚིག་མཛོད།")
    }

    @Test
    @Config(qualifiers = "xxxhdpi")
    fun launcherIconIsAdaptive() {
        assertThat(context.getDrawable(R.mipmap.ic_launcher)).isInstanceOf(AdaptiveIconDrawable::class.java)
        assertThat(context.getDrawable(R.mipmap.ic_launcher_round)).isInstanceOf(AdaptiveIconDrawable::class.java)
    }

    @Test
    @Config(qualifiers = "mcc402-xxxhdpi")
    fun mcc402LauncherIconIsAdaptiveToo() {
        // The Bhutan PNGs (for API 24 and 25) must not hide the adaptive icon, or launchers shrink it into a plate.
        assertThat(context.getDrawable(R.mipmap.ic_launcher)).isInstanceOf(AdaptiveIconDrawable::class.java)
        assertThat(context.getDrawable(R.mipmap.ic_launcher_round)).isInstanceOf(AdaptiveIconDrawable::class.java)
    }

    @Test
    @Config(qualifiers = "bo")
    fun boLocaleStillTibetan() {
        assertThat(context.getString(R.string.tab_search)).isEqualTo("འཚོལ།")
        assertThat(context.getString(R.string.default_dictionary_pack)).isEqualTo("bo")
    }
}
