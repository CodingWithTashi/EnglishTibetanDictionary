package com.kharagedition.tibetandictionary.core.audio

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the real [AudioPlayer]. The Entry and Home view models take an `Optional<AudioPlayer>` (present through
 * this binding), so their tests can leave the player out: no speaker shows and Play does nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds abstract fun bind(impl: MediaPlayerAudioPlayer): AudioPlayer

    @BindsOptionalOf abstract fun optional(): AudioPlayer
}
