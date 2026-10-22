"""Grammar shared by the table parsers (plan §5.3, "Common" grammar).

Each splitter returns `Segment(number, text)` pairs in source order. Text before the first marker is
returned first as `Segment(None, text)`; segment texts are trimmed and empty segments are dropped, so
no sense starts out empty. Callers pass text that is already `preprocess`ed, one part-of-speech
block at a time.
"""

from __future__ import annotations

import re
from typing import NamedTuple

from dictbuild.text import is_tibetan, syllables


class Segment(NamedTuple):
    number: int | None
    text: str


# Rule "numbered senses 1. 2. 3.": ASCII digits and a dot (Tibetan numerals are text; a bare "2" as
# in "run a wolf 2 miles" is text). The number is not the tail of a longer number ("1971.") or of a
# dotted number ("1.2."), is not a decimal ("2.5"), and is followed by sense text, after spaces or
# glued to it ("4.དགའ་ཞེན།"), never by closing punctuation ("2.)") or the end of the text.
# Rule "a number glued to a Latin letter is text": "the few1." and "hydro-electric1." (entotb rows
# 13406 and 16849) number homonyms inside an English phrase; they are not sense markers.
_SENSE_NUMBER = re.compile(
    r"(?<![0-9])(?<![0-9]\.)(?<![A-Za-z])(?P<number>[0-9]{1,2})\.(?![0-9])(?=\s*[^\s.,;:)\]])"
)

# Rule "numbered senses ༡ ༢ ༣ (Tibetan digits)": a marker is one or two Tibetan digits that start a token
# (the text start or whitespace before), are not the head of a longer digit run, and are followed by
# whitespace and sense text or directly by Tibetan text. A digit inside a word is text.
_TIBETAN_DIGIT_VALUES = str.maketrans("༠༡༢༣༤༥༦༧༨༩", "0123456789")
_TIBETAN_SENSE_NUMBER = re.compile(
    r"(?<!\S)(?P<number>[༠-༩]{1,2})(?![༠-༩])(?=\s+\S|[\u0f00-\u0fff])"
)

# Rule "sub-senses ཀཿ ཁཿ གཿ ངཿ ཅཿ → sub_no 1–5": the letter plus visarga starts a token, so it does not
# follow a Tibetan letter, vowel sign, subjoined letter or tsheg ("ཧེརུཀཿ" and "དུ་ཀཿ ཐོག" are words).
# A stray tsheg right after the marker ("ཀཿ་ཟས") belongs to the marker.
_SUBSENSE_LETTER = re.compile(r"(?<![\u0f0b\u0f40-\u0fbc])(?P<letter>[ཀཁགངཅ])\u0f7f\u0f0b?")
_SUB_NUMBER_BY_LETTER = {"ཀ": 1, "ཁ": 2, "ག": 3, "ང": 4, "ཅ": 5}

# Rule "sub-senses 1> 2> → sub_no": the number is not the tail of a longer number; a stray full stop
# after the marker ("2>.") belongs to the marker.
_SUBSENSE_GT = re.compile(r"(?<![0-9])(?P<number>[0-9]{1,2})>\.?")

# Rule "gloss tidy-up": once a marker, tag or link is cut out of a gloss, empty brackets, spaces
# before separators, repeated commas and separators dangling at either end are removed.
_EMPTY_BRACKETS = re.compile(r"\(\s*\)|\[\s*\]")
_WHITESPACE_RUN = re.compile(r"\s+")
_SPACE_BEFORE_SEPARATOR = re.compile(r"\s+(?=[,;:])")
_REPEATED_COMMA = re.compile(r",(?:\s*,)+")
_DANGLING_SEPARATORS = " ,;:"

# Rule "a Latin word followed by a binding particle is part of the Tibetan sentence": "X ཡང་ཟེར།"
# (also called X), "X ལ་ལྟོས།" (see X), "X ཞེས་པ" (the word X), "X གི་འདས་ཚིག" (past tense of X).
# Only syllables that never start a word count on their own; particles that also open common words
# (ཡི as in ཡི་གེ, ལ as in ལ་ཉག, ལས as in ལས་ཀ, དང as in དང་པོ, ཟེར as in ཟེར་ཐིག) count only inside a
# listed phrase.
_BOUND_PARTICLES = frozenset(
    {"གི", "ཀྱི", "གྱི", "འི", "གིས", "ཀྱིས", "གྱིས", "ཡིས", "ཞེས", "ཅེས", "སོགས", "བཅས", "ལའང", "ལྟར"}
)
_PARTICLE_PHRASES = (
    "ལ་ལྟོས", "ཡང་ཟེར", "ཟེར་བ", "ཟེར།", "ཁོངས་སུ་ལྟོས", "ཡི་འདས་ཚིག", "ཡི་བསྡུས", "ཡི་མང་ཚིག", "ལ་ཟེར"
)  # fmt: skip


