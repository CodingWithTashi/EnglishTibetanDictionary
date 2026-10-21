"""Tibetan → English parser (plan §5.3 "tben" grammar). Inputs are real `tben` rows."""

from __future__ import annotations

import importlib.util

import pytest

from dictbuild.grouping import group_rows
from dictbuild.model import ParsedRow, RelatedCandidate
from dictbuild.packs import MONLAM
from dictbuild.parsers.tben import is_wylie_syllable, parse_tben_row
from dictbuild.related import HeadwordIndex
from dictbuild.source import SourceRow
from dictbuild.wylie import NullConverter, PyewtsConverter, WylieConverter

requires_pyewts = pytest.mark.skipif(importlib.util.find_spec("pyewts") is None, reason="pyewts not installed")

EMPTY_INDEX = HeadwordIndex()
WYLIE_DIVISIONS = "dbye ba/ 1 mtshan nyid yongs su chad pa'i kun brtags/ 2 rnam grangs pa'i kun brtags/"


@pytest.fixture(scope="module")
def pyewts() -> WylieConverter:
    if importlib.util.find_spec("pyewts") is None:
        pytest.skip("pyewts not installed")
    return PyewtsConverter()


def parse(
    source_id: int,
    word: str,
    definition: str,
    converter: WylieConverter | None = None,
    index: HeadwordIndex = EMPTY_INDEX,
) -> ParsedRow:
    return parse_tben_row(SourceRow(source_id, word, definition), converter or NullConverter(), index)


def test_quotes_and_tags() -> None:
    row = parse(4523, "ཁྲོ་བར་བྱེད་", '"{C}insults, abuses"')

    assert (row.headword, row.group_key, row.homonym) == ("ཁྲོ་བར་བྱེད་", "ཁྲོ་བར་བྱེད་", None)
    assert [(s.gloss, s.gloss_lang, s.source_tags) for s in row.senses] == [("insults, abuses", "en", ["C"])]


def test_trailing_citation_tag() -> None:
    row = parse(2203, "ཁ་ཞེ་མི་མཐུན་པ་", "discordance of mouth and heart{TGP-77}")

    assert [(s.gloss, s.source_tags) for s in row.senses] == [("discordance of mouth and heart", ["TGP-77"])]


def test_multi_tags() -> None:
    row = parse(6574, "གདུངས་པར་", "{C}pained; tormented; scorched; {VM} tormented [by]")
    repeated = parse(1071, "ཀུན་སྤྱོད་ ༡", "{C}ideas; habits; befall; nurse; credit with; commit to. {C}habits")

    assert [(s.gloss, s.source_tags) for s in row.senses] == [
        ("pained; tormented; scorched; tormented [by]", ["C", "VM"]),
    ]
    assert repeated.senses[0].source_tags == ["C"]
    assert repeated.senses[0].gloss == "ideas; habits; befall; nurse; credit with; commit to. habits"
    assert repeated.homonym == "༡"


def test_def_prefix() -> None:
    row = parse(114, "ཀ་བ་", "Def.: That which can perform the function of supporting a cross-piece/rafter.", None)
    division = parse(5201, "གཅིག་ ༢", "Divisions: (1) same self isolate; (2) same entity; (3) same type")

    assert [(s.labels, s.gloss, s.gloss_lang) for s in row.senses] == [
        (["definition"], "That which can perform the function of supporting a cross-piece/rafter.", "en"),
    ]
    assert [(s.labels, s.gloss) for s in division.senses] == [
        (["division"], "(1) same self isolate; (2) same entity; (3) same type"),
    ]


def test_def_prefix_rejects_a_conversion_with_warnings(pyewts: WylieConverter) -> None:
    row = parse(114, "ཀ་བ་", "Def.: That which can perform the function of supporting a cross-piece/rafter.", pyewts)

    assert [(s.gloss, s.gloss_lang) for s in row.senses] == [
        ("That which can perform the function of supporting a cross-piece/rafter.", "en"),
    ]
    assert "wylie_conversion_rejected" in row.warnings


