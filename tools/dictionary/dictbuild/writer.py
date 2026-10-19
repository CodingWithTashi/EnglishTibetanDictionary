"""Turn entry drafts into dictionary.db rows and write the database (plan §5.1, §5.3).

`assemble_rows` assigns the build ids (entries keep `id_base + source _id`; senses, examples and
related links are numbered sequentially) and derives the entry summary columns. `write_database`
builds the file next to its destination and moves it into place atomically, so a failed build never
leaves a half-written asset behind.
"""

from __future__ import annotations

import json
import os
import re
import sqlite3
import tempfile
from collections.abc import Iterable, Mapping, Sequence
from contextlib import closing
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from dictbuild.model import (
    EntryDraft,
    EntryRow,
    ExampleRow,
    MetaRow,
    ParsedSense,
    RelatedEntryRow,
    SenseRow,
    row_values,
)
from dictbuild.schema import Schema, set_user_version
from dictbuild.text import SHAD, TSHEG

# Rule "gloss_preview = first sense gloss truncated to ≤120 chars at a word/tsheg boundary + '…'".
PREVIEW_LIMIT = 120
ELLIPSIS = "…"
_PREVIEW_TRIM = " ,;:"
# A redirect-only entry (zero senses, e.g. a bo-en row that was only `SA x`) previews its targets.
REDIRECT_PREFIX = "→ "

LABEL_SEPARATOR = ","
SOURCE_TAG_SEPARATOR = ";"
_POS_SEPARATOR = re.compile(r"\s*,\s*")


@dataclass(slots=True)
class ContentRows:
    entries: list[EntryRow] = field(default_factory=list)
    senses: list[SenseRow] = field(default_factory=list)
    examples: list[ExampleRow] = field(default_factory=list)
    related_entries: list[RelatedEntryRow] = field(default_factory=list)


def gloss_preview(text: str, limit: int = PREVIEW_LIMIT) -> str:
    """`text`, or its longest prefix ending at a space, tsheg or shad, plus "…", within `limit` chars."""
    if len(text) <= limit:
        return text
    cut = text[: limit - len(ELLIPSIS)]
    boundary = max(cut.rfind(" "), cut.rfind(TSHEG), cut.rfind(SHAD))
    if boundary > 0:
        cut = cut[: boundary + 1] if cut[boundary] in (TSHEG, SHAD) else cut[:boundary]
    return cut.rstrip(_PREVIEW_TRIM) + ELLIPSIS


def entry_part_of_speech(senses: Iterable[ParsedSense]) -> str | None:
    """Distinct sense part-of-speech codes in first-seen order ("adj,n,vt"), or None."""
    codes: list[str] = []
    for sense in senses:
        for code in _POS_SEPARATOR.split(sense.part_of_speech or ""):
            if code and code not in codes:
                codes.append(code)
    return ",".join(codes) or None


def assemble_rows(drafts: Iterable[EntryDraft], wotd_ranks: Mapping[int, int]) -> ContentRows:
    """Rows for `entries`, `senses`, `examples` and `related_entries`, in draft order."""
    rows = ContentRows()
    for draft in drafts:
        entry_id = draft.entry_id
        rows.entries.append(
            EntryRow(
                id=entry_id,
                direction_id=draft.direction_id,
                source_id=draft.source_id,
                headword=draft.headword,
                wylie=draft.wylie,
                gloss_preview=_preview(draft),
                part_of_speech=entry_part_of_speech(draft.senses),
                sense_count=len({sense.sense_no for sense in draft.senses}),
                is_truncated=draft.is_truncated,
                sort_order=draft.source_id,
                wotd_rank=wotd_ranks.get(entry_id),
            )
        )
        for sense in draft.senses:
            sense_id = len(rows.senses) + 1
            rows.senses.append(
                SenseRow(
                    id=sense_id,
                    entry_id=entry_id,
                    sense_no=sense.sense_no,
                    sub_no=sense.sub_no,
                    part_of_speech=sense.part_of_speech,
                    label=LABEL_SEPARATOR.join(sense.labels) or None,
                    gloss=sense.gloss,
                    gloss_lang=sense.gloss_lang,
                    note=sense.note,
                    source_tag=SOURCE_TAG_SEPARATOR.join(sense.source_tags) or None,
                    is_truncated=sense.is_truncated,
                    source_id=sense.source_id,
                )
            )
            for position, example in enumerate(sense.examples):
                rows.examples.append(
                    ExampleRow(
                        id=len(rows.examples) + 1,
                        sense_id=sense_id,
                        entry_id=entry_id,
                        position=position,
                        text=example.text,
                        translation=example.translation,
                    )
                )
        for position, candidate in enumerate(draft.related):
            rows.related_entries.append(
                RelatedEntryRow(
                    id=len(rows.related_entries) + 1,
                    source_entry_id=entry_id,
                    target_entry_id=candidate.target_entry_id,
                    target_headword=candidate.headword,
                    target_wylie=candidate.wylie,
                    relation=candidate.relation,
                    position=position,
                )
            )
    return rows


