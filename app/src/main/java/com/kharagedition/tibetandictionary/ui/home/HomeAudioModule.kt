package com.kharagedition.tibetandictionary.ui.home

import com.kharagedition.tibetandictionary.core.audio.AudioPlayer
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets [HomeViewModel] take an `Optional<AudioPlayer>`: it is present once core/audio binds the real player;
 * without that binding the graph still compiles, the word of the day shows no speaker and Play does nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeAudioModule {
    @BindsOptionalOf
    abstract fun audioPlayer(): AudioPlayer
}
