package com.kharagedition.tibetandictionary.data.repository

import com.kharagedition.tibetandictionary.core.text.TibetanScript
import com.kharagedition.tibetandictionary.data.local.dao.DirectionDao
import com.kharagedition.tibetandictionary.data.local.dao.EntryDao
import com.kharagedition.tibetandictionary.data.local.entity.DirectionEntity
import com.kharagedition.tibetandictionary.data.local.entity.EntryEntity
import com.kharagedition.tibetandictionary.data.model.MatchField
import com.kharagedition.tibetandictionary.data.model.Script
import com.kharagedition.tibetandictionary.data.model.SearchHit
import com.kharagedition.tibetandictionary.data.model.SearchResult
import com.kharagedition.tibetandictionary.data.model.Suggestion
import com.kharagedition.tibetandictionary.data.search.EditDistance
import com.kharagedition.tibetandictionary.data.search.FtsQueryBuilder
import com.kharagedition.tibetandictionary.data.search.Highlighter
import com.kharagedition.tibetandictionary.data.search.PrefixBounds
import com.kharagedition.tibetandictionary.data.search.QueryNormalizer
import com.kharagedition.tibetandictionary.data.search.ScriptDetector

/**
 * The search pipeline behind [DictionaryRepositoryImpl.search]. Stateless; callers switch to the IO dispatcher.
 *
 * 1. **Normalise and detect:** [QueryNormalizer], then [ScriptDetector]. A blank query or a hidden/unknown direction
 *    gives an empty result.
 * 2. **Mode** from the direction row's `source_script` and the query's script (no per-direction code):
 *
 *    | source | query   | prefix step                       | gloss step            |
 *    |--------|---------|-----------------------------------|-----------------------|
 *    | tibt   | TIBETAN | headword                          | –                     |
 *    | tibt   | LATIN   | Wylie (only if `wylie_count > 0`) | if `target_lang = en` |
 *    | latn   | LATIN   | headword                          | –                     |
 *    | latn   | TIBETAN | –                                 | yes                   |
 *    | hani   | any     | headword                          | LATIN query only      |
 *
 *    Han text in a tibt or latn direction searches nothing and goes straight to did-you-mean.
 * 3. **Prefix step:** `EntryDao.prefixByHeadword`/`prefixByWylie` with `limit + 1` rows at `offset` (the extra row
 *    sets [SearchResult.hasMore]); the exact match (also with a trailing tsheg) first, then Monlam `sort_order`. Every
 *    page after the first holds prefix rows only, so [SearchResult.hasMore] pages through these rows.
 * 4. **Full-text block**, page 0 only, after the prefix rows (so page 0 may hold more than `limit` hits), and only
 *    when [fullTextAllowed] (the last token is long enough to match as a prefix cheaply). At most [FULL_TEXT_LIMIT]
 *    hits in this order:
 *    - headword "contains" hits: `ftsHeadwordOutsidePrefix` with the query as one phrase, only in modes with a prefix
 *      step. The index holds headwords and Wylie, so in Wylie mode the hit is a [MatchField.WYLIE] one;
 *    - gloss hits: `ftsGloss` with every term, when the mode has a gloss step.
 *
 *    The block never repeats a row the prefix step lists on this page or a later one ([isPrefixRow]): such a row
 *    would show twice once the next page is appended.
 * 5. **Highlights** ([Highlighter]): headword hits `headwordRange`; Wylie prefix hits `wylieRange` plus
 *    `headwordRangeForWylie`; Wylie full-text hits a contains range on the Wylie; gloss hits `glossRange` on the
 *    preview.
 * 6. **Did-you-mean**, page 0 only and only when steps 3–4 found nothing; at most [MAX_SUGGESTIONS], one per entry, in
 *    this order, stopping once full:
 *    a. [shortenedPrefixes] through the mode's prefix step ([SHORTENED_LIMIT] rows); the first candidate with rows
 *       wins; ranked by Damerau-Levenshtein distance between the query and the matched field, then `sort_order`.
 *       When step (b) has a direction to try, (a) keeps one slot free for it;
 *    b. the headword prefix step in every other visible direction whose source script is the query's script, tagged
 *       with that direction ([Suggestion.directionId]);
 *    c. `ftsGloss` with any term in the searched direction, when [fullTextAllowed]. Skipped for a one-word query in a
 *       mode with a gloss step: step 4 already ran that exact query.
 *
 * Every MATCH expression comes from [FtsQueryBuilder], so no input can make SQLite throw.
 */
