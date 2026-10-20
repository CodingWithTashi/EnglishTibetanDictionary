package com.kharagedition.tibetandictionary.core.audio

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets the Entry and Home view models take an `Optional<AudioPlayer>`: present while [AudioModule] binds the real
 * player, empty when a test uninstalls that module, in which case no speaker shows and Play does nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
interface AudioOptionalModule {
    @BindsOptionalOf fun optionalAudioPlayer(): AudioPlayer
}
