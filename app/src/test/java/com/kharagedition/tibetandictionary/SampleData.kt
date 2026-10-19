package com.kharagedition.tibetandictionary

import com.kharagedition.tibetandictionary.data.model.Attribution
import com.kharagedition.tibetandictionary.data.model.DictionaryCapabilities
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.DirectionFeatures
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.EntrySummary
import com.kharagedition.tibetandictionary.data.model.Example
import com.kharagedition.tibetandictionary.data.model.RecentSearch
import com.kharagedition.tibetandictionary.data.model.RelatedEntry
import com.kharagedition.tibetandictionary.data.model.Relation
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import com.kharagedition.tibetandictionary.data.model.Script
import com.kharagedition.tibetandictionary.data.model.Sense
import com.kharagedition.tibetandictionary.data.model.SenseItem
import java.time.Instant

/**
 * Realistic sample data shared by unit tests: the three release-1 directions (counts from the data build), a few
 * entries and the user rows built from them. Direction features match the entries (e.g. only en-bo has examples).
 */
object SampleData {

    val boEn = Direction(
        id = "bo-en",
        sourceLang = "bo",
        targetLang = "en",
        sourceScript = Script.TIBETAN,
        labelEn = "Tibetan → English",
        labelBo = "བོད་ཡིག → དབྱིན་ཡིག",
        shortLabel = "བོད → EN",
        position = 0,
        entryCount = 76_828,
        features = DirectionFeatures(
            hasWylie = true,
            hasAudio = false,
            hasPartOfSpeech = false,
            hasLabels = true,
            hasExamples = false,
            hasRelated = true,
            hasWordOfTheDay = true,
            hasTruncated = true,
        ),
    )

    val boBo = Direction(
        id = "bo-bo",
        sourceLang = "bo",
        targetLang = "bo",
        sourceScript = Script.TIBETAN,
        labelEn = "Tibetan definitions",
        labelBo = "བོད་ཡིག → བོད་ཡིག",
        shortLabel = "བོད → བོད",
        position = 1,
        entryCount = 55_124,
        features = DirectionFeatures(
            hasWylie = true,
            hasAudio = false,
            hasPartOfSpeech = true,
            hasLabels = true,
            hasExamples = true,
            hasRelated = false,
            hasWordOfTheDay = false,
            hasTruncated = false,
        ),
    )

    val enBo = Direction(
        id = "en-bo",
        sourceLang = "en",
        targetLang = "bo",
        sourceScript = Script.LATIN,
        labelEn = "English → Tibetan",
        labelBo = "དབྱིན་ཡིག → བོད་ཡིག",
        shortLabel = "EN → བོད",
        position = 2,
        entryCount = 35_887,
        features = DirectionFeatures(
            hasWylie = false,
            hasAudio = false,
            hasPartOfSpeech = true,
            hasLabels = true,
            hasExamples = true,
            hasRelated = true,
            hasWordOfTheDay = false,
            hasTruncated = false,
        ),
    )

    val directions: List<Direction> = listOf(boEn, boBo, enBo)

    val attribution = Attribution(
        sourceName = "Monlam Tibetan Dictionary",
        author = "Lobsang Monlam",
        url = "https://monlam.ai",
        license = "GPL-3.0",
        text = "Dictionary data: Monlam Tibetan Dictionary by Lobsang Monlam (GPL-3).",
    )

    val capabilities = DictionaryCapabilities(
        directions = directions,
        dataVersion = "2026.10.1",
        builtAt = Instant.parse("2026-10-19T00:00:00Z"),
        attribution = attribution,
        wotdDirectionId = boEn.id,
    )

    // bo-en (id base 1 000 000)

    val tashi = EntrySummary(
        id = 1_004_211,
        directionId = boEn.id,
        headword = "བཀྲ་ཤིས་",
        wylie = "bkra shis",
        glossPreview = "auspicious; good fortune, blessing",
        partOfSpeech = emptyList(),
        phonetic = "tashi",
        audioFile = null,
        isTruncated = false,
        senseCount = 3,
    )

    val tashiDelek = EntrySummary(
        id = 1_004_219,
        directionId = boEn.id,
        headword = "བཀྲ་ཤིས་བདེ་ལེགས་",
        wylie = "bkra shis bde legs",
        glossPreview = "greeting: good luck, may all be well",
        partOfSpeech = emptyList(),
        phonetic = null,
        audioFile = null,
        isTruncated = false,
        senseCount = 1,
    )

    val nyingje = EntrySummary(
        id = 1_031_067,
        directionId = boEn.id,
        headword = "སྙིང་རྗེ་",
        wylie = "snying rje",
        glossPreview = "compassion, mercy; the wish that others be free from suffering",
        partOfSpeech = emptyList(),
        phonetic = null,
        audioFile = null,
        isTruncated = false,
        senseCount = 2,
    )

