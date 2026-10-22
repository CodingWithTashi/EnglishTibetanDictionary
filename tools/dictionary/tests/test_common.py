"""Sense and sub-sense splitting shared by the table parsers (plan §5.3 "Common" grammar)."""

from __future__ import annotations

from dictbuild.parsers.common import (
    Segment,
    split_numbered,
    split_numbered_tibetan,
    split_subsenses,
    split_subsenses_bo,
    split_subsenses_gt,
)


def test_split_numbered_sequence() -> None:
    assert split_numbered("1. ཀ་བ། 2. ཁ་བ། 3. ག་བ།") == [(1, "ཀ་བ།"), (2, "ཁ་བ།"), (3, "ག་བ།")]


def test_split_numbered_glued_marker() -> None:
    text = "1. ཀཿ བརྩེ་བ། ཁཿ བྱམས་པ། 2. འཁྲིག་སྲེད། བརྩེ་དགའ། 3. དགའ་ས། ཆགས་ས། 4.དགའ་ཞེན།"

    assert split_numbered(text) == [
        (1, "ཀཿ བརྩེ་བ། ཁཿ བྱམས་པ།"),
        (2, "འཁྲིག་སྲེད། བརྩེ་དགའ།"),
        (3, "དགའ་ས། ཆགས་ས།"),
        (4, "དགའ་ཞེན།"),
    ]


def test_split_numbered_digit_without_dot_is_text() -> None:
    assert split_numbered("run a wolf 2 miles") is None
    assert split_numbered("1. run a wolf 2 miles 2. to flee") == [(1, "run a wolf 2 miles"), (2, "to flee")]


def test_split_numbered_sequence_must_start_at_one() -> None:
    assert split_numbered("2. ཟད་དུ་འཇུག་པ།") is None
    assert split_numbered("4. རུས་ཁོག 5. རྡོ་སོལ། 6. རྩིག་འདམ།") is None
    assert split_numbered("") is None


def test_split_numbered_leading_text_comes_first() -> None:
    assert split_numbered("(མངོན་བརྗོད།) 1. ཉི་མ། 2. ནམ་མཁའ། 3. མེ་ལོང་།") == [
        (None, "(མངོན་བརྗོད།)"),
        (1, "ཉི་མ།"),
        (2, "ནམ་མཁའ།"),
        (3, "མེ་ལོང་།"),
    ]


def test_split_numbered_out_of_sequence_numbers_stay_text() -> None:
    text = "1. དྲག་པོ། 2. འདོར་བ། 4. མངགས་པ། 3. གློད་པ།"

    assert split_numbered(text) == [(1, "དྲག་པོ།"), (2, "འདོར་བ། 4. མངགས་པ།"), (3, "གློད་པ།")]


def test_split_numbered_marker_glued_after_shad_or_bracket() -> None:
    assert split_numbered("(ལས་ཚིག)1. སྟེར་བ། ལྟ་བུ།2. འདོར་བ།") == [
        (None, "(ལས་ཚིག)"),
        (1, "སྟེར་བ། ལྟ་བུ།"),
        (2, "འདོར་བ།"),
    ]


def test_split_numbered_ignores_decimals_years_and_tibetan_numerals() -> None:
    assert split_numbered("1. ཚད་ 2.5 ཡིན། 2. ལོ་ 1971. ནང་། log2 16=4.") == [
        (1, "ཚད་ 2.5 ཡིན།"),
        (2, "ལོ་ 1971. ནང་། log2 16=4."),
    ]
    assert split_numbered("༡. ཀ་བ། ༢. ཁ་བ།") is None


def test_split_subsenses_bo_with_leading_text() -> None:
    text = "(A) ཀཿ དབྱིན་ཇིའི་གསལ་བྱེད་དང་པོ། ཁཿ རོལ་དབྱངས་ཀྱི་སྒྲ་རྟགས་དྲུག་པ།"

    assert split_subsenses_bo(text) == [
        (None, "(A)"),
        (1, "དབྱིན་ཇིའི་གསལ་བྱེད་དང་པོ།"),
        (2, "རོལ་དབྱངས་ཀྱི་སྒྲ་རྟགས་དྲུག་པ།"),
    ]


def test_split_subsenses_bo_letters_map_to_sub_numbers() -> None:
    assert split_subsenses_bo("ཀཿ ཀ་བ། ཁཿ ཁ་བ། གཿ ག་བ། ངཿ ང་བ། ཅཿ ཅ་བ།") == [
        (1, "ཀ་བ།"),
        (2, "ཁ་བ།"),
        (3, "ག་བ།"),
        (4, "ང་བ།"),
        (5, "ཅ་བ།"),
    ]


def test_split_subsenses_bo_without_markers() -> None:
    assert split_subsenses_bo("བརྩེ་བ། བྱམས་པ།") == [(None, "བརྩེ་བ། བྱམས་པ།")]
    assert split_subsenses_bo("") == []


