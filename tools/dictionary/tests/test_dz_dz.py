"""DDC Dzongkha → Dzongkha parser. Inputs are real `dz-dz` rows (stardict-dz-dz/dz-dz)."""

from __future__ import annotations

from dictbuild.grouping import group_rows
from dictbuild.model import GLOSS_LANG_DZ, ParsedRow
from dictbuild.packs import DDC
from dictbuild.parsers.dz_dz import parse_dz_dz_row
from dictbuild.source import SourceRow
from dictbuild.wylie import NullConverter

ROWS = {
    "ཀ": "<k>ཀ </k>གསལ་བྱེད་སུམ་ཅུའི་དང་པོ་ཀ། མིང་གཞི་ཕོའི་ཡི་གུ། སྟོན་ཀ། དཔྱིད་ཀ། ཐལ་ཀ། བེར་ཀ་ལ་སོགས་པ་མིང་མཐའ་ལུ་འཇུག་པ་ལེ་ཤ་ཡོད།",
    "མི་ཆོས་གཙང་མ་བཅུ་དྲུག": "<k>མི་ཆོས་གཙང་མ་བཅུ་དྲུག </k>༡ དཀོན་མཆོག་གསུམ་ལུ་ སྐྱབས་སུ་གཟུང་ནི། ༢ དམ་པའི་ཆོས་བསྒྲུབ་ནི། "
    "༣ ཕ་མ་ལུ་དྲིན་ལན་འཇལ་ནི། ༤ ཡོན་ཏན་ཡོད་མི་ལུ་བརྩི་མཐོང་འབད་ནི། ༥ རང་ལས་མཐོ་བ་དང་རྒན་པའི་རིགས་ལུ་གུས་ཞབས་འབད་ནི། "
    "༦ ཁྱིམ་མཚེས་གདང་རས་དང་ཡུལ་མི་ཚུ་ལུ་ཕན་ཐོགས་འབད་ནི། ༧ བློ་ཕྲང་སྟེ་སླབ་ཞིན་ན་སེམས་ཆུང་སྦེ་སྡོད་ནི། "
    "༨ གཉེན་ཉེ་དུ་དང་ གྲོགས་མཛའ་བཤེས་ལུ་སེམས་ཕྲང་སྟེ་འབད་ནི། ༩ ཡ་རབས་ཀྱི་རྗེས་སུ་འབྲང་ནི། ༡༠ ཟས་ནོར་ལུ་སྤྱོད་ཤེསཔ་འབད་ནི། "
    "༡༡ ཧེ་མ་རང་ལུ་དྲིན་ཡོད་མི་ཚུ་ལུ་བཟང་ལན་འཇལ་ནི། ༡༢ བུ་ལོན་དུས་ཚོད་ཁར་བཏབ་སྟེ་བྲེ་སྲང་ལུ་གཡོ་སྒྱུ་སྤང་ནི། "
    "༡༣ ཕྲག་དོག་མ་བསྐྱེད་པར་ག་ར་ལུ་ཕྱོགས་རིས་མེད་པར་སྙོམས་ཏེ་འབད་ནི། ༡༤ ཆ་རོགས་ངན་པའི་ཁར་མ་ཉན་པར་རང་ཚོད་རང་གིས་གཟུང་ནི། "
    "༡༥ བློ་སླབ་ནི་ཁ་ཉུང་། སླབ་མི་འདི་ཧན་ཏོང་ཏོ་སྦེ་སླབ་ནི། "
    "༡༦ ག་ཅི་ར་འབད་རུང་བློ་ཁོག་ཡངས་ཏོག་ཏོ་དང་བཟོད་བསྲན་ཆེ་ཏོག་ཏོ་སྦེ་སྡོད་ནི་ཚུ།",
    "ཀླུ་མོ": "<k>ཀླུ་མོ </k>ཀླུ་མོ་གི་མིང༌།\n<k>ཀླུ་མོ </k>ཀླུ་མོའི་རིགས།",
    "": "<k> </k>\n<k> </k>\n<k> </k>གྲ་ཁེག༌ཁེ༌གྲུ་ལམ་ལམ་ཟེར་དོ་བཟུམ།",
}


