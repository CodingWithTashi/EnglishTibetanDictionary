"""Dictionary packs: everything that differs between the databases the app ships.

A `Pack` carries one database's directions, source reader and per-table parsers, headword lookup
order, word-of-the-day direction, `meta` strings and the expected counts of a full build. The
generic stages (grouping, linking, ranking, writing) take what they need from it. `MONLAM` is the
Tibetan pack (`bo.db`, built from `MLDic.ml`); `PACKS` maps the `--pack` names to packs.
"""

from __future__ import annotations

from collections.abc import Callable
from contextlib import closing
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING

from dictbuild.model import DirectionRow, ParsedRow
from dictbuild.parsers.ddc_common import sort_case_duplicates
from dictbuild.parsers.dz_dz import parse_dz_dz_row
from dictbuild.parsers.dz_en import parse_dz_en_row
from dictbuild.parsers.en_dz import parse_en_dz_row
from dictbuild.parsers.entotb import parse_entotb_row
from dictbuild.parsers.tben import parse_tben_row
from dictbuild.parsers.tbtb import parse_tbtb_row
from dictbuild.source import MONLAM_TABLES, SourceRow, open_source, read_rows
from dictbuild.stardict import read_stardict
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


def _read_monlam_source(path: Path, limit: int | None) -> dict[str, list[SourceRow]]:
    """The rows of every Monlam table in `_id` order (the first `limit` rows of each when given)."""
    with closing(open_source(path)) as conn:
        return {table: list(read_rows(conn, table, limit=limit)) for table in MONLAM.tables}


def _parse_monlam_row(table: str, row: SourceRow, converter: WylieConverter, index: HeadwordIndex) -> ParsedRow:
    if table == "tben":
        return parse_tben_row(row, converter, index)
    if table == "tbtb":
        return parse_tbtb_row(row, converter)
    if table == "entotb":
        return parse_entotb_row(row)
    raise ValueError(f"unknown Monlam source table {table!r}")


MONLAM = Pack(
    id="bo",
    directions=(
        DirectionRow(
            id="bo-en",
            source_lang="bo",
            target_lang="en",
            source_script="tibt",
            label_en="Tibetan → English",
            label_bo="བོད་ཡིག → དབྱིན་ཡིག",
            short_label="བོད→EN",
            position=0,
            id_base=1_000_000,
        ),
        DirectionRow(
            id="bo-bo",
            source_lang="bo",
            target_lang="bo",
            source_script="tibt",
            label_en="Tibetan → Tibetan",
            label_bo="བོད་ཡིག → བོད་ཡིག",
            short_label="བོད→བོད",
            position=1,
            id_base=2_000_000,
        ),
        DirectionRow(
            id="en-bo",
            source_lang="en",
            target_lang="bo",
            source_script="latn",
            label_en="English → Tibetan",
            label_bo="དབྱིན་ཡིག → བོད་ཡིག",
            short_label="EN→བོད",
            position=2,
            id_base=3_000_000,
        ),
    ),
    tables=MONLAM_TABLES,
    direction_by_table={"tben": "bo-en", "tbtb": "bo-bo", "entotb": "en-bo"},
    # Rule "SA … resolved by longest Wylie-prefix match against bo-en then bo-bo headwords".
    tibetan_lookup_order=("bo-en", "bo-bo"),
    english_direction="en-bo",
    wotd_direction_id="bo-en",
    meta={
        "source_name": "Monlam Dictionary (MLDic.ml)",
        "source_author": "Lobsang Monlam",
        "source_url": "https://github.com/iamironrabbit/monlam-dictionary",
        "source_license": "GPL-3.0",
        "attribution_text": (
            "Dictionary data from the Monlam Tibetan Dictionary by Lobsang Monlam, as published in the "
            "open-source Monlam Dictionary app (https://github.com/iamironrabbit/monlam-dictionary) under the "
            "GNU General Public License, version 3 (GPL-3.0)."
        ),
    },
    # Expected counts after a full build (plan §5.3). The en-bo count follows the T1.2 ruling (one more
    # headword than the plan's 35,887 once "K2" keeps its digit). bo-en has one entry fewer than its
    # 76,828 source headwords: དཔལ་གྱི་ཁབ་ (row 23263, only "{Ship 64b.2}") has no sense and no link.
    # Synonyms are `= English term` links only (T1.3 follow-up ruling). The source has 38 `= Latin`
    # occurrences; 17 are headword-like terms (the rest are equations and expansions in example text),
    # so the range brackets 17 rather than the ruled 200–3,500. Monlam has no antonym links.
    expected=Expected(
        directions=3,
        entries={"bo-en": (76_827, 76_827), "bo-bo": (55_124, 55_124), "en-bo": (35_887, 35_888)},
        senses={"bo-en": (88_000, 88_938), "bo-bo": (65_000, 80_000), "en-bo": (55_000, 75_000)},
        examples={"bo-bo": (18_000, 19_000), "en-bo": (2_000, 5_000)},
        see_also=(4_000, 5_000),
        see_also_resolved_percent=(70.0, 100.0),
        synonyms=(10, 40),
        antonyms=(0, 0),
        wylie=131_951,  # every bo-en and bo-bo entry
        truncated=(480, 600),
        wotd=(25_000, 60_000),
    ),
    read_source=_read_monlam_source,
    parse=_parse_monlam_row,
)

