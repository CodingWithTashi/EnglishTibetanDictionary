"""English → Dzongkha parser for the DDC `en-dz` StarDict (spec §7).

One record is one English headword with one `<k>` block per part of speech (`<k>round</k>
<pos>…noun…</pos> ༡ … ༦ …`); a block may carry several `<pos>` labels (`adv.` `adj.`), whose codes are joined by a comma. Each block's body is split into senses at the Tibetan numerals `༡ ༢ ༣`
(a block without them gives one sense); sense numbers continue across the blocks and each sense carries
its block's part of speech. Glosses are Dzongkha; a Latin echo of the block's own label at the start of
the body (`PHR V འབྱུང་བཅུག་ནི།`) is dropped.
"""

from __future__ import annotations

import re

from dictbuild.model import GLOSS_LANG_DZ, ParsedRow, ParsedSense
from dictbuild.parsers.common import split_numbered_tibetan
from dictbuild.parsers.ddc_common import clean_gloss, pos_code, split_k_blocks, strip_tags
from dictbuild.source import SourceRow
from dictbuild.text import normalise_english_headword, preprocess

__all__ = ["parse_en_dz_row"]

_POS_BLOCK = re.compile(r"<pos>(?P<label>.*?)</pos>", re.DOTALL)
_LABEL_TRAILER = ".:;”"


def parse_en_dz_row(row: SourceRow) -> ParsedRow:
    """Parse one `en-dz` record. Blocks without text (`<k>modernize</k> <k>modernise</k> …`) add nothing."""
    headword, homonym, group_key = normalise_english_headword(row.word)
    parsed = ParsedRow(source_id=row.source_id, headword=headword, group_key=group_key, homonym=homonym)
    for _, rest in split_k_blocks(row.definition):
        rest = preprocess(rest)
        labels = [strip_tags(found.group("label")) for found in _POS_BLOCK.finditer(rest)]
        codes: list[str] = []
        for label in labels:
            code, known = pos_code(label)
            if not known:
                parsed.unknown_labels.append(label)
            codes += [part for part in (code or "").split(",") if part and part not in codes]
        text = strip_tags(_POS_BLOCK.sub(" ", rest))
        for label in labels:
            text = _drop_label_echo(text, label)
        code = ",".join(codes) or None
        if not text:
            continue
        segments = split_numbered_tibetan(text)
        for piece in [segment.text for segment in segments] if segments else [text]:
            _add_sense(piece, code, parsed)
    if not parsed.senses:
        parsed.warnings.append("row_without_senses")
    return parsed


def _drop_label_echo(text: str, label: str) -> str:
    """Remove the block's own label, spelled in Latin, from the start of the body (`PHR V.` → `PHR V`)."""
    words = label.rstrip(_LABEL_TRAILER + " ").split()
    if not words:
        return text
    echo = re.compile(r"\s+".join(map(re.escape, words)) + r"[.:;]?(?![A-Za-z])", re.IGNORECASE)
    if found := echo.match(text):
        return text[found.end() :].strip()
    return text


def _add_sense(text: str, code: str | None, parsed: ParsedRow) -> None:
    gloss = clean_gloss(text)
    if not gloss:
        parsed.warnings.append("sense_without_gloss")
        return
    parsed.senses.append(
        ParsedSense(
            gloss=gloss,
            gloss_lang=GLOSS_LANG_DZ,
            source_id=parsed.source_id,
            sense_no=len(parsed.senses) + 1,
            part_of_speech=code,
        )
    )