@requires_pyewts
def test_wylie_only_conversion(pyewts: WylieConverter) -> None:
    row = parse(861, "ཀུན་བརྟགས་ ༣", WYLIE_DIVISIONS, pyewts)

    assert [(s.labels, s.gloss, s.gloss_lang) for s in row.senses] == [
        (["division"], "༡་མཚན་ཉིད་ཡོངས་སུ་ཆད་པའི་ཀུན་བརྟགས། ༢་རྣམ་གྲངས་པའི་ཀུན་བརྟགས།", "bo"),
    ]
    assert row.wylie == "kun brtags"


def test_english_with_slashes_is_not_converted(pyewts: WylieConverter) -> None:
    # pyewts 0.2.0 converts this without a warning; the Wylie syllable check rejects "understand".
    row = parse(3264, "ཁོང་དུ་ཆུད་དཀའ་བ་", "hard to understand/penetrate/internalize/realize", pyewts)

    assert [(s.gloss, s.gloss_lang) for s in row.senses] == [("hard to understand/penetrate/internalize/realize", "en")]
    assert row.warnings == ["wylie_conversion_rejected"]


@pytest.mark.parametrize("token", ["bsgrubs", "mtshan", "pa'i", "g.yu", "'phags", "mkha'", "rgyal", "Sha", "ba."])
def test_wylie_syllables(token: str) -> None:
    assert is_wylie_syllable(token)


@pytest.mark.parametrize("token", ["understand", "meditate", "ground", "beyd", "which", "base", "of"])
def test_english_words_are_not_wylie_syllables(token: str) -> None:
    assert not is_wylie_syllable(token)


def test_wylie_only_fallback_without_converter() -> None:
    row = parse(861, "ཀུན་བརྟགས་ ༣", WYLIE_DIVISIONS)

    assert [(s.labels, s.gloss, s.gloss_lang) for s in row.senses] == [
        (["division"], "1 mtshan nyid yongs su chad pa'i kun brtags/ 2 rnam grangs pa'i kun brtags/", "en"),
    ]
    assert row.wylie is None


@requires_pyewts
def test_sa_whole(pyewts: WylieConverter) -> None:
    index = HeadwordIndex(tibetan={"bo-en": {"ཀ་པི་": 1_000_099}})

    row = parse(103, "ཀ་པི་ད་", "SA ka pi", pyewts, index)

    assert row.senses == []
    assert row.related == [RelatedCandidate("ཀ་པི་", "see_also", "ka pi", 1_000_099)]


def test_sa_whole_without_converter_keeps_the_wylie() -> None:
    row = parse(103, "ཀ་པི་ད་", "SA ka pi")

    assert row.senses == []
    assert row.related == [RelatedCandidate("ka pi", "see_also", "ka pi", None)]


@requires_pyewts
def test_sa_inline_comma(pyewts: WylieConverter) -> None:
    index = HeadwordIndex(tibetan={"bo-en": {"ཀ་བ་": 1_000_114}})

    row = parse(
        1,
        "ཀ་",
        '"one, pillar, all, the, the very, the beginning, the root, power, all the, indeed, surely, SA ka ba, '
        'symbol of origin of reality, infinitive particle"',
        pyewts,
        index,
    )

    assert [s.gloss for s in row.senses] == [
        "one, pillar, all, the, the very, the beginning, the root, power, all the, indeed, surely, "
        "symbol of origin of reality, infinitive particle"
    ]
    assert row.related == [RelatedCandidate("ཀ་བ་", "see_also", "ka ba", 1_000_114)]


