"""English → Tibetan parser (plan §5.3 "entotb" grammar). Inputs are real `entotb` rows."""

from __future__ import annotations

import pytest

from dictbuild.model import ParsedExample, ParsedRow, ParsedSense, RelatedCandidate
from dictbuild.parsers.entotb import parse_entotb_row
from dictbuild.source import SourceRow


def parse(source_id: int, word: str, definition: str) -> ParsedRow:
    return parse_entotb_row(SourceRow(source_id, word, definition))


def shape(senses: list[ParsedSense]) -> list[tuple]:
    """(sense_no, sub_no, part_of_speech, labels, gloss) per sense record."""
    return [(s.sense_no, s.sub_no, s.part_of_speech, s.labels, s.gloss) for s in senses]


def pos_per_sense(senses: list[ParsedSense]) -> list[str | None]:
    """The part of speech of each numbered sense (sub-senses share their sense's)."""
    first_records = {s.sense_no: s for s in reversed(senses)}
    return [first_records[number].part_of_speech for number in sorted(first_records)]


def test_simple_pos() -> None:
    row = parse(16, "abandon", "v.t. འདོར་བ། སྤོང་བ། སྐྱུར་བ། གཡུག་པ། n. རང་དབང་།")

    assert (row.headword, row.homonym, row.group_key) == ("abandon", None, "abandon")
    assert shape(row.senses) == [
        (1, None, "vt", [], "འདོར་བ། སྤོང་བ། སྐྱུར་བ། གཡུག་པ།"),
        (2, None, "n", [], "རང་དབང་།"),
    ]
    assert {s.gloss_lang for s in row.senses} == {"bo"}
    assert {s.source_id for s in row.senses} == {16}
    assert row.related == []


def test_house_blocks_subsenses_label() -> None:
    row = parse(
        16677,
        "house",
        "n. 1. ཀཿ ཁང་པ། ཁཿ མི་ཚང་། རིགས་རྒྱུད་(དབྱིན་ཡུལ་དུ་དཔོན་རབས་ཀྱི་རིགས་རྒྱུད་ལྟ་བུ་ལ། house ཞེས་ཟེར།) "
        "2. ཀཿ ཁྲིམས་བཟོ་ལྷན་ཚོགས། གྲོས་ཚོགས། ཁཿ སྤྱི་འཐུས་ལྷན་ཚོགས། 3. སློབ་གྲྭའི་ཁང་ཚན། 4. astrol. ཁྱིམ། "
        "v.t. སྡོད་ཁང་གི་མཐུན་རྐྱེན་སྦྱོར་བ། ཁང་པར་འཇོག་པ།",
    )

    assert shape(row.senses) == [
        (1, 1, "n", [], "ཁང་པ།"),
        (1, 2, "n", [], "མི་ཚང་། རིགས་རྒྱུད་(དབྱིན་ཡུལ་དུ་དཔོན་རབས་ཀྱི་རིགས་རྒྱུད་ལྟ་བུ་ལ། house ཞེས་ཟེར།)"),
        (2, 1, "n", [], "ཁྲིམས་བཟོ་ལྷན་ཚོགས། གྲོས་ཚོགས།"),
        (2, 2, "n", [], "སྤྱི་འཐུས་ལྷན་ཚོགས།"),
        (3, None, "n", [], "སློབ་གྲྭའི་ཁང་ཚན།"),
        (4, None, "n", ["astrol"], "ཁྱིམ།"),
        (5, None, "vt", [], "སྡོད་ཁང་གི་མཐུན་རྐྱེན་སྦྱོར་བ། ཁང་པར་འཇོག་པ།"),
    ]
    assert len({s.sense_no for s in row.senses}) == 5
    assert all(not s.examples for s in row.senses)


def test_water() -> None:
    row = parse(
        37286,
        "water",
        "n. 1. ཆུ། 2. གཤེར་རྫས། 3. ཀཿ གཙང་ཆུ། ཁཿ མཚོ། གཿ རྫིང་བུ། 4. གཅིན། v.t. 1. ཆུས་བྲན་པ། ཆུ་འདྲེན་པ། "
        "2. སྦང་པ། 3. ཆུ་ལྡུད་པ། v.i. ཆུ་ལྡང་བ།",
    )

    assert pos_per_sense(row.senses) == ["n", "n", "n", "n", "vt", "vt", "vt", "vi"]
    assert [(s.sense_no, s.sub_no, s.gloss) for s in row.senses if s.sense_no == 3] == [
        (3, 1, "གཙང་ཆུ།"),
        (3, 2, "མཚོ།"),
        (3, 3, "རྫིང་བུ།"),
    ]
    assert row.senses[-1].gloss == "ཆུ་ལྡང་བ།"


def test_love_trailing_example_without_translation() -> None:
    row = parse(
        20379,
        "love",
        "n. 1. ཀཿ བརྩེ་བ། ཁཿ བྱམས་པ། གཿ བརྩེ་དུངས། མཛའ་བརྩེ། 2. འཁྲིག་སྲེད། བརྩེ་དགའ། 3. དགའ་ས། ཆགས་ས། "
        "མཛའ་གྲོགས། 4.དགའ་ཞེན། v. 1.བརྩེ་དུང་བྱེད་པ། 2. བྱམས་པོ་བྱེད་པ། 3. སེམས་འཆོར་བ། fall in love.",
    )

    assert pos_per_sense(row.senses) == ["n", "n", "n", "n", "v", "v", "v"]
    assert [s.gloss for s in row.senses if s.sense_no in (4, 5)] == ["དགའ་ཞེན།", "བརྩེ་དུང་བྱེད་པ།"]
    last = row.senses[-1]
    assert (last.sense_no, last.gloss) == (7, "སེམས་འཆོར་བ།")
    assert last.examples == [ParsedExample("fall in love.", None)]


