package com.kharagedition.tibetandictionary.data

import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
import com.kharagedition.tibetandictionary.data.local.direction
import com.kharagedition.tibetandictionary.data.local.entity.DirectionEntity
import com.kharagedition.tibetandictionary.data.local.entity.MetaEntity
import com.kharagedition.tibetandictionary.data.local.entry
import com.kharagedition.tibetandictionary.data.local.inMemoryDictionaryDb
import com.kharagedition.tibetandictionary.data.local.sense

/**
 * A miniature dictionary release for repository tests: [inMemoryDictionaryDb] (the production schema, in memory)
 * filled through [com.kharagedition.tibetandictionary.data.local.dao.TestWriteDao] with the three release-1
 * directions (with build-time counts), the `meta` keys and a few entries per direction. The rows are chosen so that
 * every search mode, each did-you-mean step and the word of the day have something to find; [MiniDictionary] names
 * the ids.
 */
object TestDb {

    const val DATA_VERSION = "2026.10.1-test"
    const val BUILT_AT = "2026-10-19T06:30:00Z"

    /** Entries of bo-en carrying word-of-the-day ranks 0 until [WOTD_COUNT]. */
    const val WOTD_COUNT = 5

    /** A new in-memory database holding [MiniDictionary]. */
    suspend fun miniDictionary(wotdSeed: Long = 0, wotdCount: Int = WOTD_COUNT): DictionaryDatabase =
        inMemoryDictionaryDb().apply { seedMiniDictionary(wotdSeed, wotdCount) }

    /** A new in-memory database holding [MiniDzongkha]: the three Dzongkha directions and one entry in each. */
    suspend fun miniDzongkhaDictionary(): DictionaryDatabase =
        inMemoryDictionaryDb().apply {
            val write = testWriteDao()
            write.insertDirections(
                listOf(
                    directionRow(
                        MiniDzongkha.DZ_EN, position = 0, sourceScript = "tibt", entryCount = 1, wotdCount = 1,
                    ).copy(idBase = 4_000_000L),
                    directionRow(MiniDzongkha.DZ_DZ, position = 1, sourceScript = "tibt", entryCount = 1)
                        .copy(idBase = 5_000_000L),
                    directionRow(MiniDzongkha.EN_DZ, position = 2, sourceScript = "latn", entryCount = 1)
                        .copy(idBase = 6_000_000L),
                ),
            )
            write.insertMeta(
                metaRows(wotdSeed = 0, wotdCount = 1).map {
                    when (it.key) {
                        "wotd_direction_id" -> it.copy(value = MiniDzongkha.DZ_EN)
                        "source_name" -> it.copy(value = "Dzongkha Development Commission dictionary")
                        else -> it
                    }
                },
            )
            write.insertEntries(
                listOf(
                    row(MiniDzongkha.CHU, MiniDzongkha.DZ_EN, "ཆུ་", 10, null, "water", wotdRank = 0),
                    row(MiniDzongkha.CHU_DZ, MiniDzongkha.DZ_DZ, "ཆུ་", 10, null, "ཆུ་ཚོད།"),
                    row(MiniDzongkha.WATER, MiniDzongkha.EN_DZ, "water", 10, null, "ཆུ་"),
                ),
            )
            write.insertSenses(
                listOf(
                    sense(101, MiniDzongkha.CHU, senseNo = 1, gloss = "water"),
                    sense(102, MiniDzongkha.CHU_DZ, senseNo = 1, gloss = "ཆུ་ཚོད།", glossLang = "dz"),
                    sense(103, MiniDzongkha.WATER, senseNo = 1, gloss = "ཆུ་", glossLang = "dz"),
                ),
            )
        }

    /**
     * A `directions` row with its counts; every count not given is 0 (the feature is absent). Built on
     * [com.kharagedition.tibetandictionary.data.local.direction], so `source_lang`/`target_lang` come from [id].
     */
    fun directionRow(
        id: String,
        position: Int,
        sourceScript: String,
        entryCount: Int,
        wylieCount: Int = 0,
        audioCount: Int = 0,
        posCount: Int = 0,
        labelCount: Int = 0,
        exampleCount: Int = 0,
        relatedCount: Int = 0,
        truncatedCount: Int = 0,
        wotdCount: Int = 0,
    ): DirectionEntity = direction(id, position, sourceScript, entryCount).copy(
        wylieCount = wylieCount,
        audioCount = audioCount,
        posCount = posCount,
        labelCount = labelCount,
        exampleCount = exampleCount,
        relatedCount = relatedCount,
        truncatedCount = truncatedCount,
        wotdCount = wotdCount,
    )

