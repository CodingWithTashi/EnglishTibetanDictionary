"""Build a pack's prebuilt dictionary database (`bo.db` from the Monlam source, `dz.db` from the DDC files).

    .venv/bin/python -I build_db.py --pack bo --source MLDic.ml \\
        --schema-dir ../../app/schemas/com.kharagedition.tibetandictionary.data.local.DictionaryDatabase \\
        --out ../../app/src/main/assets/dictionary/bo.db --data-version 2026.10.1 \\
        --stats-xml ../../app/src/main/res/values/dictionary_stats_bo.xml --built-at 2026-10-19T00:00:00Z

`--pack dz` takes the directory holding the DDC StarDict folders as `--source` and writes `dz.db` and
`dictionary_stats_dz.xml` the same way.

Steps: load the highest `<v>.json` schema → read the three source tables → parse each row → group
rows into entries → resolve related links across directions → assign word-of-the-day ranks →
compute direction counts and meta → write the database to a temporary file → write the
`--stats-xml` resource (total entry count, asset size) → rename the database into place → report.
`meta.built_at` is `--built-at`, else `SOURCE_DATE_EPOCH`, else now: with a fixed time a rebuild of
unchanged data is byte-identical.
Exit status: 0 on success, 1 when an input cannot be read, `--built-at`/`SOURCE_DATE_EPOCH` is not a
time, or the database or `--stats-xml` cannot be written (both are then left as they were), 2 when a
full build's counts leave the expected ranges: `--out` and `--stats-xml` are then left as they were,
and the database is written to a temporary directory for inspection (the report is still written).
"""

from __future__ import annotations

import argparse
import os
import sqlite3
import sys
import tempfile
from collections.abc import Mapping, Sequence
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

# Isolated mode (-I) keeps this script's directory off sys.path; the dictbuild package lives next to it.
sys.path.insert(0, str(Path(__file__).resolve().parent))

import dictbuild  # noqa: E402
from dictbuild.grouping import group_rows  # noqa: E402
from dictbuild.model import EntryDraft, ParsedRow  # noqa: E402
from dictbuild.packs import PACKS, Pack  # noqa: E402
from dictbuild.related import HeadwordIndex, RelatedStats, resolve_related  # noqa: E402
from dictbuild.report import BuildReport  # noqa: E402
from dictbuild.resources import write_stats_xml, write_text_atomically  # noqa: E402
from dictbuild.schema import Schema, latest_schema_path, load_schema  # noqa: E402
from dictbuild.source import SourceRow  # noqa: E402
from dictbuild.stats import RangeCheck, check_ranges, direction_rows, table_counts  # noqa: E402
from dictbuild.wotd import DEFAULT_WOTD_SEED, assign_ranks  # noqa: E402
from dictbuild.writer import assemble_rows, meta_rows, staged_database  # noqa: E402
from dictbuild.wylie import WylieConverter, WylieUnavailableError, load_converter  # noqa: E402

EXIT_INPUT_ERROR = 1
EXIT_OUT_OF_RANGE = 2

# Reproducible-builds convention: a fixed build time in seconds since the epoch.
SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH"
BUILT_AT_FORMAT = "%Y-%m-%dT%H:%M:%SZ"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build a pack's prebuilt dictionary database from its source.")
    parser.add_argument("--version", action="version", version=f"dictbuild {dictbuild.__version__}")
    parser.add_argument("--pack", choices=sorted(PACKS), required=True, help="the dictionary pack to build")
    parser.add_argument(
        "--source",
        type=Path,
        required=True,
        help=(
            "source of the pack, read-only (bo: the Monlam MLDic.ml database; "
            "dz: the directory holding stardict-dz-en/, stardict-en-dz/ and stardict-dz-dz/)"
        ),
    )
    parser.add_argument("--schema-dir", type=Path, required=True, help="app/schemas/<DictionaryDatabase fqcn>/")
    parser.add_argument("--out", type=Path, required=True, help="database to write")
    parser.add_argument("--data-version", required=True, help="data release, e.g. 2026.10.1")
    parser.add_argument(
        "--wotd-seed", type=int, default=DEFAULT_WOTD_SEED, help=f"word-of-the-day shuffle seed ({DEFAULT_WOTD_SEED})"
    )
    parser.add_argument("--require-wylie", action="store_true", help="fail when pyewts is not installed")
    parser.add_argument("--report", type=Path, help="write a JSON build report here")
    parser.add_argument("--limit", type=int, help="parse only the first N rows of each source table")
    parser.add_argument(
        "--stats-xml",
        type=Path,
        help="also write the pack's res/values/dictionary_stats_<pack>.xml (total entry count, asset size) for the database",
    )
    parser.add_argument(
        "--built-at",
        help=f"meta.built_at as an ISO 8601 time, e.g. 2026-10-19T00:00:00Z (default: {SOURCE_DATE_EPOCH} if set, "
        "else now); fixing it makes a rebuild of unchanged data byte-identical",
    )
    return parser.parse_args(argv)