@requires_pyewts
def test_sa_list(pyewts: WylieConverter) -> None:
    index = HeadwordIndex(
        tibetan={
            "bo-en": {"ངོ་རང་ཐོག་ཏུ་སྤྲོད་པ་": 1_000_001, "གདེངས་གྲོལ་ཐོག་ཏུ་འཆའ་བ་": 1_000_003},
            "bo-bo": {"ཐག་གཅིག་ཐོག་ཏུ་ཆོད་པ་": 2_000_002},
        }
    )

    row = parse(
        68127,
        "རྡོ་རྗེའི་ཚིགས་གསུམ་",
        "\"3 vajra verses; SA ngo rang thog tu sprod pa, thag gcig thog tu chod pa, gdengs grol thog tu 'cha' ba\"",
        pyewts,
        index,
    )

    assert [s.gloss for s in row.senses] == ["3 vajra verses"]
    assert row.related == [
        RelatedCandidate("ངོ་རང་ཐོག་ཏུ་སྤྲོད་པ་", "see_also", "ngo rang thog tu sprod pa", 1_000_001),
        RelatedCandidate("ཐག་གཅིག་ཐོག་ཏུ་ཆོད་པ་", "see_also", "thag gcig thog tu chod pa", 2_000_002),
        RelatedCandidate("གདེངས་གྲོལ་ཐོག་ཏུ་འཆའ་བ་", "see_also", "gdengs grol thog tu 'cha' ba", 1_000_003),
    ]


@requires_pyewts
def test_sa_list_in_parentheses(pyewts: WylieConverter) -> None:
    index = HeadwordIndex(
        tibetan={"bo-en": {"ཡུལ་འཁོར་སྲུང་": 1, "འཕགས་སྐྱེས་བུ་": 2, "སྤྱན་མི་བཟང་": 3, "རྣམ་ཐོས་སྲས་": 4}}
    )

    row = parse(
        65583,
        "རྒྱལ་ཆེན་བཞི་",
        "\"4 great kings (SA yul 'khor srung, 'phags skyes bu, spyan mi bzang, rnam thos sras), "
        "1 of rgyal chen ris bzhi\"",
        pyewts,
        index,
    )

    assert [s.gloss for s in row.senses] == ["4 great kings, 1 of rgyal chen ris bzhi"]
    assert [candidate.target_entry_id for candidate in row.related] == [1, 2, 3, 4]


@requires_pyewts
def test_sa_list_stops_at_an_item_that_is_not_a_headword(pyewts: WylieConverter) -> None:
    index = HeadwordIndex(tibetan={"bo-en": {"ཁོལ་མ་": 7}})

    row = parse(17, "ཀ་ཁོལ་མ་", "SA khol ma, historical pillar in lhasa", pyewts, index)

    assert [s.gloss for s in row.senses] == ["historical pillar in lhasa"]
    assert row.related == [RelatedCandidate("ཁོལ་མ་", "see_also", "khol ma", 7)]


@requires_pyewts
def test_sa_longest_prefix_resolution(pyewts: WylieConverter) -> None:
    index = HeadwordIndex(tibetan={"bo-en": {"ཀ་": 1_000_001}, "bo-bo": {"ཀ་སྟེགས་": 2_000_021}})

    row = parse(21, "ཀ་གདན་", "SA ka stegs base or pedestal on which a pillar stands", pyewts, index)

    assert [s.gloss for s in row.senses] == ["base or pedestal on which a pillar stands"]
    assert row.related == [RelatedCandidate("ཀ་སྟེགས་", "see_also", "ka stegs", 2_000_021)]


@requires_pyewts
def test_sa_prefers_bo_en_over_bo_bo(pyewts: WylieConverter) -> None:
    index = HeadwordIndex(tibetan={"bo-en": {"ཀ་བ་": 1_000_114}, "bo-bo": {"ཀ་བ་": 2_000_090}})

    row = parse(64, "ཀ་ཐེ་", "SA ka ba", pyewts, index)

    assert row.related == [RelatedCandidate("ཀ་བ་", "see_also", "ka ba", 1_000_114)]