    /** The meta rows of a build; [wotdCount] 0 means no word of the day. */
    fun metaRows(wotdSeed: Long = 0, wotdCount: Int = WOTD_COUNT): List<MetaEntity> = listOf(
        MetaEntity("schema_version", "1"),
        MetaEntity("data_version", DATA_VERSION),
        MetaEntity("built_at", BUILT_AT),
        MetaEntity("source_name", "Monlam Tibetan Dictionary"),
        MetaEntity("source_author", "Lobsang Monlam"),
        MetaEntity("source_url", "https://monlam.ai"),
        MetaEntity("source_license", "GPL-3.0"),
        MetaEntity("attribution_text", "Dictionary data: Monlam Tibetan Dictionary by Lobsang Monlam (GPL-3)."),
        MetaEntity("wotd_seed", wotdSeed.toString()),
        MetaEntity("wotd_count", wotdCount.toString()),
        MetaEntity("wotd_direction_id", MiniDictionary.BO_EN),
    )

    private suspend fun DictionaryDatabase.seedMiniDictionary(wotdSeed: Long, wotdCount: Int) {
        val write = testWriteDao()
        write.insertDirections(
            listOf(
                directionRow(
                    MiniDictionary.BO_EN, position = 0, sourceScript = "tibt", entryCount = 10, wylieCount = 10,
                    labelCount = 1, relatedCount = 1, wotdCount = wotdCount,
                ),
                directionRow(MiniDictionary.BO_BO, position = 1, sourceScript = "tibt", entryCount = 2, wylieCount = 2),
                directionRow(
                    MiniDictionary.EN_BO, position = 2, sourceScript = "latn", entryCount = 5, posCount = 5,
                    exampleCount = 1,
                ),
            ),
        )
        write.insertMeta(metaRows(wotdSeed, wotdCount))

        with(MiniDictionary) {
            val ranked = wotdCount > 0
            write.insertEntries(
                listOf(
                    row(TRA, BO_EN, "བཀྲ་", 10, "bkra", "bright; glorious", wotdRank = 0.takeIf { ranked }),
                    row(
                        TASHI_DELEK, BO_EN, "བཀྲ་ཤིས་བདེ་ལེགས་", 15, "bkra shis bde legs", "greetings; good luck",
                        wotdRank = 1.takeIf { ranked },
                    ),
                    row(
                        TASHI, BO_EN, "བཀྲ་ཤིས་", 20, "bkra shis", "auspiciousness; good fortune",
                        wotdRank = 2.takeIf { ranked },
                    ),
                    row(TRAG, BO_EN, "བཀྲག་", 40, "bkrag", "lustre, brilliance", wotdRank = 3.takeIf { ranked }),
                    row(PAL_TASHI, BO_EN, "དཔལ་བཀྲ་ཤིས་", 50, "dpal bkra shis", "glorious good fortune"),
                    row(KAWA, BO_EN, "ཀ་བ་", 60, "ka ba", "pillar, column", wotdRank = 4.takeIf { ranked }),
                    row(KALWA, BO_EN, "སྐལ་བ་", 70, "skal ba", "fortune; fate"),
                    row(BAL, BO_EN, "བལ་", 75, "bal", "wool"),
                    row(BA, BO_EN, "བ་", 80, "ba", "cow"),
                    row(GYALTSEN, BO_EN, "རྒྱལ་མཚན་", 90, "rgyal mtshan", "victory banner"),
                    row(BOBO_TASHI, BO_BO, "བཀྲ་ཤིས་", 20, "bkra shis", "བདེ་ལེགས།"),
                    row(BOBO_KAWA, BO_BO, "ཀ་བ་", 60, "ka ba", "ཁང་པའི་གདུང་འདེགས་བྱེད་ཡོ་བྱད།"),
                    row(PILL, EN_BO, "pill", 10, null, "རིལ་བུ།"),
                    row(PILLAR, EN_BO, "pillar", 20, null, "ཀ་བ།"),
                    row(PILLOW, EN_BO, "pillow", 30, null, "སྔས།"),
                    row(FORTUNE, EN_BO, "fortune", 40, null, "སྐལ་བ། བཀྲ་ཤིས།"),
                    row(GOOD_FORTUNE, EN_BO, "good fortune", 50, null, "བཀྲ་ཤིས།"),
                ),
            )
            write.insertSenses(
                listOf(
                    sense(1, TRA, senseNo = 1, gloss = "bright; glorious"),
                    sense(2, TASHI_DELEK, senseNo = 1, gloss = "greetings; good luck"),
                    sense(3, TASHI, senseNo = 1, gloss = "auspiciousness, good fortune"),
                    sense(4, TRAG, senseNo = 1, gloss = "lustre, brilliance"),
                    sense(5, PAL_TASHI, senseNo = 1, gloss = "glorious good fortune"),
                    sense(6, KAWA, senseNo = 1, gloss = "pillar, column"),
                    sense(7, KALWA, senseNo = 1, gloss = "fortune; fate"),
                    sense(8, BAL, senseNo = 1, gloss = "wool"),
                    sense(9, BA, senseNo = 1, gloss = "cow"),
                    sense(10, GYALTSEN, senseNo = 1, gloss = "victory banner"),
                    sense(11, BOBO_TASHI, senseNo = 1, gloss = "བདེ་ལེགས།", glossLang = "bo"),
                    // A Tibetan definition quoting an English word: Latin input must not search bo-bo glosses.
                    sense(
                        12, BOBO_KAWA, senseNo = 1, gloss = "ཁང་པའི་གདུང་འདེགས་བྱེད་ཡོ་བྱད། pillar", glossLang = "bo",
                    ),
                    sense(21, PILL, senseNo = 1, gloss = "རིལ་བུ།", glossLang = "bo"),
                    sense(22, PILLAR, senseNo = 1, gloss = "ཀ་བ།", glossLang = "bo"),
                    sense(23, PILLOW, senseNo = 1, gloss = "སྔས།", glossLang = "bo"),
                    sense(24, FORTUNE, senseNo = 1, gloss = "སྐལ་བ། བཀྲ་ཤིས།", glossLang = "bo"),
                    sense(25, GOOD_FORTUNE, senseNo = 1, gloss = "བཀྲ་ཤིས།", glossLang = "bo"),
                ),
            )
        }
    }

