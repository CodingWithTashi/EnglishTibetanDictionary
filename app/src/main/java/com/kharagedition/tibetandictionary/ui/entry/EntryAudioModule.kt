package com.kharagedition.tibetandictionary.ui.entry

import com.kharagedition.tibetandictionary.core.audio.AudioPlayer
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets [EntryViewModel] take an `Optional<AudioPlayer>`: no player is bound yet (T3.3 adds the MediaPlayer one),
 * so the graph compiles and Play does nothing until a `@Binds AudioPlayer` exists; then the optional is present.
 */
@Module
@InstallIn(SingletonComponent::class)
interface EntryAudioModule {
    @BindsOptionalOf
    fun optionalAudioPlayer(): AudioPlayer
}
