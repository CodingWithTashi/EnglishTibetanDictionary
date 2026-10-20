"""Tibetan → English parser for the `tben` table (plan §5.3 "tben" grammar).

Each row is one homonym of a headword (`ཀ་བ་`, `ཀ་བ་ ༡`, …) with an English definition. `{TAG}`
citations become `source_tag`s, `Def.:`/`Div.:` (or their Wylie forms) become labels, `SA a, b`
becomes see-also links resolved against the headword index, and a definition written only in Wylie
is converted to Tibetan when the conversion is clean. Truncated rows flag their last sense.
"""

from __future__ import annotations

import re

from dictbuild.model import GLOSS_LANG_BO, GLOSS_LANG_EN, RELATION_SEE_ALSO, ParsedRow, ParsedSense, RelatedCandidate
from dictbuild.parsers.common import split_numbered, tidy
from dictbuild.related import HeadwordIndex
from dictbuild.source import SourceRow
from dictbuild.text import clean_definition, is_truncated, normalise_tibetan_headword, tibetan_ratio
from dictbuild.wylie import (
    ENGLISH_STOPWORDS,
    LATIN_TOKEN,
    NOT_MODERN_SPELLING,
    WylieConverter,
    has_wylie_term_shape,
    is_wylie_syllable,
)

# Rule "`{TAG}` anywhere → source_tag" ("{C}", "{GD:515}", "{TGP-77}"), per sense, as written.
_TAG = re.compile(r"\{(?P<tag>[^{}]*)\}")

# Rule "`Def.:`/`Definition:`/`Div.:` and Wylie prefixes `mtshan nyid/`, `dbye ba/` → label", with the
# source's other spellings of the same two prefixes.
_LABEL_PREFIX = re.compile(
    r"^(?:(?P<definition>Def\.:|Def:|Def\.|Definitions?:|mtshan nyid/)"
    r"|(?P<division>Div\.:|Divisions?:|dbye ba/))\s*"
)
LABEL_DEFINITION = "definition"
LABEL_DIVISION = "division"

# Rule "ASCII-only definitions containing `/` → toUnicode(); accept if no warnings and ≥80 % Tibetan".
# Every Latin token must also be a well-formed Wylie syllable (`is_wylie_syllable`): English words
# almost never all pass.
WYLIE_MIN_TIBETAN_RATIO = 0.8
__all__ = ["is_wylie_syllable", "parse_tben_row"]

# Rule "`SA a, b, c` → see-also candidates": "SA" as a word at the start, after a space, comma,
# semicolon or opening bracket. Its list ends at ";", ")", "{" or the next "SA".
_SEE_ALSO = re.compile(r"(?<![^\s,;(])SA\s+")
_SEE_ALSO_END = re.compile(r"[;){]|(?<![^\s,;(])SA\s")
_TOKEN = re.compile(r"\S+")
MAX_SEE_ALSO_TOKENS = 12
# An unresolved target stops before the first English stopword or non-Wylie token. A stopword that is
# also a Wylie syllable ("to", "the") belongs to the run when it is the item's last token: "SA phon to"
# names ཕོན་ཏོ་, not ཕོན་ with the gloss "to".
# A later SA item that is a Tibetan term in Wylie (`is_wylie_term`) is an unresolved target ("SA kha sten,
# tshig sten"); short English words are often valid Wylie too ("SA grang ba, cold", "spin, send", "old man").


def parse_tben_row(row: SourceRow, converter: WylieConverter, index: HeadwordIndex) -> ParsedRow:
    """Parse one `tben` row; see-also targets are looked up in `index` (bo-en, then bo-bo)."""
    headword, homonym = normalise_tibetan_headword(row.word)
    parsed = ParsedRow(
        source_id=row.source_id,
        headword=headword,
        group_key=headword,
        homonym=homonym,
        wylie=converter.to_wylie(headword) if headword else None,
    )
    text = clean_definition(row.definition)
    tagless = _TAG.sub("", text)
    wylie_only = tagless.isascii() and "/" in tagless
    labels: list[str] = []
    if match := _LABEL_PREFIX.match(text):
        labels.append(LABEL_DEFINITION if match.group("definition") else LABEL_DIVISION)
        text = text[match.end() :]
    text = _extract_see_also(text, converter, index, parsed)
    for _, segment in split_numbered(text) or [(None, text)]:
        tags = list(dict.fromkeys(tidy(tag.group("tag")) for tag in _TAG.finditer(segment)))
        gloss, lang = tidy(_TAG.sub(" ", segment)), GLOSS_LANG_EN
        if wylie_only and converter.available and gloss:
            gloss, lang = _convert_wylie(gloss, converter, parsed)
        if gloss:
            parsed.senses.append(
                ParsedSense(
                    gloss=gloss,
                    gloss_lang=lang,
                    source_id=row.source_id,
                    sense_no=len(parsed.senses) + 1,
                    labels=list(labels),
                    source_tags=[tag for tag in tags if tag],
                )
            )
    if parsed.senses and is_truncated(row.definition):
        parsed.senses[-1].is_truncated = True
    return parsed


def _convert_wylie(text: str, converter: WylieConverter, parsed: ParsedRow) -> tuple[str, str]:
    if all(is_wylie_syllable(token) for token in LATIN_TOKEN.findall(text)):
        conversion = converter.to_unicode(text)
        if conversion is not None and conversion.ok and tibetan_ratio(conversion.text) >= WYLIE_MIN_TIBETAN_RATIO:
            return tidy(conversion.text), GLOSS_LANG_BO
    parsed.warnings.append("wylie_conversion_rejected")
    return text, GLOSS_LANG_EN


