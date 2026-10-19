package com.kharagedition.tibetandictionary.core.di

import com.kharagedition.tibetandictionary.core.error.CrashlyticsErrorReporter
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.core.speech.SpeechAvailability
import com.kharagedition.tibetandictionary.core.speech.SystemSpeechAvailability
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.core.time.SystemAppClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds @Singleton abstract fun bindClock(impl: SystemAppClock): AppClock
    @Binds @Singleton abstract fun bindErrorReporter(impl: CrashlyticsErrorReporter): ErrorReporter
    @Binds @Singleton abstract fun bindSpeechAvailability(impl: SystemSpeechAvailability): SpeechAvailability
}
