"""Word-of-the-day eligibility and seeded ranks (plan §5.3 "WOTD eligibility")."""

from __future__ import annotations

import pytest

from dictbuild.model import EntryDraft, ParsedSense
from dictbuild.wotd import assign_ranks, is_eligible


def entry(
    headword: str = "བཀྲ་ཤིས་",
    gloss: str = "good fortune",
    *,
    direction_id: str = "bo-en",
    source_id: int = 1,
    gloss_lang: str = "en",
    truncated: bool = False,
    senses: list[ParsedSense] | None = None,
) -> EntryDraft:
    if senses is None:
        senses = [ParsedSense(gloss=gloss, gloss_lang=gloss_lang, source_id=source_id, is_truncated=truncated)]
    return EntryDraft(
        direction_id=direction_id, source_id=source_id, headword=headword, group_key=headword, senses=senses
    )


def test_eligible_entry() -> None:
    assert is_eligible(entry())
    assert is_eligible(entry("ཀ་"))  # one syllable
    assert is_eligible(entry("རྒྱ་མཚོ་ཆེན་པོ་"))  # four syllables
    assert is_eligible(entry(gloss="sea"))  # three characters
    assert is_eligible(entry(gloss="x" * 100))


@pytest.mark.parametrize(
    "candidate",
    [
        entry(direction_id="bo-bo"),
        entry(direction_id="en-bo", headword="fortune"),
        entry("རྒྱ་མཚོ་ཆེན་པོ་ཞིག་"),  # five syllables
        entry("བསྒྲུབས་བསྒྲུབས་བསྒྲུབས་བསྒྲུབས་"),  # over 24 code points
        entry("ཀ་༡་"),  # Tibetan digit
        entry("ཀ།"),  # shad is not a letter
        entry("ཀaཁ་"),  # Latin letter
        entry(senses=[]),
        entry(gloss="ཀ་བ།", gloss_lang="bo"),
        entry(gloss="ab"),
        entry(gloss="x" * 101),
        entry(truncated=True),
    ],
)
def test_ineligible_entries(candidate: EntryDraft) -> None:
    assert not is_eligible(candidate)


def test_first_gloss_decides() -> None:
    senses = [
        ParsedSense(gloss="ཀ་བ།", gloss_lang="bo", source_id=1, sense_no=1),
        ParsedSense(gloss="pillar", gloss_lang="en", source_id=2, sense_no=2),
    ]

    assert not is_eligible(entry(senses=senses))
    assert is_eligible(entry(senses=list(reversed(senses))))


def test_ranks_are_a_deterministic_permutation_of_eligible_entries() -> None:
    entries = [entry(source_id=i) for i in range(1, 51)] + [entry(source_id=99, direction_id="bo-bo")]

    ranks = assign_ranks(entries, seed=20261019)

    assert sorted(ranks) == [1_000_000 + i for i in range(1, 51)]
    assert sorted(ranks.values()) == list(range(50))
    assert assign_ranks(list(reversed(entries)), seed=20261019) == ranks
    assert assign_ranks(entries, seed=7) != ranks


def test_ranks_do_not_depend_on_other_entries() -> None:
    small = assign_ranks([entry(source_id=i) for i in range(1, 4)], seed=1)
    large = assign_ranks([entry(source_id=i) for i in range(1, 10)], seed=1)

    order_small = sorted(small, key=small.__getitem__)
    order_large = [entry_id for entry_id in sorted(large, key=large.__getitem__) if entry_id in small]
    assert order_small == order_large
