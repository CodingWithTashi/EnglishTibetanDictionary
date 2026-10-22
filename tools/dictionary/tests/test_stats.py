"""Expected-range checks of a full build (plan §5.3 "Expected after build")."""

from __future__ import annotations

import dataclasses

import pytest

from dictbuild.model import RelatedEntryRow
from dictbuild.packs import DDC, MONLAM
from dictbuild.stats import check_ranges
from dictbuild.writer import ContentRows

EXPECTED = MONLAM.expected


def directions(**overrides: dict[str, int]):
    """Direction rows whose counts sit inside every expected range, with per-direction overrides."""
    rows = []
    for direction in MONLAM.directions:
        counts = {
            "entry_count": EXPECTED.entries[direction.id][0],
            "sense_count": EXPECTED.senses[direction.id][0],
            "example_count": EXPECTED.examples.get(direction.id, (0, 0))[0],
            "wylie_count": EXPECTED.entries[direction.id][0] if direction.id != "en-bo" else 0,
            "truncated_count": 519 if direction.id == "bo-en" else 0,
            "wotd_count": EXPECTED.wotd[0] if direction.id == "bo-en" else 0,
        }
        counts.update(overrides.get(direction.id.replace("-", "_"), {}))
        rows.append(dataclasses.replace(direction, **counts))
    return rows


def content(see_also: int = 4_500, resolved: int = 4_000, synonyms: int = 17, antonyms: int = 0) -> ContentRows:
    related = [
        RelatedEntryRow(
            id=i + 1,
            source_entry_id=1_000_001,
            target_entry_id=1_000_002 if i < resolved else None,
            target_headword="ཀ་",
            relation="see_also",
            position=i,
        )
        for i in range(see_also)
    ]
    related += [
        RelatedEntryRow(
            id=see_also + i + 1,
            source_entry_id=3_000_001,
            target_entry_id=None,
            target_headword="pillar",
            relation="synonym",
            position=i,
        )
        for i in range(synonyms)
    ]
    related += [
        RelatedEntryRow(
            id=see_also + synonyms + i + 1,
            source_entry_id=3_000_001,
            target_entry_id=None,
            target_headword="friend",
            relation="antonym",
            position=i,
        )
        for i in range(antonyms)
    ]
    return ContentRows(related_entries=related)


def failing(checks) -> set[str]:
    return {check.name for check in checks if not check.ok}


def test_counts_inside_every_range_pass() -> None:
    checks = check_ranges(MONLAM, directions(), content(), wylie_available=True)

    assert failing(checks) == set()
    assert sum(1 for check in checks if check.name == "entries.wylie non-null" and check.value == EXPECTED.wylie) == 1


@pytest.mark.parametrize(
    ("overrides", "rows", "name"),
    [
        ({"bo_en": {"entry_count": 76_828}}, {}, "entries bo-en"),
        ({"en_bo": {"entry_count": 35_889}}, {}, "entries en-bo"),
        ({"bo_bo": {"sense_count": 64_999}}, {}, "senses bo-bo"),
        ({"en_bo": {"example_count": 1_999}}, {}, "examples en-bo"),
        ({}, {"see_also": 5_001, "resolved": 4_000}, "related see_also"),
        ({}, {"see_also": 4_500, "resolved": 3_000}, "related see_also resolved %"),
        ({}, {"synonyms": 41}, "related synonym"),
        ({}, {"antonyms": 1}, "related antonym"),
        ({"bo_bo": {"wylie_count": 55_123}}, {}, "entries.wylie non-null"),
        ({"bo_en": {"truncated_count": 601}}, {}, "senses.is_truncated"),
        ({"bo_en": {"wotd_count": 60_001}}, {}, "wotd_rank non-null"),
    ],
)
def test_a_count_outside_its_range_fails_that_check_only(overrides, rows, name: str) -> None:
    checks = check_ranges(MONLAM, directions(**overrides), content(**rows), wylie_available=True)

    assert failing(checks) == {name}


def test_without_pyewts_wylie_must_be_zero_and_resolution_is_not_checked() -> None:
    no_wylie = directions(bo_en={"wylie_count": 0}, bo_bo={"wylie_count": 0})

    checks = check_ranges(MONLAM, no_wylie, content(resolved=0), wylie_available=False)

    assert failing(checks) == set()
    assert "related see_also resolved %" not in {check.name for check in checks}


def ddc_directions():
    return [
        dataclasses.replace(
            direction,
            entry_count=DDC.expected.entries[direction.id][0],
            sense_count=DDC.expected.senses[direction.id][0],
            wylie_count=DDC.expected.entries[direction.id][0] if direction.id != "en-dz" else 0,
            wotd_count=DDC.expected.wotd[0] if direction.id == "dz-en" else 0,
        )
        for direction in DDC.directions
    ]


def test_ddc_ranges_include_antonyms() -> None:
    syn_low, syn_high = DDC.expected.synonyms
    ant_low, ant_high = DDC.expected.antonyms
    base = {"see_also": 0, "resolved": 0, "synonyms": syn_low, "antonyms": ant_low}
    ok = check_ranges(DDC, ddc_directions(), content(**base), wylie_available=True)
    assert failing(ok) == set()
    assert "related see_also resolved %" not in {check.name for check in ok}
    assert {check.name: check.value for check in ok}["related antonym"] == ant_low

    for rows, name in (
        ({"antonyms": ant_low - 1}, "related antonym"),
        ({"antonyms": ant_high + 1}, "related antonym"),
        ({"synonyms": syn_low - 1}, "related synonym"),
        ({"synonyms": syn_high + 1}, "related synonym"),
    ):
        assert failing(check_ranges(DDC, ddc_directions(), content(**{**base, **rows}), wylie_available=True)) == {name}
    see_also = content(**{**base, "see_also": 1, "resolved": 1})
    assert failing(check_ranges(DDC, ddc_directions(), see_also, wylie_available=True)) == {"related see_also"}
