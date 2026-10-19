"""Grouping source rows into entries: homonyms merge, senses renumber continuously (plan §5.3)."""

from __future__ import annotations

from dictbuild.grouping import group_rows
from dictbuild.model import ParsedRow, ParsedSense, RelatedCandidate
from dictbuild.parsers.entotb import parse_entotb_row
from dictbuild.source import SourceRow


def english(source_id: int, word: str, definition: str) -> ParsedRow:
    return parse_entotb_row(SourceRow(source_id, word, definition))


def tibetan(source_id: int, headword: str, *glosses: str, sub_nos: tuple[int | None, ...] = ()) -> ParsedRow:
    subs = sub_nos or (None,) * len(glosses)
    senses = [
        ParsedSense(gloss=gloss, gloss_lang="en", source_id=source_id, sense_no=1 if sub else number, sub_no=sub)
        for number, (gloss, sub) in enumerate(zip(glosses, subs), start=1)
    ]
    return ParsedRow(source_id=source_id, headword=headword, group_key=headword, senses=senses)


def test_english_homonyms_merge_even_when_not_adjacent() -> None:
    rows = [
        english(19600, "lead", "v.t. 1. ཞ་ཉེ་འགེབས་པ། 2. ལྗིད་འཇོག་པ།3. འཚང་རྒྱག་པ།"),
        english(19605, "leaden", "adj. ཞ་ཉེའི།"),
        english(19608, "lead1", "chem ཞ་ཉེ།"),
        english(19609, "lead1", "n. 1. ཞ་ཉེ། བཤའ་ཚེ། 2. ཞ་སྨྱུག = lead pencil"),
        english(19610, "lead2", "v. led 1. ལམ་སྣ་འཁྲིད་པ། 2. འགོ་འཁྲིད་པ།"),
    ]

    lead, leaden = group_rows("en-bo", rows)

    assert (lead.headword, lead.source_id, lead.entry_id) == ("lead", 19600, 3_019_600)
    assert lead.source_ids == [19600, 19608, 19609, 19610]
    assert [(s.sense_no, s.source_id) for s in lead.senses] == [
        (1, 19600),
        (2, 19600),
        (3, 19600),
        (4, 19608),
        (5, 19609),
        (6, 19609),
        (7, 19610),
        (8, 19610),
    ]
    assert lead.related == [RelatedCandidate("lead pencil", "synonym")]
    assert (leaden.headword, leaden.source_ids) == ("leaden", [19605])


def test_english_grouping_is_case_insensitive_and_strips_the_homonym_digit() -> None:
    rows = [english(17000, "i", "(I) ཀཿ ཡི་གེ་དགུ་པ།"), english(17010, "I1", "pron. ང་།")]

    [entry] = group_rows("en-bo", rows)

    assert (entry.headword, entry.group_key, entry.source_ids) == ("i", "i", [17000, 17010])
    assert [s.sense_no for s in entry.senses] == [1, 2]


def test_tibetan_duplicates_merge_even_when_not_consecutive() -> None:
    rows = [tibetan(1, "ཀ་", "a"), tibetan(2, "ཁ་", "b"), tibetan(3, "ཀ་", "c")]

    first, second = group_rows("bo-en", rows)

    assert (first.headword, first.source_ids, [s.gloss for s in first.senses]) == ("ཀ་", [1, 3], ["a", "c"])
    assert (second.headword, second.source_ids) == ("ཁ་", [2])


def test_sub_senses_keep_sharing_their_renumbered_sense() -> None:
    rows = [tibetan(1, "ཀ་", "a"), tibetan(2, "ཀ་", "b1", "b2", sub_nos=(1, 2))]

    [entry] = group_rows("bo-bo", rows)

    assert [(s.sense_no, s.sub_no, s.gloss) for s in entry.senses] == [(1, None, "a"), (2, 1, "b1"), (2, 2, "b2")]


def test_related_links_merge_without_duplicates() -> None:
    first = tibetan(1, "ཀ་", "a")
    first.related = [RelatedCandidate("ཁ་", "see_also", "kha", 7)]
    second = tibetan(2, "ཀ་", "b")
    second.related = [RelatedCandidate("ཁ་", "see_also", "kha", 7), RelatedCandidate("ག་", "see_also", "ga")]

    [entry] = group_rows("bo-en", [first, second])

    assert entry.related == [RelatedCandidate("ཁ་", "see_also", "kha", 7), RelatedCandidate("ག་", "see_also", "ga")]


def test_wylie_comes_from_the_first_row() -> None:
    first = tibetan(1, "ཀ་", "a")
    first.wylie = "ka"
    second = tibetan(2, "ཀ་", "b")

    [entry] = group_rows("bo-en", [first, second])

    assert entry.wylie == "ka"


def test_rows_without_a_headword_are_skipped() -> None:
    rows = [tibetan(1, "", "a"), tibetan(2, "ཀ་", "b")]

    [entry] = group_rows("bo-en", rows)

    assert entry.source_ids == [2]
