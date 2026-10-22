package com.kharagedition.tibetandictionary.data.repository

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.data.local.related
import com.kharagedition.tibetandictionary.data.model.Relation
import org.junit.Test

class MappersTest {

    private fun relationOf(value: String): Relation =
        related(id = 1, sourceEntryId = 1, targetEntryId = null, targetHeadword = "x", position = 0, relation = value)
            .toDomain().relation

    @Test
    fun relationAntonymMaps() {
        assertThat(relationOf("antonym")).isEqualTo(Relation.ANTONYM)
        assertThat(relationOf("synonym")).isEqualTo(Relation.SYNONYM)
        assertThat(relationOf("see_also")).isEqualTo(Relation.SEE_ALSO)
        assertThat(relationOf("something_else")).isEqualTo(Relation.SEE_ALSO)
    }
}
