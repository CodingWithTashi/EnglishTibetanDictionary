"""Direction capability counts and the build's expected ranges (plan §4 "Capabilities", §5.3 table).

Every `*_count` column of `directions` is computed from the rows actually written, so the app's
feature flags (`count > 0`) always agree with the data. `check_ranges` compares a full build with
the plan's expected counts; the build fails when any of them is off.
"""

from __future__ import annotations

import dataclasses
from collections import Counter
from collections.abc import Sequence
from dataclasses import dataclass

from dictbuild.model import DIRECTIONS, RELATION_SEE_ALSO, RELATION_SYNONYM, DirectionRow
from dictbuild.writer import ContentRows

# Expected counts after a full build (plan §5.3). The en-bo count follows the T1.2 ruling (one more
# headword than the plan's 35,887 once "K2" keeps its digit). bo-en has one entry fewer than its
# 76,828 source headwords: དཔལ་གྱི་ཁབ་ (row 23263, only "{Ship 64b.2}") has no sense and no link.
EXPECTED_ENTRIES = {"bo-en": (76_827, 76_827), "bo-bo": (55_124, 55_124), "en-bo": (35_887, 35_888)}
EXPECTED_SENSES = {"bo-en": (88_000, 88_938), "bo-bo": (65_000, 80_000), "en-bo": (55_000, 75_000)}
EXPECTED_EXAMPLES = {"bo-bo": (18_000, 19_000), "en-bo": (2_000, 5_000)}
EXPECTED_SEE_ALSO = (4_000, 5_000)
EXPECTED_SEE_ALSO_RESOLVED_PERCENT = (70.0, 100.0)
# Synonyms are `= English term` links only (T1.3 follow-up ruling). The source has 38 `= Latin`
# occurrences; 17 are headword-like terms (the rest are equations and expansions in example text),
# so the range brackets 17 rather than the ruled 200–3,500.
EXPECTED_SYNONYMS = (10, 40)
EXPECTED_WYLIE = 131_951  # every bo-en and bo-bo entry
EXPECTED_TRUNCATED = (480, 600)
EXPECTED_WOTD = (25_000, 60_000)


@dataclass(frozen=True, slots=True)
class RangeCheck:
    name: str
    value: float
    minimum: float
    maximum: float

    @property
    def ok(self) -> bool:
        return self.minimum <= self.value <= self.maximum

    def as_dict(self) -> dict[str, object]:
        return {"name": self.name, "value": self.value, "min": self.minimum, "max": self.maximum, "ok": self.ok}


def direction_rows(rows: ContentRows) -> list[DirectionRow]:
    """The `directions` rows with every capability count taken from `rows`."""
    direction_of = {entry.id: entry.direction_id for entry in rows.entries}
    counts: dict[str, Counter[str]] = {direction.id: Counter() for direction in DIRECTIONS}
    for entry in rows.entries:
        count = counts[entry.direction_id]
        count["entry_count"] += 1
        count["wylie_count"] += entry.wylie is not None
        count["audio_count"] += entry.audio_file is not None
        count["wotd_count"] += entry.wotd_rank is not None
    for sense in rows.senses:
        count = counts[direction_of[sense.entry_id]]
        count["sense_count"] += 1
        count["pos_count"] += sense.part_of_speech is not None
        count["label_count"] += sense.label is not None
        count["truncated_count"] += bool(sense.is_truncated)
    for example in rows.examples:
        counts[direction_of[example.entry_id]]["example_count"] += 1
    for related in rows.related_entries:
        counts[direction_of[related.source_entry_id]]["related_count"] += 1
    return [dataclasses.replace(direction, **counts[direction.id]) for direction in DIRECTIONS]


def table_counts(directions: Sequence[DirectionRow], rows: ContentRows) -> dict[str, int]:
    """Row count of every content table except `meta`."""
    return {
        "directions": len(directions),
        "entries": len(rows.entries),
        "senses": len(rows.senses),
        "examples": len(rows.examples),
        "related_entries": len(rows.related_entries),
    }


def check_ranges(directions: Sequence[DirectionRow], rows: ContentRows, *, wylie_available: bool) -> list[RangeCheck]:
    """Compare a full build with the plan's expected counts (§5.3 "Expected after build")."""
    by_id = {direction.id: direction for direction in directions}
    checks = [RangeCheck("directions", len(directions), 3, 3)]
    for direction_id, (low, high) in EXPECTED_ENTRIES.items():
        checks.append(RangeCheck(f"entries {direction_id}", by_id[direction_id].entry_count, low, high))
    for direction_id, (low, high) in EXPECTED_SENSES.items():
        checks.append(RangeCheck(f"senses {direction_id}", by_id[direction_id].sense_count, low, high))
    for direction_id, (low, high) in EXPECTED_EXAMPLES.items():
        checks.append(RangeCheck(f"examples {direction_id}", by_id[direction_id].example_count, low, high))
    relations = Counter(related.relation for related in rows.related_entries)
    see_also_resolved = sum(
        1 for related in rows.related_entries
        if related.relation == RELATION_SEE_ALSO and related.target_entry_id is not None
    )  # fmt: skip
    checks.append(RangeCheck("related see_also", relations[RELATION_SEE_ALSO], *EXPECTED_SEE_ALSO))
    if wylie_available:
        percent = round(100 * see_also_resolved / max(relations[RELATION_SEE_ALSO], 1), 1)
        checks.append(RangeCheck("related see_also resolved %", percent, *EXPECTED_SEE_ALSO_RESOLVED_PERCENT))
    checks.append(RangeCheck("related synonym", relations[RELATION_SYNONYM], *EXPECTED_SYNONYMS))
    wylie = sum(direction.wylie_count for direction in directions)
    expected_wylie = EXPECTED_WYLIE if wylie_available else 0
    checks.append(RangeCheck("entries.wylie non-null", wylie, expected_wylie, expected_wylie))
    truncated = sum(direction.truncated_count for direction in directions)
    checks.append(RangeCheck("senses.is_truncated", truncated, *EXPECTED_TRUNCATED))
    wotd = sum(direction.wotd_count for direction in directions)
    checks.append(RangeCheck("wotd_rank non-null", wotd, *EXPECTED_WOTD))
    return checks
