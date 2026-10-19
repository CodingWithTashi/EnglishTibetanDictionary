"""Text normalisation shared by every source table (plan §5.3 "Common" grammar)."""

from __future__ import annotations

import pytest

from dictbuild.text import (
    is_tibetan,
    is_truncated,
    normalise_english_headword,
    normalise_tibetan_headword,
    preprocess,
    strip_csv_quotes,
    syllables,
    tibetan_ratio,
)


def _text_of_length(length: int, ending: str) -> str:
    """Filler prose of exactly `length` characters that ends with `ending`."""
    filler = ("seed of abrus precatorius used as a weight in medicine, " * 10)[: length - len(ending)]
    text = filler + ending
    assert len(text) == length
    return text


def test_preprocess_normalises_spaces_marks_and_tsheg() -> None:
    raw = "\u00a0ཀ\u200b་ཁ\u0f0c\ufeff  good\t\n fortune  "

    assert preprocess(raw) == "ཀ་ཁ་ good fortune"


def test_preprocess_maps_non_breaking_tsheg_to_tsheg() -> None:
    assert preprocess("ཀ\u0f0cཁ\u0f0c") == "ཀ\u0f0bཁ\u0f0b"


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ('"a"', "a"),
        ('"one, pillar, all, the very"', "one, pillar, all, the very"),
        ('"leading quote only', "leading quote only"),
        ('"on, upon, ""about to"" clause connective"', 'on, upon, "about to" clause connective'),
        ('"delimited by the term ""only"""', 'delimited by the term "only"'),
        ('  "padded"  ', "padded"),
        ("not quoted", "not quoted"),
        ("", ""),
    ],
)
def test_strip_csv_quotes(raw: str, expected: str) -> None:
    assert strip_csv_quotes(raw) == expected


@pytest.mark.parametrize(
    ("raw", "headword", "marker"),
    [
        ("ཀ་དག་ ༡", "ཀ་དག་", "༡"),
        ("ཀ་བ་ ༢", "ཀ་བ་", "༢"),
        ("མངོན་དུ་ཕྱོགས་པ་\u0f0c༡", "མངོན་དུ་ཕྱོགས་པ་", "༡"),
        ("འཇིག་རྟེན་པ་ ༡༡༡", "འཇིག་རྟེན་པ་", "༡༡༡"),
        ("དབང་པོ་ལྔ་1", "དབང་པོ་ལྔ་", "1"),
        ("སྙན་ཐོས་ཀྱི་མཐོང་ལམ་རྣམ་གྲོལ་ལམ་༠", "སྙན་ཐོས་ཀྱི་མཐོང་ལམ་རྣམ་གྲོལ་ལམ་", "༠"),
        ("ཀུང་ཧྲེ", "ཀུང་ཧྲེ་", None),
        ("གཞའ།", "གཞའ་", None),
        ("བཀྲ་ཤིས་", "བཀྲ་ཤིས་", None),
        ("ཀོ་ཥ་ ", "ཀོ་ཥ་", None),
    ],
)
def test_normalise_tibetan_headword(raw: str, headword: str, marker: str | None) -> None:
    assert normalise_tibetan_headword(raw) == (headword, marker)


@pytest.mark.parametrize(
    ("raw", "display", "digit", "group_key"),
    [
        ("lead1", "lead", "1", "lead"),
        ("lead2", "lead", "2", "lead"),
        ("Adam &  Eve", "Adam & Eve", None, "adam & eve"),
        ("bright’s disease1", "bright’s disease", "1", "bright’s disease"),
        ("May", "May", None, "may"),
        # Only a digit glued to the final letter is a homonym marker.
        ("calender  1", "calender 1", None, "calender 1"),
        ("housing 1.", "housing 1.", None, "housing 1."),
        ("vide page 20", "vide page 20", None, "vide page 20"),
    ],
)
def test_normalise_english_headword(raw: str, display: str, digit: str | None, group_key: str) -> None:
    assert normalise_english_headword(raw) == (display, digit, group_key)


@pytest.mark.parametrize("quoted", [True, False])
def test_is_truncated_long_text_cut_mid_word(quoted: bool) -> None:
    text = _text_of_length(255 if quoted else 257, "to perv")
    raw = f'"{text}"' if quoted else text
    assert len(raw) == 257

    assert is_truncated(raw)


@pytest.mark.parametrize("quoted", [True, False])
def test_is_truncated_long_text_ending_with_full_stop(quoted: bool) -> None:
    text = _text_of_length(255 if quoted else 257, ".")
    raw = f'"{text}"' if quoted else text
    assert len(raw) == 257

    assert not is_truncated(raw)


def test_is_truncated_short_text_is_complete() -> None:
    raw = _text_of_length(100, "used as ")

    assert not is_truncated(raw)


@pytest.mark.parametrize("ending", ["!", "?", ")", "]", "།", '"'])
def test_is_truncated_complete_endings(ending: str) -> None:
    assert not is_truncated(_text_of_length(257, ending))


def test_is_truncated_threshold_is_250_characters() -> None:
    assert not is_truncated(_text_of_length(249, "to perv"))
    assert is_truncated(_text_of_length(250, "to perv"))


def test_is_tibetan() -> None:
    assert is_tibetan("ཀ")
    assert is_tibetan("་")
    assert not is_tibetan("k")
    assert not is_tibetan(" ")


def test_tibetan_ratio_counts_non_space_characters() -> None:
    assert tibetan_ratio("བཀྲ་ཤིས།") == 1.0
    assert tibetan_ratio("ཀཁ ab") == 0.5
    assert tibetan_ratio("bkra shis") == 0.0
    assert tibetan_ratio("  ") == 0.0


def test_syllables_split_on_tsheg_shad_and_space() -> None:
    assert syllables("བཀྲ་ཤིས་") == ["བཀྲ", "ཤིས"]
    assert syllables("བཀྲ་ཤིས་བདེ་ལེགས། ཞུ") == ["བཀྲ", "ཤིས", "བདེ", "ལེགས", "ཞུ"]
    assert syllables("") == []
