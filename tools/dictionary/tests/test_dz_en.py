"""DDC Dzongkha → English parser. Inputs are real `dz-en` rows (stardict-dz-en/dz-en)."""

from __future__ import annotations

import importlib.util

import pytest

from dictbuild.model import ParsedRow, RelatedCandidate
from dictbuild.parsers.ddc_common import pos_code, split_k_blocks, strip_tags
from dictbuild.parsers.dz_en import parse_dz_en_row
from dictbuild.related import HeadwordIndex
from dictbuild.source import SourceRow
from dictbuild.wylie import NullConverter, PyewtsConverter, WylieConverter

EMPTY_INDEX = HeadwordIndex()

ROWS = {
    "ཀ": '<k>ཀ</k> <def> <pos><abbr><i>noun</i></abbr></pos>the 1st letter of the Dzongkha alphabet</def> ',
    "ཀ་ཀུ": '<k>ཀ་ཀུ</k> <def> <pos><abbr><i>noun</i></abbr></pos> <abbr><i>syn.</i></abbr>   '
    '<kref type="syn">ཀ་སྨད</kref>།  <kref type="syn">ཀ་མད</kref>།  <kref type="syn">ཀ་ཁ</kref>། alphabet</def> ',
    "ཀ་ཀུ་རུ": '<k>ཀ་ཀུ་རུ</k> <def> <pos><abbr><i>noun</i></abbr></pos>pumpkin, squash</def> ',
    "ཀ་ཀྲ་ཀོ་ཀྲ": '<k>ཀ་ཀྲ་ཀོ་ཀྲ</k> <def> <pos><abbr><i>adj.</i></abbr></pos> <b>1</b> curl, winding, cock-eyed, '
    "crooked (གཡོ་གཡོཝ།)  <b>2</b> devious (གཡོ་སྒྱུ་ཅན།)</def> ",
    "ཆུ": '<k>ཆུ</k> <def> <pos><abbr><i>noun</i></abbr></pos> <b>1</b> fluid, water, liquid (གཤེར་རིགས།) '
    '<abbr><i>hon.</i></abbr> ཆབ།  <abbr><i>opp.</i></abbr> <kref type="ant">མེ།</kref>  <b>2</b> urine, pee, '
    "piss (དྲི་ཆུ།)</def> ",
    "ཕ་མིང": "<k>ཕ་མིང</k> <def> <pos><abbr><i>noun</i></abbr></pos>father&apos;s name</def> ",
    "འགྱོ": "<k>འགྱོ</k> <def> <pos><abbr><i>verb</i></abbr></pos>( <abbr><i>fut.</i></abbr>,  "
    "<abbr><i>prs.</i></abbr>,  <abbr><i>pst.</i></abbr>, <abbr><i>imp.</i></abbr>) <abbr><i>hon.</i></abbr> འབྱོན། "
    "go, proceed, depart, draw, get, go off, head, leave, start, travel, undergo </def> ",
}


@pytest.fixture(scope="module")
def pyewts() -> WylieConverter:
    if importlib.util.find_spec("pyewts") is None:
        pytest.skip("pyewts not installed")
    return PyewtsConverter()


def parse(word: str, definition: str | None = None, converter: WylieConverter | None = None) -> ParsedRow:
    return parse_dz_en_row(SourceRow(1, word, definition or ROWS[word]), converter or NullConverter(), EMPTY_INDEX)


def test_headword_gets_final_tsheg() -> None:
    row = parse("ཀ")

    assert (row.headword, row.group_key, row.homonym) == ("ཀ་", "ཀ་", None)
    assert [(s.gloss, s.part_of_speech, s.gloss_lang) for s in row.senses] == [
        ("the 1st letter of the Dzongkha alphabet", "n", "en")
    ]


def test_pos_and_single_gloss() -> None:
    row = parse("ཀ་ཀུ་རུ")

    assert [(s.part_of_speech, s.gloss, s.notes) for s in row.senses] == [("n", "pumpkin, squash", [])]


