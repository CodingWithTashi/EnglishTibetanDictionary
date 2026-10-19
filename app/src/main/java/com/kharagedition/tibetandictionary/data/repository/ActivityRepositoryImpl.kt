package com.kharagedition.tibetandictionary.data.repository

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Stub so the Hilt graph compiles; L1 (T1.7) replaces the bodies. */
@Singleton
class ActivityRepositoryImpl @Inject constructor() : ActivityRepository {
    override suspend fun markSearch(): Unit = TODO("L1")

    override suspend fun markEntryOpened(): Unit = TODO("L1")

    override fun observeActiveDays(limit: Int): Flow<List<LocalDate>> = TODO("L1")
}
