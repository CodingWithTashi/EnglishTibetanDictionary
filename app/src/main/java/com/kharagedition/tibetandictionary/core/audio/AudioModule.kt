package com.kharagedition.tibetandictionary.core.audio

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the real [AudioPlayer], which makes the `Optional<AudioPlayer>` the Entry screen takes present. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds abstract fun bind(impl: MediaPlayerAudioPlayer): AudioPlayer
}
