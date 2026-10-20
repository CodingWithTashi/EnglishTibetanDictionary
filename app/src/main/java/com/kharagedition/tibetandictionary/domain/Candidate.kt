package com.kharagedition.tibetandictionary.domain

import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.MatchField

/**
 * A direction a Latin or Wylie query may be sent to, and the field its prefix hits are counted on:
 * [MatchField.HEADWORD] for a Latin-source direction, [MatchField.WYLIE] for a Tibetan-source one. See
 * [ResolveDirection.candidates].
 */
data class Candidate(val direction: Direction, val field: MatchField)
