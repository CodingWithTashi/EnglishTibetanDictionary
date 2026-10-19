"""Wylie conversion: pyewts when installed, a null converter otherwise (plan §5.3)."""

from __future__ import annotations

import importlib.util
import sys

import pytest

from dictbuild.text import normalise_tibetan_headword
from dictbuild.wylie import (
    Conversion,
    NullConverter,
    PyewtsConverter,
    WylieUnavailableError,
    load_converter,
)

requires_pyewts = pytest.mark.skipif(importlib.util.find_spec("pyewts") is None, reason="pyewts not installed")


@requires_pyewts
def test_pyewts_round_trip() -> None:
    converter = PyewtsConverter()

    assert converter.to_wylie("བཀྲ་ཤིས་") == "bkra shis"
    assert converter.to_unicode("bkra shis") == Conversion(text="བཀྲ་ཤིས", warnings=())
    for headword in ("བཀྲ་ཤིས་", "ཀ་ཀ་ནི་", "པདྨ་", "ཧཾ་", "ཀུང་ཧྲེ་"):
        back = converter.to_unicode(converter.to_wylie(headword))
        assert back.ok, back.warnings
        assert normalise_tibetan_headword(back.text) == (headword, None)


@requires_pyewts
def test_pyewts_converts_shad_and_reports_warnings() -> None:
    converter = PyewtsConverter()

    assert converter.to_unicode("bkra shis/") == Conversion(text="བཀྲ་ཤིས།", warnings=())
    garbled = converter.to_unicode("xyz qq")
    assert not garbled.ok
    assert garbled.warnings


@requires_pyewts
def test_load_converter_uses_pyewts_when_installed() -> None:
    converter = load_converter(require=True)

    assert isinstance(converter, PyewtsConverter)
    assert converter.available
    assert converter.name.startswith("pyewts ")


def test_null_converter_converts_nothing() -> None:
    converter = NullConverter()

    assert converter.name == "none"
    assert not converter.available
    assert converter.to_wylie("བཀྲ་ཤིས་") is None
    assert converter.to_unicode("bkra shis") is None


def test_load_converter_without_pyewts(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setitem(sys.modules, "pyewts", None)  # makes `import pyewts` fail

    assert isinstance(load_converter(require=False), NullConverter)
    with pytest.raises(WylieUnavailableError):
        load_converter(require=True)