    private fun row(
        id: Long,
        directionId: String,
        headword: String,
        sortOrder: Int,
        wylie: String?,
        glossPreview: String,
        wotdRank: Int? = null,
    ) = entry(id, directionId, headword, sortOrder, wylie = wylie, glossPreview = glossPreview, wotdRank = wotdRank)
}

/** Ids of the [TestDb.miniDictionary] rows (`id_base` 1 000 000 / 2 000 000 / 3 000 000 + `sort_order`). */
object MiniDictionary {
    const val BO_EN = "bo-en"
    const val BO_BO = "bo-bo"
    const val EN_BO = "en-bo"

    // bo-en, in Monlam (sort_order) order. TRA..KAWA (except PAL_TASHI) carry wotd ranks 0..4.
    const val TRA = 1_000_010L // བཀྲ་ bkra
    const val TASHI_DELEK = 1_000_015L // བཀྲ་ཤིས་བདེ་ལེགས་ (sorts before བཀྲ་ཤིས་ on purpose)
    const val TASHI = 1_000_020L // བཀྲ་ཤིས་ bkra shis
    const val TRAG = 1_000_040L // བཀྲག་ bkrag
    const val PAL_TASHI = 1_000_050L // དཔལ་བཀྲ་ཤིས་: contains བཀྲ་ཤིས, never a prefix match
    const val KAWA = 1_000_060L // ཀ་བ་ ka ba, "pillar, column"
    const val KALWA = 1_000_070L // སྐལ་བ་ skal ba, "fortune; fate"
    const val BAL = 1_000_075L // བལ་ bal
    const val BA = 1_000_080L // བ་ ba: an exact Wylie match sorting after the prefix match bal
    const val GYALTSEN = 1_000_090L // རྒྱལ་མཚན་, "victory banner": the only English gloss word starting with "ba"

    // bo-bo
    const val BOBO_TASHI = 2_000_020L
    const val BOBO_KAWA = 2_000_060L // its gloss ends with the English word "pillar"

    // en-bo
    const val PILL = 3_000_010L
    const val PILLAR = 3_000_020L
    const val PILLOW = 3_000_030L
    const val FORTUNE = 3_000_040L
    const val GOOD_FORTUNE = 3_000_050L
}

/** Ids of the [TestDb.miniDzongkhaDictionary] rows (`id_base` 4 000 000 / 5 000 000 / 6 000 000 + `sort_order`). */
object MiniDzongkha {
    const val DZ_EN = "dz-en"
    const val DZ_DZ = "dz-dz"
    const val EN_DZ = "en-dz"

    const val CHU = 4_000_010L // ཆུ་ "water", word-of-the-day rank 0
    const val CHU_DZ = 5_000_010L
    const val WATER = 6_000_010L
}
