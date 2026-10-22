"""The Dzongkha pack (`dz.db`), built from the DDC StarDict files (dz-en, dz-dz, en-dz)."""

from __future__ import annotations

from pathlib import Path
from typing import TYPE_CHECKING

from dictbuild.model import DirectionRow, ParsedRow
from dictbuild.packs.base import Expected, Pack
from dictbuild.parsers.ddc_common import sort_case_duplicates
from dictbuild.parsers.dz_dz import parse_dz_dz_row
from dictbuild.parsers.dz_en import parse_dz_en_row
from dictbuild.parsers.en_dz import parse_en_dz_row
from dictbuild.source import SourceRow
from dictbuild.stardict import read_stardict
from dictbuild.wylie import WylieConverter

if TYPE_CHECKING:
    from dictbuild.related import HeadwordIndex


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
