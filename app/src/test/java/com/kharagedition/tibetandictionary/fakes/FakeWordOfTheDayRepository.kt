package com.kharagedition.tibetandictionary.fakes

import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.repository.WordOfTheDayRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [WordOfTheDayRepository] that returns [entry] for every date; set it to null for "no word of the day". */
class FakeWordOfTheDayRepository(initial: Entry? = SampleData.tashiEntry) : WordOfTheDayRepository {
    val entry = MutableStateFlow(initial)

    override fun observe(date: LocalDate): Flow<Entry?> = entry

    override suspend fun forDate(date: LocalDate): Entry? = entry.value
}