def test_synonym_krefs_become_links() -> None:
    row = parse("ཀ་ཀུ")

    assert row.related == [
        RelatedCandidate("ཀ་སྨད་", "synonym"),
        RelatedCandidate("ཀ་མད་", "synonym"),
        RelatedCandidate("ཀ་ཁ་", "synonym"),
    ]
    assert [(s.gloss, s.notes) for s in row.senses] == [("alphabet", [])]


def test_numbered_senses_and_bracketed_dzongkha() -> None:
    row = parse("ཀ་ཀྲ་ཀོ་ཀྲ")

    assert [(s.sense_no, s.part_of_speech, s.gloss) for s in row.senses] == [
        (1, "adj", "curl, winding, cock-eyed, crooked (གཡོ་གཡོཝ།)"),
        (2, "adj", "devious (གཡོ་སྒྱུ་ཅན།)"),
    ]


def test_verb_forms_and_honorific_become_notes() -> None:
    row = parse("འགྱོ")

    (sense,) = row.senses
    assert sense.part_of_speech == "v"
    assert sense.notes == ["forms: fut., prs., pst., imp.", "hon. འབྱོན།"]
    assert sense.gloss == "go, proceed, depart, draw, get, go off, head, leave, start, travel, undergo"
    assert "hon." not in sense.gloss and "fut." not in sense.gloss


def test_opposite_becomes_antonym_link_and_note() -> None:
    row = parse("ཆུ")

    assert [(s.gloss, s.notes) for s in row.senses] == [
        ("fluid, water, liquid (གཤེར་རིགས།)", ["hon. ཆབ།", "opp. མེ།"]),
        ("urine, pee, piss (དྲི་ཆུ།)", []),
    ]
    assert row.related == [RelatedCandidate("མེ་", "antonym")]


def test_apostrophe_entity_unescaped() -> None:
    assert parse("ཕ་མིང").senses[0].gloss == "father's name"


def test_unknown_pos_reported() -> None:
    row = parse("ཀ", "<k>ཀ</k> <def> <pos><abbr><i>zzz.</i></abbr></pos>letter</def>")
    idiom = parse("ཀ", "<k>ཀ</k> <def> <pos><abbr><i>IDM</i></abbr></pos>letter</def>")

    assert row.unknown_labels == ["zzz."]
    assert [(s.part_of_speech, s.gloss) for s in row.senses] == [(None, "letter")]
    assert idiom.unknown_labels == [] and idiom.senses[0].part_of_speech is None


def test_second_pos_run_and_second_block_continue_numbering() -> None:
    row = parse(
        "ཀ",
        "<k>ཀ</k> <def> <pos><abbr><i>noun</i></abbr></pos>mind <pos><abbr><i>verb</i></abbr></pos>recall</def> \n"
        "<k>ཀ</k> <def> <pos><abbr><i>noun</i></abbr></pos>entreaty</def> ",
    )

    assert [(s.sense_no, s.part_of_speech, s.gloss) for s in row.senses] == [
        (1, "n", "mind"),
        (2, "v", "recall"),
        (3, "n", "entreaty"),
    ]


def test_notes_before_first_number_go_to_the_first_sense() -> None:
    row = parse(
        "ཀ",
        "<k>ཀ</k> <def> <pos><abbr><i>noun</i></abbr></pos> <abbr><i>short</i></abbr> ཀུན་ར།  "
        "<b>1</b> a grove  <b>2</b> hall</def> ",
    )

    assert [(s.gloss, s.notes) for s in row.senses] == [("a grove", ["short ཀུན་ར།"]), ("hall", [])]


def test_empty_gloss_is_dropped_with_a_warning() -> None:
    row = parse("ཀ", "<k>ཀ</k> <def> <pos><abbr><i>noun</i></abbr></pos> </def>")

    assert row.senses == [] and row.warnings == ["sense_without_gloss"]


def test_tags_glued_to_text_do_not_join_words() -> None:
    assert strip_tags("<k>ཀ</k>༡<b>x</b>y") == "ཀ ༡ x y"
    assert split_k_blocks("<k>ཀ</k>༡ <def>a</def><k>ཁ</k> b") == [("ཀ", "༡ <def>a</def>"), ("ཁ", " b")]


