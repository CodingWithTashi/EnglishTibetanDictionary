package com.kharagedition.tibetandictionary.ui.entry

import com.kharagedition.tibetandictionary.core.audio.AudioPlayer
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets [EntryViewModel] take an `Optional<AudioPlayer>`: the optional is present when core/audio binds
 * `MediaPlayerAudioPlayer` (`AudioModule`); without that binding the graph still compiles and Play does nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
interface EntryAudioModule {
    @BindsOptionalOf
    fun optionalAudioPlayer(): AudioPlayer
}
