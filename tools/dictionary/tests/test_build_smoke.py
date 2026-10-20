"""End-to-end build (plan §5.3): `build_db.py` against a tiny fake source and, with `MLDIC_SOURCE`
set to the real Monlam `MLDic.ml`, `--limit 300` against the real one."""

from __future__ import annotations

import json
import os
import sqlite3
import tempfile
import xml.etree.ElementTree as ElementTree
from contextlib import closing
from pathlib import Path

import pytest

import build_db
from dictbuild.model import DIRECTIONS, EntryDraft, EntryRow, ParsedExample, ParsedSense, RelatedCandidate, RelatedEntryRow
from dictbuild.schema import latest_schema_path, load_schema
from dictbuild.writer import entry_part_of_speech, entry_preview, gloss_preview, write_database

SOURCE_ENV = "MLDIC_SOURCE"
SOURCE = Path(os.environ[SOURCE_ENV]) if os.environ.get(SOURCE_ENV) else None


def fake_source(path: Path, rows: dict[str, list[tuple[int, str, str]]]) -> Path:
    """A source database with the three Monlam tables holding `rows` (table → (_id, word, definition))."""
    with closing(sqlite3.connect(path)) as db:
        for table in ("entotb", "tben", "tbtb"):
            db.execute(f"CREATE TABLE {table} (_id INTEGER PRIMARY KEY, word TEXT, definition TEXT)")
            db.executemany(f"INSERT INTO {table} VALUES (?, ?, ?)", rows.get(table, []))
        db.commit()
    return path


TINY_ROWS = {
    "tben": [(1, "ཀ་", "pillar"), (2, "ཀ་བ་", "SA ka"), (3, "དཔལ་གྱི་ཁབ་", "{Ship 64b.2}")],
    "tbtb": [(1, "ཀ་", "ཀ་བ།")],
    "entotb": [(1, "pillar", "n. ཀ་བ།")],
}


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


def test_entry_preview_skips_senses_without_a_gloss() -> None:
    def draft(*senses: ParsedSense, related: list[RelatedCandidate] | None = None) -> EntryDraft:
        return EntryDraft(
            direction_id="bo-bo", source_id=1, headword="ནུས་", group_key="ནུས་", senses=list(senses), related=related or []
        )

    examples_only = ParsedSense(gloss="", gloss_lang="bo", source_id=1, examples=[ParsedExample("ནུ་མ་མ་ནུས།")])
    defined = ParsedSense(gloss="ནུས་པ།", gloss_lang="bo", source_id=1)

    assert entry_preview(draft(examples_only, defined)) == "ནུས་པ།"
    assert entry_preview(draft(examples_only)) == "ནུ་མ་མ་ནུས།"  # no gloss at all: the first example
    assert entry_preview(draft(related=[RelatedCandidate("ཀ་བ་", "see_also")])) == "→ ཀ་བ་"
    assert entry_preview(draft()) == ""


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
    source = fake_source(tmp_path / "MLDic.ml", TINY_ROWS)

    code = build_db.main(
        ["--source", str(source), "--schema-dir", str(tmp_path), "--out", str(out), "--data-version", "t"]
    )

    assert code != 0
    assert str(tmp_path) in capsys.readouterr().err


def test_out_of_range_build_exits_2_and_leaves_out_untouched(
    tmp_path: Path, schema_dir: Path, capsys, monkeypatch
) -> None:
    monkeypatch.setattr(tempfile, "tempdir", str(tmp_path))  # the out-of-range database lands in tmp_path
    source = fake_source(tmp_path / "MLDic.ml", TINY_ROWS)
    out = tmp_path / "assets" / "dictionary.db"
    out.parent.mkdir()
    out.write_bytes(b"shipped asset")
    report = tmp_path / "report.json"

    code = build_db.main(
        [
            "--source", str(source),
            "--schema-dir", str(schema_dir),
            "--out", str(out),
            "--data-version", "t",
            "--report", str(report),
        ]
    )  # fmt: skip

    assert code == build_db.EXIT_OUT_OF_RANGE == 2
    assert out.read_bytes() == b"shipped asset"
    assert sorted(path.name for path in out.parent.iterdir()) == ["dictionary.db"]
    [inspection] = tmp_path.glob("dictionary-out-of-range-*")
    assert sorted(path.name for path in inspection.iterdir()) == ["dictionary.db"]
    assert f"{out} was not replaced" in capsys.readouterr().err
    checks = json.loads(report.read_text(encoding="utf-8"))["range_checks"]
    assert {check["name"] for check in checks if not check["ok"]} >= {"entries bo-en", "senses bo-bo"}