def test_pos_code() -> None:
    assert pos_code("noun") == ("n", True)
    assert [pos_code(label) for label in ("n:", "PHR V.", "combining form n.", "excl.", "num.")] == [
        ("n", True),
        ("v", True),
        ("comb", True),
        ("interj", True),
        ("num", True),
    ]
    assert pos_code("IDM") == (None, True)
    assert pos_code("") == (None, True)
    assert pos_code("zzz.") == (None, False)


def test_wylie_from_converter(pyewts: WylieConverter) -> None:
    assert parse("ཀ་ཀུ་རུ", converter=pyewts).wylie == "ka ku ru"


def test_notes_and_links_before_the_first_pos_go_to_the_first_sense() -> None:
    row = parse("ཆག", '<k>ཆག</k> <def>  <abbr><i>syn.</i></abbr><kref type="syn"> གྲུམ</kref>། '
                "<pos><abbr><i>noun</i></abbr></pos>fodder <pos><abbr><i>verb</i></abbr></pos>break</def> ")
    opposite = parse("སྐམ", '<k>སྐམ</k> <def>  <abbr><i>opp.</i></abbr> <kref type="ant">རློནམ</kref>། '
                     "<pos><abbr><i>verb</i></abbr></pos>wither</def> ")

    assert [(s.gloss, s.notes) for s in row.senses] == [("fodder", []), ("break", [])]
    assert row.related == [RelatedCandidate("གྲུམ་", "synonym")] and row.warnings == []
    assert [(s.gloss, s.notes) for s in opposite.senses] == [("wither", ["opp. རློནམ"])]


def test_syn_marker_without_pos_is_not_a_part_of_speech() -> None:
    row = parse("སྤུད་ཟླ", '<k>སྤུད་ཟླ</k> <def>  <abbr><i>syn.</i></abbr><kref type="syn"> སྤུན་ཆ</kref>། sibling</def> ')
    number = parse("ཁལ་གཅིག", "<k>ཁལ་གཅིག</k> <def>  <abbr><i>num.</i></abbr> twenty</def> ")
    unknown = parse("ཁལ་གཅིག", "<k>ཁལ་གཅིག</k> <def>  <abbr><i>zzz.</i></abbr> twenty</def> ")

    assert row.unknown_labels == [] and [(s.part_of_speech, s.gloss) for s in row.senses] == [(None, "sibling")]
    assert number.unknown_labels == [] and [(s.part_of_speech, s.gloss) for s in number.senses] == [("num", "twenty")]
    assert unknown.unknown_labels == ["zzz."] and unknown.senses[0].gloss == "twenty"


def test_verb_forms_that_differ_become_notes_and_not_a_sense() -> None:
    row = parse(
        "ཀྲིག་ཀྲི་གཏང་བ",
        "<k>ཀྲིག་ཀྲི་གཏང་བ</k> <def> <pos><abbr><i>verb</i></abbr></pos>( <abbr><i>fut.</i></abbr>,  "
        "<abbr><i>prs.</i></abbr>, <abbr><i>imp.</i></abbr>)  <abbr><i>pst.</i></abbr> བཏང་།  <b>1</b> appreciation, "
        "satisfaction (ངལ་རངས་པ།)  <b>2</b> displeasure (ངལ་མ་རངས་པ།)</def> ",
    )

    assert [(s.sense_no, s.gloss, s.notes) for s in row.senses] == [
        (1, "appreciation, satisfaction (ངལ་རངས་པ།)", ["forms: fut., prs., imp.", "pst. བཏང་།"]),
        (2, "displeasure (ངལ་མ་རངས་པ།)", []),
    ]


