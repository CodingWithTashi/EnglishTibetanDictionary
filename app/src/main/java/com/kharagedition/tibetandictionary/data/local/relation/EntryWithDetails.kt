package com.kharagedition.tibetandictionary.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.kharagedition.tibetandictionary.data.local.entity.EntryEntity
import com.kharagedition.tibetandictionary.data.local.entity.ExampleEntity
import com.kharagedition.tibetandictionary.data.local.entity.RelatedEntryEntity
import com.kharagedition.tibetandictionary.data.local.entity.SenseEntity

/**
 * An entry with everything the Entry screen shows. `@Relation` lists carry no ORDER BY: mappers sort senses by
 * (`sense_no`, `sub_no`), examples and related links by `position`.
 */
data class EntryWithDetails(
    @Embedded val entry: EntryEntity,
    @Relation(entity = SenseEntity::class, parentColumn = "id", entityColumn = "entry_id")
    val senses: List<SenseWithExamples>,
    @Relation(parentColumn = "id", entityColumn = "source_entry_id")
    val related: List<RelatedEntryEntity>,
)

/** A sense with its usage examples (unordered; sort by `position`). */
data class SenseWithExamples(
    @Embedded val sense: SenseEntity,
    @Relation(parentColumn = "id", entityColumn = "sense_id")
    val examples: List<ExampleEntity>,
)