def test_pound_of_butter_example() -> None:
    row = parse(3, "a pound of butter", "མར་རྒྱ་མ་གང་། གཿ རེ་རེར། twice a year ལོ་རེ་རེར་ཐེངས་གཉིས་རེ།")

    assert shape(row.senses) == [
        (1, None, None, [], "མར་རྒྱ་མ་གང་།"),
        (1, 3, None, [], "རེ་རེར།"),
    ]
    assert row.senses[0].examples == []
    assert row.senses[1].examples == [ParsedExample("twice a year", "ལོ་རེ་རེར་ཐེངས་གཉིས་རེ།")]


def test_good_combined_pos() -> None:
    row = parse(
        15397,
        "good",
        "adj. & n. 1. ལེགས་པོ། ཡག་པོ། འོས་པོ། 2. དྲང་པོ། 3. བཟང་པོ། 4. མཁས་པོ། n. ཁེ་ཕན། ཕན་ཐོགས། "
        "for the good of mankind.",
    )

    assert pos_per_sense(row.senses) == ["adj,n", "adj,n", "adj,n", "adj,n", "n"]
    assert row.senses[-1].gloss == "ཁེ་ཕན། ཕན་ཐོགས།"
    assert row.senses[-1].examples == [ParsedExample("for the good of mankind.", None)]


def test_run_inflection_note() -> None:
    row = parse(
        29146,
        "run",
        '"v.i. ran, run 1. རྒྱུག་པ། བང་རྒྱུག་གཏོང་བ། 2. འོས་ལངས་བྱེད་པ། 3. འགྲུལ་འཐུད་བྱེད་པ། This bus runs '
        "between Lhasa & Sera. 4. བྲོས་པ། 5. (འགྲན་སྡུར་སོགས་སུ།)ཞུགས་པ། 6. བཞུ་བ། ཞུན་རྒྱུག་པ། 7. ( "
        "འཁྲུལ་འཁོར་སོགས)འཁོར་བ། 8. ཁྱབ་བརྡལ་འགྲོ་བ། 9. ( སྣག་ཚ་སོགས)འདལ་བ། 10. འཛགས་པ། v.t. 1. "
        "ནག་ཚོང་རྒྱག་པ། 2. འཁོར་དུ་འཇུག་པ། 3. རྗེས་འདེད་བྱེད་པ། run a wolf 2 miles, 4. སྟངས་འཛིན་བྱེད་པ།\"",
    )

    assert pos_per_sense(row.senses) == ["vi"] * 10 + ["vt"] * 4
    assert [s.note for s in row.senses] == ["ran, run"] + [None] * 13  # a block note annotates its first sense
    by_number = {s.sense_no: s for s in row.senses}
    assert by_number[3].gloss == "འགྲུལ་འཐུད་བྱེད་པ།"
    assert by_number[3].examples == [ParsedExample("This bus runs between Lhasa & Sera.", None)]
    assert by_number[5].gloss == "(འགྲན་སྡུར་སོགས་སུ།)ཞུགས་པ།"
    assert by_number[13].gloss == "རྗེས་འདེད་བྱེད་པ།"
    assert by_number[13].examples == [ParsedExample("run a wolf 2 miles", None)]
    assert by_number[14].gloss == "སྟངས་འཛིན་བྱེད་པ།"


def test_sloppy_pos_without_dot() -> None:
    disaster = parse(
        10233,
        "disaster",
        "n 1. བྱུས་ཉེས། སྟབས་ཉེས། རྐྱེན་ངན། བཀྲ་མ་ཤིས། 2. གཟའ་སྐར་ངན་པ། འཕྲོད་སྦྱོར་ངན་པ། adj. བྱུས་ཉེས་ཀྱི། "
        "བཀྲ་མི་ཤིས་པའི། རྐྱེན་ངན་གྱི།",
    )
    debar = parse(9202, "debar", "v.t 1. བཀག་འགོག་བྱེད་པ།  2. ཕྱིར་འབུད་བྱེད་པ། ( ཚོགས་པ་སོགས་ཀྱི་ནང་ནས།)")

    assert pos_per_sense(disaster.senses) == ["n", "n", "adj"]
    assert disaster.senses[0].gloss == "བྱུས་ཉེས། སྟབས་ཉེས། རྐྱེན་ངན། བཀྲ་མ་ཤིས།"
    assert shape(debar.senses) == [
        (1, None, "vt", [], "བཀག་འགོག་བྱེད་པ།"),
        (2, None, "vt", [], "ཕྱིར་འབུད་བྱེད་པ། ( ཚོགས་པ་སོགས་ཀྱི་ནང་ནས།)"),
    ]


def test_undotted_domain_tags() -> None:
    actionable = parse(
        490,
        "actionable1",
        '"adm,law ཁྲིམས་གཅོད་འོས་པའི། ལག་བསྟར་འོས་པའི། ཁྲིམས་གཅོད་བྱ་རྒྱུའི་གཞི་རྩ་ཡོད་པའི།"',
    )
    lead = parse(19608, "lead1", "chem ཞ་ཉེ། ལྕགས་རིགས་ཁམས་རྫས་ལྗིད་ཆེ་ཞིང་མཉེན་པ་དང\u0f0c།")

    assert shape(actionable.senses) == [
        (1, None, None, ["adm", "law"], "ཁྲིམས་གཅོད་འོས་པའི། ལག་བསྟར་འོས་པའི། ཁྲིམས་གཅོད་བྱ་རྒྱུའི་གཞི་རྩ་ཡོད་པའི།"),
    ]
    assert (actionable.headword, actionable.homonym) == ("actionable", "1")
    assert shape(lead.senses) == [(1, None, None, ["chem"], "ཞ་ཉེ། ལྕགས་རིགས་ཁམས་རྫས་ལྗིད་ཆེ་ཞིང་མཉེན་པ་དང་།")]


