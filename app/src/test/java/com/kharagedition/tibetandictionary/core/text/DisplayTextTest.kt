package com.kharagedition.tibetandictionary.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DisplayTextTest {

    @Test
    fun tibetanTextIsTibetanScriptOnly() {
        assertThat(isTibetanText("བཀྲ་ཤིས་")).isTrue()
        assertThat(isTibetanText("བཀྲ་ཤིས་ བདེ་ལེགས།")).isTrue()
        // Latin punctuation and spaces do not count against it.
        assertThat(isTibetanText("(བཀྲ་ཤིས་), ཁང་པ།")).isTrue()
        assertThat(isTibetanText("༡༢")).isTrue()
    }

    @Test
    fun latinLettersOrDigitsMakeTextNotTibetan() {
        assertThat(isTibetanText("བཀྲ shis")).isFalse()
        assertThat(isTibetanText("ཁང་པ། 2")).isFalse()
        assertThat(isTibetanText("house")).isFalse()
        assertThat(isTibetanText("")).isFalse()
        assertThat(isTibetanText(" , ")).isFalse()
    }
}
