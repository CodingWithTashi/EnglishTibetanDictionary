"""English → Tibetan parser for the `entotb` table (plan §5.3 "entotb" grammar).

A definition is a run of part-of-speech blocks (`v.t. … n. …`). Each block may open with labels
(`adm,law`, `chem.`), Latin parentheticals (`(to)`) and an English lead (`ran, run`, `pl. mice`); its
body is split into numbered senses once per block, and each sense into `ཀཿ ཁཿ` sub-senses. Inside a
sense, `*` becomes a note, `= English term` a synonym link and an English phrase of two or more words an
example (with the Tibetan that follows as its translation). Glosses are Tibetan (`gloss_lang='bo'`):
English text that would make a sense of its own becomes a note on the next sense instead.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from dictbuild.model import GLOSS_LANG_BO, RELATION_SYNONYM, ParsedExample, ParsedRow, ParsedSense, RelatedCandidate
from dictbuild.parsers.common import (
    Segment,
    has_tibetan,
    split_numbered,
    split_subsenses,
    starts_with_particle,
    tidy,
)
from dictbuild.parsers.labels_en import MID_HEADER, SENSE_HEADER, START_HEADER, VERB_CODES, pos_codes, take_labels
from dictbuild.source import SourceRow
from dictbuild.text import clean_definition, is_tibetan, normalise_english_headword

# Rule "`(A)`/`(to)` prefix → note": a parenthetical without Tibetan at the start of the definition
# or of a block ("(fem. authoress)", "(a.m.)").
_LATIN_PARENTHETICAL = re.compile(r"^\((?P<inner>[^()\u0f00-\u0fff]+)\)\s*")

# Rule "an English lead is a note, never a sense": text without Tibetan at the head of a block or of a
# numbered sense, before its first sense number or sub-sense marker ("adj. worse, worst 1.", "n.
# revision n. 1.", "2. Congress ཀཿ …"), is held for the next sense of the row: a note, or the word a
# "see"/"also called" sense refers to ("arbiter 1. ལ་ལྟོས།" → "arbiter ལ་ལྟོས།").
# Rule "verb inflections and plurals → note": after a verb or plural header the lead runs up to the
# first sense number, Tibetan text, "*", "=" or bracket holding Tibetan ("v.i. ran, run 1.", "v. gave,
# given . 1.", "v.t. -sook, -saken ཀཿ", "v. -swore,-sworn མནའ་…"); a plural becomes the note "pl. mice"
# and the block loses "pl". Forms bound to the Tibetan after them stay text ("v. get ཡི་འདས་ཚིག").
_INFLECTIONS_END = re.compile(
    r"(?<![0-9])[0-9]{1,2}\.(?![0-9])|[\u0f00-\u0fff]|[*=]|\((?=[^()]*[\u0f00-\u0fff])"
)
_MAX_INFLECTION_WORDS = 6
_LEAD_TRIM = " .,&;:"
_DETACHED_DOT = re.compile(r"\s+\.$")
# Header leftovers ("n. & .", "adj. .", "n. I 1.", "adj. a, 1.", "2. ཀཿ") have no word of two letters;
# they are dropped and counted in the report.
_LEAD_WORD = re.compile(r"[A-Za-z’']{2}")
PLURAL_CODE = "pl"
# "adj. 1 ཀཿ …": a bare "1" opening a block before Tibetan text is its first sense number.
_BARE_FIRST_NUMBER = re.compile(r"^1(?=\s+[\u0f00-\u0fff])")
# A header right before a "see"/"also called" phrase belongs to a cross-reference ("cox n. ལ་ལྟོས།",
# "escape n. 3. ལ་ལྟོས།"); it does not open a block.
_CROSS_REFERENCE_TAIL = re.compile(r"\s*(?:[0-9]{1,2}\.\s*)?(?:[ཀཁགངཅ]\u0f7f\s*)?")

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
_PHRASE_TRIM = " ,;:"
_OPENING_BRACKET = "("
# "to black out 1. མུན་ནག་བཟོ་བ། 2. …": a number ending the phrase numbers its translations, so it opens
# the translation ("We played three games. 5. (རྩེད་མོ།)…" repeats a sense number in the source).
_PHRASE_NUMBER = re.compile(r"\s+(?P<number>[0-9]{1,2}\.)$")
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
    builder.hold_notes(prefix_notes)
    for block in _split_blocks(text):
        _parse_block(block, builder)
    builder.finish()
    return parsed


def _split_blocks(text: str) -> list[_Block]:
    """Cut the definition at part-of-speech headers (rule "numbering restarts per POS block")."""
    header, pos, start = "", (), 0
    if match := START_HEADER.match(text):
        header, pos, start = match.group("header"), pos_codes(match.group("header")), match.end()
    blocks: list[_Block] = []
    for match in MID_HEADER.finditer(text, start):
        if (
            _depth(text, match.start())
            or _SENSE_NUMBER_BEFORE.search(text, 0, match.start())
            or _is_cross_reference(text, match.end())
        ):
            continue
        blocks.append(_Block(header, pos, text[start : match.start()].strip()))
        header, pos, start = match.group("header"), pos_codes(match.group("header")), match.end()
    blocks.append(_Block(header, pos, text[start:].strip()))
    return [block for block in blocks if block.body or block.pos]


def _parse_block(block: _Block, builder: _SenseBuilder) -> None:
    if not block.body:
        return
    if not has_tibetan(block.body):
        if block.pos and builder.previous is not None:
            # "… pl. aides": a header followed only by English words annotates the previous sense.
            builder.previous.notes.append(f"{block.header} {block.body}")
        else:
            builder.hold_lead(block.body)  # "fem. masseuse n. …", "n. cox n. ལ་ལྟོས།"
        return
    labels, body = take_labels(block.body)
    more_labels, body = take_labels(body.lstrip(_LEAD_TRIM))  # "n. us .sl. 1.", "com ,law 1."
    labels = _merge(labels, more_labels)
    notes, body = _take_parentheticals(body, labels)
    _record_unknown_abbreviation(body, builder.row)
    builder.hold_notes(notes)
    pos = block.pos
    body = _BARE_FIRST_NUMBER.sub("1.", body)
    if PLURAL_CODE in pos or VERB_CODES.intersection(pos):
        forms, body = _take_inflections(body)
        if forms and PLURAL_CODE in pos:
            pos = tuple(code for code in pos if code != PLURAL_CODE)
            builder.hold_notes([f"{PLURAL_CODE}. {forms}"])
        elif forms:
            builder.hold_notes([forms])
    segments = split_numbered(body) or [Segment(None, body)]
    for number, text in segments:
        if number is None and len(segments) > 1 and not has_tibetan(text):
            builder.hold_lead(text)  # "n. revision n. 1. …", "adj. worse, worst 1. …"
            continue
        sense_pos, sense_labels = pos, []
        if number is not None:
            sense_labels, text = take_labels(text)
            if match := SENSE_HEADER.match(text):
                sense_pos = _combine_pos(pos, pos_codes(match.group("header")))
                more_labels, text = take_labels(text[match.end() :])
                sense_labels += more_labels
            _record_unknown_abbreviation(text, builder.row)
        builder.start_sense()
        items = split_subsenses(text)
        for sub_no, item in items:
            if sub_no is None and len(items) > 1 and not has_tibetan(item):
                builder.hold_lead(item)  # "2. Congress ཀཿ …", "4. anat. head ཀཿ …"
                continue
            item_labels: list[str] = []
            if sub_no is not None:
                item_labels, item = take_labels(item)
                _record_unknown_abbreviation(item, builder.row)
            builder.add(
                item,
                sub_no=sub_no,
                pos=",".join(sense_pos) or None,
                labels=_merge(labels, sense_labels, item_labels),
            )


class _SenseBuilder:
    """Numbers senses continuously within the row and decides where text without Tibetan goes.

    A sense is only ever written with Tibetan in its gloss. Block notes and English leads are held
    and given to the next sense (block notes thus annotate a block's first sense only); what is still
    held when the row ends annotates its last sense.
    """

    def __init__(self, row: ParsedRow) -> None:
        self.row = row
        self._number = 0
        self._numbered = False
        self._held_notes: list[str] = []
        self._held_leads: list[str] = []

    @property
    def previous(self) -> ParsedSense | None:
        return self.row.senses[-1] if self.row.senses else None

    def start_sense(self) -> None:
        self._numbered = False

    def hold_notes(self, notes: list[str]) -> None:
        self._held_notes.extend(note for note in notes if note)

    def hold_lead(self, text: str) -> None:
        """Hold an English lead for the next sense, or drop it when it is only a header leftover."""
        lead = _clean_lead(text)
        if _LEAD_WORD.search(lead):
            self._held_leads.append(lead)
        elif lead:
            self.row.warnings.append("header_leftover_dropped")

    def add(self, text: str, *, sub_no: int | None, pos: str | None, labels: list[str]) -> None:
        notes: list[str] = []
        if "*" in text:
            notes.append(ASTERISK_NOTE)
            text = text.replace("*", " ")
        if not tidy(text):
            return
        if not has_tibetan(text):
            self._without_tibetan(text)
            return
        rest, synonyms = _extract_synonyms(text)
        self.row.related.extend(synonyms)
        gloss, examples = _extract_examples(rest)
        gloss = tidy(gloss).lstrip("= ")  # "= ཀུན་འདུས་གཡེར་མ།": a leading "=" is dropped
        if not gloss and examples and examples[0].translation:
            # "5. in bond ཀཿ …", "4. sl. chuck out ཕྱིར་འབུད་པ།": a sense opening with an English phrase
            # keeps its Tibetan as the gloss and the phrase as a note.
            notes.insert(0, examples[0].text)
            gloss, examples = examples[0].translation, examples[1:]
        elif not gloss:
            gloss, examples = tidy(rest).lstrip("= "), []  # "in good fig ལྟ་བུ།": nothing to split off
        self._emit(gloss, examples, sub_no=sub_no, pos=pos, labels=labels, notes=notes)

    def finish(self) -> None:
        """Give what is still held to the last sense of the row."""
        held = self._held_notes + self._held_leads
        if held and self.previous is not None:
            self.previous.notes.extend(note for note in held if note not in self.previous.notes)
            if self._held_leads:
                self.row.warnings.append("lead_kept_as_note")
        self._held_notes, self._held_leads = [], []

    def _without_tibetan(self, text: str) -> None:
        """A sense text with no Tibetan ("1. mat 2. ལ་ལྟོས།", "2. biographer n. …") is held as a lead."""
        rest, synonyms = _extract_synonyms(text)
        if synonyms and not tidy(rest):
            self.row.related.extend(synonyms)  # "2. = absorbent cotton": the sense is a synonym link
            return
        self.hold_lead(text)

    def _emit(
        self,
        gloss: str,
        examples: list[ParsedExample],
        *,
        sub_no: int | None,
        pos: str | None,
        labels: list[str],
        notes: list[str],
    ) -> None:
        if self._held_leads:
            lead = " ".join(self._held_leads)
            if starts_with_particle(gloss):
                gloss = f"{lead} {gloss}"  # "arbiter ལ་ལྟོས།" (see arbiter), "charwoman ཡང་ཟེར།"
            else:
                notes = [lead, *notes]
                self.row.warnings.append("lead_kept_as_note")
        notes = [*self._held_notes, *notes]
        self._held_notes, self._held_leads = [], []
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


def _clean_lead(text: str) -> str:
    """"& gave, given ." → "gave, given"; an abbreviation keeps its dot ("nat.", "aerie.")."""
    return _DETACHED_DOT.sub("", tidy(text).lstrip(_LEAD_TRIM).rstrip(" ,&;:"))


def _take_inflections(text: str) -> tuple[str, str]:
    """Verb or plural forms at the head of a block body (see `_INFLECTIONS_END`) and the text after them."""
    match = _INFLECTIONS_END.search(text)
    end = match.start() if match else len(text)
    forms, rest = _clean_lead(text[:end]), text[end:].lstrip()
    if not _LEAD_WORD.search(forms) or len(_LATIN_WORD.findall(forms)) > _MAX_INFLECTION_WORDS:
        return "", text
    if starts_with_particle(rest):
        return "", text
    return forms, rest


def _is_cross_reference(text: str, position: int) -> bool:
    """Whether a "see"/"also called" phrase follows `position`, after an optional sense number."""
    tail = _CROSS_REFERENCE_TAIL.match(text, position)
    return tail is not None and starts_with_particle(text[tail.end() :])


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
        opening = ""
        if phrase.endswith(_OPENING_BRACKET):
            # "We played three games. (རྩེད་མོ།)རྩེ་སྟངས།": the bracket opens the translation.
            phrase, opening = phrase[:-1].rstrip(_PHRASE_TRIM), _OPENING_BRACKET
        if number := _PHRASE_NUMBER.search(phrase):
            phrase, opening = phrase[: number.start()].rstrip(_PHRASE_TRIM), f"{number.group('number')} {opening}"
        translation = tidy(opening + text[end:stop])
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