def stats_entry_count(path: Path) -> int:
    """The `dictionary_entry_count` integer resource of a generated `dictionary_stats.xml`."""
    resources = ElementTree.parse(path).getroot()
    assert resources.tag == "resources"
    [integer] = resources
    assert (integer.tag, integer.attrib) == ("integer", {"name": "dictionary_entry_count"})
    return int(integer.text or "")


def test_stats_xml_holds_the_total_entry_count(tmp_path: Path, schema_dir: Path) -> None:
    source = fake_source(tmp_path / "MLDic.ml", TINY_ROWS)
    out = tmp_path / "dictionary.db"
    stats = tmp_path / "res" / "values" / "dictionary_stats.xml"
    stats.parent.mkdir(parents=True)
    stats.write_text("<resources><integer name=\"dictionary_entry_count\">0</integer></resources>")

    code = build_db.main(
        [
            "--source", str(source),
            "--schema-dir", str(schema_dir),
            "--out", str(out),
            "--data-version", "t",
            "--stats-xml", str(stats),
            "--limit", "10",
        ]
    )  # fmt: skip

    assert code == 0
    with closing(sqlite3.connect(out)) as db:
        total = db.execute("SELECT COUNT(*) FROM entries").fetchone()[0]
        assert db.execute("SELECT SUM(entry_count) FROM directions").fetchone()[0] == total == 4
    assert stats_entry_count(stats) == total
    assert sorted(path.name for path in stats.parent.iterdir()) == ["dictionary_stats.xml"]  # no temp file left


def test_out_of_range_build_leaves_the_stats_xml_untouched(
    tmp_path: Path, schema_dir: Path, monkeypatch
) -> None:
    monkeypatch.setattr(tempfile, "tempdir", str(tmp_path))
    source = fake_source(tmp_path / "MLDic.ml", TINY_ROWS)
    stats = tmp_path / "dictionary_stats.xml"
    stats.write_text("shipped stats")

    code = build_db.main(
        [
            "--source", str(source),
            "--schema-dir", str(schema_dir),
            "--out", str(tmp_path / "dictionary.db"),
            "--data-version", "t",
            "--stats-xml", str(stats),
        ]
    )  # fmt: skip

    assert code == build_db.EXIT_OUT_OF_RANGE
    assert stats.read_text() == "shipped stats"


def test_entry_without_senses_or_links_is_not_written(tmp_path: Path, schema_dir: Path) -> None:
    source = fake_source(tmp_path / "MLDic.ml", TINY_ROWS)
    out = tmp_path / "dictionary.db"
    report = tmp_path / "report.json"

    code = build_db.main(
        [
            "--source", str(source),
            "--schema-dir", str(schema_dir),
            "--out", str(out),
            "--data-version", "t",
            "--report", str(report),
            "--limit", "10",
        ]
    )  # fmt: skip

    assert code == 0
    with closing(sqlite3.connect(out)) as db:
        headwords = [row[0] for row in db.execute("SELECT headword FROM entries WHERE direction_id = 'bo-en'")]
        assert headwords == ["ཀ་", "ཀ་བ་"]  # དཔལ་གྱི་ཁབ་ ("{Ship 64b.2}") has nothing to show
        assert db.execute("SELECT COUNT(*) FROM entries WHERE gloss_preview = ''").fetchone()[0] == 0
    warnings = json.loads(report.read_text(encoding="utf-8"))["warnings"]
    assert warnings["build:empty_entry_dropped"] == {"count": 1, "sample_source_ids": [3]}


@pytest.mark.slow
@pytest.mark.skipif(
    SOURCE is None or not SOURCE.is_file(),
    reason=f"set {SOURCE_ENV} to the Monlam MLDic.ml to run the end-to-end build on real data",
)
def test_build_limit_300(tmp_path: Path, schema_dir: Path) -> None:
    assert SOURCE is not None
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
