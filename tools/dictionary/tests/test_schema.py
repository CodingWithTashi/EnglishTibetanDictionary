"""The asset DDL is Room's exported schema, executed verbatim (plan §5.1, §5.3)."""

from __future__ import annotations

import dataclasses
import json
import re
import sqlite3
from collections.abc import Iterator
from contextlib import closing
from pathlib import Path

import pytest

from dictbuild.model import ROW_TYPES, DirectionRow, EntryRow, SenseRow, row_values
from dictbuild.schema import Schema, create_database, latest_schema_path, load_schema


@pytest.fixture(scope="module")
def schema_path(schema_dir: Path) -> Path:
    return latest_schema_path(schema_dir)


@pytest.fixture(scope="module")
def raw_database(schema_path: Path) -> dict:
    return json.loads(schema_path.read_text(encoding="utf-8"))["database"]


@pytest.fixture(scope="module")
def schema(schema_path: Path) -> Schema:
    return load_schema(schema_path)


@pytest.fixture
def db(schema: Schema) -> Iterator[sqlite3.Connection]:
    with closing(sqlite3.connect(":memory:")) as conn:
        create_database(conn, schema)
        yield conn


def test_latest_schema_path_picks_highest_numeric_version(tmp_path: Path) -> None:
    for name in ("1.json", "2.json", "10.json", "notes.txt", "draft.json"):
        (tmp_path / name).write_text("{}", encoding="utf-8")

    assert latest_schema_path(tmp_path) == tmp_path / "10.json"


def test_latest_schema_path_without_schemas_fails(tmp_path: Path) -> None:
    with pytest.raises(FileNotFoundError):
        latest_schema_path(tmp_path)


def test_schema_roundtrip(db: sqlite3.Connection, raw_database: dict) -> None:
    for entity in raw_database["entities"]:
        name = entity["tableName"]
        info = db.execute(f"PRAGMA table_info(`{name}`)").fetchall()
        assert [row[1] for row in info] == [field["columnName"] for field in entity["fields"]], name

        if "ftsVersion" in entity:
            sql = db.execute("SELECT sql FROM sqlite_master WHERE name = ?", (name,)).fetchone()[0]
            assert "tokenchars" in sql
            tokenchars = re.search(r"`tokenchars=[^`]+`", entity["createSql"]).group(0)
            assert f"tokenize=unicode61 {tokenchars}" in sql
            continue

        primary_key = entity["primaryKey"]["columnNames"]
        for (_, column, col_type, not_null, default, pk_index), field in zip(info, entity["fields"]):
            assert col_type == field["affinity"], (name, column)
            assert bool(not_null) == field.get("notNull", False), (name, column)
            assert default == field.get("defaultValue"), (name, column)
            assert (pk_index > 0) == (column in primary_key), (name, column)

        created_indices = {row[1] for row in db.execute(f"PRAGMA index_list(`{name}`)") if row[3] == "c"}
        assert created_indices == {index["name"] for index in entity.get("indices", [])}, name

    expected_triggers = {
        re.search(r"CREATE TRIGGER IF NOT EXISTS (\S+)", trigger).group(1)
        for entity in raw_database["entities"]
        for trigger in entity.get("contentSyncTriggers", [])
    }
    triggers = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type = 'trigger'")}
    assert expected_triggers and triggers == expected_triggers

    # Room's setupQueries are not executed: the asset carries no room_master_table.
    tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}
    assert "room_master_table" not in tables


def test_fts_tables_keep_no_docsize_table(db: sqlite3.Connection, schema: Schema) -> None:
    # `matchinfo=fts3` (Room's `MatchInfo.FTS3`): nothing ranks by matchinfo(), so FTS4 keeps no `%_docsize` table.
    fts_tables = [table for table in schema.tables if table.is_fts]
    assert [table.name for table in fts_tables] == ["entries_fts", "senses_fts"]
    tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}
    for table in fts_tables:
        assert table.create_sql.endswith(", matchinfo=fts3)"), table.name
        assert f"{table.name}_segments" in tables  # shadow tables exist, so the check below is meaningful
        assert f"{table.name}_docsize" not in tables


def test_insert_sql_lists_schema_columns_in_order(schema: Schema) -> None:
    assert schema.table("meta").insert_sql() == "INSERT INTO `meta` (`key`, `value`) VALUES (?, ?)"
    entries = schema.table("entries")
    assert entries.insert_sql().count("?") == len(entries.columns)


def test_model_rows_match_schema_columns(schema: Schema) -> None:
    content_tables = {table.name for table in schema.tables if not table.is_fts}
    assert set(ROW_TYPES) == content_tables
    for name, row_type in ROW_TYPES.items():
        assert tuple(field.name for field in dataclasses.fields(row_type)) == schema.table(name).columns, name


def test_fts_tokenizer_keeps_tibetan_syllables(db: sqlite3.Connection, schema: Schema) -> None:
    direction = DirectionRow(
        id="bo-en",
        source_lang="bo",
        target_lang="en",
        source_script="tibt",
        label_en="Tibetan → English",
        label_bo="བོད་དབྱིན།",
        short_label="བོད→EN",
        position=0,
        id_base=1_000_000,
    )
    entry = EntryRow(
        id=1_000_001,
        direction_id="bo-en",
        source_id=1,
        headword="བཀྲ་ཤིས་",
        wylie="bkra shis",
        gloss_preview="good fortune",
        sense_count=1,
        is_truncated=False,
        sort_order=1,
    )
    sense = SenseRow(
        id=1,
        entry_id=entry.id,
        sense_no=1,
        gloss="བཀྲ་ཤིས་བདེ་ལེགས།",
        gloss_lang="bo",
        is_truncated=False,
        source_id=1,
    )
    for table, row in (("directions", direction), ("entries", entry), ("senses", sense)):
        db.execute(schema.table(table).insert_sql(), row_values(row))
    db.execute("INSERT INTO entries_fts(entries_fts) VALUES('rebuild')")
    db.execute("INSERT INTO senses_fts(senses_fts) VALUES('rebuild')")

    def entries_matching(query: str) -> list[int]:
        return [row[0] for row in db.execute("SELECT docid FROM entries_fts WHERE entries_fts MATCH ?", (query,))]

    def senses_matching(query: str) -> list[int]:
        return [row[0] for row in db.execute("SELECT docid FROM senses_fts WHERE senses_fts MATCH ?", (query,))]

    assert entries_matching("ཤིས") == [entry.id]
    assert entries_matching("ཤ") == []  # a bare root letter is not the syllable ཤིས
    assert entries_matching("བཀ") == []  # the subjoined ྲ stays inside the token བཀྲ
    assert entries_matching("ཤི*") == [entry.id]
    assert entries_matching("shis") == [entry.id]
    assert senses_matching("ཤིས") == [sense.id]
    assert senses_matching("ཤ") == []


def test_user_version_set(schema: Schema, raw_database: dict, tmp_path: Path) -> None:
    path = tmp_path / "dictionary.db"
    with closing(sqlite3.connect(path)) as conn:
        create_database(conn, schema)
        conn.commit()
        assert conn.execute("PRAGMA user_version").fetchone()[0] == raw_database["version"]

    assert schema.version == raw_database["version"]
    # The app's installer reads the version from header bytes 60-63 (big-endian).
    assert int.from_bytes(path.read_bytes()[60:64], "big") == raw_database["version"]
