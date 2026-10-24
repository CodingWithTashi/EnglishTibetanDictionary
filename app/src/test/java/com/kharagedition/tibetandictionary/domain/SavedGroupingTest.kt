package com.kharagedition.tibetandictionary.domain

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class SavedGroupingTest {

    private val today = LocalDate.parse("2026-10-19")

    private fun saved(id: Long, at: String): SavedEntry =
        SampleData.savedTashi.copy(entryId = id, savedAt = Instant.parse(at).toEpochMilli())

    private fun group(vararg items: SavedEntry, today: LocalDate = this.today, zone: ZoneId = ZoneOffset.UTC) =
        SavedGrouping.group(items.toList(), today, zone)

    private fun List<SavedGroup>.ids(): List<Pair<SavedPeriod, List<Long>>> = map { group ->
        group.period to group.items.map { it.entryId }
    }

    @Test
    fun `groupBy uses the same periods as group`() {
        val items = listOf(
            saved(1, "2026-10-19T08:00:00Z"),
            saved(2, "2026-10-14T10:00:00Z"),
            saved(3, "2026-10-02T10:00:00Z"),
            saved(4, "2025-01-01T10:00:00Z"),
        )
        val generic = SavedGrouping.groupBy(items, { Instant.ofEpochMilli(it.savedAt) }, today, ZoneOffset.UTC)

        assertThat(generic.map { (period, list) -> period to list.map { it.entryId } })
            .isEqualTo(group(*items.toTypedArray()).ids())
    }

    @Test
    fun `words fall into today, this week, this month and earlier`() {
        val groups = group(
            saved(1, "2026-10-19T08:00:00Z"),
            saved(2, "2026-10-19T00:00:00Z"),
            saved(3, "2026-10-18T23:59:00Z"),
            saved(4, "2026-10-12T10:00:00Z"),
            saved(5, "2026-10-11T10:00:00Z"),
            saved(6, "2026-10-01T00:00:00Z"),
            saved(7, "2026-09-30T23:59:00Z"),
            saved(8, "2025-10-19T08:00:00Z"),
        )

        assertThat(groups.ids()).containsExactly(
            SavedPeriod.TODAY to listOf(1L, 2L),
            SavedPeriod.THIS_WEEK to listOf(3L, 4L),
            SavedPeriod.THIS_MONTH to listOf(5L, 6L),
            SavedPeriod.EARLIER to listOf(7L, 8L),
        ).inOrder()
    }

    @Test
    fun `each group lists its words newest first whatever the input order`() {
        val groups = group(
            saved(1, "2026-10-15T08:00:00Z"),
            saved(2, "2026-10-19T07:00:00Z"),
            saved(3, "2026-10-17T08:00:00Z"),
            saved(4, "2026-10-19T09:00:00Z"),
        )

        assertThat(groups.ids()).containsExactly(
            SavedPeriod.TODAY to listOf(4L, 2L),
            SavedPeriod.THIS_WEEK to listOf(3L, 1L),
        ).inOrder()
    }

    @Test
    fun `empty groups are left out`() {
        val groups = group(saved(1, "2026-10-19T08:00:00Z"), saved(2, "2026-03-02T08:00:00Z"))

        assertThat(groups.map { it.period }).containsExactly(SavedPeriod.TODAY, SavedPeriod.EARLIER).inOrder()
    }

    @Test
    fun `no words give no groups`() {
        assertThat(group()).isEmpty()
    }

    @Test
    fun `the week reaches back across a month boundary before the month starts`() {
        val groups = group(
            saved(1, "2026-09-28T08:00:00Z"),
            saved(2, "2026-09-20T08:00:00Z"),
            today = LocalDate.parse("2026-10-03"),
        )

        assertThat(groups.ids()).containsExactly(
            SavedPeriod.THIS_WEEK to listOf(1L),
            SavedPeriod.EARLIER to listOf(2L),
        ).inOrder()
    }

    @Test
    fun `the day a word was saved is read in the given zone`() {
        // 23:30 UTC on the 18th is already the 19th in Lhasa (UTC+8).
        val lateEvening = saved(1, "2026-10-18T23:30:00Z")

        assertThat(group(lateEvening, zone = ZoneId.of("Asia/Shanghai")).single().period).isEqualTo(SavedPeriod.TODAY)
        assertThat(group(lateEvening, zone = ZoneOffset.UTC).single().period).isEqualTo(SavedPeriod.THIS_WEEK)
    }

    @Test
    fun `a word saved after today counts as today`() {
        assertThat(group(saved(1, "2026-10-20T08:00:00Z")).single().period).isEqualTo(SavedPeriod.TODAY)
    }
}