def _extract_see_also(text: str, converter: WylieConverter, index: HeadwordIndex, parsed: ParsedRow) -> str:
    """Cut each `SA …` list out of the text, adding its targets to `parsed.related`."""
    pieces: list[str] = []
    position = 0
    for marker in _SEE_ALSO.finditer(text):
        if marker.start() < position:
            continue
        end = _SEE_ALSO_END.search(text, marker.end())
        span = text[marker.end() : end.start() if end else len(text)]
        consumed = _see_also_targets(span, converter, index, parsed)
        if not consumed:
            continue
        pieces.append(text[position : marker.start()])
        position = marker.end() + consumed
    pieces.append(text[position:])
    return " ".join(pieces)


def _see_also_targets(span: str, converter: WylieConverter, index: HeadwordIndex, parsed: ParsedRow) -> int:
    """Add the targets listed in `span` and return how many characters of it they took.

    The first item is always a target: the longest Wylie prefix that is a headword and ends the
    Wylie run, else the whole Wylie run up to the first English word, unresolved. Each later item is
    a target only if it is a headword as a whole or is only Wylie syllables (then unresolved); the
    first one that is neither ends the list (the rest is gloss).
    """
    offset = 0
    consumed = 0
    for position, item in enumerate(span.split(",")):
        tokens = list(_TOKEN.finditer(item))
        if not tokens:
            break
        if position == 0:
            target = _first_target(tokens, converter, index)
        else:
            target = _whole_item_target(tokens, converter, index) or _wylie_item_target(tokens, converter)
            if target is None:
                break
        candidate, end = target
        parsed.related.append(candidate)
        if candidate.target_entry_id is None:
            parsed.warnings.append("sa_unresolved")
        consumed = offset + end
        offset += len(item) + 1
    return consumed


def _first_target(
    tokens: list[re.Match[str]], converter: WylieConverter, index: HeadwordIndex
) -> tuple[RelatedCandidate, int]:
    if not converter.available:
        wylie = _wylie(tokens)
        return RelatedCandidate(wylie, RELATION_SEE_ALSO, wylie), tokens[-1].end()
    for count in range(min(len(tokens), MAX_SEE_ALSO_TOKENS), 0, -1):
        if count < len(tokens) and _continues_run(tokens, count):
            continue  # "SA kha pags" names ཁ་ལྤགས་: a prefix may not cut the Wylie run ("kha" + "pags")
        if (found := _resolve(tokens[:count], converter, index)) is not None:
            return found
    count = 1
    while count < len(tokens) and _continues_run(tokens, count):
        count += 1
    wylie = _wylie(tokens[:count])
    return RelatedCandidate(_unicode_headword(wylie, converter), RELATION_SEE_ALSO, wylie), tokens[count - 1].end()


def _whole_item_target(
    tokens: list[re.Match[str]], converter: WylieConverter, index: HeadwordIndex
) -> tuple[RelatedCandidate, int] | None:
    if not converter.available or len(tokens) > MAX_SEE_ALSO_TOKENS:
        return None
    return _resolve(tokens, converter, index)


def _wylie_item_target(tokens: list[re.Match[str]], converter: WylieConverter) -> tuple[RelatedCandidate, int] | None:
    """A later list item that is only Wylie ("SA kha sten, tshig sten") is a target even if unresolved."""
    if not converter.available or len(tokens) > MAX_SEE_ALSO_TOKENS:
        return None
    words = [token.group() for token in tokens]
    if len(words) > 1 and _final_stopword(tokens, len(tokens) - 1):
        words = words[:-1]  # "gzhan la bsnyon to": the stopword ends the run but does not shape it
    if not has_wylie_term_shape(words):
        return None
    wylie = _wylie(tokens)
    conversion = converter.to_unicode(wylie)
    if conversion is None or not conversion.ok:
        return None
    return RelatedCandidate(_unicode_headword(wylie, converter), RELATION_SEE_ALSO, wylie), tokens[-1].end()


def _resolve(
    tokens: list[re.Match[str]], converter: WylieConverter, index: HeadwordIndex
) -> tuple[RelatedCandidate, int] | None:
    wylie = _wylie(tokens)
    headword = _unicode_headword(wylie, converter)
    entry_id = index.lookup_tibetan(headword) if headword else None
    if entry_id is None:
        return None
    return RelatedCandidate(headword, RELATION_SEE_ALSO, wylie, entry_id), tokens[-1].end()


def _plausible_wylie(token: str) -> bool:
    return token.lower() not in ENGLISH_STOPWORDS and is_wylie_syllable(token)


def _continues_run(tokens: list[re.Match[str]], position: int) -> bool:
    """Whether the token at `position` continues the Wylie run of the tokens before it."""
    return _plausible_wylie(tokens[position].group()) or _final_stopword(tokens, position)


def _final_stopword(tokens: list[re.Match[str]], position: int) -> bool:
    """The item's last token is a stopword that is also a modern Wylie syllable ("to", "the"; not "and")."""
    token = tokens[position].group()
    return (
        position == len(tokens) - 1
        and token.lower() in ENGLISH_STOPWORDS
        and is_wylie_syllable(token)
        and not NOT_MODERN_SPELLING.search(token)
    )


def _wylie(tokens: list[re.Match[str]]) -> str:
    return " ".join(token.group() for token in tokens).rstrip(".:")


def _unicode_headword(wylie: str, converter: WylieConverter) -> str:
    conversion = converter.to_unicode(wylie)
    return normalise_tibetan_headword(conversion.text)[0] if conversion is not None else wylie
