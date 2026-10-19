"""Headword index and cross-entry link resolution (plan §5.3 "SA" and "= synonym" rules)."""

from __future__ import annotations

import importlib.util

import pytest

from dictbuild.model import EntryDraft, RelatedCandidate
from dictbuild.related import HeadwordIndex, resolve_related
from dictbuild.source import SourceRow
from dictbuild.wylie import NullConverter, PyewtsConverter


def draft(direction_id: str, source_id: int, headword: str, *related: RelatedCandidate) -> EntryDraft:
    return EntryDraft(
        direction_id=direction_id,
        source_id=source_id,
        headword=headword,
        group_key=headword.lower(),
        related=list(related),
    )


@pytest.fixture
def index() -> HeadwordIndex:
    return HeadwordIndex.from_source_rows(
        {
            "tben": [SourceRow(5, "ཀ་བ་", ""), SourceRow(6, "ཀ་བ་ ༡", ""), SourceRow(9, "ཁ།", "")],
            "tbtb": [SourceRow(3, "ཀ་བ་", ""), SourceRow(4, "ག་", "")],
            "entotb": [SourceRow(10, "Lead", ""), SourceRow(11, "lead1", ""), SourceRow(12, "lead pencil", "")],
        }
    )


def test_index_maps_headwords_to_the_first_row_of_their_group(index: HeadwordIndex) -> None:
    assert index.lookup_tibetan("ཀ་བ་") == 1_000_005  # bo-en wins over bo-bo
    assert index.lookup_tibetan("ཁ་") == 1_000_009
    assert index.lookup_tibetan("ག་") == 2_000_004
    assert index.lookup_tibetan("ང་") is None
    assert index.lookup_english("lead") == 3_000_010
    assert index.lookup_english("LEAD") == 3_000_010
    assert index.lookup_english("Lead Pencil") == 3_000_012
    assert index.lookup_english("pencil") is None


def test_index_normalises_tibetan_lookups(index: HeadwordIndex) -> None:
    assert index.lookup_tibetan("ཀ་བ།") == 1_000_005
    assert index.lookup_tibetan("ཀ་བ") == 1_000_005


def test_resolve_english_synonyms(index: HeadwordIndex) -> None:
    entry = draft(
        "en-bo", 11, "lead", RelatedCandidate("lead pencil", "synonym"), RelatedCandidate("graphite", "synonym")
    )

    stats = resolve_related([entry], index, NullConverter())

    assert entry.related == [
        RelatedCandidate("lead pencil", "synonym", None, 3_000_012),
        RelatedCandidate("graphite", "synonym", None, None),
    ]
    assert (stats.resolved, stats.unresolved) == (1, 1)


def test_resolve_tibetan_synonym_from_an_english_entry(index: HeadwordIndex) -> None:
    if importlib.util.find_spec("pyewts") is None:
        pytest.skip("pyewts not installed")
    entry = draft("en-bo", 12, "aardvark", RelatedCandidate("ཀ་བ་", "synonym"))

    resolve_related([entry], index, PyewtsConverter())

    assert entry.related == [RelatedCandidate("ཀ་བ་", "synonym", "ka ba", 1_000_005)]


def test_wylie_targets_without_a_converter_stay_unresolved(index: HeadwordIndex) -> None:
    entry = draft("bo-en", 20, "ཀ་ཐེ་", RelatedCandidate("ka ba", "see_also", "ka ba"))

    resolve_related([entry], index, NullConverter())

    assert entry.related == [RelatedCandidate("ka ba", "see_also", "ka ba", None)]


def test_self_links_are_dropped(index: HeadwordIndex) -> None:
    entry = draft(
        "bo-en",
        5,
        "ཀ་བ་",
        RelatedCandidate("ཀ་བ་", "see_also", "ka ba", 1_000_005),
        RelatedCandidate("ཁ་", "see_also", "kha", 1_000_009),
    )

    stats = resolve_related([entry], index, NullConverter())

    assert entry.related == [RelatedCandidate("ཁ་", "see_also", "kha", 1_000_009)]
    assert stats.self_links == 1


def test_stats_count_by_relation(index: HeadwordIndex) -> None:
    entries = [
        draft("bo-en", 20, "ཀ་ཐེ་", RelatedCandidate("ཀ་བ་", "see_also", "ka ba", 1_000_005)),
        draft("bo-en", 21, "ཀ་ཐོ་", RelatedCandidate("ཀ་ཀོ་ལ་", "see_also", "ka ko la")),
    ]

    stats = resolve_related(entries, index, NullConverter())

    assert stats.by_relation == {"see_also": (1, 2)}
    assert stats.unresolved_samples == {"see_also": [("ཀ་ཐོ་", "ཀ་ཀོ་ལ་")]}
