"""Tibetan → Tibetan parser for the `tbtb` table (plan §5.3 "tbtb" grammar).

A definition may open with whitelisted `(label)` parentheticals that apply to the whole entry; it is
split into numbered senses, each of which may carry its own labels and `1>` sub-senses. Text after
`(དཔེར་ན།)` ("for example") becomes an example of the sense it appears in.
"""

from __future__ import annotations

import re

from dictbuild.model import GLOSS_LANG_BO, ParsedExample, ParsedRow, ParsedSense
from dictbuild.parsers.common import split_numbered, split_subsenses, tidy
from dictbuild.parsers.labels_bo import LABEL, POS, BoLabel, lookup_labels
from dictbuild.source import SourceRow
from dictbuild.text import clean_definition, normalise_tibetan_headword
from dictbuild.wylie import WylieConverter

# Rule "leading (label)": a parenthetical at the start of the definition, a sense or a sub-sense.
_LEADING_PARENTHETICAL = re.compile(r"^\((?P<inner>[^()]*)\)\s*")

# Rule "`(དཔེར་ན།) …` → examples": the marker, tolerating a missing shad or inner spaces.
_EXAMPLE_MARKER = re.compile(r"\(\s*དཔེར་ན[\u0f0b\u0f0d]?\s*\)")


def parse_tbtb_row(row: SourceRow, converter: WylieConverter) -> ParsedRow:
    """Parse one `tbtb` row into labelled senses with their examples."""
    headword, homonym = normalise_tibetan_headword(row.word)
    parsed = ParsedRow(
        source_id=row.source_id,
        headword=headword,
        group_key=headword,
        homonym=homonym,
        wylie=converter.to_wylie(headword) if headword else None,
    )
    entry_labels, text = _take_labels(clean_definition(row.definition), parsed)
    number = 0
    for sense_no, segment in split_numbered(text) or [(None, text)]:
        sense_labels: list[BoLabel] = []
        if sense_no is not None:
            sense_labels, segment = _take_labels(segment, parsed)
        added = False
        for sub_no, item in split_subsenses(segment):
            item_labels: list[BoLabel] = []
            if sub_no is not None:
                item_labels, item = _take_labels(item, parsed)
            sense = _sense(item, parsed, sub_no=sub_no, labels=sense_labels + item_labels)
            if sense is not None:
                if not added:
                    number, added = number + 1, True
                sense.sense_no = number
                parsed.senses.append(sense)
    _apply_entry_labels(parsed.senses, entry_labels)
    return parsed


def _take_labels(text: str, parsed: ParsedRow) -> tuple[list[BoLabel], str]:
    """Whitelisted parentheticals at the start of `text`; the first other one is recorded as unknown."""
    labels: list[BoLabel] = []
    while match := _LEADING_PARENTHETICAL.match(text):
        found = lookup_labels(match.group("inner"))
        if not found:
            if not _EXAMPLE_MARKER.match(match.group(0)):
                parsed.unknown_labels.append(match.group("inner").strip())
            break
        labels.extend(label for label in found if label not in labels)
        text = text[match.end() :]
    return labels, text


def _sense(text: str, parsed: ParsedRow, *, sub_no: int | None, labels: list[BoLabel]) -> ParsedSense | None:
    """One sense record, or None when it has no gloss (its examples then join the previous sense)."""
    gloss, *example_texts = _EXAMPLE_MARKER.split(text)
    examples = [ParsedExample(tidy(example)) for example in example_texts if tidy(example)]
    gloss = tidy(gloss)
    if not gloss:
        if not examples:
            return None
        if parsed.senses:
            parsed.senses[-1].examples.extend(examples)
            parsed.warnings.append("sense_dropped_empty")
            return None
        gloss, examples = tidy(text), []  # nothing to attach to: the example text is the gloss
    return ParsedSense(
        gloss=gloss,
        gloss_lang=GLOSS_LANG_BO,
        source_id=parsed.source_id,
        sub_no=sub_no,
        part_of_speech=_pos(labels),
        labels=[label.key for label in labels if label.kind == LABEL],
        examples=examples,
    )


def _apply_entry_labels(senses: list[ParsedSense], entry_labels: list[BoLabel]) -> None:
    """Rule "entry-level label applies to senses without their own" (per kind: POS, usage label)."""
    pos = _pos(entry_labels)
    keys = [label.key for label in entry_labels if label.kind == LABEL]
    for sense in senses:
        if sense.part_of_speech is None:
            sense.part_of_speech = pos
        if not sense.labels:
            sense.labels = list(keys)


def _pos(labels: list[BoLabel]) -> str | None:
    return ",".join(label.key for label in labels if label.kind == POS) or None
