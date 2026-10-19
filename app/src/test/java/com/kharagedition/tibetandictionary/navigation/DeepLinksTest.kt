package com.kharagedition.tibetandictionary.navigation

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepLinksTest {

    private fun parse(uri: String): DeepLink? = DeepLinks.parse(Uri.parse(uri))

    @Test
    fun `an entry link opens that entry`() {
        assertThat(parse("app://entry/42")).isEqualTo(DeepLink.OpenEntry(42))
    }

    @Test
    fun `a search link opens search with the decoded query`() {
        assertThat(parse("app://search?query=bkra%20shis")).isEqualTo(DeepLink.OpenSearch("bkra shis"))
        assertThat(parse("app://search?query=%E0%BD%96%E0%BD%80%E0%BE%B2"))
            .isEqualTo(DeepLink.OpenSearch("བཀྲ"))
    }

    @Test
    fun `a search link without a query opens an empty search`() {
        assertThat(parse("app://search")).isEqualTo(DeepLink.OpenSearch(""))
    }

    @Test
    fun `links that are not ours or are malformed are ignored`() {
        assertThat(parse("app://entry/abc")).isNull()
        assertThat(parse("app://entry")).isNull()
        assertThat(parse("app://entry/1/2")).isNull()
        assertThat(parse("app://settings")).isNull()
        assertThat(parse("https://entry/42")).isNull()
        assertThat(DeepLinks.parse(null as Uri?)).isNull()
    }

    @Test
    fun `an intent is parsed from its data`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("app://entry/9"))

        assertThat(DeepLinks.parse(intent)).isEqualTo(DeepLink.OpenEntry(9))
        assertThat(DeepLinks.parse(Intent(Intent.ACTION_MAIN))).isNull()
    }

    @Test
    fun `the patterns declared on the destinations use the same scheme and hosts`() {
        assertThat(DeepLinks.ENTRY_PATTERN).isEqualTo("app://entry/{entryId}")
        assertThat(DeepLinks.SEARCH_PATTERN).isEqualTo("app://search?query={query}")
    }
}
