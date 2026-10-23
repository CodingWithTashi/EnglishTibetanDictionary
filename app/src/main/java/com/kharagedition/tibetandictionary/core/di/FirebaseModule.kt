package com.kharagedition.tibetandictionary.core.di

import com.kharagedition.tibetandictionary.core.analytics.Analytics
import com.kharagedition.tibetandictionary.core.analytics.FirebaseAnalyticsLogger
import com.kharagedition.tibetandictionary.data.auth.AuthBackend
import com.kharagedition.tibetandictionary.data.auth.AuthRepository
import com.kharagedition.tibetandictionary.data.auth.CredentialManagerGoogleSource
import com.kharagedition.tibetandictionary.data.auth.FirebaseAuthBackend
import com.kharagedition.tibetandictionary.data.auth.FirebaseAuthRepository
import com.kharagedition.tibetandictionary.data.auth.GoogleCredentialSource
import com.kharagedition.tibetandictionary.data.translation.CallableInvoker
import com.kharagedition.tibetandictionary.data.translation.CallableTranslationApi
import com.kharagedition.tibetandictionary.data.translation.FirebaseCallableInvoker
import com.kharagedition.tibetandictionary.data.translation.TranslationApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Everything backed by Firebase SDKs, so Robolectric tests can swap the whole set for fakes. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseModule {
    @Binds @Singleton abstract fun bindAnalytics(impl: FirebaseAnalyticsLogger): Analytics
    @Binds @Singleton abstract fun bindCallableInvoker(impl: FirebaseCallableInvoker): CallableInvoker
    @Binds @Singleton abstract fun bindTranslationApi(impl: CallableTranslationApi): TranslationApi
    @Binds @Singleton abstract fun bindAuthBackend(impl: FirebaseAuthBackend): AuthBackend
    @Binds abstract fun bindGoogleCredentialSource(impl: CredentialManagerGoogleSource): GoogleCredentialSource
    @Binds @Singleton abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
}
