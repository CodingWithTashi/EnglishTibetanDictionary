package com.kharagedition.tibetandictionary.domain

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.translation.Lang
import com.kharagedition.tibetandictionary.data.translation.LangPair
import org.junit.Test

/** Mirrors backend/functions/src/domain/normalize.ts; the server's cases are in its normalize tests. */
class TranslationTextTest {

    @Test
    fun `normalize trims and drops zero-width characters`() {
        assertThat(TranslationText.normalize("  བཀྲ་ཤིས\u200B  ")).isEqualTo("བཀྲ་ཤིས")
    }

    @Test
    fun `normalize keeps at most one blank line and uses LF`() {
        assertThat(TranslationText.normalize("a\r\n\r\n\r\nb")).isEqualTo("a\n\nb")
    }

    @Test
    fun `normalize collapses runs of horizontal space`() {
        assertThat(TranslationText.normalize("a\t  b")).isEqualTo("a b")
    }

    @Test
    fun `normalize trims each line`() {
        assertThat(TranslationText.normalize(" a \n  b\u3000")).isEqualTo("a\nb")
    }

    @Test
    fun `normalize replaces a lone surrogate`() {
        assertThat(TranslationText.normalize("\uD800x")).isEqualTo("\uFFFDx")
    }

    @Test
    fun `normalize keeps a surrogate pair`() {
        assertThat(TranslationText.normalize("\uD83D\uDE00")).isEqualTo("\uD83D\uDE00")
    }

    @Test
    fun `NFC decomposes gha, so it counts as two`() {
        assertThat(TranslationText.length(TranslationText.normalize("\u0F43"))).isEqualTo(2)
    }

    @Test
    fun `NFC decomposes the vowel sign ii, so it counts as two`() {
        assertThat(TranslationText.length(TranslationText.normalize("\u0F73"))).isEqualTo(2)
    }

    @Test
    fun `length counts code points`() {
        assertThat(TranslationText.length("ཀ".repeat(100))).isEqualTo(100)
    }

    @Test
    fun `length counts a surrogate pair once`() {
        assertThat(TranslationText.length("\uD83D\uDE00")).isEqualTo(1)
    }

    @Test
    fun `a Wylie source for Tibetan is not Tibetan script`() {
        assertThat(TranslationText.check("bkra shis", Lang.BO)).isEqualTo(ScriptCheck.NOT_TIBETAN_SCRIPT)
    }

    @Test
    fun `Tibetan with some Latin is fine for a Tibetan source`() {
        assertThat(TranslationText.check("བཀྲ་ཤིས། tashi", Lang.BO)).isEqualTo(ScriptCheck.OK)
    }

    @Test
    fun `Tibetan only is the wrong script for an English source`() {
        assertThat(TranslationText.check("བཀྲ་ཤིས།", Lang.EN)).isEqualTo(ScriptCheck.WRONG_SCRIPT)
    }

    @Test
    fun `English with some Tibetan is fine for an English source`() {
        assertThat(TranslationText.check("hello བཀྲ", Lang.EN)).isEqualTo(ScriptCheck.OK)
    }

    @Test
    fun `empty text is empty`() {
        assertThat(TranslationText.check("", Lang.EN)).isEqualTo(ScriptCheck.EMPTY)
    }

    @Test
    fun `Tibetan punctuation alone is not Tibetan script`() {
        assertThat(TranslationText.check("།་", Lang.BO)).isEqualTo(ScriptCheck.NOT_TIBETAN_SCRIPT)
    }

    @Test
    fun `Dzongkha follows the Tibetan script rule`() {
        assertThat(TranslationText.check("hello", Lang.DZ)).isEqualTo(ScriptCheck.NOT_TIBETAN_SCRIPT)
    }

    @Test
    fun `a long Tibetan sentence is a phrase`() {
        assertThat(TranslationText.isPhrase("ཁྱེད་རང་སྐུ་གཟུགས་བདེ་པོ་ཡིན་པས།", Lang.BO)).isTrue()
    }

    @Test
    fun `two Tibetan syllables are not a phrase`() {
        assertThat(TranslationText.isPhrase("བཀྲ་ཤིས", Lang.BO)).isFalse()
    }

    @Test
    fun `Tibetan ending in a shad is a phrase`() {
        assertThat(TranslationText.isPhrase("བཀྲ་ཤིས།", Lang.BO)).isTrue()
    }

    @Test
    fun `three Tibetan syllables split by spaces are a phrase`() {
        assertThat(TranslationText.isPhrase("བཀྲ ཤིས བདེ", Lang.BO)).isTrue()
    }

    @Test
    fun `five English words are a phrase`() {
        assertThat(TranslationText.isPhrase("May all beings be happy", Lang.EN)).isTrue()
    }

    @Test
    fun `two English words are not a phrase`() {
        assertThat(TranslationText.isPhrase("thank you", Lang.EN)).isFalse()
    }

    @Test
    fun `short English words are not tappable`() {
        assertThat(TranslationText.words("How are you?", Lang.EN)).containsExactly(
            OutputWord("How", null),
            OutputWord("are", null),
            OutputWord("you?", null),
        ).inOrder()
    }

    @Test
    fun `an English word searches without its punctuation`() {
        assertThat(TranslationText.words("Meditate on compassion.", Lang.EN).last())
            .isEqualTo(OutputWord("compassion.", "compassion"))
    }

    @Test
    fun `an English word keeps an inner apostrophe`() {
        assertThat(TranslationText.words("“Don’t”", Lang.EN)).containsExactly(OutputWord("“Don’t”", "Don’t"))
    }

    @Test
    fun `Tibetan words split on spaces and search without the trailing tsheg`() {
        assertThat(TranslationText.words("སེམས་ཅན་ ཐམས་ཅད་", Lang.BO)).containsExactly(
            OutputWord("སེམས་ཅན་", "སེམས་ཅན"),
            OutputWord("ཐམས་ཅད་", "ཐམས་ཅད"),
        ).inOrder()
    }

    @Test
    fun `Tibetan with no spaces is one word that searches without the final shad`() {
        assertThat(TranslationText.words("དགོན་པ་ག་པར་ཡོད།", Lang.BO))
            .containsExactly(OutputWord("དགོན་པ་ག་པར་ཡོད།", "དགོན་པ་ག་པར་ཡོད"))
    }

    @Test
    fun `a Tibetan-target token with no Tibetan letter is not tappable`() {
        assertThat(TranslationText.words("༡༢ Google", Lang.BO)).containsExactly(
            OutputWord("༡༢", null),
            OutputWord("Google", null),
        ).inOrder()
    }

    @Test
    fun `words split across lines`() {
        assertThat(TranslationText.words("Great\ncompassion", Lang.EN)).containsExactly(
            OutputWord("Great", "Great"),
            OutputWord("compassion", "compassion"),
        ).inOrder()
    }

    @Test
    fun `a pair parses from its key`() {
        assertThat(LangPair.fromKey("bo-en")).isEqualTo(LangPair(Lang.BO, Lang.EN))
    }

    @Test
    fun `an unknown language is no pair`() {
        assertThat(LangPair.fromKey("zh-en")).isNull()
    }

    @Test
    fun `a pair swaps and keys`() {
        assertThat(LangPair(Lang.DZ, Lang.EN).swapped().key).isEqualTo("en-dz")
    }
}