def resolve_built_at(value: str | None, environ: Mapping[str, str]) -> str:
    """`meta.built_at` in UTC to the second: `value` (ISO 8601; no offset means UTC), else the
    `SOURCE_DATE_EPOCH` seconds in `environ` (empty counts as unset), else the current time.

    Raises ValueError when the time given cannot be parsed.
    """
    if value is not None:
        # Python 3.10's fromisoformat does not accept the "Z" suffix (3.11+ does).
        iso = value[:-1] + "+00:00" if value.endswith(("Z", "z")) else value
        try:
            moment = datetime.fromisoformat(iso)
        except ValueError:
            raise ValueError(f"--built-at {value!r} is not an ISO 8601 time such as 2026-10-19T00:00:00Z") from None
        if moment.tzinfo is None:
            moment = moment.replace(tzinfo=timezone.utc)
    elif epoch := environ.get(SOURCE_DATE_EPOCH, "").strip():
        try:
            moment = datetime.fromtimestamp(int(epoch), timezone.utc)
        except (ValueError, OverflowError, OSError):
            raise ValueError(f"{SOURCE_DATE_EPOCH}={epoch!r} is not a number of seconds since 1970") from None
    else:
        moment = datetime.now(timezone.utc)
    return moment.astimezone(timezone.utc).strftime(BUILT_AT_FORMAT)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    pack = PACKS[args.pack]
    try:
        schema = load_schema(latest_schema_path(args.schema_dir))
    except (OSError, ValueError, KeyError) as error:
        return _fail(f"cannot load the Room schema from {args.schema_dir}: {error}")
    try:
        converter = load_converter(require=args.require_wylie)
    except WylieUnavailableError as error:
        return _fail(str(error))
    try:
        built_at = resolve_built_at(args.built_at, os.environ)
    except ValueError as error:
        return _fail(str(error))
    try:
        rows_by_table = pack.read_source(args.source, args.limit)
    except sqlite3.Error as error:
        return _fail(f"cannot read the source database {args.source}: {error}")

    report = BuildReport(
        inputs={
            "source": str(args.source),
            "schema_version": schema.version,
            "data_version": args.data_version,
            "built_at": built_at,
            "wylie_converter": converter.name,
            "wotd_seed": args.wotd_seed,
            "limit": args.limit,
        }
    )
    tables, checks = build(
        pack, rows_by_table, schema, converter, args.wotd_seed, args.data_version, built_at, report
    )
    if args.limit is not None:
        checks = []  # a partial build cannot meet the full-build counts
    else:
        report.add_range_checks(checks)
    failed = [check for check in checks if not check.ok]
    # An out-of-range build never replaces --out (which may be the shipped asset).
    out = Path(tempfile.mkdtemp(prefix="dictionary-out-of-range-")) / args.out.name if failed else args.out
    total_entries = sum(direction.entry_count for direction in tables["directions"])
    stats_xml = args.stats_xml if not failed else None
    stats = _StatsXml(stats_xml)
    try:
        # The stats file is written while the database is staged, before the rename, so the pair agrees:
        # a failed stats write leaves `out` as it was, and a failed rename puts the old stats file back.
        with staged_database(out, schema, tables) as staged:
            asset_bytes = staged.stat().st_size
            stats.write(total_entries, asset_bytes, pack.id)
    except _StatsXmlError as error:
        return _fail(str(error))
    except (OSError, sqlite3.Error) as error:
        stats.restore()
        return _fail(f"cannot write {out}: {error}")
    if args.report is not None:
        report.write(args.report)

    entries = ", ".join(f"{direction.id} {direction.entry_count}" for direction in tables["directions"])
    print(
        f"build_db: pack {pack.id}: wrote {out} ({asset_bytes} bytes; entries: {entries}; schema v{schema.version}; "
        f"{converter.name}; built_at {built_at})"
    )
    for check in failed:
        print(f"build_db: {check.name} = {check.value} is outside {check.minimum}–{check.maximum}", file=sys.stderr)
    if stats_xml is not None:
        print(
            f"build_db: wrote {stats_xml} (dictionary_entry_count_{pack.id} {total_entries}, "
            f"dictionary_asset_bytes_{pack.id} {asset_bytes})"
        )
    if failed:
        print(f"build_db: {args.out} was not replaced", file=sys.stderr)
    return EXIT_OUT_OF_RANGE if failed else 0


