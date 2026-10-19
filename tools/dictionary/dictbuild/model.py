"""Records the pipeline passes between stages.

`*Row` classes are the rows written to dictionary.db: their field names and order mirror the exported
Room schema's columns (pinned by tests/test_schema.py), so `row_values(row)` binds straight to
`Table.insert_sql()`. `Parsed*` classes are table-parser output, before grouping assigns entry ids
and continuous sense numbers.
"""

from __future__ import annotations

import dataclasses
from dataclasses import dataclass, field
from typing import Any

GLOSS_LANG_BO = "bo"
GLOSS_LANG_EN = "en"

RELATION_SEE_ALSO = "see_also"
RELATION_SYNONYM = "synonym"


@dataclass(frozen=True, slots=True, kw_only=True)
class DirectionRow:
    id: str
    source_lang: str
    target_lang: str
    source_script: str
    label_en: str
    label_bo: str
    short_label: str
    position: int
    id_base: int
    entry_count: int = 0
    sense_count: int = 0
    example_count: int = 0
    related_count: int = 0
    wylie_count: int = 0
    audio_count: int = 0
    pos_count: int = 0
    label_count: int = 0
    truncated_count: int = 0
    wotd_count: int = 0


@dataclass(frozen=True, slots=True, kw_only=True)
class EntryRow:
    id: int
    direction_id: str
    source_id: int
    headword: str
    wylie: str | None = None
    gloss_preview: str
    part_of_speech: str | None = None
    phonetic: str | None = None
    audio_file: str | None = None
    sense_count: int
    is_truncated: bool = False
    sort_order: int
    wotd_rank: int | None = None


@dataclass(frozen=True, slots=True, kw_only=True)
class SenseRow:
    id: int
    entry_id: int
    sense_no: int
    sub_no: int | None = None
    part_of_speech: str | None = None
    label: str | None = None
    gloss: str
    gloss_lang: str
    note: str | None = None
    source_tag: str | None = None
    is_truncated: bool = False
    source_id: int


@dataclass(frozen=True, slots=True, kw_only=True)
class ExampleRow:
    id: int
    sense_id: int
    entry_id: int
    position: int
    text: str
    translation: str | None = None
    audio_file: str | None = None


@dataclass(frozen=True, slots=True, kw_only=True)
class RelatedEntryRow:
    id: int
    source_entry_id: int
    target_entry_id: int | None = None
    target_headword: str
    target_wylie: str | None = None
    relation: str
    position: int


@dataclass(frozen=True, slots=True, kw_only=True)
class MetaRow:
    key: str
    value: str | None = None


ROW_TYPES: dict[str, type] = {
    "directions": DirectionRow,
    "entries": EntryRow,
    "senses": SenseRow,
    "examples": ExampleRow,
    "related_entries": RelatedEntryRow,
    "meta": MetaRow,
}


def row_values(row: Any) -> tuple[Any, ...]:
    """A row's values in column order, for `Table.insert_sql()`."""
    return tuple(getattr(row, column.name) for column in dataclasses.fields(row))


@dataclass(slots=True)
class ParsedExample:
    text: str
    translation: str | None = None


@dataclass(slots=True)
class ParsedSense:
    """One future `senses` row. `sense_no` is block-relative until grouping renumbers it per entry."""

    gloss: str
    gloss_lang: str
    sense_no: int = 1
    sub_no: int | None = None
    part_of_speech: str | None = None
    labels: list[str] = field(default_factory=list)
    note: str | None = None
    source_tags: list[str] = field(default_factory=list)
    is_truncated: bool = False
    examples: list[ParsedExample] = field(default_factory=list)


@dataclass(slots=True)
class RelatedCandidate:
    """A see-also or synonym target as written in the source, resolved to an entry id later."""

    headword: str
    relation: str
    wylie: str | None = None


@dataclass(slots=True)
class ParsedRow:
    """One source row after table-specific parsing, before rows are grouped into entries."""

    source_id: int
    headword: str
    group_key: str
    homonym: str | None = None
    senses: list[ParsedSense] = field(default_factory=list)
    related: list[RelatedCandidate] = field(default_factory=list)
