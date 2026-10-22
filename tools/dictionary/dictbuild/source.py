"""Read-only access to the Monlam source database (tables `entotb`, `tben`, `tbtb`)."""

from __future__ import annotations

import sqlite3
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path

# The tables `read_rows` accepts (the table name is spliced into the SQL).
MONLAM_TABLES = ("tben", "tbtb", "entotb")


@dataclass(frozen=True, slots=True)
class SourceRow:
    source_id: int
    word: str
    definition: str


def open_source(path: Path | str) -> sqlite3.Connection:
    """Open the source database read-only; a missing file raises instead of being created."""
    uri = Path(path).resolve().as_uri() + "?mode=ro"
    return sqlite3.connect(uri, uri=True)


def read_rows(conn: sqlite3.Connection, table: str, *, limit: int | None = None) -> Iterator[SourceRow]:
    """Rows of a Monlam source table in `_id` order; NULL text reads as "".

    This is the Monlam reader only; the DDC StarDict files are read by `stardict.py`.
    """
    if table not in MONLAM_TABLES:
        raise ValueError(f"unknown source table {table!r}; expected one of {sorted(MONLAM_TABLES)}")
    sql = f"SELECT _id, word, definition FROM {table} ORDER BY _id"
    params: tuple[int, ...] = ()
    if limit is not None:
        sql += " LIMIT ?"
        params = (limit,)
    cursor = conn.execute(sql, params)
    return (SourceRow(source_id, word or "", definition or "") for source_id, word, definition in cursor)
