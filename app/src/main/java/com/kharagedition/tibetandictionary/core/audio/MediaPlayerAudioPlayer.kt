package com.kharagedition.tibetandictionary.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AudioPlayer] on a fresh [MediaPlayer] per clip, prepared asynchronously and started once ready. Starting a new
 * clip, [stop], the end of the clip and a playback error each release the current player. Failures (a missing asset,
 * an unsupported file, a player error) go to [errors] and never reach the caller. Call it from the main thread; the
 * player's callbacks arrive there too.
 */
@Singleton
class MediaPlayerAudioPlayer internal constructor(
    private val context: Context,
    private val errors: ErrorReporter,
    private val newPlayer: () -> MediaPlayer,
) : AudioPlayer {

    @Inject
    constructor(@ApplicationContext context: Context, errors: ErrorReporter) : this(context, errors, { MediaPlayer() })

    private var current: MediaPlayer? = null
    private var started = false

    override fun play(source: AudioSource) {
        stop()
        try {
            val player = newPlayer()
            current = player
            player.setAudioAttributes(SPEECH)
            player.setOnPreparedListener {
                if (it === current) {
                    it.start()
                    started = true
                }
            }
            player.setOnCompletionListener { if (it === current) release(stopPlayback = false) }
            player.setOnErrorListener { mp, what, extra ->
                errors.record(IOException("MediaPlayer error (what=$what, extra=$extra) playing $source"))
                if (mp === current) release(stopPlayback = false)
                true
            }
            when (source) {
                is AudioSource.Asset -> context.assets.openFd(source.path).use { player.setDataSource(it) }
                is AudioSource.File -> player.setDataSource(source.path)
            }
            player.prepareAsync()
        } catch (e: Exception) {
            errors.record(e)
            release(stopPlayback = false)
        }
    }

    override fun stop() = release(stopPlayback = true)

    /** Releases the current player, if any; [stopPlayback] stops it first when it has started. */
    private fun release(stopPlayback: Boolean) {
        val player = current ?: return
        val playing = started && stopPlayback
        current = null
        started = false
        try {
            if (playing) player.stop()
        } catch (e: IllegalStateException) {
            errors.record(e)
        }
        player.release()
    }

    private companion object {
        val SPEECH: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
