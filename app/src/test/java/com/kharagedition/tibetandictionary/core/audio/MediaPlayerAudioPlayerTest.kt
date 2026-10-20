package com.kharagedition.tibetandictionary.core.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.fakes.RecordingErrorReporter
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaPlayerAudioPlayerTest {

    private val errors = RecordingErrorReporter()

    /** Every [MediaPlayer] the audio player asked for, in order; each is a relaxed mock that records its calls. */
    private val created = mutableListOf<MediaPlayer>()
    private val newPlayer: () -> MediaPlayer = { mockk<MediaPlayer>(relaxed = true).also(created::add) }

    private val descriptor = mockk<AssetFileDescriptor>(relaxed = true)
    private val assets = mockk<AssetManager> { every { openFd(ASSET) } returns descriptor }
    private val context = mockk<Context> { every { assets } returns this@MediaPlayerAudioPlayerTest.assets }

    private val audio = MediaPlayerAudioPlayer(context, errors, newPlayer)

    /** Captures the prepared listener [player] was given and fires it, as the framework does once it is ready. */
    private fun prepare(player: MediaPlayer) {
        val listener = slot<MediaPlayer.OnPreparedListener>()
        verify { player.setOnPreparedListener(capture(listener)) }
        listener.captured.onPrepared(player)
    }

    @Test
    fun `plays an asset through its file descriptor once prepared`() {
        audio.play(AudioSource.Asset(ASSET))

        val player = created.single()
        verifyOrder {
            player.setDataSource(descriptor)
            descriptor.close()
            player.prepareAsync()
        }
        verify(exactly = 0) { player.start() }

        prepare(player)

        verify(exactly = 1) { player.start() }
        assertThat(errors.recorded).isEmpty()
    }

    @Test
    fun `plays speech through the media stream`() {
        audio.play(AudioSource.Asset(ASSET))

        val attributes = slot<AudioAttributes>()
        verify { created.single().setAudioAttributes(capture(attributes)) }
        assertThat(attributes.captured.usage).isEqualTo(AudioAttributes.USAGE_MEDIA)
        assertThat(attributes.captured.contentType).isEqualTo(AudioAttributes.CONTENT_TYPE_SPEECH)
    }

    @Test
    fun `plays a file by its path`() {
        audio.play(AudioSource.File(FILE))

        val player = created.single()
        verifyOrder {
            player.setDataSource(FILE)
            player.prepareAsync()
        }
        verify(exactly = 0) { context.assets }

        prepare(player)

        verify(exactly = 1) { player.start() }
    }

    @Test
    fun `a new play stops and releases the previous one`() {
        audio.play(AudioSource.Asset(ASSET))
        val first = created.single()
        prepare(first)

        audio.play(AudioSource.File(FILE))

        val second = created[1]
        verifyOrder {
            first.stop()
            first.release()
            second.setDataSource(FILE)
        }
        verify(exactly = 0) { second.release() }
    }

    @Test
    fun `stop stops and releases the player once`() {
        audio.play(AudioSource.File(FILE))
        val player = created.single()
        prepare(player)

        audio.stop()
        audio.stop()

        verifyOrder {
            player.stop()
            player.release()
        }
        verify(exactly = 1) { player.stop() }
        verify(exactly = 1) { player.release() }
    }

    @Test
    fun `stop with nothing playing does nothing`() {
        audio.stop()

        assertThat(created).isEmpty()
        assertThat(errors.recorded).isEmpty()
    }

    @Test
    fun `stop before the player is prepared only releases it`() {
        audio.play(AudioSource.File(FILE))
        val player = created.single()

        audio.stop()

        verify(exactly = 0) { player.stop() }
        verify(exactly = 1) { player.release() }
    }

    @Test
    fun `a finished clip releases its player`() {
        audio.play(AudioSource.File(FILE))
        val player = created.single()
        prepare(player)
        val completion = slot<MediaPlayer.OnCompletionListener>()
        verify { player.setOnCompletionListener(capture(completion)) }

        completion.captured.onCompletion(player)
        audio.stop()

        verify(exactly = 1) { player.release() }
        verify(exactly = 0) { player.stop() }
    }

    @Test
    fun `an error from the player is reported and swallowed`() {
        audio.play(AudioSource.File(FILE))
        val player = created.single()
        prepare(player)
        val error = slot<MediaPlayer.OnErrorListener>()
        verify { player.setOnErrorListener(capture(error)) }

        val handled = error.captured.onError(player, MediaPlayer.MEDIA_ERROR_UNKNOWN, MediaPlayer.MEDIA_ERROR_IO)
        audio.stop()

        assertThat(handled).isTrue()
        assertThat(errors.recorded.single()).isInstanceOf(IOException::class.java)
        verify(exactly = 0) { player.stop() }
        verify(exactly = 1) { player.release() }
    }

    @Test
    fun `a player that throws is reported, released and swallowed`() {
        val failure = IOException("unsupported format")
        val throwing = mockk<MediaPlayer>(relaxed = true) { every { setDataSource(FILE) } throws failure }
        val failing = MediaPlayerAudioPlayer(context, errors) { throwing }

        failing.play(AudioSource.File(FILE))

        assertThat(errors.recorded).containsExactly(failure)
        verify(exactly = 0) { throwing.prepareAsync() }
        verify(exactly = 1) { throwing.release() }
    }

    @Test
    fun `a missing asset is reported and swallowed`() {
        val realContext = ApplicationProvider.getApplicationContext<Context>()
        val withRealAssets = MediaPlayerAudioPlayer(realContext, errors, newPlayer)

        withRealAssets.play(AudioSource.Asset("audio/missing.ogg"))

        assertThat(errors.recorded.single()).isInstanceOf(FileNotFoundException::class.java)
        val player = created.single()
        verify(exactly = 0) { player.prepareAsync() }
        verify(exactly = 1) { player.release() }
    }

    private companion object {
        const val ASSET = "audio/bkra_shis.ogg"
        const val FILE = "/data/user/0/app/files/audio/bkra_shis.ogg"
    }
}
