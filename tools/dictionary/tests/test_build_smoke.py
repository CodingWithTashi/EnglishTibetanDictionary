"""End-to-end build (plan §5.3): `build_db.py --limit 300` against the real Monlam source."""

from __future__ import annotations

import json
import sqlite3
from contextlib import closing
from pathlib import Path

import pytest

import build_db
from dictbuild.model import DIRECTIONS, EntryRow, ParsedSense, RelatedEntryRow
from dictbuild.schema import latest_schema_path, load_schema
from dictbuild.writer import entry_part_of_speech, gloss_preview, write_database

SOURCE = Path("/Users/kunchoktashi/Downloads/monlam-dictionary-master/app/src/main/assets/MLDic.ml")


def test_gloss_preview_keeps_short_glosses() -> None:
    assert gloss_preview("pillar; column") == "pillar; column"
    assert gloss_preview("x" * 120) == "x" * 120


def test_gloss_preview_cuts_latin_at_a_word_boundary() -> None:
    text = "all-pervading, pervades everything, omnipresent, all-pervasive, universal, that which encompasses"

    preview = gloss_preview(text * 2)

    assert len(preview) <= 120
    assert preview.endswith("…")
    assert (text * 2).startswith(preview[:-1])
    assert preview[:-1] == preview[:-1].rstrip(" ,;")
    assert (text * 2)[len(preview) - 1] in " ,"


def test_gloss_preview_cuts_tibetan_at_a_tsheg() -> None:
    text = "བཀྲ་ཤིས་བདེ་ལེགས་" * 20

    preview = gloss_preview(text)

    assert len(preview) <= 120
    assert preview.endswith("་…")
    assert text.startswith(preview[:-1])


def test_entry_part_of_speech_lists_distinct_codes_in_order() -> None:
    senses = [
        ParsedSense(gloss="a", gloss_lang="bo", source_id=1, part_of_speech="adj,n"),
        ParsedSense(gloss="b", gloss_lang="bo", source_id=1, part_of_speech="n"),
        ParsedSense(gloss="c", gloss_lang="bo", source_id=1),
        ParsedSense(gloss="d", gloss_lang="bo", source_id=1, part_of_speech="vt"),
    ]

    assert entry_part_of_speech(senses) == "adj,n,vt"
    assert entry_part_of_speech(senses[2:3]) is None


def test_write_database_leaves_no_file_behind_on_failure(tmp_path: Path, schema_dir: Path) -> None:
    entry = EntryRow(
        id=1_000_001, direction_id="bo-en", source_id=1, headword="ཀ་", gloss_preview="", sense_count=0, sort_order=1
    )
    dangling = RelatedEntryRow(
        id=1, source_entry_id=entry.id, target_entry_id=42, target_headword="ཁ་", relation="see_also", position=0
    )
    out = tmp_path / "dictionary.db"

    with pytest.raises(sqlite3.IntegrityError):
        write_database(
            out,
            load_schema(latest_schema_path(schema_dir)),
            {"directions": DIRECTIONS, "entries": [entry], "related_entries": [dangling]},
        )

    assert list(tmp_path.iterdir()) == []


def test_missing_source_is_reported_with_its_path(tmp_path: Path, schema_dir: Path, capsys) -> None:
    missing = tmp_path / "nope" / "MLDic.ml"
    out = tmp_path / "d.db"

    code = build_db.main(
        ["--source", str(missing), "--schema-dir", str(schema_dir), "--out", str(out), "--data-version", "t"]
    )

    assert code != 0
    assert str(missing) in capsys.readouterr().err
    assert not out.exists()


def test_missing_schema_is_reported_with_its_path(tmp_path: Path, capsys) -> None:
    out = tmp_path / "d.db"

    code = build_db.main(
        ["--source", str(SOURCE), "--schema-dir", str(tmp_path), "--out", str(out), "--data-version", "t"]
    )

    assert code != 0
    assert str(tmp_path) in capsys.readouterr().err


@pytest.mark.slow
@pytest.mark.skipif(not SOURCE.is_file(), reason="Monlam source database not available")
def test_build_limit_300(tmp_path: Path, schema_dir: Path) -> None:
    out = tmp_path / "assets" / "dictionary.db"
    report = tmp_path / "report.json"

    code = build_db.main(
        [
            "--source", str(SOURCE),
            "--schema-dir", str(schema_dir),
            "--out", str(out),
            "--data-version", "2026.10.1-test",
            "--report", str(report),
            "--limit", "300",
        ]
    )  # fmt: skip

    assert code == 0
    assert sorted(path.name for path in out.parent.iterdir()) == ["dictionary.db"]  # no temp file left
    with closing(sqlite3.connect(out)) as db:
        counts = {
            table: db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
            for table in ("directions", "entries", "senses", "examples", "related_entries", "meta")
        }
        assert counts["directions"] == 3
        assert all(count > 0 for count in counts.values()), counts
        for direction in ("bo-en", "bo-bo", "en-bo"):
            assert db.execute("SELECT COUNT(*) FROM entries WHERE direction_id = ?", (direction,)).fetchone()[0] > 0
        assert db.execute("PRAGMA foreign_key_check").fetchall() == []
        tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}
        assert "room_master_table" not in tables
        assert db.execute("SELECT COUNT(*) FROM entries_fts").fetchone()[0] == counts["entries"]
        assert db.execute("SELECT COUNT(*) FROM senses_fts").fetchone()[0] == counts["senses"]
        assert db.execute("SELECT COUNT(*) FROM entries_fts WHERE entries_fts MATCH 'ཀ'").fetchone()[0] > 0
        assert db.execute("PRAGMA journal_mode").fetchone()[0] == "delete"
        assert db.execute("PRAGMA user_version").fetchone()[0] == load_schema(latest_schema_path(schema_dir)).version
        meta = dict(db.execute("SELECT key, value FROM meta"))
        assert meta["data_version"] == "2026.10.1-test"
        assert meta["wotd_direction_id"] == "bo-en"
        assert json.loads(meta["row_counts_json"])["entries"] == counts["entries"]
        directions = {
            row[0]: row[1:]
            for row in db.execute("SELECT id, entry_count, sense_count, wotd_count FROM directions")
        }
        assert directions["bo-en"][0] == db.execute(
            "SELECT COUNT(*) FROM entries WHERE direction_id = 'bo-en'"
        ).fetchone()[0]
        assert directions["bo-en"][2] == int(meta["wotd_count"]) > 0
        assert db.execute("SELECT COUNT(*) FROM senses WHERE gloss = ''").fetchone()[0] == 0

    data = json.loads(report.read_text(encoding="utf-8"))
    assert data["table_counts"] == counts
    assert data["limit"] == 300