def test_dotted_labels_at_block_start_and_after_a_sense_number() -> None:
    row = parse(
        408,
        "acid",
        "n. 1. སྐྱུར་ཚྭ། སྐྱུར་རྫས། 2. adj. མཚུར་གྱི་རོ་ལྟར། རོ་བསྐ་བའི། 3. chem. སྐྱུར། ཡང་རླུང་དང་བསྲེ་སྦྱོར་བྱས་པའི་སྐྱུར་རྫས།",
    )
    persona = parse(24840, "persona", "n. psychol. སྐྱེས་བུའི་ཕྱིའི་རྣམ་འགྱུར། རང་གཤིས།")

    assert shape(row.senses) == [
        (1, None, "n", [], "སྐྱུར་ཚྭ། སྐྱུར་རྫས།"),
        (2, None, "adj", [], "མཚུར་གྱི་རོ་ལྟར། རོ་བསྐ་བའི།"),
        (3, None, "n", ["chem"], "སྐྱུར། ཡང་རླུང་དང་བསྲེ་སྦྱོར་བྱས་པའི་སྐྱུར་རྫས།"),
    ]
    assert shape(persona.senses) == [(1, None, "n", ["psych"], "སྐྱེས་བུའི་ཕྱིའི་རྣམ་འགྱུར། རང་གཤིས།")]


def test_label_followed_by_a_particle_is_text() -> None:
    row = parse(11192, "Dutch auction", "auction ལ་ལྟོས།")

    assert shape(row.senses) == [(1, None, None, [], "auction ལ་ལྟོས།")]


def test_label_before_a_word_that_starts_like_a_particle() -> None:
    alb = parse(1153, "alb", "n. eccl. ཡི་ཤུའི་བླ་གོས་དཀར་པོ།")
    col = parse(7145, "col", "n. 1. geog. ལ་ཉག 2. meteorol. དྲག་རླུང་འདུས་མཚམས།")

    assert shape(alb.senses) == [(1, None, "n", ["eccl"], "ཡི་ཤུའི་བླ་གོས་དཀར་པོ།")]
    assert [s.labels for s in col.senses] == [["geog"], ["meteorol"]]
    assert alb.unknown_labels == col.unknown_labels == []
    ray = parse(27647, "ray1", "n. 1. འོད་ཟེར། 2. geom. ཟེར་ཐིག 3. vulg. ཟེར་མདའ།")
    assert [s.labels for s in ray.senses] == [[], ["geom"], ["vulg"]]


def test_example_translation_may_start_like_a_particle() -> None:
    row = parse(9996, "capital", "n. གཙོ་བོ། capital letter ཡི་གེ་ཆེན་པོ།")

    assert row.senses[0].examples == [ParsedExample("capital letter", "ཡི་གེ་ཆེན་པོ།")]


def test_unknown_abbreviation_in_a_label_position_is_reported() -> None:
    row = parse(1599, "anal", "adj. nat. བཤང་ལམ་གྱི། རྐུབ་ཀྱི། བཤང་ལམ་དང་ཉེ་བའི།")
    exponent = parse(12750, "exponent", "n. 1. འགྲེལ་བཤད་བྱེད་པོ་/མཁན། 2. ཀཿ མཚན་གཞི། ཁཿ མཚོན་བྱེད། 3. alg. ཐོབ་གྲངས།")

    assert shape(row.senses) == [(1, None, "adj", [], "nat. བཤང་ལམ་གྱི། རྐུབ་ཀྱི། བཤང་ལམ་དང་ཉེ་བའི།")]
    assert row.unknown_labels == ["nat."]
    assert exponent.senses[-1].labels == ["math"]
    assert exponent.unknown_labels == []
    assert parse(1735, "angularity", "n. 1. ཟུར་ཁུག 2. pl. ཟུར་ཁུག་གི་གྲུ་ཟུར།").unknown_labels == []


def test_english_word_bound_to_a_particle_is_not_an_unknown_label() -> None:
    aery = parse(847, "aery2", "aerie. ལ་ལྟོས་")
    broke = parse(5000, "broke", "v. 1. break. གི་འདས་ཚིག 2. འཛད་པ། ལག་སྣེ་རྫོགས་པ།")
    acuminate = parse(517, "acuminate", "acuminated. ཡང་ཟེར།")

    assert aery.unknown_labels == broke.unknown_labels == acuminate.unknown_labels == []
    assert aery.senses[0].gloss == "aerie. ལ་ལྟོས་"
    assert broke.senses[0].gloss == "break. གི་འདས་ཚིག"


def test_misspelt_domain_labels_are_whitelisted() -> None:
    row = parse(
        30010,
        "secondary",
        "n. 1. hort. གཅད་སྡུམ། 2. alchem. གསེར། 3. orithol. འདབས་སྐྱེ། 4. psycol. བློ་ཁ་སྤོ་སྒྱུར།",
    )

    assert [s.labels for s in row.senses] == [["hort"], ["alchem"], ["ornithol"], ["psych"]]
    assert row.unknown_labels == []