def meta_rows(values: Mapping[str, Any], row_counts: Mapping[str, int]) -> list[MetaRow]:
    """`meta` rows: the given values as text, plus `row_counts_json` (every table, meta included)."""
    rows = [MetaRow(key=key, value=None if value is None else str(value)) for key, value in values.items()]
    counts = {**row_counts, "meta": len(rows) + 1}
    rows.append(MetaRow(key="row_counts_json", value=json.dumps(counts, sort_keys=True)))
    return rows


def write_database(path: Path, schema: Schema, tables: Mapping[str, Sequence[Any]]) -> None:
    """Create `path` from the schema's DDL and `tables` (table name → rows, in insert order).

    The database is built in memory: tables and indices first, then the rows (foreign keys
    enforced), Room's FTS content-sync triggers verbatim, an FTS `rebuild` and `PRAGMA
    user_version`. `VACUUM INTO` then writes it compacted to a temporary file beside `path`, which
    is set to `journal_mode=DELETE` and renamed over `path`; disk use peaks at the final file size
    and a failed build leaves nothing behind.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    handle, name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    os.close(handle)
    temporary = Path(name)
    try:
        temporary.unlink()  # VACUUM INTO needs a file name that does not exist yet
        with closing(sqlite3.connect(":memory:", isolation_level=None)) as conn:
            _fill(conn, schema, tables)
            conn.execute("VACUUM INTO ?", (str(temporary),))
        with closing(sqlite3.connect(temporary, isolation_level=None)) as conn:
            conn.execute("PRAGMA journal_mode = DELETE")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)
        temporary.with_name(temporary.name + "-journal").unlink(missing_ok=True)


def _fill(conn: sqlite3.Connection, schema: Schema, tables: Mapping[str, Sequence[Any]]) -> None:
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("BEGIN")
    for table in schema.tables:
        conn.execute(table.create_sql)
    for table in schema.tables:
        for sql in table.index_sqls:
            conn.execute(sql)
    for name, rows in tables.items():
        conn.executemany(schema.table(name).insert_sql(), (row_values(row) for row in rows))
    for table in schema.tables:
        for sql in table.trigger_sqls:
            conn.execute(sql)
    for table in schema.tables:
        if table.is_fts:
            conn.execute(f"INSERT INTO `{table.name}`(`{table.name}`) VALUES('rebuild')")
    set_user_version(conn, schema.version)
    conn.execute("COMMIT")
    if violations := conn.execute("PRAGMA foreign_key_check").fetchall():
        raise sqlite3.IntegrityError(f"foreign key violations: {violations[:5]}")


def _preview(draft: EntryDraft) -> str:
    if draft.senses:
        return gloss_preview(draft.senses[0].gloss)
    if draft.related:
        return gloss_preview(REDIRECT_PREFIX + ", ".join(candidate.headword for candidate in draft.related))
    return ""