def split_numbered(text: str) -> list[Segment] | None:
    """Split numbered senses `1. … 2. … 3. …`, or return None when the text has no such sequence.

    Markers count only as the sequence 1, 2, 3, … in order; any other number stays in the text
    of the sense it appears in. Text before `1.` comes first as `Segment(None, …)`.
    """
    markers: list[tuple[re.Match[str], int]] = []
    for match in _SENSE_NUMBER.finditer(text):
        number = int(match.group("number"))
        if number == len(markers) + 1:
            markers.append((match, number))
    if not markers:
        return None
    return _segments(text, markers)


def split_numbered_tibetan(text: str) -> list[Segment] | None:
    """Split senses numbered with Tibetan digits `༡ … ༢ … ༣ …`, or return None without a `༡` marker.

    Like `split_numbered`: markers count only as the sequence ༡, ༢, ༣, … (up to ༡༠ and beyond) in
    order, any other Tibetan digit stays in the text, and text before `༡` comes first as `Segment(None, …)`.
    """
    markers: list[tuple[re.Match[str], int]] = []
    for match in _TIBETAN_SENSE_NUMBER.finditer(text):
        number = int(match.group("number").translate(_TIBETAN_DIGIT_VALUES))
        if number == len(markers) + 1:
            markers.append((match, number))
    if not markers:
        return None
    return _segments(text, markers)


def split_subsenses_bo(text: str) -> list[Segment]:
    """Split Tibetan-letter sub-senses `ཀཿ … ཁཿ …` into sub numbers 1–5."""
    markers = [(match, _SUB_NUMBER_BY_LETTER[match.group("letter")]) for match in _SUBSENSE_LETTER.finditer(text)]
    return _segments(text, markers)


def split_subsenses_gt(text: str) -> list[Segment]:
    """Split numbered sub-senses `1> … 2> …` into their numbers."""
    markers = [(match, int(match.group("number"))) for match in _SUBSENSE_GT.finditer(text)]
    return _segments(text, markers)


def split_subsenses(text: str) -> list[Segment]:
    """Sub-senses of one sense: Tibetan-letter markers when there are any, otherwise `1>` markers."""
    segments = split_subsenses_bo(text)
    if any(segment.number is not None for segment in segments):
        return segments
    return split_subsenses_gt(text)


def tidy(text: str) -> str:
    """Collapse whitespace and drop the separators left dangling after text was cut out of a gloss."""
    text = _EMPTY_BRACKETS.sub("", text)
    text = _WHITESPACE_RUN.sub(" ", text)
    text = _SPACE_BEFORE_SEPARATOR.sub("", text)
    text = _REPEATED_COMMA.sub(",", text)
    return text.strip(_DANGLING_SEPARATORS)


def starts_with_particle(text: str) -> bool:
    """Whether `text` (after spaces) opens with a Tibetan particle that binds the preceding word."""
    text = text.lstrip()
    if text.startswith(_PARTICLE_PHRASES):
        return True
    first = syllables(text[:12])
    return bool(first) and first[0] in _BOUND_PARTICLES


def has_tibetan(text: str) -> bool:
    """Whether any character of `text` is in the Tibetan block."""
    return any(is_tibetan(ch) for ch in text)


def _segments(text: str, markers: list[tuple[re.Match[str], int]]) -> list[Segment]:
    """Cut `text` at the markers: the leading text (if any), then each non-empty marker's text."""
    if not markers:
        stripped = text.strip()
        return [Segment(None, stripped)] if stripped else []
    segments: list[Segment] = []
    leading = text[: markers[0][0].start()].strip()
    if leading:
        segments.append(Segment(None, leading))
    ends = [match.start() for match, _ in markers[1:]] + [len(text)]
    for (match, number), end in zip(markers, ends):
        body = text[match.end() : end].strip()
        if body:
            segments.append(Segment(number, body))
    return segments