def test_plural_after_a_sense_number_adds_to_the_block_pos() -> None:
    row = parse(1735, "angularity", "n. 1. ཟུར་ཁུག་ཅན་གྱི་རང་བཞིན། 2. pl. ཟུར་ཁུག་གི་གྲུ་ཟུར།")

    assert pos_per_sense(row.senses) == ["n", "n,pl"]


def test_parenthetical_prefix() -> None:
    according = parse(315, "according", "(to) adv. དེ་དང་བསྟུན་ནས། དེ་བསྟུན། གཞིར་བཟུང་གིས།")
    letter_a = parse(1, "a", "(A) ཀཿ དབྱིན་ཇི་དང་རོ་མན་གྱི་གསལ་བྱེད་ཡི་གེ་དང་པོ། ཁཿ རོལ་དབྱངས་ཀྱི་སྒྲ་རྟགས་དྲུག་པ།")

    assert shape(according.senses) == [(1, None, "adv", [], "དེ་དང་བསྟུན་ནས། དེ་བསྟུན། གཞིར་བཟུང་གིས།")]
    assert according.senses[0].note == "to"
    assert shape(letter_a.senses) == [
        (1, 1, None, [], "དབྱིན་ཇི་དང་རོ་མན་གྱི་གསལ་བྱེད་ཡི་གེ་དང་པོ།"),
        (1, 2, None, [], "རོལ་དབྱངས་ཀྱི་སྒྲ་རྟགས་དྲུག་པ།"),
    ]
    assert [s.note for s in letter_a.senses] == ["A", None]


def test_asterisk_note() -> None:
    acetone = parse(388, "acetone", "n. chem. * མཚུར་ཚི།")
    meson = parse(21431, "meson", "n. *བར་མའི་ཕྲ་རྡུལ། དབུས་རྡུལ་ཕྲ་མོ།")

    assert shape(acetone.senses) == [(1, None, "n", ["chem"], "མཚུར་ཚི།")]
    assert acetone.senses[0].note == "*"
    assert shape(meson.senses) == [(1, None, "n", [], "བར་མའི་ཕྲ་རྡུལ། དབུས་རྡུལ་ཕྲ་མོ།")]
    assert meson.senses[0].note == "*"


def test_synonym_marker() -> None:
    row = parse(
        19609,
        "lead1",
        "n. 1. ཞ་ཉེ། བཤའ་ཚེ།  2. ཞ་སྨྱུག = lead pencil 3. པར་ཡིག་གི་བར་འཚང་རྒྱག་བྱེད། black lead བཤའ་ཚེ་ནག་པོ།",
    )

    assert (row.headword, row.homonym, row.group_key) == ("lead", "1", "lead")
    assert row.related == [RelatedCandidate("lead pencil", "synonym")]
    assert shape(row.senses) == [
        (1, None, "n", [], "ཞ་ཉེ། བཤའ་ཚེ།"),
        (2, None, "n", [], "ཞ་སྨྱུག"),
        (3, None, "n", [], "པར་ཡིག་གི་བར་འཚང་རྒྱག་བྱེད།"),
    ]
    assert row.senses[2].examples == [ParsedExample("black lead", "བཤའ་ཚེ་ནག་པོ།")]


def test_tibetan_equals_term_is_an_equivalent_that_stays_in_the_gloss() -> None:
    aardvark = parse(5, "aardvark", "n. ཨཕྲི་ཀའི་(ཨ་ཧྥི་རི་ཀ)ཡུལ་གྱི་སྲོག་ཆགས་གྲོག་འབུ་ཟ་མཁན་ཞིག=ས་ཕག")
    abbot = parse(37, "abbot", "n. མཁན་པོ། = ཆོས་སྡེའམ་དགོན་པའི་གོ་གནས་ཤིག")

    assert aardvark.related == abbot.related == []
    assert aardvark.senses[0].gloss == "ཨཕྲི་ཀའི་(ཨ་ཧྥི་རི་ཀ)ཡུལ་གྱི་སྲོག་ཆགས་གྲོག་འབུ་ཟ་མཁན་ཞིག=ས་ཕག"
    assert abbot.senses[0].gloss == "མཁན་པོ། = ཆོས་སྡེའམ་དགོན་པའི་གོ་གནས་ཤིག"


def test_latin_equals_term_longer_than_five_words_is_not_a_synonym() -> None:
    row = parse(9, "abc", "n. ཀ་ཁ། = the first letters of the alphabet ཀ་ཁ་ག")

    assert row.related == []


@pytest.mark.parametrize(
    ("source_id", "word", "definition"),
    [
        (10531, "dissimilation", "n. སྒྲ་མི་མཐུན་པ། དཔེར་ན། libere=library, turtur=turtle ལྟ་བུ།"),
        (2074, "apostrophe1", "n. 1. ཕྲད་རྟགས། can not = can’t 2. འབྲེལ་སྒྲའི་མཚོན་རྟགས།"),
        (18667, "its", "pron. དེའི། ལྟ་བུ། it’s = it is, it has."),
        (12159, "equation", "n. སྙོམ་རྩིས། Na2 CO3 + H2 SO4 = Na2 SO4 +CO2 +H2O ལྟ་བུ།"),
        (15333, "goalkeeper", "n. སྒོ་སྲུང་བ། སྒོ་རགས་པ་= goal kie colloq; goalkeeper line སྒོ་ཐིག"),
        (10353, "diseuse", "n. གཏམ་རྒྱུད་བཤད་མཁན་བུད་མེད། སྐྱེས་པ། =deseur (dezer)"),
    ],
)
def test_equation_in_english_text_is_not_a_synonym(source_id: int, word: str, definition: str) -> None:
    assert parse(source_id, word, definition).related == []


