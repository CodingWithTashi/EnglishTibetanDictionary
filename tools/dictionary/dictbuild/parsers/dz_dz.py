"""Dzongkha → Dzongkha parser for the DDC `dz-dz` StarDict (spec §7).

One record is one headword with one or more `<k>` blocks (a second block is a further homonym or
sense group). The text after each `</k>` is the running Dzongkha definition; Tibetan numerals
`༡ ༢ ༣` split it into senses (a block without them gives one sense) and the sense numbers continue
across the blocks. There are no part-of-speech labels or links in this file.
"""

from __future__ import annotations

from dictbuild.model import GLOSS_LANG_DZ, ParsedRow, ParsedSense
from dictbuild.parsers.common import split_numbered_tibetan, tidy
from dictbuild.parsers.ddc_common import split_k_blocks, strip_tags
from dictbuild.source import SourceRow
from dictbuild.text import normalise_tibetan_headword
from dictbuild.wylie import WylieConverter

__all__ = ["parse_dz_dz_row"]


def parse_dz_dz_row(row: SourceRow, converter: WylieConverter) -> ParsedRow:
    """Parse one `dz-dz` record. The first `<k>` block's text is the key (the row's own key when it has none)."""
    blocks = split_k_blocks(row.definition)
    headword, homonym = normalise_tibetan_headword(blocks[0][0] if blocks else row.word)
    parsed = ParsedRow(
        source_id=row.source_id,
        headword=headword,
        group_key=headword,
        homonym=homonym,
        wylie=converter.to_wylie(headword) if headword else None,
    )
    for _, rest in blocks or [("", row.definition)]:
        text = strip_tags(rest)  # a tag becomes a space: `</k>༡` is glued in the source
        if not text:
            continue
        segments = split_numbered_tibetan(text)
        for piece in [segment.text for segment in segments] if segments else [text]:
            _add_sense(piece, parsed)
    if not parsed.senses:
        parsed.warnings.append("row_without_senses")
    return parsed


def _add_sense(text: str, parsed: ParsedRow) -> None:
    gloss = tidy(text)
    if not gloss:
        parsed.warnings.append("sense_without_gloss")
        return
    parsed.senses.append(
        ParsedSense(
            gloss=gloss,
            gloss_lang=GLOSS_LANG_DZ,
            source_id=parsed.source_id,
            sense_no=len(parsed.senses) + 1,
        )
    )