class _StatsXmlError(Exception):
    """The `--stats-xml` file could not be written; the database was not renamed into place."""


class _StatsXml:
    """Writes the `--stats-xml` file (when `path` is given) and can put its previous content back."""

    def __init__(self, path: Path | None) -> None:
        self.path = path
        self._previous: str | None = None
        self._written = False

    def write(self, total_entries: int, asset_bytes: int, pack_id: str) -> None:
        if self.path is None:
            return
        try:
            self._previous = self.path.read_text(encoding="utf-8") if self.path.is_file() else None
            write_stats_xml(self.path, total_entries, asset_bytes, pack_id)
        except (OSError, ValueError) as error:
            raise _StatsXmlError(f"cannot write {self.path}: {error}") from error
        self._written = True

    def restore(self) -> None:
        """Undo `write` after the database could not be renamed into place (best effort)."""
        if self.path is None or not self._written:
            return
        try:
            if self._previous is None:
                self.path.unlink(missing_ok=True)
            else:
                write_text_atomically(self.path, self._previous)
        except OSError as error:
            print(f"build_db: cannot restore {self.path}: {error}", file=sys.stderr)


def build(
    pack: Pack,
    rows_by_table: Mapping[str, Sequence[SourceRow]],
    schema: Schema,
    converter: WylieConverter,
    wotd_seed: int,
    data_version: str,
    built_at: str,
    report: BuildReport,
) -> tuple[dict[str, list[Any]], list[RangeCheck]]:
    """Parse, group, link and rank the source rows; return every table's rows and the range checks."""
    index = HeadwordIndex.from_source_rows(pack, rows_by_table)
    parsed: dict[str, list[ParsedRow]] = {
        table: [pack.parse(table, row, converter, index) for row in rows_by_table[table]] for table in pack.tables
    }
    drafts: dict[str, list[EntryDraft]] = {}
    for table, rows in parsed.items():
        report.add_parsed(table, rows)
        direction_id = pack.direction_by_table[table]
        drafts[direction_id] = group_rows(pack, direction_id, rows)
    ordered = [draft for direction in pack.directions for draft in drafts[direction.id]]
    stats = resolve_related(ordered, index, converter)
    ordered = _drop_empty_entries(ordered, stats, report)
    report.add_related(stats)
    ranks = assign_ranks(
        (draft for draft in ordered if draft.direction_id == pack.wotd_direction_id),
        wotd_seed,
        converter,
        index,
        wotd_direction_id=pack.wotd_direction_id,
    )

    content = assemble_rows(ordered, ranks)
    directions = direction_rows(pack, content)
    counts = table_counts(directions, content)
    meta = meta_rows(
        {
            "schema_version": schema.version,
            "data_version": data_version,
            "built_at": built_at,
            "source_name": pack.meta["source_name"],
            "source_author": pack.meta["source_author"],
            "source_url": pack.meta["source_url"],
            "source_license": pack.meta["source_license"],
            "attribution_text": pack.meta["attribution_text"],
            "wylie_converter": converter.name,
            "wotd_seed": wotd_seed,
            "wotd_count": len(ranks),
            "wotd_direction_id": pack.wotd_direction_id,
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
    return tables, check_ranges(pack, directions, content, wylie_available=converter.available)


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
