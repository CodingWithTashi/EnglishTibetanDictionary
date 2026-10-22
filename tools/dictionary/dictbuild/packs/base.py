"""What a dictionary pack is: `Pack` and the `Expected` counts of its full build.

A `Pack` carries one database's directions, source reader and per-table parsers, headword lookup
order, word-of-the-day direction, `meta` strings and the expected counts of a full build. The
generic stages (grouping, linking, ranking, writing) take what they need from it.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING

from dictbuild.model import DirectionRow, ParsedRow
from dictbuild.source import SourceRow
from dictbuild.wylie import WylieConverter

if TYPE_CHECKING:
    from dictbuild.related import HeadwordIndex


@dataclass(frozen=True, slots=True, kw_only=True)
class Expected:
    """The ranges (inclusive) a full build's counts must fall in."""

    directions: int
    entries: dict[str, tuple[int, int]]
    senses: dict[str, tuple[int, int]]
    examples: dict[str, tuple[int, int]]
    see_also: tuple[int, int]
    see_also_resolved_percent: tuple[float, float]
    synonyms: tuple[int, int]
    antonyms: tuple[int, int]
    wylie: int  # every entry of a Tibetan-script direction
    truncated: tuple[int, int]
    wotd: tuple[int, int]


@dataclass(frozen=True, slots=True, kw_only=True)
class Pack:
    id: str
    directions: tuple[DirectionRow, ...]  # in switcher order
    tables: tuple[str, ...]  # source tables, in parse order
    direction_by_table: dict[str, str]
    tibetan_lookup_order: tuple[str, ...]  # directions searched for a Tibetan link target
    english_direction: str  # the direction whose headwords answer English link targets
    wotd_direction_id: str
    meta: dict[str, str]  # source_name, source_author, source_url, source_license, attribution_text
    expected: Expected
    read_source: Callable[[Path, int | None], dict[str, list[SourceRow]]]
    parse: Callable[[str, SourceRow, WylieConverter, HeadwordIndex], ParsedRow]  # dispatches on the table
    # Whether an entry drops a sense identical (gloss, part of speech, notes) to an earlier one of the entry.
    drop_identical_senses: bool = False

    def direction(self, direction_id: str) -> DirectionRow:
        """The pack's direction with this id; KeyError when it has none."""
        for direction in self.directions:
            if direction.id == direction_id:
                return direction
        raise KeyError(f"pack {self.id!r} has no direction {direction_id!r}")

    def entry_id_for(self, direction_id: str, source_id: int) -> int:
        """The stable entry id: the direction's `id_base` plus the source `_id` of the group's first row."""
        return self.direction(direction_id).id_base + source_id
