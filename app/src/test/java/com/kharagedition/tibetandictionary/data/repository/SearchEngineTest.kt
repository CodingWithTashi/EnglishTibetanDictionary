package com.kharagedition.tibetandictionary.data.repository

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.model.Script
import org.junit.Test

/** The did-you-mean prefix candidates; the database steps are covered by [DictionaryRepositoryImplTest]. */
class SearchEngineTest {

    @Test
    fun tibetanCutsToThePreviousTshegThenDropsCharacters() {
        assertThat(shortenedPrefixes("བཀྲ་ཤིསས", Script.TIBETAN))
            .containsExactly("བཀྲ་", "བཀྲ", "བཀ", "བ").inOrder()
        // A trailing tsheg is not the "previous" one; at most four characters are dropped after the cut.
        assertThat(shortenedPrefixes("བཀྲ་ཤིས་བདེ་", Script.TIBETAN))
            .containsExactly("བཀྲ་ཤིས་", "བཀྲ་ཤིས", "བཀྲ་ཤི", "བཀྲ་ཤ", "བཀྲ་").inOrder()
    }

    @Test
    fun tibetanWithoutInnerTshegDropsCharacters() {
        assertThat(shortenedPrefixes("བཀྲ", Script.TIBETAN)).containsExactly("བཀ", "བ").inOrder()
        assertThat(shortenedPrefixes("བཀྲ་", Script.TIBETAN)).containsExactly("བཀྲ", "བཀ", "བ").inOrder()
        // Mixed input: spaces left at the end of a candidate are trimmed.
        assertThat(shortenedPrefixes("བཀྲ shis", Script.TIBETAN))
            .containsExactly("བཀྲ shi", "བཀྲ sh", "བཀྲ s", "བཀྲ").inOrder()
        assertThat(shortenedPrefixes("་", Script.TIBETAN)).isEmpty()
    }

    @Test
    fun latinDropsUpToFourCharacters() {
        assertThat(shortenedPrefixes("pillar", Script.LATIN)).containsExactly("pilla", "pill", "pil", "pi").inOrder()
        assertThat(shortenedPrefixes("bkra s", Script.LATIN)).containsExactly("bkra", "bkr", "bk").inOrder()
        assertThat(shortenedPrefixes("ab", Script.LATIN)).containsExactly("a")
        assertThat(shortenedPrefixes("a", Script.LATIN)).isEmpty()
        assertThat(shortenedPrefixes("", Script.LATIN)).isEmpty()
    }
}