_DDC_TABLES = ("dz-en", "dz-dz", "en-dz")


def _read_ddc_source(directory: Path, limit: int | None) -> dict[str, list[SourceRow]]:
    """The rows of the three StarDict files under `directory` (`stardict-<name>/<name>`), in `.idx` order.

    The en-dz rows are ordered so that, among spellings that differ only in case, the lower-case one
    comes first and so becomes the display headword of the merged entry.
    """
    rows = {name: read_stardict(directory / f"stardict-{name}" / name, limit=limit) for name in _DDC_TABLES}
    rows["en-dz"] = sort_case_duplicates(rows["en-dz"])
    return rows


def _parse_ddc_row(table: str, row: SourceRow, converter: WylieConverter, index: HeadwordIndex) -> ParsedRow:
    if table == "dz-en":
        return parse_dz_en_row(row, converter, index)
    if table == "dz-dz":
        return parse_dz_dz_row(row, converter)
    if table == "en-dz":
        return parse_en_dz_row(row)
    raise ValueError(f"unknown DDC source table {table!r}")


DDC = Pack(
    id="dz",
    directions=(
        DirectionRow(
            id="dz-en",
            source_lang="dz",
            target_lang="en",
            source_script="tibt",
            label_en="Dzongkha → English",
            label_bo="རྫོང་ཁ → དབྱིན་ཡིག",
            short_label="རྫོང→EN",
            position=0,
            id_base=4_000_000,
        ),
        DirectionRow(
            id="dz-dz",
            source_lang="dz",
            target_lang="dz",
            source_script="tibt",
            label_en="Dzongkha → Dzongkha",
            label_bo="རྫོང་ཁ → རྫོང་ཁ",
            short_label="རྫོང→རྫོང",
            position=1,
            id_base=5_000_000,
        ),
        DirectionRow(
            id="en-dz",
            source_lang="en",
            target_lang="dz",
            source_script="latn",
            label_en="English → Dzongkha",
            label_bo="དབྱིན་ཡིག → རྫོང་ཁ",
            short_label="EN→རྫོང",
            position=2,
            id_base=6_000_000,
        ),
    ),
    tables=_DDC_TABLES,
    direction_by_table={"dz-en": "dz-en", "dz-dz": "dz-dz", "en-dz": "en-dz"},
    tibetan_lookup_order=("dz-en", "dz-dz"),
    english_direction="en-dz",
    wotd_direction_id="dz-en",
    # OWNER WORDING: draft, to be confirmed
    meta={
        "source_name": "DDC Dzongkha Dictionaries (StarDict, 2014-02-03)",
        "source_author": "Dzongkha Development Commission",
        "source_url": "https://www.dzongkha.gov.bt/",
        "source_license": "Used with permission of the DCDD",
        "attribution_text": (
            "Dictionary data from the Dzongkha–English, English–Dzongkha and Dzongkha dictionaries of the "
            "Dzongkha Development Commission (2014 digital edition), used with the permission of the "
            "Dzongkha Development and Culture Division."
        ),
    },
    # Measured on the final full build (2026-10-22). Entry counts are exact (dz-en 35,543 and dz-dz 22,786 source
    # headwords; en-dz 23,145 after case merging). The other counts are the measured value
    # (senses 36,238 / 26,551 / 37,404; synonyms 923; antonyms 454; word-of-the-day 31,135) with a 5 %
    # window each way, rounded outward. The DDC files hold no see-also links, examples or truncated
    # glosses; synonym and antonym links come from the dz-en `<kref>` targets.
    expected=Expected(
        directions=3,
        entries={"dz-en": (35_543, 35_543), "dz-dz": (22_786, 22_786), "en-dz": (23_145, 23_145)},
        senses={"dz-en": (34_426, 38_050), "dz-dz": (25_223, 27_879), "en-dz": (35_533, 39_275)},
        examples={},
        see_also=(0, 0),
        see_also_resolved_percent=(0.0, 100.0),
        synonyms=(876, 970),
        antonyms=(431, 477),
        wylie=58_329,  # every dz-en and dz-dz entry
        truncated=(0, 0),
        wotd=(29_578, 32_693),
    ),
    read_source=_read_ddc_source,
    parse=_parse_ddc_row,
    drop_identical_senses=True,
)

PACKS: dict[str, Pack] = {MONLAM.id: MONLAM, DDC.id: DDC}
