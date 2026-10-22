"""DDC English → Dzongkha parser. Inputs are real `en-dz` rows (stardict-en-dz/en-dz)."""

from __future__ import annotations

from dictbuild.model import ParsedRow
from dictbuild.parsers.ddc_common import pos_code, sort_case_duplicates
from dictbuild.parsers.en_dz import parse_en_dz_row
from dictbuild.source import SourceRow

ROWS = {
    "A": '<k>A</k> <pos><abbr><i>noun</i></abbr></pos> ༡ ཨིང་ལིཤ་གི་ཡི་གུ་དང་པ། ༢ སྡེ་ཚན་ཀ་པ། སྡེ་རིམ་ཀ་པ། ༣ དྲག་ཤོས།',
    "a": "<k>a</k> <pos>ia:</pos> ཅིག ཞིག ཤིག གང༌།",
    "bring about": "<k>bring about</k> <pos>PHR V.</pos> PHR V འབྱུང་བཅུག་ནི།",
    "daughter-in-law": "<k>daughter-in-law</k> <pos>n:\n</pos> མནའ་མ།",
    "locust": "<k>locust</k> <pos><abbr><i>noun</i></abbr></pos> ཅིའུ་དབ།",
    "mother": "<k>mother</k> <pos><abbr><i>noun</i></abbr></pos> ༡ ཨའི། ཨ་མ།༢ ཨ་ཎེམོ་སློབ་དཔོན་མོའིི་གོ་གནས།",
    "round": "<k>round </k> <pos><abbr><i>noun</i></abbr></pos> ༡ རིལ་རི། ༢ སྒོར་སྒོ། ༣ ཞིབ་བཤལ། རྒྱུན་བཤལ། "
    "དྲུང་འཚོ་གིས་ནད་པའི་ཞིབ་དཔྱད་ལུ་འོང་དོ་བཟུམ། ༤ རྩེད་ཐེངས། སྐོར་ཐེངས། རིམ་ཐེངས། ༥ རྐྱབ་ཐེངས། "
    "ཀྲོབ་མདའ་ལྟ་བུ། ༦ སྐོར་གླུ། ཚིག་གཅིག་སྐོར་རྒྱབ་སྦེ་འཐེན་པའི་གླུ་གཞས།\n"
    "<k>round</k> <pos><abbr><i>verb</i></abbr></pos> ༡ སྒོར་སྒོ་བཟོ་ནི། ༢ བསྐོར་ཏེ་འགྱོ་ནི། བསྒོར་ཏེ་འགྱོ་ནི།\n"
    "<k>round</k> <pos><abbr><i>adv.</i></abbr></pos> ༡ བསྐོར་ཏེ། པད་སྐོར་བསྒྱིར་ཏེ། ༢ ཕྱོགས་ཀུན་ལུ། ༣ ཕྱོགས་སོ་སོ་ལུ།\n"
    "<k>round </k> <pos><abbr><i>prep.</i></abbr></pos> ༡ མཐའ་སྐོར་ལུ། ཕྱོགས་ཀུན་ལུ། ༢ མཐའ་བསྐོར་ཏེ། "
    "༣ ཡན་ལག་ཆ་ཚང་ལུ། ༤ མཐའ་མར། མཐའ་ཟུར་ལུ།\n"
    "<k>round</k> <pos><abbr><i>adj.</i></abbr></pos> ༡ སྒོར་སྒོ། རིལ་རི། ༢ ཆ་ཚང༌། ཡོངས་རྫོགས། "
    "༣ སྐོར་སྐྱོད། འགྲུལ་སྐོར། ཕར་སོང་སྟེ་ཚུར་ལོག་པའི།",
    "ablaze": "<k>ablaze</k> <pos><abbr><i>adv.</i></abbr></pos> <pos><abbr><i>adj.</i></abbr></pos> "
    "༡ མེ་འབར་བ། མེས་འཚིག་པ། ༢ མདངས་འཕྲོ་བ།",
    "": "<k></k> <pos></pos> ཆུང་རིམ།",
    "(the Cubs)": "<k>(the Cubs)</k> <pos></pos> ཆུང་རིམ་སི་ཀའུཊ་ཚོགས་པའི་འཐུས་མི།",
    "discrimination\xa0": "<k>discrimination\xa0</k> <pos>adj.\xa0n.</pos> ༡ ཉེ་རིང༌། ཕྱོགས་རིས། "
    "༢ ལེགས་མཐོང༌། ལེགས་ཞན་དབྱེ་བ། དབྱེ་བ་ཕྱེ་ཚུགས་པའི་ལྕོགས་གྲུབ།",
    "impart": "<k>impart</k> <pos>erb</pos> ༡ སྤྲོད་ནི། བྱིན་ནི།",
    "modernise": "<k>modernize</k> <k>modernise</k> <pos></pos> ༡ དུས་མཐུན་བཟོ་ནི། ༢ གསར་མཐུན་བཟོ་ནི།",
}


def parse(word: str) -> ParsedRow:
    return parse_en_dz_row(SourceRow(1, word, ROWS[word]))


