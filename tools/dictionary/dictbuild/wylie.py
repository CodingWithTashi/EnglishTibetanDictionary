"""Tibetan ↔ Wylie conversion behind a small protocol (plan §5.3).

`PyewtsConverter` wraps pyewts when it is installed. `NullConverter` converts nothing, so a build
without pyewts still succeeds with `entries.wylie` NULL, unless `--require-wylie` asks
`load_converter(require=True)` to fail instead.
"""

from __future__ import annotations

import importlib.metadata
from dataclasses import dataclass
from typing import Protocol


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
        self._name = f"pyewts {importlib.metadata.version('pyewts')}"

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