def test_nested_abbr_pairs_and_honorific() -> None:
    row = parse(
        "ཁ་ཕྱེ",
        "<k>ཁ་ཕྱེ</k> <def> <pos><abbr><i>verb</i></abbr></pos>( <abbr><i>fut.</i></abbr>,  <abbr><i>prs.</i></abbr>)  "
        "<abbr><i>pst.</i></abbr> ཕྱེས།  <abbr><i><abbr><i>imp.</i></abbr></i></abbr> ཕྱེས། "
        "<abbr><i>hon.</i></abbr> ཞལ་ཕྱེ། open, gape, prise, unfurl</def> ",
    )

    assert [(s.gloss, s.notes) for s in row.senses] == [
        (
            "open, gape, prise, unfurl",
            ["forms: fut., prs.", "pst. ཕྱེས།", "imp. ཕྱེས།", "hon. ཞལ་ཕྱེ།"],
        )
    ]


def test_pairs_across_newlines_and_a_form_without_shad() -> None:
    row = parse(
        "གཞན་ལས་མགྱོགསཔ་སྦེ་བརྒྱུག་པ",
        "<k>གཞན་ལས་མགྱོགསཔ་སྦེ་བརྒྱུག་པ</k> <def> <pos><abbr><i>verb</i></abbr></pos>( <abbr><i>fut.</i></abbr>)  "
        "<abbr><i>prs.</i></abbr> རྒྱུག \n <abbr><i>pst.</i></abbr> བརྒྱུགས།  "
        "<abbr><i><abbr><i>imp.</i></abbr></i></abbr> རྒྱུགས། outrun</def> ",
    )

    assert [(s.gloss, s.notes) for s in row.senses] == [
        ("outrun", ["forms: fut.", "prs. རྒྱུག", "pst. བརྒྱུགས།", "imp. རྒྱུགས།"])
    ]


def test_part_of_speech_in_a_bare_abbr() -> None:
    row = parse(
        "ཁྲག་ལོང",
        "<k>ཁྲག་ལོང</k> <def> <abbr><i>verb</i></abbr>( <abbr><i>fut.</i></abbr>,  <abbr><i>prs.</i></abbr>)  "
        "<abbr><i>pst.</i></abbr> ལོངས།  <abbr><i><abbr><i>imp.</i></abbr></i></abbr> ལོངས། exasperation, rage</def> ",
    )

    assert [(s.part_of_speech, s.gloss, s.notes) for s in row.senses] == [
        ("v", "exasperation, rage", ["forms: fut., prs.", "pst. ལོངས།", "imp. ལོངས།"])
    ]
    assert row.unknown_labels == []


def test_hon_form_is_one_note() -> None:
    row = parse(
        "སྐུ་དྲུང",
        "<k>སྐུ་དྲུང</k> <def> <pos><abbr><i>adv.</i></abbr></pos>in the presence of (གདོང་ཁར།) "
        '<abbr><i>hon.</i></abbr> form  <abbr><i>syn.</i></abbr> <kref type="syn"> ཞབས་སར</kref>། '
        "<kref type=\"syn\"> སྐུ་མདུན</kref>། <pos><abbr><i>noun</i></abbr></pos>discipline master</def> ",
    )
    abbr = parse("ཁྱེད", "<k>ཁྱེད</k> <def> <pos><abbr><i>pron.</i></abbr></pos><abbr><i>hon. form</i></abbr> you</def>")

    assert [(s.gloss, s.notes) for s in row.senses] == [
        ("in the presence of (གདོང་ཁར།)", ["hon. form"]),
        ("discipline master", []),
    ]
    assert [r.headword for r in row.related] == ["ཞབས་སར་", "སྐུ་མདུན་"]
    assert [(s.gloss, s.notes) for s in abbr.senses] == [("you", ["hon. form"])]


def test_notes_do_not_carry_across_k_blocks() -> None:
    row = parse(
        "ད་ལན",
        '<k>ད་ལན</k> <def>  <abbr><i>hon.</i></abbr> ཀ། </def> \n<k>ད་ལན</k> <def> <pos><abbr><i>adv.</i></abbr></pos> '
        '<abbr><i>syn.</i></abbr> <kref type="syn">ད་ལམ</kref>། hereby </def> ',
    )

    assert [(s.gloss, s.notes) for s in row.senses] == [("hereby", [])]
    assert row.warnings == ["sense_without_gloss"]