def parse(word: str, key: str | None = None) -> ParsedRow:
    return parse_dz_dz_row(SourceRow(1, word if key is None else key, ROWS[word]), NullConverter())


def test_running_definition() -> None:
    row = parse("ཀ", key="ཀ ")
    assert (row.headword, row.group_key, row.homonym) == ("ཀ་", "ཀ་", None)
    assert len(row.senses) == 1
    assert row.senses[0].gloss.startswith("གསལ་བྱེད་སུམ་ཅུའི་དང་པོ་ཀ།")
    assert row.senses[0].gloss_lang == GLOSS_LANG_DZ
    assert row.senses[0].part_of_speech is None
    assert not row.warnings


def test_numbered_senses() -> None:
    row = parse("མི་ཆོས་གཙང་མ་བཅུ་དྲུག")
    assert len(row.senses) == 16
    assert [sense.sense_no for sense in row.senses] == list(range(1, 17))
    assert row.senses[0].gloss == "དཀོན་མཆོག་གསུམ་ལུ་ སྐྱབས་སུ་གཟུང་ནི།"
    assert row.senses[15].gloss.startswith("ག་ཅི་ར་འབད་རུང་")


def test_second_block_continues_the_sense_numbers() -> None:
    row = parse("ཀླུ་མོ")
    assert [sense.gloss for sense in row.senses] == ["ཀླུ་མོ་གི་མིང་།", "ཀླུ་མོའི་རིགས།"]
    assert [sense.sense_no for sense in row.senses] == [1, 2]


def test_empty_key() -> None:
    row = parse("")
    assert (row.headword, row.group_key) == ("", "")
    assert row.wylie is None


def test_nbsp_tsheg_becomes_a_tsheg() -> None:
    # Real rows with U+0F0C in the running definition (the second one has no key).
    assert [s.gloss for s in parse("ཀླུ་མོ").senses] == ["ཀླུ་མོ་གི་མིང་།", "ཀླུ་མོའི་རིགས།"]
    assert [s.gloss for s in parse("").senses] == ["གྲ་ཁེག་ཁེ་གྲུ་ལམ་ལམ་ཟེར་དོ་བཟུམ།"]


def test_a_repeated_k_block_does_not_repeat_its_sense() -> None:
    # Real row 9985: the second `<k>` block repeats the first.
    text = "ཡི་གུ་བསྐྱལ་བ་ཅིན་ཟེར་བའི་མིང་མཐའ་བསྡུས་ཚིག"
    parsed = parse_dz_dz_row(SourceRow(9985, "བསྐྱལན ", f"<k>བསྐྱལན </k>{text}\n<k>བསྐྱལན </k>{text}"), NullConverter())

    entry = group_rows(DDC, "dz-dz", [parsed])[0]

    assert [(s.sense_no, s.gloss) for s in entry.senses] == [(1, text)]


def test_a_shad_or_tsheg_left_before_the_first_sense_is_not_a_gloss() -> None:
    # Real rows 17371 (`།༡ …`) and 6525 (the definition starts with a tsheg).
    shad = parse_dz_dz_row(
        SourceRow(
            17371,
            "རྦབ་རིལ ",
            "<k>རྦབ་རིལ </k>།༡ རང་བཞིན་གྱིས་རྦོབ་རིལ་འགྱོ་ནི། ༢ རྦོབ་སྒྲིལ་བཏང་ད་ནུག་ཟེར་བ་ཅིན་ བྱེད་འབྲེལ་ཚིག",
        ),
        NullConverter(),
    )
    tsheg = parse_dz_dz_row(
        SourceRow(6525, "དབོན ", "<k>དབོན </k>་གི་བུ་ཚུ་ལུ་གོ་ནི་ཨིན།"), NullConverter()
    )

    assert [s.gloss for s in shad.senses] == ["རང་བཞིན་གྱིས་རྦོབ་རིལ་འགྱོ་ནི།", "རྦོབ་སྒྲིལ་བཏང་ད་ནུག་ཟེར་བ་ཅིན་ བྱེད་འབྲེལ་ཚིག"]
    assert [s.sense_no for s in shad.senses] == [1, 2]
    assert [s.gloss for s in tsheg.senses] == ["གི་བུ་ཚུ་ལུ་གོ་ནི་ཨིན།"]
