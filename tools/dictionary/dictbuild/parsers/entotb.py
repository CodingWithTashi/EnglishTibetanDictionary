"""English → Tibetan parser for the `entotb` table (plan §5.3 "entotb" grammar).

A definition is a run of part-of-speech blocks (`v.t. … n. …`). Each block may open with labels
(`adm,law`, `chem.`), Latin parentheticals (`(to)`) and, for verbs, inflections (`ran, run`); its
body is split into numbered senses once per block, and each sense into `ཀཿ ཁཿ` sub-senses. Inside a
sense, `*` becomes a note, `= English term` a synonym link and an English phrase of two or more words an
example (with the Tibetan that follows as its translation). Glosses are Tibetan (`gloss_lang='bo'`).
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from dictbuild.model import GLOSS_LANG_BO, RELATION_SYNONYM, ParsedExample, ParsedRow, ParsedSense, RelatedCandidate
from dictbuild.parsers.common import has_tibetan, split_numbered, split_subsenses, starts_with_particle, tidy
from dictbuild.parsers.labels_en import MID_HEADER, SENSE_HEADER, START_HEADER, VERB_CODES, pos_codes, take_labels
from dictbuild.source import SourceRow
from dictbuild.text import clean_definition, is_tibetan, normalise_english_headword

# Rule "`(A)`/`(to)` prefix → note": a parenthetical without Tibetan at the start of the definition
# or of a block ("(fem. authoress)", "(a.m.)").
_LATIN_PARENTHETICAL = re.compile(r"^\((?P<inner>[^()\u0f00-\u0fff]+)\)\s*")

# Rule "verb inflections `ran, run` → note": Latin words between a verb header and the first sense
# number, Tibetan text or asterisk ("v.i. ran, run 1.", "v. led 1.", "v.t. burnt, (burned) 1.").
_INFLECTIONS = re.compile(r"^(?P<forms>[A-Za-z][A-Za-z ,'’()/\-]*?)\s*(?=[0-9]{1,2}\.|[\u0f00-\u0fff]|\*|$)")
_MAX_INFLECTION_WORDS = 6

# A sense number right before a header makes it a sense-level part of speech ("2. pl. …"), not a block.
_SENSE_NUMBER_BEFORE = re.compile(r"[0-9]{1,2}\.\s*$")

# A dotted lower-case abbreviation in a label position that is not whitelisted ("nat. …") stays in
# the gloss and is listed in the build report, so the whitelist can grow. An English word bound to a
# following particle ("aerie. ལ་ལྟོས།", "break. གི་འདས་ཚིག") is text, not a label, and is not listed.
_UNKNOWN_ABBREVIATION = re.compile(r"^(?P<abbreviation>[a-z]{2,12}\.)(?=\s*[\u0f00-\u0fff])")

# Rule "`*` → note": the asterisk marks a coined term.
ASTERISK_NOTE = "*"

# Rule "`= lead pencil` → related(synonym)": an equals sign outside brackets followed by an English
# term of at most five words, which is meant to resolve to an English headword. A Tibetan `= …` is an
# equivalent or an explanation, not a synonym, and stays in the gloss. So does an equation inside
# English text ("libere=library", "it’s = it is, it has", "H2 SO4 = …"): an equals sign right after a
# Latin letter, digit or "+" is not a marker, and a term with digits or other punctuation is no headword.
_EQUALS = re.compile(r"=\s*")
_LATIN_TERM = re.compile(r"^(?P<term>[A-Za-z][^=\u0f00-\u0fff]*?)(?=\.\s|[.,;:]?\s*[\u0f00-\u0fff=]|[.,;:]?\s*$)")
_HEADWORD_LIKE = re.compile(r"[A-Za-z][A-Za-z .’'\-]*")
_EQUATION_LEFT = re.compile(r"[A-Za-z0-9+’']\s*$")
_MAX_LATIN_SYNONYM_WORDS = 5

# Rule "English example phrases (≥2 Latin words) with following Tibetan → examples(text, translation)":
# a Latin run outside brackets; its translation runs to the next example or the end of the sense.
# A word has two or more letters, or is "a"/"I": initials such as "U.N." are not words.
_LATIN_WORD = re.compile(r"[A-Za-z][A-Za-z0-9'’\-]+|\b[aAI]\b")
_MIN_EXAMPLE_WORDS = 2
_PHRASE_TRIM = " ,;:("
# "… ལྟ་བུ།" after a phrase means "for example": the phrase has no translation.
_FOR_EXAMPLE = re.compile(r"^ལྟ་བུ[\u0f0b\u0f0d]*$")


@dataclass(frozen=True, slots=True)
class _Block:
    header: str
    pos: tuple[str, ...]
    body: str


def parse_entotb_row(row: SourceRow) -> ParsedRow:
    """Parse one `entotb` row into senses, examples and synonym candidates."""
    headword, homonym, group_key = normalise_english_headword(row.word)
    parsed = ParsedRow(source_id=row.source_id, headword=headword, group_key=group_key, homonym=homonym)
    text = clean_definition(row.definition)
    prefix_notes, text = _take_parentheticals(text)
    builder = _SenseBuilder(parsed)
    for index, block in enumerate(_split_blocks(text)):
        _parse_block(block, prefix_notes if index == 0 else [], builder)
    return parsed


def _split_blocks(text: str) -> list[_Block]:
    """Cut the definition at part-of-speech headers (rule "numbering restarts per POS block")."""
    header, pos, start = "", (), 0
    if match := START_HEADER.match(text):
        header, pos, start = match.group("header"), pos_codes(match.group("header")), match.end()
    blocks: list[_Block] = []
    for match in MID_HEADER.finditer(text, start):
        if _depth(text, match.start()) or _SENSE_NUMBER_BEFORE.search(text, 0, match.start()):
            continue
        blocks.append(_Block(header, pos, text[start : match.start()].strip()))
        header, pos, start = match.group("header"), pos_codes(match.group("header")), match.end()
    blocks.append(_Block(header, pos, text[start:].strip()))
    return [block for block in blocks if block.body or block.pos]


def _parse_block(block: _Block, prefix_notes: list[str], builder: _SenseBuilder) -> None:
    labels, body = take_labels(block.body)
    notes, body = _take_parentheticals(body, labels)
    _record_unknown_abbreviation(body, builder.row)
    notes = prefix_notes + notes
    if VERB_CODES.intersection(block.pos) and (match := _INFLECTIONS.match(body)):
        forms = match.group("forms").strip(" ,")
        if forms and len(_LATIN_WORD.findall(forms)) <= _MAX_INFLECTION_WORDS:
            notes.append(forms)
            body = body[match.end() :].lstrip()
    if not body:
        return
    if block.pos and not has_tibetan(body) and builder.previous is not None:
        # "… pl. aides": a header followed only by English words annotates the previous sense.
        builder.previous.notes.append(f"{block.header} {body}")
        return
    for number, text in split_numbered(body) or [(None, body)]:
        pos, sense_labels = block.pos, []
        if number is not None:
            sense_labels, text = take_labels(text)
            if match := SENSE_HEADER.match(text):
                pos = _combine_pos(block.pos, pos_codes(match.group("header")))
                more_labels, text = take_labels(text[match.end() :])
                sense_labels += more_labels
            _record_unknown_abbreviation(text, builder.row)
        builder.start_sense()
        for sub_no, item in split_subsenses(text):
            item_labels: list[str] = []
            if sub_no is not None:
                item_labels, item = take_labels(item)
                _record_unknown_abbreviation(item, builder.row)
            builder.add(
                item,
                sub_no=sub_no,
                pos=",".join(pos) or None,
                labels=_merge(labels, sense_labels, item_labels),
                notes=notes,
            )


class _SenseBuilder:
    """Numbers senses continuously within the row and applies the empty-gloss rule."""

    def __init__(self, row: ParsedRow) -> None:
        self.row = row
        self._number = 0
        self._numbered = False

    @property
    def previous(self) -> ParsedSense | None:
        return self.row.senses[-1] if self.row.senses else None

    def start_sense(self) -> None:
        self._numbered = False

    def add(self, text: str, *, sub_no: int | None, pos: str | None, labels: list[str], notes: list[str]) -> None:
        notes = list(notes)
        if "*" in text:
            notes.append(ASTERISK_NOTE)
            text = text.replace("*", " ")
        rest, synonyms = _extract_synonyms(text)
        if synonyms and not tidy(rest):
            # "n. = cotton wool": the term after "=" is the sense's only gloss, not a link or an example.
            gloss, examples = tidy(text).lstrip("= "), []
        else:
            self.row.related.extend(synonyms)
            gloss, examples = _extract_examples(rest)
            gloss = tidy(gloss).lstrip("= ")  # "= ཀུན་འདུས་གཡེར་མ།": a leading "=" is dropped
            text = rest
        if not gloss:
            if not tidy(text):
                return
            if examples and self.previous is not None:
                self.previous.examples.extend(examples)
                self.row.warnings.append("sense_dropped_empty")
                return
            gloss, examples = tidy(text).lstrip("= "), []  # nothing to attach to: keep the phrase
        if not self._numbered:
            self._number += 1
            self._numbered = True
        self.row.senses.append(
            ParsedSense(
                gloss=gloss,
                gloss_lang=GLOSS_LANG_BO,
                source_id=self.row.source_id,
                sense_no=self._number,
                sub_no=sub_no,
                part_of_speech=pos,
                labels=labels,
                notes=notes,
                examples=examples,
            )
        )


def _record_unknown_abbreviation(text: str, row: ParsedRow) -> None:
    """Record a non-whitelisted abbreviation left at a label position (the start of `text`)."""
    if (match := _UNKNOWN_ABBREVIATION.match(text)) and not starts_with_particle(text[match.end() :]):
        row.unknown_labels.append(match.group("abbreviation"))


def _take_parentheticals(text: str, labels: list[str] | None = None) -> tuple[list[str], str]:
    """Leading Latin parentheticals: labels (`(sl.)`) go to `labels`, anything else becomes a note."""
    notes: list[str] = []
    while match := _LATIN_PARENTHETICAL.match(text):
        inner = match.group("inner").strip()
        found, rest = take_labels(inner)
        if labels is not None and found and not rest:
            labels.extend(key for key in found if key not in labels)
        elif inner:
            notes.append(inner)
        text = text[match.end() :]
    return notes, text


def _extract_synonyms(text: str) -> tuple[str, list[RelatedCandidate]]:
    """Cut `= term` synonyms out of a sense text (rule "`= lead pencil` → related(synonym)")."""
    synonyms: list[RelatedCandidate] = []
    pieces: list[str] = []
    position = 0
    for match in _EQUALS.finditer(text):
        if match.start() < position or _depth(text, match.start()) or _EQUATION_LEFT.search(text[: match.start()]):
            continue
        after = text[match.end() :]
        term, consumed = _synonym_term(after)
        if term is None:
            continue
        synonyms.append(RelatedCandidate(term, RELATION_SYNONYM))
        pieces.append(text[position : match.start()])
        position = match.end() + consumed
    pieces.append(text[position:])
    return " ".join(pieces), synonyms


def _synonym_term(after: str) -> tuple[str | None, int]:
    """The synonym right after an equals sign and the length of text it spans, or (None, 0)."""
    if match := _LATIN_TERM.match(after):
        term = match.group("term").strip(" .,;:")
        words = _LATIN_WORD.findall(term)
        if (
            1 <= len(words) <= _MAX_LATIN_SYNONYM_WORDS
            and _HEADWORD_LIKE.fullmatch(term)
            and not starts_with_particle(after[match.end() :])
        ):
            consumed = match.end() + (1 if after[match.end() : match.end() + 1] in ".,;:" else 0)
            return term, consumed
    return None, 0


def _extract_examples(text: str) -> tuple[str, list[ParsedExample]]:
    """Split a sense text into its gloss and the English example phrases that follow it."""
    runs = [run for run in _latin_runs(text) if _is_example(text, run)]
    if not runs:
        return text, []
    examples: list[ParsedExample] = []
    for index, (start, end) in enumerate(runs):
        stop = runs[index + 1][0] if index + 1 < len(runs) else len(text)
        phrase = text[start:end].strip().rstrip(_PHRASE_TRIM)
        translation = tidy(text[end:stop])
        if _FOR_EXAMPLE.match(translation):
            translation = ""
        examples.append(ParsedExample(phrase, translation or None))
    return text[: runs[0][0]], examples


def _latin_runs(text: str) -> list[tuple[int, int]]:
    """(start, end) of each stretch of non-Tibetan text that starts with a Latin letter outside brackets."""
    runs: list[tuple[int, int]] = []
    depth, index = 0, 0
    while index < len(text):
        if depth == 0 and _starts_latin_word(text, index):
            end = index
            while end < len(text) and not is_tibetan(text[end]):
                depth = _step_depth(depth, text[end])
                end += 1
            runs.append((index, end))
            index = end
        else:
            depth = _step_depth(depth, text[index])
            index += 1
    return runs


def _starts_latin_word(text: str, index: int) -> bool:
    ch, before = text[index], text[index - 1 : index]
    return ch.isascii() and ch.isalpha() and not (before.isascii() and before.isalnum())


def _is_example(text: str, run: tuple[int, int]) -> bool:
    phrase = text[run[0] : run[1]]
    return len(_LATIN_WORD.findall(phrase)) >= _MIN_EXAMPLE_WORDS and not starts_with_particle(text[run[1] :])


def _depth(text: str, position: int) -> int:
    """Bracket nesting depth at `position` (unbalanced closing brackets are ignored)."""
    depth = 0
    for ch in text[:position]:
        depth = _step_depth(depth, ch)
    return depth


def _step_depth(depth: int, ch: str) -> int:
    if ch in "([":
        return depth + 1
    if ch in ")]":
        return max(depth - 1, 0)
    return depth


def _combine_pos(block: tuple[str, ...], sense: tuple[str, ...]) -> tuple[str, ...]:
    """A sense-level `pl.` adds to the block's part of speech ("n,pl"); any other replaces it."""
    if sense == ("pl",):
        return block + tuple(code for code in sense if code not in block)
    return sense


def _merge(*groups: list[str]) -> list[str]:
    merged: list[str] = []
    for group in groups:
        merged.extend(key for key in group if key not in merged)
    return merged
