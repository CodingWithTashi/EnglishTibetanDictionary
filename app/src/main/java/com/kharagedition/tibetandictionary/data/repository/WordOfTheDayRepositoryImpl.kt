package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.data.model.Entry
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Stub so the Hilt graph compiles; L1 (T1.6) replaces the bodies. */
@Singleton
class WordOfTheDayRepositoryImpl @Inject constructor() : WordOfTheDayRepository {
    override fun observe(date: LocalDate): Flow<Entry?> = TODO("L1")

    override suspend fun forDate(date: LocalDate): Entry? = TODO("L1")
}
