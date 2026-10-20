package com.kharagedition.tibetandictionary.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.MiniDictionary.BA
import com.kharagedition.tibetandictionary.data.MiniDictionary.BAL
import com.kharagedition.tibetandictionary.data.MiniDictionary.BOBO_KAWA
import com.kharagedition.tibetandictionary.data.MiniDictionary.BOBO_TASHI
import com.kharagedition.tibetandictionary.data.MiniDictionary.BO_BO
import com.kharagedition.tibetandictionary.data.MiniDictionary.BO_EN
import com.kharagedition.tibetandictionary.data.MiniDictionary.EN_BO
import com.kharagedition.tibetandictionary.data.MiniDictionary.FORTUNE
import com.kharagedition.tibetandictionary.data.MiniDictionary.GOOD_FORTUNE
import com.kharagedition.tibetandictionary.data.MiniDictionary.GYALTSEN
import com.kharagedition.tibetandictionary.data.MiniDictionary.KALWA
import com.kharagedition.tibetandictionary.data.MiniDictionary.KAWA
import com.kharagedition.tibetandictionary.data.MiniDictionary.PAL_TASHI
import com.kharagedition.tibetandictionary.data.MiniDictionary.PILL
import com.kharagedition.tibetandictionary.data.MiniDictionary.PILLAR
import com.kharagedition.tibetandictionary.data.MiniDictionary.PILLOW
import com.kharagedition.tibetandictionary.data.MiniDictionary.TASHI
import com.kharagedition.tibetandictionary.data.MiniDictionary.TASHI_DELEK
import com.kharagedition.tibetandictionary.data.MiniDictionary.TRA
import com.kharagedition.tibetandictionary.data.MiniDictionary.TRAG
import com.kharagedition.tibetandictionary.data.TestDb
import com.kharagedition.tibetandictionary.data.TestDb.directionRow
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
import com.kharagedition.tibetandictionary.data.local.entity.MetaEntity
import com.kharagedition.tibetandictionary.data.local.entry
import com.kharagedition.tibetandictionary.data.local.example
import com.kharagedition.tibetandictionary.data.local.inMemoryDictionaryDb
import com.kharagedition.tibetandictionary.data.local.related
import com.kharagedition.tibetandictionary.data.local.sense
import com.kharagedition.tibetandictionary.data.model.Attribution
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Example
import com.kharagedition.tibetandictionary.data.model.MatchField
import com.kharagedition.tibetandictionary.data.model.RelatedEntry
import com.kharagedition.tibetandictionary.data.model.Relation
import com.kharagedition.tibetandictionary.data.model.Script
import com.kharagedition.tibetandictionary.data.model.SearchHit
import com.kharagedition.tibetandictionary.data.model.SearchResult
import com.kharagedition.tibetandictionary.data.model.Sense
import com.kharagedition.tibetandictionary.data.model.SenseItem
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryRepositoryImplTest {

    private lateinit var db: DictionaryDatabase

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    private fun TestScope.repository() = DictionaryRepositoryImpl(
        directionDao = db.directionDao(),
        entryDao = db.entryDao(),
        metaDao = db.metaDao(),
        io = StandardTestDispatcher(testScheduler),
    )

    private suspend fun TestScope.miniRepository(wotdSeed: Long = 0): DictionaryRepositoryImpl {
        db = TestDb.miniDictionary(wotdSeed)
        return repository()
    }

    private fun SearchResult.ids() = hits.map { it.entry.id }

    private fun SearchResult.fields() = hits.map { it.field }

    private fun SearchResult.hit(id: Long): SearchHit = hits.single { it.entry.id == id }

    // Capabilities

    @Test
    fun capabilitiesHideDirectionsWithZeroEntries() = runTest {
        db = inMemoryDictionaryDb()
        db.testWriteDao().insertDirections(
            listOf(
                directionRow("zh-bo", position = 3, sourceScript = "hani", entryCount = 4),
                directionRow("en-bo", position = 2, sourceScript = "latn", entryCount = 2),
                directionRow("bo-bo", position = 1, sourceScript = "tibt", entryCount = 0, wylieCount = 5),
                directionRow("bo-en", position = 0, sourceScript = "tibt", entryCount = 3),
            ),
        )
        db.testWriteDao().insertMeta(TestDb.metaRows())
        val repo = repository()

        val caps = repo.capabilities.first()

        assertThat(caps.directions.map { it.id }).containsExactly("bo-en", "en-bo", "zh-bo").inOrder()
        assertThat(caps.directions.map { it.sourceScript })
            .containsExactly(Script.TIBETAN, Script.LATIN, Script.HAN).inOrder()
        assertThat(caps.totalEntries).isEqualTo(9)
        assertThat(caps.primary.map { it.id }).containsExactly("bo-en", "en-bo").inOrder()
        assertThat(caps.overflow.map { it.id }).containsExactly("zh-bo")
        // bo-bo's Wylie never shows through: the direction is hidden as a whole.
        assertThat(caps.hasWylie).isFalse()
        assertThat(repo.direction("bo-bo")).isNull()
        assertThat(repo.direction("xx-yy")).isNull()
        assertThat(repo.direction("zh-bo")?.sourceScript).isEqualTo(Script.HAN)
        assertThat(repo.direction("en-bo")).isEqualTo(caps.directions[1])
    }

    @Test
    fun capabilitiesCarryMetaAndAttribution() = runTest {
        val repo = miniRepository()

        val caps = repo.capabilities.first()
        val boEn = caps.directions.first()

        assertThat(caps.dataVersion).isEqualTo(TestDb.DATA_VERSION)
        assertThat(caps.builtAt).isEqualTo(Instant.parse(TestDb.BUILT_AT))
        assertThat(caps.attribution).isEqualTo(
            Attribution(
                sourceName = "Monlam Tibetan Dictionary",
                author = "Lobsang Monlam",
                url = "https://monlam.ai",
                license = "GPL-3.0",
                text = "Dictionary data: Monlam Tibetan Dictionary by Lobsang Monlam (GPL-3).",
            ),
        )
        assertThat(caps.wotdDirectionId).isEqualTo(BO_EN)
        assertThat(boEn.id).isEqualTo(BO_EN)
        assertThat(boEn.sourceLang).isEqualTo("bo")
        assertThat(boEn.targetLang).isEqualTo("en")
        assertThat(boEn.position).isEqualTo(0)
        assertThat(boEn.entryCount).isEqualTo(10)
    }

    @Test
    fun capabilitiesWithoutWordOfTheDayOrBuildTime() = runTest {
        db = inMemoryDictionaryDb()
        db.testWriteDao().insertDirections(listOf(directionRow(BO_EN, 0, "tibt", entryCount = 1)))
        db.testWriteDao().insertMeta(
            listOf(
                MetaEntity("built_at", "yesterday"),
                MetaEntity("wotd_count", "0"),
                MetaEntity("wotd_direction_id", BO_EN),
            ),
        )
        val repo = repository()

        val caps = repo.capabilities.first()

        assertThat(caps.wotdDirectionId).isNull()
        assertThat(caps.builtAt).isNull()
        assertThat(caps.dataVersion).isEmpty()
        assertThat(caps.attribution).isEqualTo(Attribution("", "", "", "", ""))
    }

    @Test
    fun capabilitiesBuiltAtAcceptsOffsets() = runTest {
        db = inMemoryDictionaryDb()
        db.testWriteDao().insertDirections(listOf(directionRow(BO_EN, 0, "tibt", entryCount = 1)))
        db.testWriteDao().insertMeta(listOf(MetaEntity("built_at", "2026-10-19T08:30:00+02:00")))

        assertThat(repository().capabilities.first().builtAt).isEqualTo(Instant.parse("2026-10-19T06:30:00Z"))
    }

    @Test
    fun capabilitiesFeaturesFollowCounts() = runTest {
        val repo = miniRepository()

        repo.capabilities.test {
            val first = awaitItem()
            val boEn = first.directions.single { it.id == BO_EN }.features
            val enBo = first.directions.single { it.id == EN_BO }.features
            assertThat(boEn.hasAudio).isFalse()
            assertThat(boEn.hasWylie).isTrue()
            assertThat(boEn.hasLabels).isTrue()
            assertThat(boEn.hasRelated).isTrue()
            assertThat(boEn.hasWordOfTheDay).isTrue()
            assertThat(boEn.hasPartOfSpeech).isFalse()
            assertThat(boEn.hasExamples).isFalse()
            assertThat(boEn.hasTruncated).isFalse()
            assertThat(enBo.hasWylie).isFalse()
            assertThat(enBo.hasPartOfSpeech).isTrue()
            assertThat(enBo.hasExamples).isTrue()
            assertThat(enBo.hasWordOfTheDay).isFalse()

            // A data release that adds audio: one entry gets a file and the build counts it. No code change.
            db.testWriteDao().insertEntries(
                listOf(entry(1_000_100, BO_EN, "སྒྲ་", 100, glossPreview = "sound").copy(audioFile = "sgra.ogg")),
            )
            db.runInTransaction {
                db.openHelper.writableDatabase.execSQL("UPDATE directions SET audio_count = 1 WHERE id = 'bo-en'")
            }

            assertThat(awaitItem().directions.single { it.id == BO_EN }.features.hasAudio).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repo.search("སྒྲ", BO_EN).hits.single().entry.hasAudio).isTrue()
    }

    // Search modes

    @Test
    fun searchTibetanInBoEnUsesHeadword() = runTest {
        val repo = miniRepository()

        val result = repo.search("བཀྲ", BO_EN)

        assertThat(result.query).isEqualTo("བཀྲ")
        assertThat(result.directionId).isEqualTo(BO_EN)
        assertThat(result.script).isEqualTo(Script.TIBETAN)
        // Exact (with tsheg) first, then Monlam order, then the full-text "contains" hit.
        assertThat(result.ids()).containsExactly(TRA, TASHI_DELEK, TASHI, TRAG, PAL_TASHI).inOrder()
        assertThat(result.fields().toSet()).containsExactly(MatchField.HEADWORD)
        assertThat(result.hasMore).isFalse()
        assertThat(result.suggestions).isEmpty()
    }

    @Test
    fun searchLatinInBoEnUsesWylieThenGloss() = runTest {
        val repo = miniRepository()

        val ba = repo.search("ba", BO_EN)
        val baFinished = repo.search("ba ", BO_EN)
        val shis = repo.search("shis", BO_EN)
        val ban = repo.search("ban", BO_EN)
        val fortune = repo.search("fortune", BO_EN)
        val boBo = repo.search("ka", BO_BO)

        assertThat(ba.script).isEqualTo(Script.LATIN)
        // Two letters: the Wylie prefix step only (exact "ba" before "bal"), no full-text block.
        assertThat(ba.ids()).containsExactly(BA, BAL).inOrder()
        assertThat(ba.fields()).containsExactly(MatchField.WYLIE, MatchField.WYLIE)
        // A space finishes the syllable: the full-text block runs with "ba" matched exactly, so the Wylie "ka ba" and
        // "skal ba" follow, but not the gloss word "banner".
        assertThat(baFinished.ids()).containsExactly(BA, BAL, KAWA, KALWA).inOrder()
        assertThat(baFinished.query).isEqualTo("ba ")
        // A syllable inside the Wylie: full-text "contains" hits in Monlam order.
        assertThat(shis.ids()).containsExactly(TASHI_DELEK, TASHI, PAL_TASHI).inOrder()
        assertThat(shis.fields().toSet()).containsExactly(MatchField.WYLIE)
        // English reverse lookup: "banner" starts with "ban"; shortest gloss preview first.
        assertThat(ban.ids()).containsExactly(GYALTSEN)
        assertThat(ban.fields()).containsExactly(MatchField.GLOSS)
        assertThat(fortune.ids()).containsExactly(KALWA, PAL_TASHI, TASHI).inOrder()
        assertThat(fortune.fields().toSet()).containsExactly(MatchField.GLOSS)
        // bo-bo glosses are Tibetan: Latin input searches its Wylie only.
        assertThat(boBo.ids()).containsExactly(BOBO_KAWA)
        assertThat(boBo.fields()).containsExactly(MatchField.WYLIE)
        assertThat(repo.search("fortune", BO_BO).hits).isEmpty()
    }

    @Test
    fun searchLatinInEnBoUsesHeadword() = runTest {
        val repo = miniRepository()

        val pill = repo.search("Pill", EN_BO)
        val fortune = repo.search("fortune", EN_BO)

        assertThat(pill.script).isEqualTo(Script.LATIN)
        assertThat(pill.ids()).containsExactly(PILL, PILLAR, PILLOW).inOrder()
        assertThat(pill.fields().toSet()).containsExactly(MatchField.HEADWORD)
        assertThat(pill.hits.map { it.headwordHighlight }.toSet()).containsExactly(0 until 4)
        // "good fortune" contains the word: full-text headword hit after the prefix hit.
        assertThat(fortune.ids()).containsExactly(FORTUNE, GOOD_FORTUNE).inOrder()
        assertThat(fortune.hit(GOOD_FORTUNE).field).isEqualTo(MatchField.HEADWORD)
        assertThat(fortune.hit(GOOD_FORTUNE).headwordHighlight).isEqualTo(5 until 12)
    }

    @Test
    fun searchTibetanInEnBoUsesGloss() = runTest {
        val repo = miniRepository()

        val tashi = repo.search("བཀྲ་ཤིས", EN_BO)
        val pillow = repo.search("སྔས", EN_BO)
        val kawa = repo.search("ཀ་བ", EN_BO)
        val kawaFinished = repo.search("ཀ་བ་", EN_BO)

        assertThat(tashi.script).isEqualTo(Script.TIBETAN)
        assertThat(tashi.ids()).containsExactly(GOOD_FORTUNE, FORTUNE).inOrder()
        assertThat(tashi.fields().toSet()).containsExactly(MatchField.GLOSS)
        assertThat(tashi.hit(GOOD_FORTUNE).glossHighlight).isEqualTo(0 until 3)
        assertThat(tashi.hit(FORTUNE).glossHighlight).isEqualTo(7 until 10)
        assertThat(tashi.hasMore).isFalse()
        assertThat(pillow.ids()).containsExactly(PILLOW)
        // A last syllable of one letter (བ) is too short for the gloss step: did-you-mean offers the headword in the
        // Tibetan directions instead.
        assertThat(kawa.hits).isEmpty()
        assertThat(kawa.suggestions.map { it.hit.entry.id to it.directionId })
            .containsExactly(KAWA to BO_EN, BOBO_KAWA to BO_BO).inOrder()
        // With the closing tsheg the syllable is finished: matched exactly, it reaches the gloss "ཀ་བ།" of "pillar".
        assertThat(kawaFinished.ids()).containsExactly(PILLAR)
        assertThat(kawaFinished.fields()).containsExactly(MatchField.GLOSS)
        assertThat(kawaFinished.suggestions).isEmpty()
    }

    @Test
    fun searchOrdersExactThenPrefixThenFts() = runTest {
        val repo = miniRepository()

        val tibetan = repo.search("བཀྲ་ཤིས", BO_EN)
        val latin = repo.search("bkra shis", BO_EN)

        // TASHI (sort 20) is exact and leads TASHI_DELEK (sort 15); PAL_TASHI only contains the phrase.
        assertThat(tibetan.ids()).containsExactly(TASHI, TASHI_DELEK, PAL_TASHI).inOrder()
        // The same in Wylie. Gloss hits come after these: see fullTextBlockFollowsAFullPrefixPage.
        assertThat(latin.ids()).containsExactly(TASHI, TASHI_DELEK, PAL_TASHI).inOrder()
        assertThat(latin.fields().toSet()).containsExactly(MatchField.WYLIE)
    }

    @Test
    fun fullTextBlockFollowsAFullPrefixPage() = runTest {
        val repo = miniRepository()
        val raBal = 1_000_005L // ར་བལ་ "ra bal": "bal" inside the Wylie
        val snyoms = 1_000_006L // སྙོམས་: "balance" in the gloss
        val balPo = 1_000_076L
        val balYul = 1_000_077L // its gloss has the word "Bal", but its Wylie starts with the query
        db.testWriteDao().insertEntries(
            listOf(
                entry(raBal, BO_EN, "ར་བལ་", 5, wylie = "ra bal", glossPreview = "goat hair"),
                entry(snyoms, BO_EN, "སྙོམས་", 6, wylie = "snyoms", glossPreview = "balance; evenness"),
                entry(balPo, BO_EN, "བལ་པོ་", 76, wylie = "bal po", glossPreview = "Nepali"),
                entry(balYul, BO_EN, "བལ་ཡུལ་", 77, wylie = "bal yul", glossPreview = "Nepal; the Bal country"),
            ),
        )
        db.testWriteDao().insertSenses(
            listOf(
                sense(101, raBal, senseNo = 1, gloss = "goat hair"),
                sense(102, snyoms, senseNo = 1, gloss = "balance; evenness"),
                sense(103, balPo, senseNo = 1, gloss = "Nepali"),
                sense(104, balYul, senseNo = 1, gloss = "Nepal; the Bal country"),
            ),
        )

        val first = repo.search("bal", BO_EN, limit = 2)
        val second = repo.search("bal", BO_EN, limit = 2, offset = 2)
        val short = repo.search("ba", BO_EN, limit = 2)

        // Page 0: the prefix rows fill the page (exact first), then the full-text block follows: the Wylie "contains"
        // hit, then the gloss hit. Page 0 may therefore hold more than `limit` hits.
        assertThat(first.ids()).containsExactly(BAL, balPo, raBal, snyoms).inOrder()
        assertThat(first.fields())
            .containsExactly(MatchField.WYLIE, MatchField.WYLIE, MatchField.WYLIE, MatchField.GLOSS).inOrder()
        assertThat(first.hit(raBal).wylieHighlight).isEqualTo(3 until 6)
        assertThat(first.hit(snyoms).glossHighlight).isEqualTo(0 until 3)
        assertThat(first.hasMore).isTrue()
        // bal yul starts with the query: it waits for its prefix page and never joins the full-text block.
        assertThat(second.ids()).containsExactly(balYul)
        assertThat(second.fields()).containsExactly(MatchField.WYLIE)
        assertThat(second.hasMore).isFalse()
        // Two letters: no full-text block, although "ka ba", "ra bal", "banner" and "balance" would match.
        assertThat(short.ids()).containsExactly(BA, BAL).inOrder()
        assertThat(short.hasMore).isTrue()
    }

    @Test
    fun searchHighlightsWylieAndHeadword() = runTest {
        val repo = miniRepository()

        val wylie = repo.search("bkra shi", BO_EN)
        val tibetan = repo.search("བཀ", BO_EN)
        val gloss = repo.search("fortune", BO_EN)

        assertThat(wylie.ids()).containsExactly(TASHI_DELEK, TASHI, PAL_TASHI).inOrder()
        assertThat(wylie.hit(TASHI)).isEqualTo(
            SearchHit(
                entry = wylie.hit(TASHI).entry,
                field = MatchField.WYLIE,
                headwordHighlight = 0 until 7, // བཀྲ་ཤིས: the two syllables the Wylie covers
                wylieHighlight = 0 until 8,
                glossHighlight = null,
            ),
        )
        assertThat(wylie.hit(TASHI_DELEK).headwordHighlight).isEqualTo(0 until 7)
        // A full-text hit inside the Wylie ("dpal bkra shis") highlights where the query occurs.
        assertThat(wylie.hit(PAL_TASHI).field).isEqualTo(MatchField.WYLIE)
        assertThat(wylie.hit(PAL_TASHI).wylieHighlight).isEqualTo(5 until 13)
        assertThat(wylie.hit(PAL_TASHI).headwordHighlight).isNull()
        // The range never ends inside a stack: བཀ covers the subjoined ra of བཀྲ.
        assertThat(tibetan.hit(TRA).headwordHighlight).isEqualTo(0 until 3)
        assertThat(tibetan.hit(TRA).wylieHighlight).isNull()
        assertThat(tibetan.hit(PAL_TASHI).headwordHighlight).isEqualTo(4 until 7)
        assertThat(gloss.hit(KALWA).glossHighlight).isEqualTo(0 until 7)
        assertThat(gloss.hit(TASHI).glossHighlight).isEqualTo(21 until 28)
        assertThat(gloss.hit(TASHI).headwordHighlight).isNull()
    }

    // Did-you-mean

    @Test
    fun didYouMeanShortensPrefix() = runTest {
        val repo = miniRepository()

        val result = repo.search("བཀྲ་ཤིསས", BO_EN)

        assertThat(result.hits).isEmpty()
        assertThat(result.hasMore).isFalse()
        // Cut to the previous tsheg (བཀྲ་), then closest by edit distance.
        assertThat(result.suggestions.first().hit.entry.headword).isEqualTo("བཀྲ་ཤིས་")
        assertThat(result.suggestions.map { it.hit.entry.id }).containsExactly(TASHI, TRA, TASHI_DELEK).inOrder()
        assertThat(result.suggestions.map { it.directionId }.toSet()).containsExactly(BO_EN)
        assertThat(result.suggestions.first().hit.field).isEqualTo(MatchField.HEADWORD)
    }

    @Test
    fun didYouMeanShortensLatinPrefix() = runTest {
        val repo = miniRepository()

        val enBo = repo.search("pilar", EN_BO)
        val wylie = repo.search("bkra shiss", BO_EN)

        // "pila" finds nothing; "pil" finds three, ranked by edit distance to "pilar".
        assertThat(enBo.hits).isEmpty()
        assertThat(enBo.suggestions.map { it.hit.entry.id }).containsExactly(PILLAR, PILL, PILLOW).inOrder()
        assertThat(wylie.hits).isEmpty()
        assertThat(wylie.suggestions.map { it.hit.entry.id }.first()).isEqualTo(TASHI)
        assertThat(wylie.suggestions.first().hit.field).isEqualTo(MatchField.WYLIE)
    }

    @Test
    fun didYouMeanCrossDirection() = runTest {
        val repo = miniRepository()

        val result = repo.search("pillar", BO_BO)

        // bo-bo is Tibetan → Tibetan: Latin input searches its Wylie only, never its glosses.
        assertThat(result.hits).isEmpty()
        val suggestion = result.suggestions.first()
        assertThat(suggestion.directionId).isEqualTo(EN_BO)
        assertThat(suggestion.hit.entry.id).isEqualTo(PILLAR)
        assertThat(suggestion.hit.entry.directionId).isEqualTo(EN_BO)
        assertThat(suggestion.hit.field).isEqualTo(MatchField.HEADWORD)
        assertThat(suggestion.hit.headwordHighlight).isEqualTo(0 until 6)
        // Then step (c): a bo-bo gloss containing the word, tagged with the searched direction.
        assertThat(result.suggestions.map { it.hit.entry.id to it.directionId })
            .containsExactly(PILLAR to EN_BO, BOBO_KAWA to BO_BO).inOrder()
        assertThat(result.suggestions[1].hit.field).isEqualTo(MatchField.GLOSS)
    }

    @Test
    fun didYouMeanKeepsASlotForAnotherDirection() = runTest {
        val repo = miniRepository()
        val pi = listOf(
            "པི་" to "pi",
            "པི་ཝང་" to "pi wang",
            "པི་པི་" to "pi pi",
            "པི་ལིང་" to "pi ling",
            "པི་ཏོ་" to "pi to",
        ).mapIndexed { i, (headword, wylie) -> entry(1_000_100L + i, BO_EN, headword, 100 + i, wylie = wylie) }
        db.testWriteDao().insertEntries(pi)

        val result = repo.search("pillo", BO_EN)

        // Step (a) finds five Wylie rows for "pi", but only four may stay: en-bo also reads Latin, and its "pillow"
        // starts with the query.
        assertThat(result.hits).isEmpty()
        assertThat(result.suggestions).hasSize(5)
        assertThat(pi.map { it.id }).containsAtLeastElementsIn(result.suggestions.take(4).map { it.hit.entry.id })
        assertThat(result.suggestions.take(4).map { it.directionId }.toSet()).containsExactly(BO_EN)
        assertThat(result.suggestions.last().hit.entry.id to result.suggestions.last().directionId)
            .isEqualTo(PILLOW to EN_BO)
    }

    @Test
    fun didYouMeanFallsBackToAnyGlossTerm() = runTest {
        val repo = miniRepository()

        // No Wylie starts with "zzz wool" or a shortening of it, no en-bo headword with it; one gloss word matches.
        val result = repo.search("zzz wool", BO_EN)

        assertThat(result.hits).isEmpty()
        assertThat(result.suggestions.map { it.hit.entry.id }).containsExactly(BAL)
        assertThat(result.suggestions.single().hit.field).isEqualTo(MatchField.GLOSS)
        assertThat(result.suggestions.single().hit.glossHighlight).isEqualTo(0 until 4)
        assertThat(result.suggestions.single().directionId).isEqualTo(BO_EN)
    }

    // Paging

    @Test
    fun pagingHasMore() = runTest {
        db = inMemoryDictionaryDb()
        db.testWriteDao().insertDirections(listOf(directionRow(EN_BO, 2, "latn", entryCount = 51)))
        db.testWriteDao().insertEntries(
            (1..51).map { n -> entry(3_000_000L + n, EN_BO, "term%02d".format(n), sortOrder = n) },
        )
        val repo = repository()

        val first = repo.search("term", EN_BO, limit = 50)
        val second = repo.search("term", EN_BO, limit = 50, offset = 50)

        assertThat(first.hits).hasSize(50)
        assertThat(first.hasMore).isTrue()
        assertThat(first.hits.first().entry.headword).isEqualTo("term01")
        assertThat(first.hits.last().entry.headword).isEqualTo("term50")
        assertThat(second.hits.map { it.entry.headword }).containsExactly("term51")
        assertThat(second.hasMore).isFalse()
        assertThat(second.suggestions).isEmpty()
        // Exactly `limit` rows: nothing more to load.
        val all = repo.search("term", EN_BO, limit = 51)
        assertThat(all.hits).hasSize(51)
        assertThat(all.hasMore).isFalse()
    }

    // Entries

    @Test
    fun entryDetailGroupsSubSensesAndExamples() = runTest {
        val repo = miniRepository()
        val lead = 3_000_060L
        val write = db.testWriteDao()
        write.insertEntries(
            listOf(entry(lead, EN_BO, "lead", 60, glossPreview = "ཞ་ཉེ།").copy(partOfSpeech = "n,vt", senseCount = 2)),
        )
        write.insertSenses(
            listOf(
                sense(31, lead, senseNo = 2, gloss = "འཁྲིད་པ།", glossLang = "bo")
                    .copy(partOfSpeech = "vt", note = "led, led"),
                sense(32, lead, senseNo = 1, subNo = 2, gloss = "ཞ་ཉེ་ནག་པོ།", glossLang = "bo")
                    .copy(partOfSpeech = "n", label = "chem,fig", sourceTag = "C;TGP-77", isTruncated = true),
                sense(33, lead, senseNo = 1, subNo = 1, gloss = "ཞ་ཉེ།", glossLang = "bo")
                    .copy(partOfSpeech = "n", label = "chem", note = "(A)"),
            ),
        )
        write.insertExamples(
            listOf(
                example(41, senseId = 33, entryId = lead, position = 1, text = "lead pipe")
                    .copy(translation = "ཞ་ཉེའི་སྦུ་གུ།"),
                example(42, senseId = 33, entryId = lead, position = 0, text = "red lead"),
                example(43, senseId = 31, entryId = lead, position = 0, text = "lead the way")
                    .copy(translation = "ལམ་སྟོན་པ།", audioFile = "lead_the_way.ogg"),
            ),
        )
        write.insertRelated(
            listOf(
                related(51, lead, targetEntryId = PILL, targetHeadword = "pill", position = 1, relation = "synonym"),
                related(52, sourceEntryId = lead, targetEntryId = null, targetHeadword = "leader", position = 0),
            ),
        )
        val expected = Entry(
            summary = EntrySummary(
                id = lead,
                directionId = EN_BO,
                headword = "lead",
                wylie = null,
                glossPreview = "ཞ་ཉེ།",
                partOfSpeech = listOf("n", "vt"),
                phonetic = null,
                audioFile = null,
                isTruncated = false,
                senseCount = 2,
            ),
            senses = listOf(
                Sense(
                    number = 1,
                    partOfSpeech = "n",
                    labels = listOf("chem", "fig"),
                    note = "(A)",
                    sourceTags = listOf("C", "TGP-77"),
                    items = listOf(
                        SenseItem(
                            subNumber = 1,
                            gloss = "ཞ་ཉེ།",
                            glossLang = "bo",
                            isTruncated = false,
                            examples = listOf(
                                Example("red lead", null, null),
                                Example("lead pipe", "ཞ་ཉེའི་སྦུ་གུ།", null),
                            ),
                        ),
                        SenseItem(2, "ཞ་ཉེ་ནག་པོ།", "bo", isTruncated = true, examples = emptyList()),
                    ),
                ),
                Sense(
                    number = 2,
                    partOfSpeech = "vt",
                    labels = emptyList(),
                    note = "led, led",
                    sourceTags = emptyList(),
                    items = listOf(
                        SenseItem(
                            subNumber = null,
                            gloss = "འཁྲིད་པ།",
                            glossLang = "bo",
                            isTruncated = false,
                            examples = listOf(Example("lead the way", "ལམ་སྟོན་པ།", "lead_the_way.ogg")),
                        ),
                    ),
                ),
            ),
            related = listOf(
                RelatedEntry(null, "leader", null, Relation.SEE_ALSO),
                RelatedEntry(PILL, "pill", null, Relation.SYNONYM),
            ),
        )

        assertThat(repo.entry(lead)).isEqualTo(expected)
        assertThat(repo.entry(9)).isNull()
        repo.observeEntry(lead).test {
            assertThat(awaitItem()).isEqualTo(expected)
            cancelAndIgnoreRemainingEvents()
        }
        repo.observeEntry(9).test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun entryDetailRedirectOnly() = runTest {
        val repo = miniRepository()
        val redirect = 1_000_100L
        db.testWriteDao().insertEntries(
            listOf(entry(redirect, BO_EN, "བཀྲ་ཤིས་རྟགས་", 100, glossPreview = "").copy(senseCount = 0)),
        )
        db.testWriteDao().insertRelated(
            listOf(related(61, redirect, targetEntryId = TASHI, targetHeadword = "བཀྲ་ཤིས་", position = 0)),
        )

        val entry = checkNotNull(repo.entry(redirect))

        assertThat(entry.senses).isEmpty()
        assertThat(entry.related).containsExactly(RelatedEntry(TASHI, "བཀྲ་ཤིས་", null, Relation.SEE_ALSO))
        assertThat(entry.summary.senseCount).isEqualTo(0)
    }

    @Test
    fun summariesPreserveIdOrder() = runTest {
        val repo = miniRepository()

        val summaries = repo.summaries(listOf(PILLOW, TRA, 999L, BOBO_TASHI))

        assertThat(summaries.map { it.id }).containsExactly(PILLOW, TRA, BOBO_TASHI).inOrder()
        assertThat(summaries[0].headword).isEqualTo("pillow")
        assertThat(summaries[0].directionId).isEqualTo(EN_BO)
        assertThat(summaries[0].wylie).isNull()
        assertThat(summaries[0].glossPreview).isEqualTo("སྔས།")
        assertThat(summaries[1].wylie).isEqualTo("bkra")
        assertThat(summaries[1].partOfSpeech).isEmpty()
        assertThat(repo.summaries(emptyList())).isEmpty()
        // More ids than SQLite's bind-variable limit on old devices (999).
        assertThat(repo.summaries((1L..2_000L).toList() + TRAG).map { it.id }).containsExactly(TRAG)
    }

    // Empty-query suggestions

    @Test
    fun suggestionsForEmptyQueryWrapsAroundWotdCount() = runTest {
        val date = LocalDate.of(2026, 10, 19)
        // Today's rank is 3 (TRAG); the suggestions are the next ranks, wrapping after rank 4.
        val repo = miniRepository(wotdSeed = 3 - date.toEpochDay())

        val today = repo.suggestionsForEmptyQuery(BO_EN, date, count = 3)
        val earlier = repo.suggestionsForEmptyQuery(BO_EN, date.minusDays(3), count = 3)
        val more = repo.suggestionsForEmptyQuery(BO_EN, date, count = 6)
        val unranked = repo.suggestionsForEmptyQuery(EN_BO, date, count = 2)

        assertThat(today.map { it.id }).containsExactly(KAWA, TRA, TASHI_DELEK).inOrder()
        assertThat(repo.suggestionsForEmptyQuery(BO_EN, date, count = 3)).isEqualTo(today)
        assertThat(earlier.map { it.id }).containsExactly(TASHI_DELEK, TASHI, TRAG).inOrder()
        // Never more than the ranked entries.
        assertThat(more.map { it.id }).containsExactly(KAWA, TRA, TASHI_DELEK, TASHI, TRAG).inOrder()
        // No word-of-the-day ranks in en-bo: the first entries in Monlam order.
        assertThat(unranked.map { it.id }).containsExactly(PILL, PILLAR).inOrder()
        assertThat(repo.suggestionsForEmptyQuery("xx-yy", date)).isEmpty()
    }

    // closest()

    @Test
    fun closestReturnsHitsElseDidYouMean() = runTest {
        val repo = miniRepository()

        val tibetan = repo.closest("ཀ་བ།", directionId = null)
        val english = repo.closest("pill", directionId = null, limit = 2)
        val crossDirection = repo.closest("pillar", directionId = BO_BO)

        // No direction given: the first visible one whose source script matches the text.
        assertThat(tibetan.first().hit.entry.id).isEqualTo(KAWA)
        assertThat(tibetan.first().directionId).isEqualTo(BO_EN)
        assertThat(english.map { it.hit.entry.id }).containsExactly(PILL, PILLAR).inOrder()
        assertThat(english.map { it.directionId }.toSet()).containsExactly(EN_BO)
        assertThat(crossDirection.map { it.hit.entry.id to it.directionId })
            .containsExactly(PILLAR to EN_BO, BOBO_KAWA to BO_BO).inOrder()
        assertThat(repo.closest("pillar", BO_BO, limit = 1).map { it.hit.entry.id }).containsExactly(PILLAR)
        assertThat(repo.closest("   ", directionId = null)).isEmpty()
    }

    // Review Focus 1: odd queries

    @Test
    fun oddQueriesNeverThrow() = runTest {
        val repo = miniRepository()
        val queries = listOf(
            "བཀྲ shis", "བཀྲ་ཤིས།", "  a  ", "", "   ", "ཀ", "་", "།", "\"", "lead\"", "OR", "*", "(", "-", "a b c d",
        )

        for (directionId in listOf(BO_EN, BO_BO, EN_BO, "xx-yy")) {
            for (query in queries) {
                repo.search(query, directionId)
                repo.search(query, directionId, limit = 1, offset = 3)
                repo.closest(query, directionId)
            }
        }
        for (query in queries) repo.closest(query, directionId = null)

        assertThat(repo.search("", BO_EN))
            .isEqualTo(SearchResult("", BO_EN, Script.LATIN, emptyList(), hasMore = false, suggestions = emptyList()))
        assertThat(repo.search("   ", EN_BO).hits).isEmpty()
        assertThat(repo.search("བཀྲ་ཤིས།", BO_EN).ids().first()).isEqualTo(TASHI)
        assertThat(repo.search("བཀྲ shis", BO_EN).script).isEqualTo(Script.TIBETAN)
        assertThat(repo.search("  a  ", BO_EN).query).isEqualTo("  a  ")
    }
}
