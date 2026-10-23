package com.kharagedition.tibetandictionary.core.di

import com.kharagedition.tibetandictionary.data.billing.BillingRepository
import com.kharagedition.tibetandictionary.data.billing.RevenueCatBillingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Pro subscriptions over RevenueCat, so Robolectric tests can swap them for a fake. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds @Singleton abstract fun bindBillingRepository(impl: RevenueCatBillingRepository): BillingRepository
}