def test_synonym_marker_inside_brackets_or_before_a_particle_is_text() -> None:
    sum_row = parse(559, "additive identity", '"ངོ་བོའི་སྡོམ་གྲངས་/ཆ(a + a = a), (a = a + a)"')
    see_row = parse(13180, "father", "n. ཕ། = pope ལ་ལྟོས།")

    assert sum_row.related == []
    assert sum_row.senses[0].gloss == "ངོ་བོའི་སྡོམ་གྲངས་/ཆ(a + a = a), (a = a + a)"
    assert see_row.related == []
    assert see_row.senses[0].gloss == "ཕ། = pope ལ་ལྟོས།"


def test_equals_term_that_is_the_whole_sense_is_its_gloss() -> None:
    allspice = parse(1303, "allspice", "n. = *ཀུན་འདུས་གཡེར་མ།")
    plague = parse(5079, "bubonic plague", "= ཙི་ཙི་ནས་འགོ་ཞིང་སྨན་བཅོས་མེད་པའི་འགོས་ནད་ཅིག ཡུ་རོབ་ནང་མི་ས་ཡ་བྱས་ནས་ཤི་བར་བཤད།")

    assert shape(allspice.senses) == [(1, None, "n", [], "ཀུན་འདུས་གཡེར་མ།")]
    assert (allspice.senses[0].note, allspice.related) == ("*", [])
    assert plague.senses[0].gloss == "ཙི་ཙི་ནས་འགོ་ཞིང་སྨན་བཅོས་མེད་པའི་འགོས་ནད་ཅིག ཡུ་རོབ་ནང་མི་ས་ཡ་བྱས་ནས་ཤི་བར་བཤད།"
    assert plague.related == []
    # An English "= X" sense has no Tibetan, so it is a synonym link rather than a sense of its own.
    cotton = parse(8000, "cotton wool", "n. 1. རས་བལ། 2. = absorbent cotton")
    assert shape(cotton.senses) == [(1, None, "n", [], "རས་བལ།")]
    assert cotton.related == [RelatedCandidate("absorbent cotton", "synonym")]


def test_equals_sign_before_an_explanation_stays_in_the_gloss() -> None:
    row = parse(37, "abbot", "n. མཁན་པོ། = ཆོས་སྡེའམ་དགོན་པའི་གོ་གནས་ཤིག་གི་མིང་། དེའི་འགན་འཛིན་པ།")

    assert row.related == []
    assert row.senses[0].gloss == "མཁན་པོ། = ཆོས་སྡེའམ་དགོན་པའི་གོ་གནས་ཤིག་གི་མིང་། དེའི་འགན་འཛིན་པ།"


def test_single_latin_word_not_example() -> None:
    row = parse(
        6334,
        "char",
        "n. 1. ཉིན་གླས་གཡོག་མོ། charwoman ཡང་ཟེར། 2. གཡོག་ལས་ཙག་ཙིག 3. གཟུགས་པོར་དམར་ཐིག་ཅན་གྱི་གཙང་ཆུའི་ཉ་ཞིག",
    )
    few = parse(13406, "few", "adj. & n. ཉུང་ཉུང་། ཉུང་ཤས། some few ཁ་ཤས། not a few མི་ཉུང་བ།")

    assert row.senses[0].gloss == "ཉིན་གླས་གཡོག་མོ། charwoman ཡང་ཟེར།"
    assert all(not s.examples for s in row.senses)
    assert few.senses[0].gloss == "ཉུང་ཉུང་། ཉུང་ཤས།"
    assert few.senses[0].examples == [ParsedExample("some few", "ཁ་ཤས།"), ParsedExample("not a few", "མི་ཉུང་བ།")]


def test_latin_phrase_followed_by_a_particle_is_not_an_example() -> None:
    row = parse(561, "addle headed", "adj. ཐམ་མེ་ཐོམ་མེ། addle brained, addle pated ཡང་ཟེར།")

    assert row.senses[0].gloss == "ཐམ་མེ་ཐོམ་མེ། addle brained, addle pated ཡང་ཟེར།"
    assert row.senses[0].examples == []


def test_tibetan_numeral_subpoints_kept() -> None:
    row = parse(
        3412,
        "barrage1",
        '"adm,mil 1.བཅད་རགས། 2.མཚོན་རགས། ༡། གཙང་པོ་ཞིག་ནས་ཞིང་ཆུ་འདྲེན་པ་དང\u0f0c། གློག་མཁོ་འདོན་བྱེད་པ། ༢། '
        'དགྲ་ཕྱོགས་སུ་མདུན་མཆོང་བྱེད་སྐབས་ཚུར་རྒོལ་འགོག་ཐབས་སུ་མེ་སྒྱོགས་ལྟ་བུ་མཚོན་མཚམས་འཇོག་མེད་པར་འཕེན་པའི་རྒོལ་ཐབས་ཤིག"',
    )

    assert shape(row.senses) == [
        (1, None, None, ["adm", "mil"], "བཅད་རགས།"),
        (
            2,
            None,
            None,
            ["adm", "mil"],
            "མཚོན་རགས། ༡། གཙང་པོ་ཞིག་ནས་ཞིང་ཆུ་འདྲེན་པ་དང་། གློག་མཁོ་འདོན་བྱེད་པ། ༢། "
            "དགྲ་ཕྱོགས་སུ་མདུན་མཆོང་བྱེད་སྐབས་ཚུར་རྒོལ་འགོག་ཐབས་སུ་མེ་སྒྱོགས་ལྟ་བུ་མཚོན་མཚམས་འཇོག་མེད་པར་འཕེན་པའི་རྒོལ་ཐབས་ཤིག",
        ),
    ]


