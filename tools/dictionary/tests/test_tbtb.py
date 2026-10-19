"""Tibetan → Tibetan parser (plan §5.3 "tbtb" grammar). Inputs are real `tbtb` rows."""

from __future__ import annotations

import importlib.util

import pytest

from dictbuild.model import ParsedExample, ParsedRow, ParsedSense
from dictbuild.parsers.labels_bo import LABELS_BO, lookup_label
from dictbuild.parsers.tbtb import parse_tbtb_row
from dictbuild.source import SourceRow
from dictbuild.wylie import NullConverter, PyewtsConverter, WylieConverter


def parse(source_id: int, word: str, definition: str, converter: WylieConverter | None = None) -> ParsedRow:
    return parse_tbtb_row(SourceRow(source_id, word, definition), converter or NullConverter())


def shape(senses: list[ParsedSense]) -> list[tuple]:
    """(sense_no, sub_no, part_of_speech, labels, gloss) per sense record."""
    return [(s.sense_no, s.sub_no, s.part_of_speech, s.labels, s.gloss) for s in senses]


def test_numbered_with_examples() -> None:
    row = parse(
        24,
        "ཀ་ཅ་",
        "1. ནོར་རྫས་ཀྱི་སྤྱི་མིང་སྟེ། (དཔེར་ན།) ཀ་ཅ་གསོག་འཇོག   ཀ་ཅས་ཚིམ་པར་བྱེད་ཅེས་པ་ལྟ་བུ།  "
        "2. འཚོ་བའི་ཡོ་བྱད་ཀྱི་སྤྱི་མིང་སྟེ། (དཔེར་ན།) སྣོད་ཀ་ཅ་ཞེས་པ་ལྟ་བུ།",
    )

    assert (row.headword, row.group_key) == ("ཀ་ཅ་", "ཀ་ཅ་")
    assert shape(row.senses) == [
        (1, None, None, [], "ནོར་རྫས་ཀྱི་སྤྱི་མིང་སྟེ།"),
        (2, None, None, [], "འཚོ་བའི་ཡོ་བྱད་ཀྱི་སྤྱི་མིང་སྟེ།"),
    ]
    assert [s.examples for s in row.senses] == [
        [ParsedExample("ཀ་ཅ་གསོག་འཇོག ཀ་ཅས་ཚིམ་པར་བྱེད་ཅེས་པ་ལྟ་བུ།", None)],
        [ParsedExample("སྣོད་ཀ་ཅ་ཞེས་པ་ལྟ་བུ།", None)],
    ]
    assert {s.gloss_lang for s in row.senses} == {"bo"}


def test_several_examples_in_one_sense() -> None:
    row = parse(9001, "ཀ་", "ཀ་བ། (དཔེར་ན།) ཁ་བ། (དཔེར་ན) ག་བ།")

    assert row.senses[0].examples == [ParsedExample("ཁ་བ།"), ParsedExample("ག་བ།")]


def test_entry_label_applies_to_all_senses() -> None:
    row = parse(235, "ཀུན་གསལ་", "(མངོན་བརྗོད།) 1. ཉི་མ།  2. ནམ་མཁའ།   3. མེ་ལོང\u0f0c།")

    assert shape(row.senses) == [
        (1, None, None, ["poetic"], "ཉི་མ།"),
        (2, None, None, ["poetic"], "ནམ་མཁའ།"),
        (3, None, None, ["poetic"], "མེ་ལོང་།"),
    ]


def test_entry_label_does_not_override_a_sense_label() -> None:
    row = parse(9002, "ཀ་", "(མངོན་བརྗོད།) 1. ཉི་མ། 2. (བརྡ་རྙིང་།) ནམ་མཁའ།")

    assert [s.labels for s in row.senses] == [["poetic"], ["archaic"]]