internal class SearchEngine(
    private val entryDao: EntryDao,
    private val directionDao: DirectionDao,
) {

    suspend fun search(query: String, directionId: String, limit: Int, offset: Int): SearchResult {
        require(limit > 0) { "limit must be positive: $limit" }
        require(offset >= 0) { "offset must not be negative: $offset" }
        val q = QueryNormalizer.normalize(query)
        val script = ScriptDetector.detect(q)
        val empty = SearchResult(query, directionId, script, emptyList(), hasMore = false, suggestions = emptyList())
        if (q.isEmpty()) return empty
        val direction = directionDao.get(directionId)?.takeIf { it.entryCount > 0 } ?: return empty
        val sourceScript = scriptOf(direction.sourceScript) ?: return empty
        val mode = Mode.of(direction, sourceScript, script)
        val tokens = FtsQueryBuilder.tokens(q)

        val hits = LinkedHashMap<Long, SearchHit>()
        var hasMore = false
        if (mode.prefix != null) {
            val rows = prefixRows(direction.id, mode.prefix, q, limit + 1, offset)
            hasMore = rows.size > limit
            rows.take(limit).forEach { hits[it.id] = prefixHit(it, mode.prefix, q) }
        }
        if (offset == 0 && fullTextAllowed(tokens)) {
            fullTextBlock(direction.id, mode, q, tokens).forEach { hits.putIfAbsent(it.entry.id, it) }
        }
        val page = hits.values.toList()
        val suggestions =
            if (page.isEmpty() && offset == 0) didYouMean(direction, mode, q, script, tokens) else emptyList()
        return SearchResult(query, direction.id, script, page, hasMore, suggestions)
    }

    /** Step 4: "contains" hits, then gloss hits; at most [FULL_TEXT_LIMIT], none of them a prefix-step row. */
    private suspend fun fullTextBlock(dir: String, mode: Mode, q: String, tokens: List<String>): List<SearchHit> {
        val block = LinkedHashMap<Long, SearchHit>()
        if (mode.prefix != null) {
            FtsQueryBuilder.phrase(tokens)?.let { match ->
                entryDao.ftsHeadwordOutsidePrefix(dir, match, q, PrefixBounds.upper(q), FULL_TEXT_LIMIT)
                    .forEach { block.putIfAbsent(it.id, containsHit(it, mode.prefix, q)) }
            }
        }
        if (mode.gloss && block.size < FULL_TEXT_LIMIT) {
            FtsQueryBuilder.allTerms(tokens)?.let { match ->
                entryDao.ftsGloss(dir, match, FULL_TEXT_LIMIT)
                    .filterNot { mode.prefix != null && isPrefixRow(it, q) }
                    .forEach { block.putIfAbsent(it.id, glossHit(it, listOf(q))) }
            }
        }
        return block.values.take(FULL_TEXT_LIMIT)
    }

    private suspend fun didYouMean(
        direction: DirectionEntity,
        mode: Mode,
        q: String,
        script: Script,
        tokens: List<String>,
    ): List<Suggestion> {
        val found = LinkedHashMap<Long, Suggestion>()
        fun add(hit: SearchHit, directionId: String, max: Int = MAX_SUGGESTIONS) {
            if (found.size < max) found.putIfAbsent(hit.entry.id, Suggestion(hit, directionId))
        }
        // Other directions whose headwords are in the query's script, for step (b).
        val others = directionDao.visible().filter { it.id != direction.id && scriptOf(it.sourceScript) == script }

        // (a) Shorter prefixes in this direction, keeping a slot for (b) when it has somewhere to look.
        if (mode.prefix != null) {
            val max = if (others.isEmpty()) MAX_SUGGESTIONS else MAX_SUGGESTIONS - 1
            shortenedPrefixHits(direction.id, mode.prefix, q, script).forEach { add(it, direction.id, max) }
        }

        // (b) The same query in those other directions.
        for (other in others) {
            if (found.size >= MAX_SUGGESTIONS) break
            prefixRows(other.id, PrefixField.HEADWORD, q, MAX_SUGGESTIONS, 0)
                .forEach { add(prefixHit(it, PrefixField.HEADWORD, q), other.id) }
        }

        // (c) Glosses containing any of the words. With one word in a gloss mode, step 4 already ran this query.
        val repeatsGlossStep = mode.gloss && tokens.size == 1
        if (found.size < MAX_SUGGESTIONS && fullTextAllowed(tokens) && !repeatsGlossStep) {
            FtsQueryBuilder.anyTerm(tokens)?.let { match ->
                entryDao.ftsGloss(direction.id, match, MAX_SUGGESTIONS)
                    .forEach { add(glossHit(it, tokens), direction.id) }
            }
        }
        return found.values.toList()
    }

    /** Step (a): rows for the first [shortenedPrefixes] candidate that has any, closest to [q] first. */
    private suspend fun shortenedPrefixHits(
        dir: String,
        field: PrefixField,
        q: String,
        script: Script,
    ): List<SearchHit> {
        val target = comparable(q)
        for (prefix in shortenedPrefixes(q, script)) {
            val rows = prefixRows(dir, field, prefix, SHORTENED_LIMIT, 0)
            if (rows.isEmpty()) continue
            return rows
                .map { row -> row to EditDistance.damerauLevenshtein(target, comparable(field.textOf(row))) }
                .sortedWith(compareBy({ it.second }, { it.first.sortOrder }))
                .take(MAX_SUGGESTIONS)
                .map { (row, _) -> prefixHit(row, field, prefix) }
        }
        return emptyList()
    }

    /** The prefix step: rows whose [field] starts with [prefix]; the one equal to it (or to it plus a tsheg) first. */
    private suspend fun prefixRows(dir: String, field: PrefixField, prefix: String, limit: Int, offset: Int) =
        when (field) {
            PrefixField.HEADWORD -> entryDao.prefixByHeadword(
                dir = dir,
                prefix = prefix,
                upper = PrefixBounds.upper(prefix),
                exact = prefix,
                exactTsheg = TibetanScript.ensureTsheg(prefix),
                limit = limit,
                offset = offset,
            )
            PrefixField.WYLIE -> entryDao.prefixByWylie(
                dir = dir,
                prefix = prefix,
                upper = PrefixBounds.upper(prefix),
                exact = prefix,
                limit = limit,
                offset = offset,
            )
        }

    private fun prefixHit(row: EntryEntity, field: PrefixField, q: String): SearchHit = when (field) {
        PrefixField.HEADWORD -> headwordHit(row, q)
        PrefixField.WYLIE -> SearchHit(
            entry = row.toSummary(),
            field = MatchField.WYLIE,
            headwordHighlight = Highlighter.headwordRangeForWylie(row.headword, q),
            wylieHighlight = row.wylie?.let { Highlighter.wylieRange(it, q) },
            glossHighlight = null,
        )
    }

    /** A full-text headword-index hit: the query occurs inside the headword, or inside the Wylie in Wylie mode. */
    private fun containsHit(row: EntryEntity, field: PrefixField, q: String): SearchHit = when (field) {
        PrefixField.HEADWORD -> headwordHit(row, q)
        // The same "starts with, else contains the query, else its first word" rule as for headwords.
        PrefixField.WYLIE -> SearchHit(
            entry = row.toSummary(),
            field = MatchField.WYLIE,
            headwordHighlight = null,
            wylieHighlight = row.wylie?.let { Highlighter.headwordRange(it, q) },
            glossHighlight = null,
        )
    }

    private fun headwordHit(row: EntryEntity, q: String) = SearchHit(
        entry = row.toSummary(),
        field = MatchField.HEADWORD,
        headwordHighlight = Highlighter.headwordRange(row.headword, q),
        wylieHighlight = null,
        glossHighlight = null,
    )

    /** A gloss hit, highlighting the first of [queries] found in the preview. */
    private fun glossHit(row: EntryEntity, queries: List<String>) = SearchHit(
        entry = row.toSummary(),
        field = MatchField.GLOSS,
        headwordHighlight = null,
        wylieHighlight = null,
        glossHighlight = queries.firstNotNullOfOrNull { Highlighter.glossRange(row.glossPreview, it) },
    )

    /** Text compared by edit distance: normalised like a query, without a trailing tsheg. */
    private fun comparable(text: String) = TibetanScript.stripTrailingTsheg(QueryNormalizer.normalize(text))

    /**
     * Whether the prefix step lists [row] for [q] on some page: its headword or Wylie is in
     * `[q, PrefixBounds.upper(q))` under NOCASE, i.e. starts with [q] with ASCII letters folded. This is the same test
     * as in `EntryDao.ftsHeadwordOutsidePrefix`. Only the column in the query's script can match.
     */
    private fun isPrefixRow(row: EntryEntity, q: String): Boolean =
        row.headword.startsWithNoCase(q) || row.wylie?.startsWithNoCase(q) == true

    /** [startsWith] as SQLite's NOCASE compares: ASCII letters fold, every other character must be equal. */
    private fun String.startsWithNoCase(prefix: String): Boolean =
        length >= prefix.length && prefix.indices.all { asciiLower(this[it]) == asciiLower(prefix[it]) }

    private fun asciiLower(c: Char): Char = if (c in 'A'..'Z') c + ('a' - 'A') else c

    internal enum class PrefixField {
        HEADWORD,
        WYLIE,
        ;

        fun textOf(row: EntryEntity): String = when (this) {
            HEADWORD -> row.headword
            WYLIE -> row.wylie.orEmpty()
        }
    }

    /**
     * Which steps run for a (direction, query script) pair; see the class table.
     *
     * A Latin query in a Tibetan-source direction searches glosses only when `target_lang` is "en": glosses there are
     * English, written in Latin script. bo-bo glosses are Tibetan, so there a Latin query could only hit a stray quoted
     * English word, and it searches the Wylie alone.
     */
    internal data class Mode(val prefix: PrefixField?, val gloss: Boolean) {
        companion object {
            fun of(direction: DirectionEntity, sourceScript: Script, query: Script): Mode = when (sourceScript) {
                Script.TIBETAN -> when (query) {
                    Script.TIBETAN -> Mode(PrefixField.HEADWORD, gloss = false)
                    Script.LATIN -> Mode(
                        prefix = PrefixField.WYLIE.takeIf { direction.wylieCount > 0 },
                        gloss = direction.targetLang == "en", // Latin glosses only; see the KDoc
                    )
                    Script.HAN -> Mode(prefix = null, gloss = false)
                }
                Script.LATIN -> when (query) {
                    Script.LATIN -> Mode(PrefixField.HEADWORD, gloss = false)
                    Script.TIBETAN -> Mode(prefix = null, gloss = true)
                    Script.HAN -> Mode(prefix = null, gloss = false)
                }
                Script.HAN -> Mode(PrefixField.HEADWORD, gloss = query == Script.LATIN)
            }
        }
    }

    companion object {
        /** Hits in page 0's full-text block (both FTS queries sort every match before their limit; keep it small). */
        const val FULL_TEXT_LIMIT = 20

        /** Rows fetched per shortened prefix before ranking by edit distance. */
        const val SHORTENED_LIMIT = 20

        const val MAX_SUGGESTIONS = 5
    }
}