def test_number_glued_to_a_latin_word_is_not_a_sense_marker() -> None:
    row = parse(
        13406,
        "few",
        "adj. & n. ཉུང་ཉུང་། ཉུང་ཤས། the few1. གྲངས་ཉུང་། 2. འདེམས་སྒྲུག་བྱས་པའི།",
    )

    assert [s.sense_no for s in row.senses] == [1]
    assert row.senses[0].examples == [ParsedExample("the few1.", "གྲངས་ཉུང་། 2. འདེམས་སྒྲུག་བྱས་པའི།")]


def test_sense_opening_with_an_english_phrase_keeps_its_tibetan_as_gloss() -> None:
    first = parse(9999, "black lead", "black lead བཤའ་ཚེ་ནག་པོ།")
    numbered = parse(9998, "x", "n. 1. ཀ་བ། 2. some phrase ཁ་བ། other phrase ག་བ།")

    assert shape(first.senses) == [(1, None, None, [], "བཤའ་ཚེ་ནག་པོ།")]
    assert (first.senses[0].note, first.senses[0].examples) == ("black lead", [])
    assert shape(numbered.senses) == [(1, None, "n", [], "ཀ་བ།"), (2, None, "n", [], "ཁ་བ།")]
    assert numbered.senses[1].note == "some phrase"
    assert numbered.senses[1].examples == [ParsedExample("other phrase", "ག་བ།")]
    assert numbered.warnings == []


def test_numbered_senses_opening_with_a_phrase_or_a_label_are_kept() -> None:
    mouse = parse(
        22253,
        "mouse",
        "n. pl. mice 1. བྱི་བ། ( ཆུང་ངུའི་རིགས།) 2. comptr. sc. ཙི་གུ v.i. 1. བྱི་བ་འཇུ་བ། 2. འཇབ་འཛིན་བྱེད་པ།",
    )
    bond = parse(
        4530,
        "bond1",
        "n. 1. འཆིང་ཐག མཐུད་བྱེད། དཀྲིས། 2. གན་རྒྱ། འབའ་གན། 3. གན་རྒྱའི་ཡིག་ཆ། གན་འཛིན། 4. pl. ལག་ལྕགས། ལྕགས་སྒྲོག "
        "5. in bond ཀཿ ཁྲལ་སའི་དོས་ཁང་ནང་ཡོད་པ། ཁཿ སྣོལ་སྒྲིག v.t. 1. བཅིངས་པ། མཐུད་པ། བསྡམ་པ། 2. སྣོལ་སྒྲིག་བྱེད་པ།",
    )
    prison = parse(26401, "prison", '"n. བཙོན་ཁང་། བཙོན་ར། v.t. poet., rhet. བཙོན་དུ་འཇུག་པ། བཙོན་འཇུག་བྱེད་པ།"')
    fig = parse(13469, "fig", "n. 1. གནས་སྟངས། v.t. ཀཿ fig out མཆོར་གཟབ་པ། གོས་གྱོན་པ། ཁཿ རྟ་གྲུང་པོ་བཟོ་བ།")

    assert shape(mouse.senses) == [
        (1, None, "n", [], "བྱི་བ། ( ཆུང་ངུའི་རིགས།)"),
        (2, None, "n", ["comput"], "ཙི་གུ"),
        (3, None, "vi", [], "བྱི་བ་འཇུ་བ།"),
        (4, None, "vi", [], "འཇབ་འཛིན་བྱེད་པ།"),
    ]
    assert all(not s.examples for s in mouse.senses)
    assert [(s.sense_no, s.sub_no, s.part_of_speech, s.gloss) for s in bond.senses[4:]] == [
        (5, 1, "n", "ཁྲལ་སའི་དོས་ཁང་ནང་ཡོད་པ།"),
        (5, 2, "n", "སྣོལ་སྒྲིག"),
        (6, None, "vt", "བཅིངས་པ། མཐུད་པ། བསྡམ་པ།"),
        (7, None, "vt", "སྣོལ་སྒྲིག་བྱེད་པ།"),
    ]
    assert [s.note for s in bond.senses[4:6]] == ["in bond", None]
    assert shape(prison.senses) == [
        (1, None, "n", [], "བཙོན་ཁང་། བཙོན་ར།"),
        (2, None, "vt", ["poet", "rhet"], "བཙོན་དུ་འཇུག་པ། བཙོན་འཇུག་བྱེད་པ།"),
    ]
    assert [(s.sense_no, s.sub_no, s.part_of_speech, s.note, s.gloss) for s in fig.senses[1:]] == [
        (2, 1, "vt", "fig out", "མཆོར་གཟབ་པ། གོས་གྱོན་པ།"),
        (2, 2, "vt", None, "རྟ་གྲུང་པོ་བཟོ་བ།"),
    ]
    assert all("sense_dropped_empty" not in row.warnings for row in (mouse, bond, prison, fig))


@pytest.mark.parametrize(
    ("label", "keys"),
    [
        ("compt. sc.", ["comput"]),
        ("Compt. Sc.", ["comput"]),
        ("comptr. sc.", ["comput"]),
        ("poet., rhet.", ["poet", "rhet"]),
        ("tennis etc.", ["tennis"]),
        ("surg. etc.", ["surg"]),
        ("Int’l law", ["intl", "law"]),
        ("anat.", ["anat"]),
        ("F.", ["fr"]),
    ],
)
def test_multi_part_and_widened_labels(label: str, keys: list[str]) -> None:
    row = parse(9996, "x", f"n. 1. ཀ་བ། 2. {label} ཁ་བ།")

    assert row.senses[1].labels == keys
    assert row.senses[1].gloss == "ཁ་བ།"