@requires_pyewts
def test_sa_unresolved_keeps_the_unicode_of_the_wylie(pyewts: WylieConverter) -> None:
    row = parse(10, "ཀ་ཀོ་", "SA ka ko la", pyewts)

    assert row.senses == []
    assert row.related == [RelatedCandidate("ཀ་ཀོ་ལ་", "see_also", "ka ko la", None)]
    assert "sa_unresolved" in row.warnings


@requires_pyewts
def test_sa_unresolved_target_stops_before_english_words(pyewts: WylieConverter) -> None:
    row = parse(17, "ཀ་ཁོལ་མ་", "SA khol ma which stood in lhasa", pyewts)

    assert row.related == [RelatedCandidate("ཁོལ་མ་", "see_also", "khol ma", None)]
    assert [s.gloss for s in row.senses] == ["which stood in lhasa"]


@requires_pyewts
def test_sa_prefix_never_cuts_the_wylie_run(pyewts: WylieConverter) -> None:
    index = HeadwordIndex(tibetan={"bo-en": {"ཁ་": 1_000_001, "སྤུ་": 1_000_002, "སྤུ་ཟིང་": 1_000_003}})

    leather = parse(2414, "ཁ་ལྤགས་", "SA kha pags", pyewts, index)
    bristle = parse(8504, "གཟིལ་བུན་པ་", "SA spu zing byed pa", pyewts, index)

    assert leather.senses == []  # no "pags" gloss
    assert leather.related == [RelatedCandidate("ཁ་པགས་", "see_also", "kha pags", None)]
    assert bristle.senses == []
    assert bristle.related == [RelatedCandidate("སྤུ་ཟིང་བྱེད་པ་", "see_also", "spu zing byed pa", None)]


@requires_pyewts
def test_sa_final_stopword_that_is_wylie_belongs_to_the_run(pyewts: WylieConverter) -> None:
    index = HeadwordIndex(tibetan={"bo-en": {"ཕོན་": 1_030_539, "མི་སྲིད་པ་": 1_046_710}})

    ponto = parse(29450, "པོན་ཏོ་", "SA phon to", pyewts, index)
    denier = parse(37596, "བསྙོན་ཅན་", '"SA mi srid pa, gzhan la bsnyon to"', pyewts, index)

    assert ponto.senses == []  # not ཕོན་ with the gloss "to"
    assert ponto.related == [RelatedCandidate("ཕོན་ཏོ་", "see_also", "phon to", None)]
    assert denier.senses == []
    assert denier.related == [
        RelatedCandidate("མི་སྲིད་པ་", "see_also", "mi srid pa", 1_046_710),
        RelatedCandidate("གཞན་ལ་བསྙོན་ཏོ་", "see_also", "gzhan la bsnyon to", None),
    ]


@requires_pyewts
def test_sa_later_item_of_wylie_syllables_is_a_target(pyewts: WylieConverter) -> None:
    index = HeadwordIndex(tibetan={"bo-en": {"ཁ་སྟེན་": 5}})

    row = parse(14488, "ཆ་ཙེ་", "SA kha sten, tshig sten", pyewts, index)

    assert row.senses == []
    assert row.related == [
        RelatedCandidate("ཁ་སྟེན་", "see_also", "kha sten", 5),
        RelatedCandidate("ཚིག་སྟེན་", "see_also", "tshig sten", None),
    ]


@requires_pyewts
@pytest.mark.parametrize(
    ("definition", "gloss"),
    [
        ("SA grang ba, cold", "cold"),  # 11617: single English words are often valid Wylie
        ("SA bkal ba, spin, send, forward things", "spin, send, forward things"),  # 52141
        ("SA rgas pa, old man", "old man"),  # da drag ("old") is not modern spelling
        ("SA rgas pa, strong wind", "strong wind"),  # "str" does not convert cleanly
        ("SA grang ba, go to", "go to"),  # a final stopword joins the run but never makes its shape
    ],
)
def test_sa_later_english_item_is_gloss(pyewts: WylieConverter, definition: str, gloss: str) -> None:
    index = HeadwordIndex(tibetan={"bo-en": {"གྲང་བ་": 1, "བཀལ་བ་": 2, "རྒས་པ་": 3}})

    row = parse(11617, "གྲང་", definition, pyewts, index)

    assert [sense.gloss for sense in row.senses] == [gloss]
    assert len(row.related) == 1


