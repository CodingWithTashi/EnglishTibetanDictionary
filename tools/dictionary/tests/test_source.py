"""Reading the Monlam source database (read-only)."""

from __future__ import annotations

import sqlite3
from contextlib import closing
from pathlib import Path

import pytest

from dictbuild.source import SourceRow, open_source, read_rows


@pytest.fixture
def source_path(tmp_path: Path) -> Path:
    path = tmp_path / "Monlam dic.ml"  # a space in the name exercises the URI quoting
    with closing(sqlite3.connect(path)) as conn:
        conn.execute("CREATE TABLE tben (_id integer primary key autoincrement, word text, definition text)")
        conn.executemany(
            "INSERT INTO tben (_id, word, definition) VALUES (?, ?, ?)",
            [(3, "ཀ་ཀ་ནི་", '"small coin"'), (1, "ཀ་", '"one, pillar"'), (2, "ཀ་ཀ་", None)],
        )
        conn.commit()
    return path


def test_read_rows_in_source_id_order(source_path: Path) -> None:
    with closing(open_source(source_path)) as conn:
        rows = list(read_rows(conn, "tben"))

    assert rows == [
        SourceRow(source_id=1, word="ཀ་", definition='"one, pillar"'),
        SourceRow(source_id=2, word="ཀ་ཀ་", definition=""),
        SourceRow(source_id=3, word="ཀ་ཀ་ནི་", definition='"small coin"'),
    ]


def test_read_rows_limit(source_path: Path) -> None:
    with closing(open_source(source_path)) as conn:
        assert [row.source_id for row in read_rows(conn, "tben", limit=2)] == [1, 2]


def test_source_is_opened_read_only(source_path: Path) -> None:
    with closing(open_source(source_path)) as conn, pytest.raises(sqlite3.OperationalError):
        conn.execute("DELETE FROM tben")


def test_missing_source_is_not_created(tmp_path: Path) -> None:
    missing = tmp_path / "missing.ml"

    with pytest.raises(sqlite3.OperationalError):
        open_source(missing)
    assert not missing.exists()


def test_unknown_table_is_rejected(source_path: Path) -> None:
    with closing(open_source(source_path)) as conn, pytest.raises(ValueError):
        list(read_rows(conn, "sqlite_master"))

