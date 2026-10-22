package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.local.dao.EntryDao
import com.kharagedition.tibetandictionary.data.model.Entry
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [WordOfTheDayRepository] over the active pack's dictionary database: the entry of `wotd_direction_id` whose
 * `wotd_rank` is the date's index ([WotdParams]); null when `wotd_count` is 0 or missing. Stateless: it resolves the
 * active database per call and follows the active pack in [observe]; queries run on [io].
 */
@Singleton
class WordOfTheDayRepositoryImpl @Inject constructor(
    private val databases: DictionaryDatabases,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : WordOfTheDayRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(date: LocalDate): Flow<Entry?> =
        databases.active.filterNotNull().flatMapLatest { pack ->
            val db = databases.get(pack)
            db.metaDao().observeAll()
                .map { WotdParams.from(it.toMetaMap()) }
                .distinctUntilChanged()
                .map { params -> params?.let { load(db.entryDao(), it, date) } }
                .distinctUntilChanged()
        }.flowOn(io)

    override suspend fun forDate(date: LocalDate): Entry? = withContext(io) {
        val db = databases.activeDb
        val meta = listOf(MetaKeys.WOTD_COUNT, MetaKeys.WOTD_DIRECTION_ID, MetaKeys.WOTD_SEED)
            .associateWith { db.metaDao().get(it) }
        WotdParams.from(meta)?.let { load(db.entryDao(), it, date) }
    }

    /** `wotdRange(dir, rank, rank + 1)`: the range filters by direction, unlike `byWotdRank`. */
    private suspend fun load(entryDao: EntryDao, params: WotdParams, date: LocalDate): Entry? {
        val rank = params.dayIndex(date)
        val id = entryDao.wotdRange(params.directionId, rank, rank + 1).firstOrNull()?.id ?: return null
        return entryDao.getWithDetails(id)?.toDomain()
    }
}