def test_sense_level_label_pos() -> None:
    kag = parse(
        111,
        "ཀག་",
        "1. དུས་ངེས་ཅན་གྱི་བར་ཆད་ཀྱི་དོན་དུ་གོ་བའི་སྐག་དང་འདྲ། 2. (བརྡ་རྙིང་།) གློ་བུར་གྱི་དོན། "
        "3. གནོད་འཚེའི་མིང་ལོ་ཏོག་གི་ལོ་ཀག་སེལ་ལྟ་བུ།",
    )
    zhol = parse(
        5105,
        "གཞོལ་བ་",
        "1. (བྱེད་འབྲེལ་ལས་ཚིག) འབད་རྩོལ་གྱི་དོན་དུ་གོ་བའི་གཞོལ་བའི་བྱ་མིང། "
        "2. (བྱེད་མེད་ལས་ཚིག) གཡོལ་བའི་དོན་དུ་གཞོལ་བའི་བྱ་བའི་མིང་།",
    )

    assert shape(kag.senses) == [
        (1, None, None, [], "དུས་ངེས་ཅན་གྱི་བར་ཆད་ཀྱི་དོན་དུ་གོ་བའི་སྐག་དང་འདྲ།"),
        (2, None, None, ["archaic"], "གློ་བུར་གྱི་དོན།"),
        (3, None, None, [], "གནོད་འཚེའི་མིང་ལོ་ཏོག་གི་ལོ་ཀག་སེལ་ལྟ་བུ།"),
    ]
    assert shape(zhol.senses) == [
        (1, None, "vt", [], "འབད་རྩོལ་གྱི་དོན་དུ་གོ་བའི་གཞོལ་བའི་བྱ་མིང།"),
        (2, None, "vi", [], "གཡོལ་བའི་དོན་དུ་གཞོལ་བའི་བྱ་བའི་མིང་།"),
    ]


def test_subsenses_gt() -> None:
    row = parse(
        399,
        "ཀེ་ཏ་ཀ་",
        "(ལེགས་སྦྱར།) 1>ཆུ་རྙོག་དྭངས་བྱེད་ཀྱི་དངོས་པོ་ཞིག་རྡོ་ནོར་བུ་ཀེ་ཏ་ཀ་ཡང་ཟེར།  2>ཤིང་ཀེ་ཏ་ཀའི་འབྲས་བུ།",
    )

    assert shape(row.senses) == [
        (1, 1, None, ["sanskrit"], "ཆུ་རྙོག་དྭངས་བྱེད་ཀྱི་དངོས་པོ་ཞིག་རྡོ་ནོར་བུ་ཀེ་ཏ་ཀ་ཡང་ཟེར།"),
        (1, 2, None, ["sanskrit"], "ཤིང་ཀེ་ཏ་ཀའི་འབྲས་བུ།"),
    ]


def test_label_alias_and_unknown_parenthetical() -> None:
    alias = parse(932, "ཁ་ཆབ་", "(བརྡ་རྙིང།) 1>ཁ་ཆུ།   2>ཆང་སླ་པོ།")
    unknown = parse(
        50601,
        "སྙུན་གདངས་",
        "(སྙུན་དངས།) ནད་དྭངས་ཟིན་པ་སྟེ།  (དཔེར་ན།) སྙུན་གདངས་ཏེ་(དངས་ཏེ)ཕེབས་བཞུགས་གང་བདེ་གནང་ཐུབ་པ་བྱུང་སོང\u0f0cལྟ་བུ།",
    )

    assert shape(alias.senses) == [(1, 1, None, ["archaic"], "ཁ་ཆུ།"), (1, 2, None, ["archaic"], "ཆང་སླ་པོ།")]
    assert alias.unknown_labels == []
    assert shape(unknown.senses) == [(1, None, None, [], "(སྙུན་དངས།) ནད་དྭངས་ཟིན་པ་སྟེ།")]
    assert unknown.unknown_labels == ["སྙུན་དངས།"]
    assert unknown.senses[0].examples == [
        ParsedExample("སྙུན་གདངས་ཏེ་(དངས་ཏེ)ཕེབས་བཞུགས་གང་བདེ་གནང་ཐུབ་པ་བྱུང་སོང་ལྟ་བུ།")
    ]


