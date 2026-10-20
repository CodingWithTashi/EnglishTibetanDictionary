package com.kharagedition.tibetandictionary.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioSourceTest {

    @Test
    fun `a dictionary audio file is an asset under the audio folder`() {
        assertThat(AudioSource.forDictionaryFile("bkra_shis.ogg")).isEqualTo(AudioSource.Asset("audio/bkra_shis.ogg"))
    }
}
