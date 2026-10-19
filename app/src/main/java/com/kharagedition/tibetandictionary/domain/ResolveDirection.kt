package com.kharagedition.tibetandictionary.domain

import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.MatchField
import com.kharagedition.tibetandictionary.data.model.Script

/**
 * Picks the direction a query is searched in from its [InputScript], the user's selection and per-direction hit
 * counts. Only visible directions (`entryCount > 0`) count, taken in `position` order.
 */
object ResolveDirection {

    /**
     * Resolves the direction for a query written in [script]:
     * - **Pinned**: [pinned] (the user tapped a segment) keeps [selected]; Latin/Wylie still get an alternate.
     * - **Tibetan**: [selected] if its source is Tibetan, else the first Tibetan-source direction. Han works the
     *   same with Han-source directions. Neither has an alternate.
     * - **Latin/Wylie**: the [candidates] compete. The candidate with [hits] wins; when both or neither have hits,
     *   the one with [exactHits] wins; when that ties too, [selected] if it is a candidate, else the headword
     *   candidate for [InputScript.LATIN] and the Wylie candidate for [InputScript.WYLIE]. Counts only matter as
     *   zero or not, and hits in other directions are ignored.
     * - **Alternate**: the other candidate when it has hits, or when no candidate has any; for a pinned
     *   non-candidate, the better of the two by the same order.
     * - **No candidate**: [selected] is kept.
     *
     * [Resolution.autoSelected] is true when the result differs from [selected] and the selection is not pinned.
     *
     * @param selected id of the direction the user has selected (default or last tap).
     * @param hits prefix-search hit counts by direction id, counted on each candidate's field; missing ids count
     *   as zero.
     * @param exactHits exact-match hit counts by direction id, when the caller has them.
     */
    fun resolve(
        script: InputScript,
        selected: String,
        pinned: Boolean,
        directions: List<Direction>,
        hits: Map<String, Int>,
        exactHits: Map<String, Int> = emptyMap(),
    ): Resolution = when (script) {
        InputScript.TIBETAN -> bySource(Script.TIBETAN, selected, pinned, visible(directions))
        InputScript.HAN -> bySource(Script.HAN, selected, pinned, visible(directions))
        InputScript.LATIN, InputScript.WYLIE ->
            byHits(script, selected, pinned, candidates(script, selected, directions), hits, exactHits)
    }

    /**
     * The directions a Latin or Wylie query competes for, headword candidate first; either may be missing:
     * - **Headword** ([MatchField.HEADWORD]): [selected] when it is visible and Latin-source, else the first
     *   visible Latin-source direction.
     * - **Wylie** ([MatchField.WYLIE]): [selected] when it is visible, Tibetan-source and has Wylie, else the
     *   first such direction.
     *
     * Empty for Tibetan and Han queries, which are resolved without hits. The caller counts each candidate's
     * prefix hits on its [Candidate.field] and passes them to [resolve].
     */
    fun candidates(script: InputScript, selected: String, directions: List<Direction>): List<Candidate> {
        if (script == InputScript.TIBETAN || script == InputScript.HAN) return emptyList()
        val visible = visible(directions)
        val headword = selectedOrFirst(visible, selected) { it.sourceScript == Script.LATIN }
        val wylie = selectedOrFirst(visible, selected) { it.sourceScript == Script.TIBETAN && it.features.hasWylie }
        return listOfNotNull(
            headword?.let { Candidate(it, MatchField.HEADWORD) },
            wylie?.let { Candidate(it, MatchField.WYLIE) },
        )
    }

    private fun visible(directions: List<Direction>): List<Direction> =
        directions.filter { it.entryCount > 0 }.sortedBy { it.position }

    private inline fun selectedOrFirst(
        visible: List<Direction>,
        selected: String,
        predicate: (Direction) -> Boolean,
    ): Direction? = visible.firstOrNull { it.id == selected && predicate(it) } ?: visible.firstOrNull(predicate)

    private fun bySource(source: Script, selected: String, pinned: Boolean, visible: List<Direction>): Resolution {
        val id = when {
            pinned -> selected
            visible.any { it.id == selected && it.sourceScript == source } -> selected
            else -> visible.firstOrNull { it.sourceScript == source }?.id ?: selected
        }
        return Resolution(id, autoSelected = id != selected, alternateId = null)
    }

    private fun byHits(
        script: InputScript,
        selected: String,
        pinned: Boolean,
        candidates: List<Candidate>,
        hits: Map<String, Int>,
        exactHits: Map<String, Int>,
    ): Resolution {
        if (candidates.isEmpty()) return Resolution(selected, autoSelected = false, alternateId = null)

        val preferred = if (script == InputScript.LATIN) MatchField.HEADWORD else MatchField.WYLIE
        val hasHits = { c: Candidate -> (hits[c.direction.id] ?: 0) > 0 }
        val rank = compareBy<Candidate>(
            hasHits,
            { (exactHits[it.direction.id] ?: 0) > 0 },
            { it.direction.id == selected },
            { it.field == preferred },
        )

        val chosen = if (pinned) selected else candidates.maxWith(rank).direction.id
        val noneHaveHits = candidates.none(hasHits)
        val alternate = candidates
            .filter { it.direction.id != chosen && (noneHaveHits || hasHits(it)) }
            .maxWithOrNull(rank)
        return Resolution(chosen, autoSelected = !pinned && chosen != selected, alternateId = alternate?.direction?.id)
    }
}
