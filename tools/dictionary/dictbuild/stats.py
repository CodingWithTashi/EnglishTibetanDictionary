"""Direction capability counts and the build's expected ranges (plan §4 "Capabilities", §5.3 table).

Every `*_count` column of `directions` is computed from the rows actually written, so the app's
feature flags (`count > 0`) always agree with the data. `check_ranges` compares a full build with
the pack's expected counts; the build fails when any of them is off.
"""

from __future__ import annotations

import dataclasses
from collections import Counter
from collections.abc import Sequence
from dataclasses import dataclass
from typing import TYPE_CHECKING

from dictbuild.model import RELATION_ANTONYM, RELATION_SEE_ALSO, RELATION_SYNONYM, DirectionRow
from dictbuild.writer import ContentRows

if TYPE_CHECKING:
    from dictbuild.packs import Pack

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


def direction_rows(pack: Pack, rows: ContentRows) -> list[DirectionRow]:
    """The `directions` rows with every capability count taken from `rows`."""
    direction_of = {entry.id: entry.direction_id for entry in rows.entries}
    counts: dict[str, Counter[str]] = {direction.id: Counter() for direction in pack.directions}
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
    return [dataclasses.replace(direction, **counts[direction.id]) for direction in pack.directions]


def table_counts(directions: Sequence[DirectionRow], rows: ContentRows) -> dict[str, int]:
    """Row count of every content table except `meta`."""
    return {
        "directions": len(directions),
        "entries": len(rows.entries),
        "senses": len(rows.senses),
        "examples": len(rows.examples),
        "related_entries": len(rows.related_entries),
    }


def check_ranges(
    pack: Pack, directions: Sequence[DirectionRow], rows: ContentRows, *, wylie_available: bool
) -> list[RangeCheck]:
    """Compare a full build with the pack's expected counts (plan §5.3 "Expected after build")."""
    expected = pack.expected
    by_id = {direction.id: direction for direction in directions}
    checks = [RangeCheck("directions", len(directions), expected.directions, expected.directions)]
    for direction_id, (low, high) in expected.entries.items():
        checks.append(RangeCheck(f"entries {direction_id}", by_id[direction_id].entry_count, low, high))
    for direction_id, (low, high) in expected.senses.items():
        checks.append(RangeCheck(f"senses {direction_id}", by_id[direction_id].sense_count, low, high))
    for direction_id, (low, high) in expected.examples.items():
        checks.append(RangeCheck(f"examples {direction_id}", by_id[direction_id].example_count, low, high))
    relations = Counter(related.relation for related in rows.related_entries)
    see_also_resolved = sum(
        1 for related in rows.related_entries
        if related.relation == RELATION_SEE_ALSO and related.target_entry_id is not None
    )  # fmt: skip
    checks.append(RangeCheck("related see_also", relations[RELATION_SEE_ALSO], *expected.see_also))
    if wylie_available:
        percent = round(100 * see_also_resolved / max(relations[RELATION_SEE_ALSO], 1), 1)
        checks.append(RangeCheck("related see_also resolved %", percent, *expected.see_also_resolved_percent))
    checks.append(RangeCheck("related synonym", relations[RELATION_SYNONYM], *expected.synonyms))
    checks.append(RangeCheck("related antonym", relations[RELATION_ANTONYM], *expected.antonyms))
    wylie = sum(direction.wylie_count for direction in directions)
    expected_wylie = expected.wylie if wylie_available else 0
    checks.append(RangeCheck("entries.wylie non-null", wylie, expected_wylie, expected_wylie))
    truncated = sum(direction.truncated_count for direction in directions)
    checks.append(RangeCheck("senses.is_truncated", truncated, *expected.truncated))
    wotd = sum(direction.wotd_count for direction in directions)
    checks.append(RangeCheck("wotd_rank non-null", wotd, *expected.wotd))
    return checks