/**
 * Whether a full-text step may run for the query's FTS [tokens]. The last token is matched as a prefix (`tok*`), and a
 * very short one matches so many rows that sorting them for a keystroke is too slow. So the last token needs at least
 * three code points when it is Latin, and two when it is Tibetan or Han (one code point there is a whole letter or
 * syllable, e.g. the བ of ཀ་བ). The script is [ScriptDetector]'s for that token alone.
 */
internal fun fullTextAllowed(tokens: List<String>): Boolean {
    val last = tokens.lastOrNull() ?: return false
    val codePoints = last.codePointCount(0, last.length)
    return when (ScriptDetector.detect(last)) {
        Script.LATIN -> codePoints >= 3
        Script.TIBETAN, Script.HAN -> codePoints >= 2
    }
}

/** Characters did-you-mean drops at most from the end of a query (or of its tsheg cut). */
private const val MAX_DROPPED_CHARS = 4

/**
 * Did-you-mean step (a): shorter prefixes of the normalised query [q] to try in order, each trimmed, distinct and
 * non-empty.
 * - Tibetan: first cut after the tsheg before the last syllable ("བཀྲ་ཤིསས" → "བཀྲ་"; a trailing tsheg does not
 *   count), then drop one character at a time from that cut; without an inner tsheg, from the query itself.
 * - Otherwise: drop one character at a time.
 *
 * Character drops stop after [MAX_DROPPED_CHARS] characters and never go below one character; a candidate that would
 * end inside a surrogate pair is skipped.
 */
internal fun shortenedPrefixes(q: String, script: Script): List<String> {
    val candidates = LinkedHashSet<String>()
    var start = q
    if (script == Script.TIBETAN) {
        val base = TibetanScript.stripTrailingTsheg(q)
        val cut = base.lastIndexOf(TibetanScript.TSHEG)
        if (cut > 0) {
            start = base.substring(0, cut + 1)
            candidates += start
        }
    }
    val floor = maxOf(1, start.length - MAX_DROPPED_CHARS)
    for (length in start.length - 1 downTo floor) {
        if (start[length - 1].isHighSurrogate()) continue
        candidates += start.substring(0, length).trimEnd()
    }
    return candidates.filter { it.isNotEmpty() && it != q }
}