def test_single_block() -> None:
    row = parse("locust")

    assert (row.headword, row.group_key, row.homonym) == ("locust", "locust", None)
    assert [(s.sense_no, s.part_of_speech, s.gloss, s.gloss_lang) for s in row.senses] == [
        (1, "n", "ཅིའུ་དབ།", "dz")
    ]
    assert row.warnings == [] and row.unknown_labels == []


def test_tibetan_numbered_senses() -> None:
    row = parse("mother")

    assert [s.gloss for s in row.senses] == ["ཨའི། ཨ་མ།", "ཨ་ཎེམོ་སློབ་དཔོན་མོའིི་གོ་གནས།"]


def test_pos_blocks_continue_senses() -> None:
    row = parse("round")

    assert [(s.sense_no, s.part_of_speech) for s in row.senses] == (
        [(n, "n") for n in range(1, 7)]
        + [(7, "v"), (8, "v")]
        + [(n, "adv") for n in (9, 10, 11)]
        + [(n, "prep") for n in (12, 13, 14, 15)]
        + [(n, "adj") for n in (16, 17, 18)]
    )
    assert row.headword == "round"
    assert row.senses[6].gloss == "སྒོར་སྒོ་བཟོ་ནི།"


def test_empty_key_has_empty_group_key() -> None:
    row = parse("")

    assert (row.headword, row.group_key) == ("", "")
    assert [s.gloss for s in row.senses] == ["ཆུང་རིམ།"]


def test_key_in_brackets_is_kept() -> None:
    assert parse("(the Cubs)").headword == "(the Cubs)"
    assert parse("discrimination\xa0").headword == "discrimination"


def test_messy_pos_variants() -> None:
    assert parse("daughter-in-law").senses[0].part_of_speech == "n"
    assert parse("bring about").senses[0].part_of_speech == "v"
    empty = parse("(the Cubs)")
    assert empty.senses[0].part_of_speech is None
    assert empty.unknown_labels == []


def test_tolerant_labels() -> None:
    assert parse("impart").senses[0].part_of_speech == "v"
    both = parse("discrimination\xa0")
    assert [s.part_of_speech for s in both.senses] == ["adj,n", "adj,n"]
    assert both.unknown_labels == []
    for label, code in {
        "n”": "n",
        "PHR V": "v",
        "adj": "adj",
        "adv;": "adv",
        "possessive pron:": "pron",
        "abbr:": "abbr",
        "adj.,adv.": "adj,adv",
        "exclamation, n.": "interj,n",
    }.items():
        assert pos_code(label) == (code, True), label
    for label in ("ia:", "det.", "pt of\xa0GIVE", "pt. of n."):
        assert pos_code(label) == (None, False), label


def test_unknown_label_is_reported() -> None:
    row = parse("a")

    assert row.unknown_labels == ["ia:"]
    assert [(s.part_of_speech, s.gloss) for s in row.senses] == [(None, "ཅིག ཞིག ཤིག གང་།")]


def test_latin_echo_of_the_label_is_dropped() -> None:
    row = parse("bring about")

    assert [s.gloss for s in row.senses] == ["འབྱུང་བཅུག་ནི།"]


def test_adjacent_key_blocks_give_one_set_of_senses() -> None:
    row = parse("modernise")

    assert [s.sense_no for s in row.senses] == [1, 2]
    assert row.warnings == []


def test_case_duplicates_sort_lower_first() -> None:
    rows = [SourceRow(1, "A", ""), SourceRow(2, "a", ""), SourceRow(3, "b", "")]

    assert [r.word for r in sort_case_duplicates(rows)] == ["a", "A", "b"]
    far = [SourceRow(1, "Cancer", ""), SourceRow(2, "b", ""), SourceRow(3, "cancer", "")]
    assert [r.word for r in sort_case_duplicates(far)] == ["cancer", "b", "Cancer"]


def test_several_pos_labels_in_one_block_join_their_codes() -> None:
    row = parse("ablaze")

    assert [(s.part_of_speech, s.gloss) for s in row.senses] == [
        ("adv,adj", "མེ་འབར་བ། མེས་འཚིག་པ།"),
        ("adv,adj", "མདངས་འཕྲོ་བ།"),
    ]


def test_nbsp_tsheg_becomes_a_tsheg() -> None:
    # Real rows 15515 and 15492 (`pillow`, `piggery`): the source ends the gloss with U+0F0C before the shad.
    pillow = parse_en_dz_row(SourceRow(15515, "pillow", "<k>pillow</k> <pos><abbr><i>noun</i></abbr></pos> ཧང༌།"))
    piggery = parse_en_dz_row(
        SourceRow(
            15492,
            "piggery",
            "<k>piggery</k> <pos><abbr><i>noun</i></abbr></pos> ཕག་གསོ་ཁང༌། ཕགཔ་གསོ་སྐྱོང་ཁང༌།",
        )
    )

    assert [s.gloss for s in pillow.senses] == ["ཧང་།"]
    assert [s.gloss for s in piggery.senses] == ["ཕག་གསོ་ཁང་། ཕགཔ་གསོ་སྐྱོང་ཁང་།"]