@pytest.mark.parametrize(
    ("written", "kind", "key"),
    [
        ("བྱེད་འབྲེལ་ལས་ཚིག", "pos", "vt"),
        ("བྱེད་མེད་ལས་ཚིག", "pos", "vi"),
        ("ཚབ་ཚིག", "pos", "pron"),
        ("སྦྲེལ་ཚིག", "pos", "conj"),
        ("རྒྱན་ཚིག", "pos", "adj"),
        ("མངོན་བརྗོད།", "label", "poetic"),
        ("མངོན་བརྗོད", "label", "poetic"),
        ("བརྡ་རྙིང་།", "label", "archaic"),
        ("བརྡརྙིང་།", "label", "archaic"),
        ("ཡུལ་སྐད།", "label", "dialect"),
        ("ལེགས་སྦྱར།", "label", "sanskrit"),
        ("རྒྱ་སྐད།", "label", "chinese"),
        ("སོག་སྐད།", "label", "mongolian"),
        ("བྱེས་འབྲེལ་ལས་ཚིག", "pos", "vt"),
    ],
)
def test_label_whitelist(written: str, kind: str, key: str) -> None:
    label = lookup_label(written)

    assert label is not None
    assert (label.kind, label.key) == (kind, key)


def test_label_whitelist_rejects_other_parentheticals() -> None:
    assert lookup_label("སྙུན་དངས།") is None
    assert lookup_label("འགྲན་སྡུར་སོགས་སུ།") is None
    assert lookup_label("\u0f11") is None
    assert len(LABELS_BO) >= 30


def test_combined_label_applies_both() -> None:
    row = parse(9003, "ཀ་", "(ཡུལ་སྐད་དམ་བརྡ་རྙིང་།) ཀ་བ།")

    assert [s.labels for s in row.senses] == [["dialect", "archaic"]]


def test_double_spaces_collapsed() -> None:
    row = parse(
        976,
        "ཁ་ད་ཆད་",
        "(བརྡ་རྙིང།) ཁ་དང་མཉམ་པའི་དོན་ཏེ།  (དཔེར་ན།)  འབྲུ་རིགས་ཀྱིས་བང་མཛོད་ཁ་ད་ཆད་དུ་\u00a0 གང་བ།",
    )

    assert shape(row.senses) == [(1, None, None, ["archaic"], "ཁ་དང་མཉམ་པའི་དོན་ཏེ།")]
    assert row.senses[0].examples == [ParsedExample("འབྲུ་རིགས་ཀྱིས་བང་མཛོད་ཁ་ད་ཆད་དུ་ གང་བ།")]


def test_example_only_sense_attaches_to_the_previous_sense() -> None:
    row = parse(
        9004,
        "དང་",
        "1. (སྦྲེལ་ཚིག) མིང་སྦྲེལ་བྱེད་ཀྱི་ཚིག་ཕྲད་དེ། (དཔེར་ན།) དགེ་རྒན་དང་སློབ་མ། "
        "2. (སྦྱོར་ཚིག)  (དཔེར་ན།) སྒྲིག་ལམ་དང་མ་འགལ། 3. ཀ་བ།",
    )

    assert shape(row.senses) == [
        (1, None, "conj", [], "མིང་སྦྲེལ་བྱེད་ཀྱི་ཚིག་ཕྲད་དེ།"),
        (2, None, None, [], "ཀ་བ།"),
    ]
    assert row.senses[0].examples == [ParsedExample("དགེ་རྒན་དང་སློབ་མ།"), ParsedExample("སྒྲིག་ལམ་དང་མ་འགལ།")]
    assert "sense_dropped_empty" in row.warnings


def test_wylie_from_the_headword() -> None:
    if importlib.util.find_spec("pyewts") is None:
        pytest.skip("pyewts not installed")

    row = parse(24, "ཀ་ཅ།", "ནོར་རྫས།", PyewtsConverter())

    assert (row.headword, row.wylie) == ("ཀ་ཅ་", "ka ca")
    assert parse(24, "ཀ་ཅ་", "ནོར་རྫས།").wylie is None