@requires_pyewts
def test_sa_later_single_syllable_needs_a_tibetan_shape(pyewts: WylieConverter) -> None:
    index = HeadwordIndex(tibetan={"bo-en": {"ཁ་སྟེན་": 5}})

    row = parse(14488, "ཆ་ཙེ་", "SA kha sten, mgo, ra'i", pyewts, index)

    assert row.senses == []
    assert [candidate.wylie for candidate in row.related] == ["kha sten", "mgo", "ra'i"]


def test_truncated_flag() -> None:
    truncated = parse(
        446,
        "ཀུན་ཁྱབ་",
        '"all-pervading, pervades everything, omnipresent, all-pervasive, universal, that which encompasses all '
        "things, void space, sky, four quarters of heaven, he that takes away misery, all-pervading enemy, "
        'snatcher, lord of death, present in everything, to perv"',
    )
    complete = parse(2203, "ཁ་ཞེ་མི་མཐུན་པ་", "discordance of mouth and heart{TGP-77}")

    assert [s.is_truncated for s in truncated.senses] == [True]
    assert [s.is_truncated for s in complete.senses] == [False]


def test_numbered_senses_split_with_their_own_tags() -> None:
    row = parse(
        9193,
        "གཡོ་ ༡",
        "1. it is like a mirage because it fluctuates from moment to moment{TGP 56}; 2. measure of [relative]",
    )

    assert [(s.sense_no, s.gloss, s.source_tags) for s in row.senses] == [
        (1, "it is like a mirage because it fluctuates from moment to moment", ["TGP 56"]),
        (2, "measure of [relative]", []),
    ]


def test_homonym_group_merge() -> None:
    rows = [
        parse(114, "ཀ་བ་", "Def.: That which can perform the function of supporting a cross-piece/rafter."),
        parse(115, "ཀ་བ་ ༡", "mtshan nyid/  gdung 'degs don beyd nus pa"),
        parse(116, "ཀ་བ་ ༢", "pillar; column; pole; post"),
        parse(117, "ཀ་བ་ ༣", '"pillar, post, vein in abdomen, mother of bromton, column, stake, support, trident"'),
    ]

    [entry] = group_rows(MONLAM, "bo-en", rows)

    assert (entry.headword, entry.source_id, entry.entry_id) == ("ཀ་བ་", 114, 1_000_114)
    assert entry.source_ids == [114, 115, 116, 117]
    assert [(s.sense_no, s.source_id, s.labels) for s in entry.senses] == [
        (1, 114, ["definition"]),
        (2, 115, ["definition"]),
        (3, 116, []),
        (4, 117, []),
    ]
    assert entry.senses[1].gloss == "gdung 'degs don beyd nus pa"


def test_numbered_group_without_plain_row() -> None:
    rows = [
        parse(1079, "ཀུན་སློང་ ༢", "motivation; ask; beg; collect"),
        parse(1080, "ཀུན་སློང་ ༣", "{C}manifestation; arising"),
        parse(1081, "ཀུན་སློང་ ༤", '"up-lifting, consequences, emergence, motivation"'),
    ]

    [entry] = group_rows(MONLAM, "bo-en", rows)

    assert (entry.headword, entry.source_id, entry.entry_id) == ("ཀུན་སློང་", 1079, 1_001_079)
    assert [s.sense_no for s in entry.senses] == [1, 2, 3]
    assert [r.homonym for r in rows] == ["༢", "༣", "༤"]
