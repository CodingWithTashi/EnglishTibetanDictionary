"""Sense and sub-sense splitting shared by the table parsers (plan §5.3, "Common" grammar).

Each splitter returns `Segment(number, text)` pairs in source order. Text before the first marker is
returned first as `Segment(None, text)` when it is not empty; segment texts are trimmed. Callers pass
text that is already `preprocess`ed, one part-of-speech block at a time.
"""

from __future__ import annotations

import re
from typing import NamedTuple


class Segment(NamedTuple):
    number: int | None
    text: str


# Rule "numbered senses 1. 2. 3.": ASCII digits and a dot (Tibetan numerals are text; a bare "2" as
# in "run a wolf 2 miles" is text). The number is not the tail of a longer number ("1971.") or of a
# dotted number ("1.2."), is not a decimal ("2.5"), and is followed by sense text, after spaces or
# glued to it ("4.དགའ་ཞེན།"), never by closing punctuation ("2.)") or the end of the text.
_SENSE_NUMBER = re.compile(r"(?<![0-9])(?<![0-9]\.)(?P<number>[0-9]{1,2})\.(?![0-9])(?=\s*[^\s.,;:)\]])")

# Rule "sub-senses ཀཿ ཁཿ གཿ ངཿ ཅཿ → sub_no 1–5": the letter plus visarga starts a token, so it does not
# follow a Tibetan letter, vowel sign, subjoined letter or tsheg ("ཧེརུཀཿ" and "དུ་ཀཿ ཐོག" are words).
# A stray tsheg right after the marker ("ཀཿ་ཟས") belongs to the marker.
_SUBSENSE_LETTER = re.compile(r"(?<![\u0f0b\u0f40-\u0fbc])(?P<letter>[ཀཁགངཅ])\u0f7f\u0f0b?")
_SUB_NUMBER_BY_LETTER = {"ཀ": 1, "ཁ": 2, "ག": 3, "ང": 4, "ཅ": 5}

# Rule "sub-senses 1> 2> → sub_no": the number is not the tail of a longer number; a stray full stop
# after the marker ("2>.") belongs to the marker.
_SUBSENSE_GT = re.compile(r"(?<![0-9])(?P<number>[0-9]{1,2})>\.?")


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


def split_subsenses_bo(text: str) -> list[Segment]:
    """Split Tibetan-letter sub-senses `ཀཿ … ཁཿ …` into sub numbers 1–5."""
    markers = [(match, _SUB_NUMBER_BY_LETTER[match.group("letter")]) for match in _SUBSENSE_LETTER.finditer(text)]
    return _segments(text, markers)


def split_subsenses_gt(text: str) -> list[Segment]:
    """Split numbered sub-senses `1> … 2> …` into their numbers."""
    markers = [(match, int(match.group("number"))) for match in _SUBSENSE_GT.finditer(text)]
    return _segments(text, markers)


def _segments(text: str, markers: list[tuple[re.Match[str], int]]) -> list[Segment]:
    """Cut `text` at the markers: the leading text (if any), then each marker's number and text."""
    if not markers:
        stripped = text.strip()
        return [Segment(None, stripped)] if stripped else []
    segments: list[Segment] = []
    leading = text[: markers[0][0].start()].strip()
    if leading:
        segments.append(Segment(None, leading))
    ends = [match.start() for match, _ in markers[1:]] + [len(text)]
    for (match, number), end in zip(markers, ends):
        segments.append(Segment(number, text[match.end() : end].strip()))
    return segments
