"""English → Tibetan parser (plan §5.3 "entotb" grammar). Inputs are real `entotb` rows."""

from __future__ import annotations

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
    assert {s.note for s in row.senses if s.part_of_speech == "vi"} == {"ran, run"}
    assert {s.note for s in row.senses if s.part_of_speech == "vt"} == {None}
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
    assert [s.note for s in letter_a.senses] == ["A", "A"]


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


def test_synonym_marker_with_a_tibetan_term() -> None:
    row = parse(5, "aardvark", "n. ཨཕྲི་ཀའི་(ཨ་ཧྥི་རི་ཀ)ཡུལ་གྱི་སྲོག་ཆགས་གྲོག་འབུ་ཟ་མཁན་ཞིག=ས་ཕག")

    assert row.related == [RelatedCandidate("ས་ཕག་", "synonym")]
    assert row.senses[0].gloss == "ཨཕྲི་ཀའི་(ཨ་ཧྥི་རི་ཀ)ཡུལ་གྱི་སྲོག་ཆགས་གྲོག་འབུ་ཟ་མཁན་ཞིག"


def test_synonym_marker_with_a_single_tibetan_clause() -> None:
    row = parse(37, "abbot", "n. མཁན་པོ། = ཆོས་སྡེའམ་དགོན་པའི་གོ་གནས་ཤིག")

    assert row.related == [RelatedCandidate("ཆོས་སྡེའམ་དགོན་པའི་གོ་གནས་ཤིག་", "synonym")]
    assert row.senses[0].gloss == "མཁན་པོ།"


def test_synonym_marker_inside_brackets_or_before_a_particle_is_text() -> None:
    sum_row = parse(559, "additive identity", '"ངོ་བོའི་སྡོམ་གྲངས་/ཆ(a + a = a), (a = a + a)"')
    see_row = parse(13180, "father", "n. ཕ། = pope ལ་ལྟོས།")

    assert sum_row.related == []
    assert sum_row.senses[0].gloss == "ངོ་བོའི་སྡོམ་གྲངས་/ཆ(a + a = a), (a = a + a)"
    assert see_row.related == []
    assert see_row.senses[0].gloss == "ཕ། = pope ལ་ལྟོས།"


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


def test_example_at_the_start_without_a_previous_sense_stays_gloss() -> None:
    row = parse(9999, "black lead", "black lead བཤའ་ཚེ་ནག་པོ།")

    assert shape(row.senses) == [(1, None, None, [], "black lead བཤའ་ཚེ་ནག་པོ།")]
    assert row.senses[0].examples == []


def test_example_only_sense_moves_its_example_to_the_previous_sense() -> None:
    row = parse(9998, "x", "n. 1. ཀ་བ། 2. some phrase ཁ་བ།")

    assert shape(row.senses) == [(1, None, "n", [], "ཀ་བ།")]
    assert row.senses[0].examples == [ParsedExample("some phrase", "ཁ་བ།")]
    assert "sense_dropped_empty" in row.warnings


def test_glosses_are_never_empty() -> None:
    row = parse(9997, "x", "n. 1. 2. ཁ་བ། v.t.")

    assert shape(row.senses) == [(1, None, "n", [], "ཁ་བ།")]
