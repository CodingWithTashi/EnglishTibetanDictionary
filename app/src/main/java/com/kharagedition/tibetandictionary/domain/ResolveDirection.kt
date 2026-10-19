package com.kharagedition.tibetandictionary.domain

import com.kharagedition.tibetandictionary.data.model.Direction
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
     * - **Latin/Wylie**: the candidates are the first Latin-source direction (searched by headword) and the first
     *   Tibetan-source direction with Wylie (searched by Wylie). The candidate with [hits] wins; when both or
     *   neither have hits, the one with [exactHits] wins; when that ties too, [selected] if it is a candidate,
     *   else the Latin-source one for [InputScript.LATIN] and the Tibetan-source one for [InputScript.WYLIE].
     *   Counts only matter as zero or not, and hits in other directions are ignored.
     * - **Alternate**: the other candidate when it has hits, or when no candidate has any; for a pinned
     *   non-candidate, the better of the two by the same order.
     * - **No candidate**: [selected] is kept.
     *
     * [Resolution.autoSelected] is true when the result differs from [selected] and the selection is not pinned.
     *
     * @param selected id of the direction the user has selected (default or last tap).
     * @param hits prefix-search hit counts by direction id; missing ids count as zero.
     * @param exactHits exact-match hit counts by direction id, when the caller has them.
     */
    fun resolve(
        script: InputScript,
        selected: String,
        pinned: Boolean,
        directions: List<Direction>,
        hits: Map<String, Int>,
        exactHits: Map<String, Int> = emptyMap(),
    ): Resolution {
        val visible = directions.filter { it.entryCount > 0 }.sortedBy { it.position }
        return when (script) {
            InputScript.TIBETAN -> bySource(Script.TIBETAN, selected, pinned, visible)
            InputScript.HAN -> bySource(Script.HAN, selected, pinned, visible)
            InputScript.LATIN, InputScript.WYLIE -> byHits(script, selected, pinned, visible, hits, exactHits)
        }
    }

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
        visible: List<Direction>,
        hits: Map<String, Int>,
        exactHits: Map<String, Int>,
    ): Resolution {
        val headword = visible.firstOrNull { it.sourceScript == Script.LATIN }
        val wylie = visible.firstOrNull { it.sourceScript == Script.TIBETAN && it.features.hasWylie }
        val candidates = listOfNotNull(headword, wylie)
        if (candidates.isEmpty()) return Resolution(selected, autoSelected = false, alternateId = null)

        val preferred = if (script == InputScript.LATIN) headword else wylie
        val hasHits = { d: Direction -> (hits[d.id] ?: 0) > 0 }
        val rank = compareBy<Direction>(
            hasHits,
            { (exactHits[it.id] ?: 0) > 0 },
            { it.id == selected },
            { it == preferred },
        )

        val chosen = if (pinned) selected else candidates.maxWith(rank).id
        val noneHaveHits = candidates.none(hasHits)
        val alternate = candidates
            .filter { it.id != chosen && (noneHaveHits || hasHits(it)) }
            .maxWithOrNull(rank)
        return Resolution(chosen, autoSelected = !pinned && chosen != selected, alternateId = alternate?.id)
    }
}
