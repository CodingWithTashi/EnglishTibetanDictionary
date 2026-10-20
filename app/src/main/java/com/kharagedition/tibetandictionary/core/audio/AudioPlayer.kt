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

    companion object {
        /** The assets folder that holds every bundled pronunciation. */
        const val DICTIONARY_AUDIO_DIR = "audio"

        /**
         * The bundled pronunciation named by a dictionary row's `audio_file` (a bare file name such as
         * `bkra_shis.ogg`), which lives under `assets/[DICTIONARY_AUDIO_DIR]/`.
         */
        fun forDictionaryFile(name: String): AudioSource = Asset("$DICTIONARY_AUDIO_DIR/$name")
    }
}
