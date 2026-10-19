"""Text normalisation shared by every source table (plan §5.3, "Common" grammar)."""

from __future__ import annotations

import re

TSHEG = "\u0f0b"
NON_BREAKING_TSHEG = "\u0f0c"
SHAD = "\u0f0d"

TIBETAN_BLOCK_START = "\u0f00"
TIBETAN_BLOCK_END = "\u0fff"

# Rule "Common: NBSP→space, drop U+200B/U+FEFF, U+0F0C→U+0F0B".
_CHARACTER_FIXES = str.maketrans({"\u00a0": " ", "\u200b": None, "\ufeff": None, NON_BREAKING_TSHEG: TSHEG})

# Rule "Common: collapse whitespace".
_WHITESPACE_RUN = re.compile(r"\s+")

# Rule "tben: headword suffix ` ༡` / `\u0f0c༡` / `1` / `༠` → strip + group": a run of ASCII or Tibetan digits
# at the end, optionally after spaces, following a word that does not itself end in a digit.
_TIBETAN_HOMONYM_SUFFIX = re.compile(r"^(?P<word>.*[^\s0-9\u0f20-\u0f29])\s*(?P<marker>[0-9\u0f20-\u0f29]+)$")

# Rule "Tibetan headwords end with a tsheg": trailing tsheg, shad (། ༎) and spaces are replaced by one tsheg.
_TIBETAN_HEADWORD_END = re.compile(r"[\s\u0f0b\u0f0d\u0f0e]+$")

# Rule "entotb: headword homonym digit `lead1` → strip": one digit glued to a final letter, even a
# single one ("I1" is homonym 1 of the letter "i"). Other trailing numbers ("calender 1",
# "housing 1.", "vide page 20") stay part of the headword. Headwords in ENGLISH_HOMONYM_EXCEPTIONS
# end in a digit that is part of the name, so they keep it. With this rule en-bo has 35,888 entries.
_ENGLISH_HOMONYM_SUFFIX = re.compile(r"^(?P<word>.*[^\W\d_])(?P<digit>[0-9])$")

# Controller ruling (T1.2 review): headwords whose final digit is not a homonym marker.
# "K2" is the mountain, not homonym 2 of the letter "k".
ENGLISH_HOMONYM_EXCEPTIONS: frozenset[str] = frozenset({"K2"})

# Rule "Tibetan syllables are delimited by tsheg, shad marks or spaces".
_SYLLABLE_DELIMITERS = re.compile(r"[\s\u0f0b\u0f0c\u0f0d-\u0f12\u0f14]+")

# Rule "tben: is_truncated = len ≥ 250 and not ending in .!?)]།"".
_COMPLETE_ENDING = re.compile(r'[.!?)\]\u0f0d"]$')
TRUNCATION_MIN_LENGTH = 250


def preprocess(text: str) -> str:
    """NBSP→space, drop U+200B/U+FEFF, U+0F0C→U+0F0B, collapse whitespace runs to one space, trim."""
    return _WHITESPACE_RUN.sub(" ", text.translate(_CHARACTER_FIXES)).strip()


def strip_csv_quotes(text: str) -> str:
    """Remove the CSV quoting the source kept: `"…"` (or a lone leading `"`) with `""` escapes inside."""
    stripped = text.strip()
    if not stripped.startswith('"'):
        return stripped
    inner = stripped[1:]
    if inner.endswith('"'):
        inner = inner[:-1]
    return inner.replace('""', '"')


def clean_definition(raw: str) -> str:
    """A source definition as displayable text: CSV quotes removed, then `preprocess`ed."""
    return preprocess(strip_csv_quotes(raw))


def is_tibetan(ch: str) -> bool:
    """Whether the single character `ch` is in the Tibetan block (U+0F00–U+0FFF)."""
    return TIBETAN_BLOCK_START <= ch <= TIBETAN_BLOCK_END


def tibetan_ratio(text: str) -> float:
    """Share of Tibetan characters among the non-whitespace characters of `text` (0.0 when there are none)."""
    visible = [ch for ch in text if not ch.isspace()]
    if not visible:
        return 0.0
    return sum(1 for ch in visible if is_tibetan(ch)) / len(visible)


def syllables(text: str) -> list[str]:
    """Tibetan syllables of `text`: the pieces between tsheg, shad marks and spaces."""
    return [piece for piece in _SYLLABLE_DELIMITERS.split(text) if piece]


def normalise_tibetan_headword(raw: str) -> tuple[str, str | None]:
    """Return (headword ending in one tsheg, homonym marker or None).

    `"ཀ་དག་ ༡"` → `("ཀ་དག་", "༡")`, `"མངོན་དུ་ཕྱོགས་པ་\u0f0c༡"` → `("མངོན་དུ་ཕྱོགས་པ་", "༡")`,
    `"གཞའ།"` → `("གཞའ་", None)`. The marker is returned as written (`"༡"`, `"༡༡༡"`, `"1"`, `"༠"`).
    """
    text = preprocess(raw)
    marker: str | None = None
    if match := _TIBETAN_HOMONYM_SUFFIX.match(text):
        text, marker = match.group("word"), match.group("marker")
    text = _TIBETAN_HEADWORD_END.sub("", text)
    return (text + TSHEG if text else ""), marker


def normalise_english_headword(raw: str) -> tuple[str, str | None, str]:
    """Return (display headword, homonym digit or None, lower-cased group key).

    `"lead1"` → `("lead", "1", "lead")`, `"Adam &  Eve"` → `("Adam & Eve", None, "adam & eve")`,
    `"K2"` → `("K2", None, "k2")` (an `ENGLISH_HOMONYM_EXCEPTIONS` headword).
    """
    text = preprocess(raw)
    digit: str | None = None
    if text not in ENGLISH_HOMONYM_EXCEPTIONS and (match := _ENGLISH_HOMONYM_SUFFIX.match(text)):
        text, digit = match.group("word"), match.group("digit")
    return text, digit, text.lower()


def is_truncated(raw: str) -> bool:
    """Whether a source definition was cut off by the source's field limit.

    `raw` is the definition as stored (CSV quotes included): it is truncated when it is at least
    250 characters long and its cleaned text does not end in `. ! ? ) ] ། "`.
    """
    return len(raw) >= TRUNCATION_MIN_LENGTH and not _COMPLETE_ENDING.search(clean_definition(raw))
