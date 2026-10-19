package com.kharagedition.tibetandictionary.core.audio

/** Plays pronunciation audio. Starting a new [play] stops the previous one. */
interface AudioPlayer {
    fun play(source: AudioSource)
    fun stop()
}

/** Where a pronunciation file lives. */
sealed interface AudioSource {
    /** A file bundled in the APK's assets, e.g. `audio/bkra_shis.ogg`. */
    data class Asset(val path: String) : AudioSource

    /** A file on device storage, by absolute path. */
    data class File(val path: String) : AudioSource
}