def test_split_subsenses_bo_visarga_inside_a_word_is_text() -> None:
    assert split_subsenses_bo("ཧེརུཀཿ བདེ་མཆོག") == [(None, "ཧེརུཀཿ བདེ་མཆོག")]
    assert split_subsenses_bo("དཔལ་ཡུལ་དུ་ཀཿ ཐོག་དགོན་པ།") == [(None, "དཔལ་ཡུལ་དུ་ཀཿ ཐོག་དགོན་པ།")]


def test_split_subsenses_bo_glued_marker_and_stray_tsheg() -> None:
    assert split_subsenses_bo("ཀཿ་ཟས་འཇུ་བ། ཁཿ(དེབ་སོགས།)འབག་པ།") == [
        (1, "ཟས་འཇུ་བ།"),
        (2, "(དེབ་སོགས།)འབག་པ།"),
    ]


def test_split_subsenses_gt() -> None:
    assert split_subsenses_gt("1>ཉི་ཚེ། 2> ཕྱོགས་རིས།") == [(1, "ཉི་ཚེ།"), (2, "ཕྱོགས་རིས།")]


def test_split_subsenses_gt_with_leading_text_and_stray_dot() -> None:
    text = "བསླན་གྱི་ད་ལྟ་བ། 1>ལྷན་པ་བརྒྱབ་པ། 2>. དགག་པ་རྒྱག་པ།"

    assert split_subsenses_gt(text) == [
        (None, "བསླན་གྱི་ད་ལྟ་བ།"),
        (1, "ལྷན་པ་བརྒྱབ་པ།"),
        (2, "དགག་པ་རྒྱག་པ།"),
    ]


def test_split_subsenses_gt_without_markers() -> None:
    assert split_subsenses_gt("ཆེ་བའི་ལྡོག་ཟླ།") == [(None, "ཆེ་བའི་ལྡོག་ཟླ།")]
    assert split_subsenses_gt("") == []


def test_split_numbered_number_glued_to_latin_letter_is_text() -> None:
    # Rule "a number glued to a Latin letter is text": source rows 13406 ("the few1.") and 16849.
    assert split_numbered("some few ཁ་ཤས། the few1. གྲངས་ཉུང་། 2. འདེམས་སྒྲུག་བྱས་པའི།") is None
    assert split_numbered("ཆུའི་དོན་སྟོན་ཏེ། hydro-electric1. ཆུ་ཤུགས་གློག་འདོན། 2. ཆུ་སྒྲུལ་གློག་ཤུགས།") is None
    # A number after a dot, a shad or a space is still a marker.
    assert split_numbered("n.1. ཀ་བ། 2. ཁ་བ།") == [(None, "n."), (1, "ཀ་བ།"), (2, "ཁ་བ།")]


def test_splitters_drop_empty_segments() -> None:
    assert split_numbered("1. 2. ཁ་བ། 3. ག་བ།") == [(2, "ཁ་བ།"), (3, "ག་བ།")]
    assert split_subsenses_bo("ཀཿ ཁཿ ཁ་བ།") == [(2, "ཁ་བ།")]
    assert split_subsenses_gt("1> 2>ཁ་བ།") == [(2, "ཁ་བ།")]


def test_split_subsenses_prefers_tibetan_letters_then_gt() -> None:
    assert split_subsenses("ཀཿ ཀ་བ། ཁཿ ཁ་བ།") == [(1, "ཀ་བ།"), (2, "ཁ་བ།")]
    assert split_subsenses("1>ཀ་བ། 2>ཁ་བ།") == [(1, "ཀ་བ།"), (2, "ཁ་བ།")]
    assert split_subsenses("ཀ་བ།") == [(None, "ཀ་བ།")]


def test_split_numbered_tibetan_sequence() -> None:
    assert split_numbered_tibetan("༡ རིལ་རི། ༢ སྒོར་སྒོ། ༣ ཞིབ་བཤལ།") == [
        (1, "རིལ་རི།"),
        (2, "སྒོར་སྒོ།"),
        (3, "ཞིབ་བཤལ།"),
    ]


def test_split_numbered_tibetan_requires_one_first() -> None:
    assert split_numbered_tibetan("༢ x") is None
    assert split_numbered_tibetan("no marker") is None


def test_split_numbered_tibetan_keeps_lead_text() -> None:
    segments = split_numbered_tibetan("noun ༡ ཨའི། ༢ ཨ་ཎེམོ།")

    assert segments == [Segment(None, "noun"), Segment(1, "ཨའི།"), Segment(2, "ཨ་ཎེམོ།")]


def test_split_numbered_tibetan_ten() -> None:
    text = " ".join(f"{n} ཀ" for n in "༡ ༢ ༣ ༤ ༥ ༦ ༧ ༨ ༩ ༡༠".split())

    segments = split_numbered_tibetan(text)

    assert segments is not None
    assert [segment.number for segment in segments] == list(range(1, 11))


def test_split_numbered_tibetan_digit_inside_word_or_out_of_order_is_text() -> None:
    assert split_numbered_tibetan("༡ ཀ༢ཁ ༣ ག ༢ ང") == [(1, "ཀ༢ཁ ༣ ག"), (2, "ང")]
    assert split_numbered_tibetan("༡ ཀ ༡༠") == [(1, "ཀ ༡༠")]