def test_dotted_label_before_an_english_phrase() -> None:
    sinus = parse(31002, "sinus", "n. 1. སྣ་རུས་ཀྱི་སྦུག་སྟོང་། 2. anat. head ཀཿ སྣ་སྦུབས། ཁཿ ཁྲག་སྦུབས།")
    chuck = parse(6646, "chuck2", "v.t. 1. འཕང་པ། 2. sl. chuck out ཕྱིར་འབུད་པ།")

    assert [(s.sense_no, s.sub_no, s.labels, s.note, s.gloss) for s in sinus.senses[1:]] == [
        (2, 1, ["anat"], "head", "སྣ་སྦུབས།"),
        (2, 2, ["anat"], None, "ཁྲག་སྦུབས།"),
    ]
    assert [(s.labels, s.note, s.gloss) for s in chuck.senses[1:]] == [(["sl"], "chuck out", "ཕྱིར་འབུད་པ།")]


def test_plural_and_verb_forms_before_the_first_sense_are_a_note() -> None:
    mouse = parse(22253, "mouse", "n. pl. mice 1. བྱི་བ། 2. ཙི་གུ")
    give = parse(15186, "give", '"v. gave, given . 1. ཀཿ སྤྲོད་པ། སྟེར་བ། ཁཿ སྦྱིན་པ་གཏོང་བ། 2. སྐུར་བ།"')
    forsake = parse(14259, "forsake", '"v.t. -sook, -saken ཀཿ སྐྱུར་བ། སྤོང་བ། ཁཿ (གྲོགས་ལྟ་བུས།)འདོར་བ།"')
    forswear = parse(14261, "forswear", '"v. -swore,-sworn མནའ་ཚིག་ཟ་བ། དམ་ཚིག་དང་འགལ་བ།"')
    got = parse(15442, "got", "v. get ཡི་འདས་ཚིག get ལ་ལྟོས།")

    assert shape(mouse.senses) == [(1, None, "n", [], "བྱི་བ།"), (2, None, "n", [], "ཙི་གུ")]
    assert [s.note for s in mouse.senses] == ["pl. mice", None]
    assert shape(give.senses) == [
        (1, 1, "v", [], "སྤྲོད་པ། སྟེར་བ།"),
        (1, 2, "v", [], "སྦྱིན་པ་གཏོང་བ།"),
        (2, None, "v", [], "སྐུར་བ།"),
    ]
    assert [s.note for s in give.senses] == ["gave, given", None, None]
    assert [(s.sense_no, s.sub_no, s.note, s.gloss) for s in forsake.senses] == [
        (1, 1, "-sook, -saken", "སྐྱུར་བ། སྤོང་བ།"),
        (1, 2, None, "(གྲོགས་ལྟ་བུས།)འདོར་བ།"),
    ]
    assert [(s.note, s.gloss) for s in forswear.senses] == [("-swore,-sworn", "མནའ་ཚིག་ཟ་བ། དམ་ཚིག་དང་འགལ་བ།")]
    assert [(s.note, s.gloss) for s in got.senses] == [(None, "get ཡི་འདས་ཚིག get ལ་ལྟོས།")]


def test_header_parts_joined_by_and_or_stray_dots() -> None:
    pizzicato = parse(25350, "pizzicato", "adj. and adv. སྒྲ་སྙན་གྱི་རྒྱུད་སྐུད་སོར་མོས་འཐེན་ནས་གཏོང་བའི།")
    behind = parse(3704, "behind", "adv. & phep. 1. རྒྱབ་ཏུ། རྒྱབ་ཕྱོགས། 2. ཕ་རོལ་ན།")
    tourney = parse(34961, "tourney", "n. &. v.i. དུས་རབས་བར་མའི་རྩེད་འགྲན་གྱི་རིགས།")
    awash = parse(2993, "awash", "pred. & .adj. 1. ཆུ་ཁར། ཆུ་སྟེང་དུ།")
    incurve = parse(17572, "incurve", "v. t. 1. ནང་དུ་བཀུག་པ། 2. གུག་གུག་བཟོ་བ།")
    underlie = parse(35887, "underlie", "n.t. 1. འོག་ཏུ་འཛུད་པ། n. geol. འོག་གཞི།")

    assert shape(pizzicato.senses) == [(1, None, "adj,adv", [], "སྒྲ་སྙན་གྱི་རྒྱུད་སྐུད་སོར་མོས་འཐེན་ནས་གཏོང་བའི།")]
    assert shape(behind.senses) == [
        (1, None, "adv,prep", [], "རྒྱབ་ཏུ། རྒྱབ་ཕྱོགས།"),
        (2, None, "adv,prep", [], "ཕ་རོལ་ན།"),
    ]
    assert pos_per_sense(tourney.senses) == ["n,vi"]
    assert pos_per_sense(awash.senses) == ["pred,adj"]
    assert pos_per_sense(incurve.senses) == ["vt", "vt"]
    assert shape(underlie.senses) == [(1, None, "vt", [], "འོག་ཏུ་འཛུད་པ།"), (2, None, "n", ["geol"], "འོག་གཞི།")]
    assert all(s.note is None for row in (pizzicato, behind, tourney, awash, incurve) for s in row.senses)


