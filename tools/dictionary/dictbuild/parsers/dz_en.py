"""Dzongkha → English parser for the DDC `dz-en` StarDict (spec §7).

One record is one headword with one or two `<k>` blocks (the second is a duplicate or a further
homonym). Each block's `<def>` holds one or more part-of-speech runs (`<pos>`), each with optional
`<b>N</b>` numbered senses. Inside a sense, `hon. X`, `short X`, `also X` and `opp. X` become notes, a
leading `( fut., prs., pst., imp.)` list becomes "forms: …", `<kref>` targets become synonym/antonym
links and the `syn.` marker is dropped. Bracketed Dzongkha glosses stay in the gloss.
"""

from __future__ import annotations

import re

from dictbuild.model import (
    GLOSS_LANG_EN,
    RELATION_ANTONYM,
    RELATION_SYNONYM,
    ParsedRow,
    ParsedSense,
    RelatedCandidate,
)
from dictbuild.parsers.common import tidy
from dictbuild.parsers.ddc_common import pos_code, split_k_blocks, strip_tags
from dictbuild.related import HeadwordIndex
from dictbuild.source import SourceRow
from dictbuild.text import normalise_tibetan_headword
from dictbuild.wylie import WylieConverter

__all__ = ["parse_dz_en_row"]

_DEF_TAG = re.compile(r"</?def>")
_POS_BLOCK = re.compile(r"<pos>(?P<label>.*?)</pos>", re.DOTALL)
_LEADING_LABEL = re.compile(r"^\s*<abbr><i>(?P<label>[^<]*)</i></abbr>")
# Rule "`<b>N</b>` numbered senses": ASCII numbers counting 1, 2, 3, … in order; any other number stays text.
_SENSE_NUMBER = re.compile(r"<b>\s*(?P<number>[0-9]{1,2})\s*</b>")

_TIBETAN_RUN = r"[ༀ-࿿]+(?:\s+[ༀ-࿿]+)*"
_NESTED_ABBR = re.compile(r"<abbr><i>\s*(<abbr><i>[^<]*</i></abbr>)\s*</i></abbr>")
_TENSE = r"(?:fut|prs|pst|imp)\."
_FORMS = r"\(\s*(?P<forms>(?:<abbr><i>\s*(?:fut|prs|pst|imp)\.\s*</i></abbr>[\s,]*)+)\)"
# Inline items of a sense, in one pass so notes keep their source order. A shad or tsheg left after a
# `</kref>` separates the links and is not gloss text.
_ITEM = re.compile(
    rf"{_FORMS}"
    # A verb form that differs from the headword: `pst. བཏང་།`, in an `<abbr>` or as bare text.
    rf"|<abbr><i>\s*(?P<tense>{_TENSE})\s*</i></abbr>\s*(?P<form>{_TIBETAN_RUN})?"
    rf"|(?<![A-Za-z])(?P<bare_tense>{_TENSE})\s*(?P<bare_form>{_TIBETAN_RUN})"
    r"|<abbr><i>\s*hon\.\s*</i></abbr>\s*(?P<honform>form)\b"
    r"|<abbr><i>\s*opp\.\s*</i></abbr>\s*<kref type=\"ant\">(?P<opp>.*?)</kref>[་།\s]*"
    r"|<kref type=\"(?P<type>\w+)\">(?P<target>.*?)</kref>[་།\s]*"
    rf"|<abbr><i>\s*(?P<label>syn\.?|hon\. for ?m|hon\. form|hon\.|short|also|opp\.)\s*</i></abbr>\s*(?P<arg>{_TIBETAN_RUN})?"
)
_NOTE_LABEL = re.compile(rf"{_TENSE}|syn\.?|hon\. for ?m|hon\. form|hon\.|short|also|opp\.")
_FORM_ABBREVIATION = re.compile(r"<i>\s*([a-z]+\.)\s*</i>")
_RELATION_BY_TYPE = {"syn": RELATION_SYNONYM, "ant": RELATION_ANTONYM}


def parse_dz_en_row(row: SourceRow, converter: WylieConverter, index: HeadwordIndex) -> ParsedRow:
    """Parse one `dz-en` record. `index` is unused: its links are written as headwords, not resolved here."""
    headword, homonym = normalise_tibetan_headword(row.word)
    parsed = ParsedRow(
        source_id=row.source_id,
        headword=headword,
        group_key=headword,
        homonym=homonym,
        wylie=converter.to_wylie(headword) if headword else None,
    )
    for _, rest in split_k_blocks(row.definition):
        carried: list[str] = []  # notes of text before the first `<pos>` go to the next sense of this block only
        runs = _pos_runs(_NESTED_ABBR.sub(r"\1", _DEF_TAG.sub("", rest)))
        for position, (pos_label, markup) in enumerate(runs):
            code, known = pos_code(pos_label)
            if not known:
                parsed.unknown_labels.append(pos_label)
            # Text before the first `<pos>` that holds only notes and links is not a lost sense.
            notes_only_lead = not pos_label and position < len(runs) - 1
            carried = _parse_run(markup, code, parsed, carried, optional=notes_only_lead)
    return parsed


