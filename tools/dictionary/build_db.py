"""Build the app's prebuilt dictionary.db from the Monlam source database (plan §5.3).

    .venv/bin/python -I build_db.py --source MLDic.ml \\
        --schema-dir ../../app/schemas/com.kharagedition.tibetandictionary.data.local.DictionaryDatabase \\
        --out ../../app/src/main/assets/dictionary/dictionary.db --data-version 2026.10.1

Steps: load the highest `<v>.json` schema → read the three source tables → parse each row → group
rows into entries → resolve related links across directions → assign word-of-the-day ranks →
compute direction counts and meta → write the database (temp file, atomic rename) → report.
Exit status: 0 on success, 1 when an input cannot be read or the database cannot be written,
2 when a full build's counts leave the expected ranges: `--out` is then left as it was, and the
database is written to a temporary directory for inspection (the report is still written).
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
import tempfile
from collections.abc import Mapping, Sequence
from contextlib import closing
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

# Isolated mode (-I) keeps this script's directory off sys.path; the dictbuild package lives next to it.
sys.path.insert(0, str(Path(__file__).resolve().parent))

import dictbuild  # noqa: E402
from dictbuild.grouping import group_rows  # noqa: E402
from dictbuild.model import DIRECTIONS, EntryDraft, ParsedRow  # noqa: E402
from dictbuild.parsers.entotb import parse_entotb_row  # noqa: E402
from dictbuild.parsers.tben import parse_tben_row  # noqa: E402
from dictbuild.parsers.tbtb import parse_tbtb_row  # noqa: E402
from dictbuild.related import HeadwordIndex, RelatedStats, resolve_related  # noqa: E402
from dictbuild.report import BuildReport  # noqa: E402
from dictbuild.schema import Schema, latest_schema_path, load_schema  # noqa: E402
from dictbuild.source import DIRECTION_BY_TABLE, SourceRow, open_source, read_rows  # noqa: E402
from dictbuild.stats import RangeCheck, check_ranges, direction_rows, table_counts  # noqa: E402
from dictbuild.wotd import DEFAULT_WOTD_SEED, WOTD_DIRECTION_ID, assign_ranks  # noqa: E402
from dictbuild.writer import assemble_rows, meta_rows, write_database  # noqa: E402
from dictbuild.wylie import WylieConverter, WylieUnavailableError, load_converter  # noqa: E402

SOURCE_NAME = "Monlam Dictionary (MLDic.ml)"
SOURCE_AUTHOR = "Lobsang Monlam"
SOURCE_URL = "https://github.com/iamironrabbit/monlam-dictionary"
SOURCE_LICENSE = "GPL-3.0"
ATTRIBUTION_TEXT = (
    "Dictionary data from the Monlam Tibetan Dictionary by Lobsang Monlam, as published in the "
    "open-source Monlam Dictionary app (https://github.com/iamironrabbit/monlam-dictionary) under the "
    "GNU General Public License, version 3 (GPL-3.0)."
)

EXIT_INPUT_ERROR = 1
EXIT_OUT_OF_RANGE = 2


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build dictionary.db from the Monlam source database.")
    parser.add_argument("--version", action="version", version=f"dictbuild {dictbuild.__version__}")
    parser.add_argument("--source", type=Path, required=True, help="Monlam source database (MLDic.ml), read-only")
    parser.add_argument("--schema-dir", type=Path, required=True, help="app/schemas/<DictionaryDatabase fqcn>/")
    parser.add_argument("--out", type=Path, required=True, help="dictionary.db to write")
    parser.add_argument("--data-version", required=True, help="data release, e.g. 2026.10.1")
    parser.add_argument(
        "--wotd-seed", type=int, default=DEFAULT_WOTD_SEED, help=f"word-of-the-day shuffle seed ({DEFAULT_WOTD_SEED})"
    )
    parser.add_argument("--require-wylie", action="store_true", help="fail when pyewts is not installed")
    parser.add_argument("--report", type=Path, help="write a JSON build report here")
    parser.add_argument("--limit", type=int, help="parse only the first N rows of each source table")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        schema = load_schema(latest_schema_path(args.schema_dir))
    except (OSError, ValueError, KeyError) as error:
        return _fail(f"cannot load the Room schema from {args.schema_dir}: {error}")
    try:
        converter = load_converter(require=args.require_wylie)
    except WylieUnavailableError as error:
        return _fail(str(error))
    try:
        rows_by_table = read_source(args.source, args.limit)
    except sqlite3.Error as error:
        return _fail(f"cannot read the source database {args.source}: {error}")

    report = BuildReport(
        inputs={
            "source": str(args.source),
            "schema_version": schema.version,
            "data_version": args.data_version,
            "wylie_converter": converter.name,
            "wotd_seed": args.wotd_seed,
            "limit": args.limit,
        }
    )
    tables, checks = build(rows_by_table, schema, converter, args.wotd_seed, args.data_version, report)
    if args.limit is not None:
        checks = []  # a partial build cannot meet the full-build counts
    else:
        report.add_range_checks(checks)
    failed = [check for check in checks if not check.ok]
    # An out-of-range build never replaces --out (which may be the shipped asset).
    out = Path(tempfile.mkdtemp(prefix="dictionary-out-of-range-")) / args.out.name if failed else args.out
    try:
        write_database(out, schema, tables)
    except (OSError, sqlite3.Error) as error:
        return _fail(f"cannot write {out}: {error}")
    if args.report is not None:
        report.write(args.report)

    entries = ", ".join(f"{direction.id} {direction.entry_count}" for direction in tables["directions"])
    print(f"build_db: wrote {out} (entries: {entries}; schema v{schema.version}; {converter.name})")
    for check in failed:
        print(f"build_db: {check.name} = {check.value} is outside {check.minimum}–{check.maximum}", file=sys.stderr)
    if failed:
        print(f"build_db: {args.out} was not replaced", file=sys.stderr)
    return EXIT_OUT_OF_RANGE if failed else 0


def read_source(path: Path, limit: int | None) -> dict[str, list[SourceRow]]:
    """The rows of every source table in `_id` order (the first `limit` rows of each when given)."""
    with closing(open_source(path)) as conn:
        return {table: list(read_rows(conn, table, limit=limit)) for table in DIRECTION_BY_TABLE}


def build(
    rows_by_table: Mapping[str, Sequence[SourceRow]],
    schema: Schema,
    converter: WylieConverter,
    wotd_seed: int,
    data_version: str,
    report: BuildReport,
) -> tuple[dict[str, list[Any]], list[RangeCheck]]:
    """Parse, group, link and rank the source rows; return every table's rows and the range checks."""
    index = HeadwordIndex.from_source_rows(rows_by_table)
    parsed: dict[str, list[ParsedRow]] = {
        "tben": [parse_tben_row(row, converter, index) for row in rows_by_table["tben"]],
        "tbtb": [parse_tbtb_row(row, converter) for row in rows_by_table["tbtb"]],
        "entotb": [parse_entotb_row(row) for row in rows_by_table["entotb"]],
    }
    drafts: dict[str, list[EntryDraft]] = {}
    for table, rows in parsed.items():
        report.add_parsed(table, rows)
        drafts[DIRECTION_BY_TABLE[table]] = group_rows(DIRECTION_BY_TABLE[table], rows)
    ordered = [draft for direction in DIRECTIONS for draft in drafts[direction.id]]
    stats = resolve_related(ordered, index, converter)
    ordered = _drop_empty_entries(ordered, stats, report)
    report.add_related(stats)
    ranks = assign_ranks(
        (draft for draft in ordered if draft.direction_id == WOTD_DIRECTION_ID), wotd_seed, converter, index
    )

    content = assemble_rows(ordered, ranks)
    directions = direction_rows(content)
    counts = table_counts(directions, content)
    meta = meta_rows(
        {
            "schema_version": schema.version,
            "data_version": data_version,
            "built_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "source_name": SOURCE_NAME,
            "source_author": SOURCE_AUTHOR,
            "source_url": SOURCE_URL,
            "source_license": SOURCE_LICENSE,
            "attribution_text": ATTRIBUTION_TEXT,
            "wylie_converter": converter.name,
            "wotd_seed": wotd_seed,
            "wotd_count": len(ranks),
            "wotd_direction_id": WOTD_DIRECTION_ID,
            "build_tool_version": dictbuild.__version__,
        },
        counts,
    )
    report.add_counts({**counts, "meta": len(meta)}, directions)
    tables: dict[str, list[Any]] = {
        "directions": directions,
        "entries": content.entries,
        "senses": content.senses,
        "examples": content.examples,
        "related_entries": content.related_entries,
        "meta": meta,
    }
    return tables, check_ranges(directions, content, wylie_available=converter.available)


def _drop_empty_entries(drafts: list[EntryDraft], stats: RelatedStats, report: BuildReport) -> list[EntryDraft]:
    """Leave out entries with no sense and no link (nothing to show, an empty preview), with a warning.

    A link that resolved to a dropped entry becomes unresolved again (the app then searches for it).
    """
    dropped = {draft.entry_id: draft for draft in drafts if not draft.senses and not draft.related}
    if not dropped:
        return drafts
    kept = [draft for draft in drafts if draft.entry_id not in dropped]
    for draft in kept:
        for candidate in draft.related:
            if candidate.target_entry_id in dropped:
                candidate.target_entry_id = None
                resolved, total = stats.by_relation[candidate.relation]
                stats.by_relation[candidate.relation] = (resolved - 1, total)
    report.add_warning("build:empty_entry_dropped", [draft.source_id for draft in dropped.values()])
    return kept


def _fail(message: str) -> int:
    print(f"build_db: {message}", file=sys.stderr)
    return EXIT_INPUT_ERROR


if __name__ == "__main__":
    sys.exit(main())
