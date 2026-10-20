"""Tibetan ↔ Wylie conversion behind a small protocol (plan §5.3).

`PyewtsConverter` wraps pyewts when it is installed. `NullConverter` converts nothing, so a build
without pyewts still succeeds with `entries.wylie` NULL, unless `--require-wylie` asks
`load_converter(require=True)` to fail instead.
"""

from __future__ import annotations

import importlib.metadata
import re
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Protocol

# A well-formed Wylie syllable: [prefix][superscript]root[subscript]vowel[suffix][post-suffix][a
# particle such as 'i] ("bsgrubs", "pa'i", "g.yu"). pyewts 0.2.0 converts most English without a
# warning ("hard to understand/penetrate"), so callers check every Latin token against this first.
_WYLIE_SYLLABLE = re.compile(
    r"(?:g\.|[gdbm'])?[rls]?"
    r"(?:tsh|ts|dz|kh|ng|ch|ny|th|ph|zh|sh|Th|Dh|Sh|[kgcjtdnpbmwzy'rlshTDN])?[yrlw]?"
    r"(?:ai|au|-i|-I|[aiueoAIU])(?:ng|[gdnbm'rls])?[sd]?(?:'(?:i|o|u|am|ang|is|os|us|ung))?"
)
LATIN_TOKEN = re.compile(r"[A-Za-z'.+~-]+")


def is_wylie_syllable(token: str) -> bool:
    """Whether a Latin token is one well-formed Wylie syllable ("bsgrubs", "pa'i", "g.yu")."""
    return _WYLIE_SYLLABLE.fullmatch(token.strip(".")) is not None


def is_wylie_text(text: str) -> bool:
    """Whether `text` has Latin tokens and every one of them is a Wylie syllable ("pags", "bzo ba")."""
    tokens = LATIN_TOKEN.findall(text)
    return bool(tokens) and all(is_wylie_syllable(token) for token in tokens)


# English function words; several are valid Wylie syllables too ("to", "the", "in").
ENGLISH_STOPWORDS = frozenset(
    {"a", "also", "an", "and", "as", "by", "etc", "for", "from", "in", "is", "of", "on", "or", "see", "that", "the",
     "to", "which", "with"}
)  # fmt: skip
# Short English words are often valid Wylie ("cold", "spin", "old man"), so a Tibetan term written in
# Wylie needs more than well-formed syllables: no "ai"/"au" vowel or da drag (not modern spelling: "gain",
# "cold", "bend"), and two syllables, an apostrophe or an onset English never has ("mgo", "rta", "lha").
NOT_MODERN_SPELLING = re.compile(r"ai|au|[nrl]d\.?$")
_TIBETAN_ONSET = re.compile(r"g\.|[gdbm](?![aeiouAEIOU.yrlw])|[rl][kgcjtdnpbmz]|lh")


def has_wylie_term_shape(words: Sequence[str]) -> bool:
    """Whether `words` are shaped like a Tibetan term in Wylie ("kha sten", "mgo", "ra'i") rather than
    English ("cold", "send", "old man"); see `is_wylie_term` for the conversion check."""
    if not words or not all(
        word.lower() not in ENGLISH_STOPWORDS and is_wylie_syllable(word) and not NOT_MODERN_SPELLING.search(word)
        for word in words
    ):
        return False
    return len(words) > 1 or "'" in words[0] or _TIBETAN_ONSET.match(words[0]) is not None


def is_wylie_term(text: str, converter: WylieConverter) -> bool:
    """Whether `text` is a Tibetan term in Wylie ("rdo rje", "dkyil 'khor"): the shape of
    `has_wylie_term_shape` and a conversion without warnings. False without a converter."""
    words = text.split()
    if not converter.available or not has_wylie_term_shape(words):
        return False
    conversion = converter.to_unicode(" ".join(words))
    return conversion is not None and conversion.ok


class WylieUnavailableError(RuntimeError):
    """Wylie conversion was required but pyewts cannot be imported."""


@dataclass(frozen=True, slots=True)
class Conversion:
    """Wylie → Unicode result; `warnings` are pyewts' parse warnings (empty when the input was clean)."""

    text: str
    warnings: tuple[str, ...]

    @property
    def ok(self) -> bool:
        return not self.warnings


class WylieConverter(Protocol):
    @property
    def name(self) -> str:
        """Recorded in meta `wylie_converter`, e.g. "pyewts 0.2.0" or "none"."""
        ...

    @property
    def available(self) -> bool: ...

    def to_wylie(self, tibetan: str) -> str | None: ...

    def to_unicode(self, wylie: str) -> Conversion | None: ...


class NullConverter:
    """Used when pyewts is missing: every conversion yields None."""

    @property
    def name(self) -> str:
        return "none"

    @property
    def available(self) -> bool:
        return False

    def to_wylie(self, tibetan: str) -> str | None:
        return None

    def to_unicode(self, wylie: str) -> Conversion | None:
        return None


class PyewtsConverter:
    """EWTS conversion via pyewts. Raises ImportError when pyewts is not installed."""

    def __init__(self) -> None:
        import pyewts

        self._impl = pyewts.pyewts()
        self._name = _name_with_version("pyewts")

    @property
    def name(self) -> str:
        return self._name

    @property
    def available(self) -> bool:
        return True

    def to_wylie(self, tibetan: str) -> str | None:
        """`"བཀྲ་ཤིས་"` → `"bkra shis"` (the trailing tsheg's space is trimmed)."""
        return self._impl.toWylie(tibetan).strip() or None

    def to_unicode(self, wylie: str) -> Conversion:
        warnings: list[str] = []
        text = self._impl.toUnicode(wylie, warnings)
        return Conversion(text=text, warnings=tuple(warnings))


def _name_with_version(distribution: str) -> str:
    """`"pyewts 0.2.0"`, or just `"pyewts"` when the module imports without distribution metadata.

    PackageNotFoundError is an ImportError: left uncaught, `load_converter` would treat a working
    pyewts as missing.
    """
    try:
        return f"{distribution} {importlib.metadata.version(distribution)}"
    except importlib.metadata.PackageNotFoundError:
        return distribution


def load_converter(require: bool) -> WylieConverter:
    """pyewts when importable; otherwise `NullConverter`, or `WylieUnavailableError` when `require`."""
    try:
        return PyewtsConverter()
    except ImportError as error:
        if require:
            raise WylieUnavailableError(
                "pyewts is not installed: run `.venv/bin/python -m pip install -r requirements.txt`"
            ) from error
        return NullConverter()
