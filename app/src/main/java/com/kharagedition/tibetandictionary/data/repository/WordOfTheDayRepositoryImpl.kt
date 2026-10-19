package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.data.local.dao.EntryDao
import com.kharagedition.tibetandictionary.data.local.dao.MetaDao
import com.kharagedition.tibetandictionary.data.model.Entry
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [WordOfTheDayRepository] over `dictionary.db`: the entry of `wotd_direction_id` whose `wotd_rank` is the date's
 * index ([WotdParams]); null when `wotd_count` is 0 or missing. Stateless; queries run on [io].
 */
@Singleton
class WordOfTheDayRepositoryImpl @Inject constructor(
    private val entryDao: EntryDao,
    private val metaDao: MetaDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : WordOfTheDayRepository {

    override fun observe(date: LocalDate): Flow<Entry?> =
        metaDao.observeAll()
            .map { WotdParams.from(it.toMetaMap()) }
            .distinctUntilChanged()
            .map { params -> params?.let { load(it, date) } }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun forDate(date: LocalDate): Entry? = withContext(io) {
        val meta = listOf(MetaKeys.WOTD_COUNT, MetaKeys.WOTD_DIRECTION_ID, MetaKeys.WOTD_SEED)
            .associateWith { metaDao.get(it) }
        WotdParams.from(meta)?.let { load(it, date) }
    }

    /** `wotdRange(dir, rank, rank + 1)`: the range filters by direction, unlike `byWotdRank`. */
    private suspend fun load(params: WotdParams, date: LocalDate): Entry? {
        val rank = params.dayIndex(date)
        val id = entryDao.wotdRange(params.directionId, rank, rank + 1).firstOrNull()?.id ?: return null
        return entryDao.getWithDetails(id)?.toDomain()
    }
}