def _pos_runs(markup: str) -> list[tuple[str, str]]:
    """The `(part-of-speech label, markup)` runs of a `<def>`; text before the first `<pos>` is a run too.

    Without a `<pos>` a leading `<abbr>` label (`num.`) is taken as the part of speech, unless it is
    one of the sense notes (`syn.`, `hon.`, …).
    """
    matches = list(_POS_BLOCK.finditer(markup))
    runs: list[tuple[str, str]] = []
    lead = markup[: matches[0].start()] if matches else markup
    if strip_tags(lead):
        label = ""
        if not matches and (found := _LEADING_LABEL.match(lead)):
            candidate = strip_tags(found.group("label"))
            if not _NOTE_LABEL.fullmatch(candidate):
                label, lead = candidate, lead[found.end() :]
        runs.append((label, lead))
    for position, match in enumerate(matches):
        end = matches[position + 1].start() if position + 1 < len(matches) else len(markup)
        runs.append((strip_tags(match.group("label")), markup[match.end() : end]))
    return runs


def _parse_run(markup: str, code: str | None, parsed: ParsedRow, carried: list[str], *, optional: bool) -> list[str]:
    """Add the senses of one part-of-speech run: its `<b>N</b>` segments, or the whole run as one sense.

    `carried` are notes from earlier text without a gloss; the notes still unplaced are returned. A run
    that has no gloss is a lost sense (warning) unless it has no `<pos>` and only notes precede a later `<pos>` run.
    """
    markers: list[re.Match[str]] = []
    for match in _SENSE_NUMBER.finditer(markup):
        if int(match.group("number")) == len(markers) + 1:
            markers.append(match)
    if not markers:
        return _add_sense(markup, code, carried, parsed, required=not optional)
    ends = [match.start() for match in markers[1:]] + [len(markup)]
    # Notes before `<b>1</b>` (forms, short forms) belong to the first sense.
    if strip_tags(lead := markup[: markers[0].start()]):
        carried = _add_sense(lead, code, carried, parsed, required=False)
    for match, end in zip(markers, ends):
        carried = _add_sense(markup[match.end() : end], code, carried, parsed, required=True)
    return carried


def _add_sense(markup: str, code: str | None, carried: list[str], parsed: ParsedRow, *, required: bool) -> list[str]:
    """Parse one sense's markup. Returns the notes still to be carried when the sense is not added."""
    notes = list(carried)

    def item(match: re.Match[str]) -> str:
        if match.group("forms"):
            notes.append("forms: " + ", ".join(_FORM_ABBREVIATION.findall(match.group("forms"))))
        elif tense := match.group("tense") or match.group("bare_tense"):
            form = match.group("form") or match.group("bare_form")
            if form:
                notes.append(f"{tense} {form}")
        elif match.group("honform") is not None:
            notes.append("hon. form")
        elif (opposite := match.group("opp")) is not None:
            _link(opposite, RELATION_ANTONYM, parsed)
            if text := strip_tags(opposite):
                notes.append(f"opp. {text}")
        elif match.group("type"):
            if relation := _RELATION_BY_TYPE.get(match.group("type")):
                _link(match.group("target"), relation, parsed)
        elif (label := match.group("label")).startswith("syn"):
            pass
        else:
            notes.append(f"{label} {match.group('arg')}" if match.group("arg") else label)
        return " "

    gloss = tidy(strip_tags(_ITEM.sub(item, markup)))
    if gloss:
        parsed.senses.append(
            ParsedSense(
                gloss=gloss,
                gloss_lang=GLOSS_LANG_EN,
                source_id=parsed.source_id,
                sense_no=len(parsed.senses) + 1,
                part_of_speech=code,
                notes=notes,
            )
        )
        return []
    if required:
        parsed.warnings.append("sense_without_gloss")
        return []
    return notes


def _link(target: str, relation: str, parsed: ParsedRow) -> None:
    headword = normalise_tibetan_headword(strip_tags(target))[0]
    if headword:
        parsed.related.append(RelatedCandidate(headword, relation))