def test_header_leftovers_are_dropped() -> None:
    full = parse(14632, "full1", '"adj. 1 ཀཿ ཆ་ཚང་གང་བ། ཁེངས་པ། ཁཿ ཡོངས་སུ་ཁྱབ་པ། 2. འབེལ་བོ། མང་པོ།"')
    frankincense = parse(14404, "frankincense", "n. I 1. སྤོས་དཀར། 2. བདུག་སྤོས།")
    unbridled = parse(35791, "unbridled horse", "2. ཀཿ མཚམས་ཚོད་མེད་པའི། ཁཿ དབང་དུ་མ་འདུས་པའི།")

    assert shape(full.senses) == [
        (1, 1, "adj", [], "ཆ་ཚང་གང་བ། ཁེངས་པ།"),
        (1, 2, "adj", [], "ཡོངས་སུ་ཁྱབ་པ།"),
        (2, None, "adj", [], "འབེལ་བོ། མང་པོ།"),
    ]
    assert shape(frankincense.senses) == [(1, None, "n", [], "སྤོས་དཀར།"), (2, None, "n", [], "བདུག་སྤོས།")]
    assert shape(unbridled.senses) == [
        (1, 1, None, [], "མཚམས་ཚོད་མེད་པའི།"),
        (1, 2, None, [], "དབང་དུ་མ་འདུས་པའི།"),
    ]
    assert all(s.note is None for row in (full, frankincense, unbridled) for s in row.senses)
    assert "header_leftover_dropped" in frankincense.warnings + unbridled.warnings


def test_english_lead_before_a_sense_annotates_the_next_sense() -> None:
    revisal = parse(28610, "revisal", "n. revision n. 1. བསྐྱར་བཅོས། 2. ལེགས་བཅོས།")
    congress = parse(7689, "congress", "n. 1. ཚོགས་འདུ། 2. Congress ཀཿ ཀོང་རེ་སི། ཁཿ རྒྱ་གར་ཀོང་རེ་སི་ཚོགས་པ།")
    biography = parse(4082, "biography", "n. 1. རྣམ་ཐར། 2. biographer n. རྣམ་ཐར་འབྲི་མཁན།")

    assert shape(revisal.senses) == [(1, None, "n", [], "བསྐྱར་བཅོས།"), (2, None, "n", [], "ལེགས་བཅོས།")]
    assert [s.note for s in revisal.senses] == ["revision", None]
    assert [(s.sense_no, s.sub_no, s.note) for s in congress.senses] == [(1, None, None), (2, 1, "Congress"), (2, 2, None)]
    assert [(s.sense_no, s.note, s.gloss) for s in biography.senses] == [
        (1, None, "རྣམ་ཐར།"),
        (2, "biographer", "རྣམ་ཐར་འབྲི་མཁན།"),
    ]
    assert "lead_kept_as_note" in revisal.warnings


def test_cross_reference_keeps_the_referenced_word_in_the_gloss() -> None:
    arbitrator = parse(2246, "arbitrator", "n. arbiter 1. ལ་ལྟོས།")
    coxswain = parse(8468, "coxswain", "n. cox n. ལ་ལྟོས།")
    escapement = parse(12262, "escapement", "n. 1. escape n. 3. ལ་ལྟོས། 2. མེ་ཉེན་གཟུར་ཐབས་ཀྱི་ཡོ་ཆས།")
    matt = parse(21128, "matt", '"adj. 1. mat 2. ལ་ལྟོས། Matt, Matthew ཞེས་པའི་མིང་ཟུར།"')

    assert shape(arbitrator.senses) == [(1, None, "n", [], "arbiter ལ་ལྟོས།")]
    assert shape(coxswain.senses) == [(1, None, "n", [], "cox n. ལ་ལྟོས།")]
    assert shape(escapement.senses) == [
        (1, None, "n", [], "escape n. 3. ལ་ལྟོས།"),
        (2, None, "n", [], "མེ་ཉེན་གཟུར་ཐབས་ཀྱི་ཡོ་ཆས།"),
    ]
    assert shape(matt.senses) == [(1, None, "adj", [], "mat ལ་ལྟོས། Matt, Matthew ཞེས་པའི་མིང་ཟུར།")]


def test_bracket_before_a_translation_opens_the_translation() -> None:
    row = parse(14835, "game", "n. 1. རྩེ་འཁོར། We played three games. (རྩེད་མོ།)རྩེ་སྟངས།")

    assert row.senses[0].examples == [ParsedExample("We played three games.", "(རྩེད་མོ།)རྩེ་སྟངས།")]


def test_sense_number_after_a_phrase_opens_its_translation() -> None:
    game = parse(14835, "game", "n. 1. རྩེ་འཁོར། We played three games. 5. (རྩེད་མོ།)རྩེ་སྟངས།")
    black = parse(4157, "black", "v.t. 1. ནག་པོ་བཟོ་བ། 2. སྐྱོན་བརྗོད་པ། to black out 1. མུན་ནག་བཟོ་བ། 2. ནག་འཐོམ་ལང་བ།")

    assert game.senses[0].examples == [ParsedExample("We played three games.", "5. (རྩེད་མོ།)རྩེ་སྟངས།")]
    assert black.senses[1].examples == [ParsedExample("to black out", "1. མུན་ནག་བཟོ་བ། 2. ནག་འཐོམ་ལང་བ།")]


def test_glosses_are_never_empty() -> None:
    row = parse(9997, "x", "n. 1. 2. ཁ་བ། v.t.")

    assert shape(row.senses) == [(1, None, "n", [], "ཁ་བ།")]