    // bo-bo (id base 2 000 000)

    val tashiBoBo = EntrySummary(
        id = 2_003_870,
        directionId = boBo.id,
        headword = "བཀྲ་ཤིས་",
        wylie = "bkra shis",
        glossPreview = "བདེ་ལེགས་དང་ལེགས་པའི་མཚན་མ།",
        partOfSpeech = listOf("n"),
        phonetic = null,
        audioFile = null,
        isTruncated = false,
        senseCount = 1,
    )

    // en-bo (id base 3 000 000)

    val house = EntrySummary(
        id = 3_015_402,
        directionId = enBo.id,
        headword = "house",
        wylie = null,
        glossPreview = "ཁང་པ། ཁྱིམ་ཚང་། རིགས་རྒྱུད།",
        partOfSpeech = listOf("n", "vt"),
        phonetic = null,
        audioFile = null,
        isTruncated = false,
        senseCount = 5,
    )

    val tashiEntry = Entry(
        summary = tashi,
        senses = listOf(
            sense(1, "auspicious, fortunate, favourable"),
            sense(2, "good luck, good fortune, blessing"),
            sense(3, "a common personal name, \"Tashi\""),
        ),
        related = listOf(
            RelatedEntry(tashiDelek.id, tashiDelek.headword, tashiDelek.wylie, Relation.SEE_ALSO),
            // Not in the data: tapping it runs a search.
            RelatedEntry(null, "བཀྲ་ཤིས་རྟགས་བརྒྱད་", "bkra shis rtags brgyad", Relation.SEE_ALSO),
        ),
    )

    val tashiDelekEntry = Entry(
        summary = tashiDelek,
        senses = listOf(sense(1, "greeting: good luck, may all be well")),
        related = listOf(RelatedEntry(tashi.id, tashi.headword, tashi.wylie, Relation.SEE_ALSO)),
    )

    val nyingjeEntry = Entry(
        summary = nyingje,
        senses = listOf(
            sense(1, "compassion, mercy"),
            sense(2, "the wish that others be free from suffering"),
        ),
        related = emptyList(),
    )

    val tashiBoBoEntry = Entry(
        summary = tashiBoBo,
        senses = listOf(sense(1, "བདེ་ལེགས་དང་ལེགས་པའི་མཚན་མ།", glossLang = "bo", partOfSpeech = "n")),
        related = emptyList(),
    )

    val houseEntry = Entry(
        summary = house,
        senses = listOf(
            sense(
                1,
                "ཁང་པ།",
                glossLang = "bo",
                partOfSpeech = "n",
                examples = listOf(Example("a two-storey house", "ཐོག་བརྩེགས་གཉིས་ཅན་གྱི་ཁང་པ།", null)),
            ),
            sense(2, "ཁྱིམ་ཚང་།", glossLang = "bo", partOfSpeech = "n"),
            sense(3, "རིགས་རྒྱུད།", glossLang = "bo", partOfSpeech = "n"),
            sense(4, "ཁྱིམ།", glossLang = "bo", partOfSpeech = "n", labels = listOf("astrol")),
            sense(5, "གནས་ཚང་སྤྲོད་པ།", glossLang = "bo", partOfSpeech = "vt"),
        ),
        related = listOf(RelatedEntry(null, "home", null, Relation.SYNONYM)),
    )

    val entries: List<Entry> = listOf(tashiEntry, tashiDelekEntry, nyingjeEntry, tashiBoBoEntry, houseEntry)

    val savedTashi = SavedEntry(
        entryId = tashi.id,
        directionId = tashi.directionId,
        headword = tashi.headword,
        wylie = tashi.wylie,
        glossPreview = tashi.glossPreview,
        folderId = null,
        note = null,
        savedAt = Instant.parse("2026-10-16T08:15:00Z").toEpochMilli(),
    )

    val recentTashi = RecentSearch(
        id = 1,
        query = "bkra shis",
        directionId = boEn.id,
        entryId = tashi.id,
        headword = tashi.headword,
        searchedAt = Instant.parse("2026-10-18T17:40:00Z").toEpochMilli(),
    )

    val recentHouse = RecentSearch(
        id = 2,
        query = "house",
        directionId = enBo.id,
        entryId = null,
        headword = null,
        searchedAt = Instant.parse("2026-10-19T07:05:00Z").toEpochMilli(),
    )

    private fun sense(
        number: Int,
        gloss: String,
        glossLang: String = "en",
        partOfSpeech: String? = null,
        labels: List<String> = emptyList(),
        examples: List<Example> = emptyList(),
    ) = Sense(
        number = number,
        partOfSpeech = partOfSpeech,
        labels = labels,
        note = null,
        sourceTags = emptyList(),
        items = listOf(SenseItem(subNumber = null, gloss = gloss, glossLang = glossLang, isTruncated = false, examples = examples)),
    )
}
