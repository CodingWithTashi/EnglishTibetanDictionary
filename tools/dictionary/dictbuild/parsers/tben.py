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
from dictbuild.wylie import WylieConverter

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
WYLIE_MIN_TIBETAN_RATIO = 0.8
# pyewts 0.2.0 converts most English without a warning ("hard to understand/penetrate"), so every
# Latin token must also be a well-formed Wylie syllable: [prefix][superscript]root[subscript]vowel
# [suffix][post-suffix][a particle such as 'i]. English words almost never all pass.
_WYLIE_SYLLABLE = re.compile(
    r"(?:g\.|[gdbm'])?[rls]?"
    r"(?:tsh|ts|dz|kh|ng|ch|ny|th|ph|zh|sh|Th|Dh|Sh|[kgcjtdnpbmwzy'rlshTDN])?[yrlw]?"
    r"(?:ai|au|-i|-I|[aiueoAIU])(?:ng|[gdnbm'rls])?[sd]?(?:'(?:i|o|u|am|ang|is|os|us|ung))?"
)
_LATIN_TOKEN = re.compile(r"[A-Za-z'.+~-]+")

# Rule "`SA a, b, c` → see-also candidates": "SA" as a word at the start, after a space, comma,
# semicolon or opening bracket. Its list ends at ";", ")", "{" or the next "SA".
_SEE_ALSO = re.compile(r"(?<![^\s,;(])SA\s+")
_SEE_ALSO_END = re.compile(r"[;){]|(?<![^\s,;(])SA\s")
_TOKEN = re.compile(r"\S+")
MAX_SEE_ALSO_TOKENS = 12
# An unresolved target stops before the first English stopword or non-Wylie token.
_ENGLISH_STOPWORDS = frozenset(
    {"a", "also", "an", "and", "as", "by", "etc", "for", "from", "in", "is", "of", "on", "or", "see", "that", "the",
     "to", "which", "with"}
)  # fmt: skip


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


def is_wylie_syllable(token: str) -> bool:
    """Whether a Latin token is one well-formed Wylie syllable ("bsgrubs", "pa'i", "g.yu")."""
    return _WYLIE_SYLLABLE.fullmatch(token.strip(".")) is not None


def _convert_wylie(text: str, converter: WylieConverter, parsed: ParsedRow) -> tuple[str, str]:
    if all(is_wylie_syllable(token) for token in _LATIN_TOKEN.findall(text)):
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

    The first item is always a target: the longest Wylie prefix that is a headword, else the
    Wylie up to the first English word. Each later item is a target only if it is a headword as a
    whole; the first one that is not ends the list (the rest is gloss).
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
            target = _whole_item_target(tokens, converter, index)
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
        if (found := _resolve(tokens[:count], converter, index)) is not None:
            return found
    count = 1
    while count < len(tokens) and _plausible_wylie(tokens[count].group()):
        count += 1
    wylie = _wylie(tokens[:count])
    return RelatedCandidate(_unicode_headword(wylie, converter), RELATION_SEE_ALSO, wylie), tokens[count - 1].end()


def _whole_item_target(
    tokens: list[re.Match[str]], converter: WylieConverter, index: HeadwordIndex
) -> tuple[RelatedCandidate, int] | None:
    if not converter.available or len(tokens) > MAX_SEE_ALSO_TOKENS:
        return None
    return _resolve(tokens, converter, index)


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
    return token.lower() not in _ENGLISH_STOPWORDS and is_wylie_syllable(token)


def _wylie(tokens: list[re.Match[str]]) -> str:
    return " ".join(token.group() for token in tokens).rstrip(".:")


def _unicode_headword(wylie: str, converter: WylieConverter) -> str:
    conversion = converter.to_unicode(wylie)
    return normalise_tibetan_headword(conversion.text)[0] if conversion is not None else wylie
