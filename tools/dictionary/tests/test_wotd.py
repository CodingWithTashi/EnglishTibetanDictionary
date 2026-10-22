"""Word-of-the-day eligibility and seeded ranks (plan §5.3 "WOTD eligibility")."""

from __future__ import annotations

import importlib.util

import pytest

from dictbuild.model import EntryDraft, ParsedSense, RelatedCandidate
from dictbuild.packs import DDC, MONLAM
from dictbuild.related import HeadwordIndex
from dictbuild.wotd import assign_ranks as _assign_ranks
from dictbuild.wotd import is_eligible as _is_eligible
from dictbuild.wylie import PyewtsConverter, WylieConverter


def is_eligible(entry: EntryDraft, converter: WylieConverter | None = None, index: HeadwordIndex | None = None) -> bool:
    return _is_eligible(entry, converter, index, wotd_direction_id=MONLAM.wotd_direction_id)


def assign_ranks(
    entries: list[EntryDraft],
    seed: int,
    converter: WylieConverter | None = None,
    index: HeadwordIndex | None = None,
) -> dict[int, int]:
    return _assign_ranks(entries, seed, converter, index, wotd_direction_id=MONLAM.wotd_direction_id)


def entry(
    headword: str = "བཀྲ་ཤིས་",
    gloss: str = "good fortune",
    *,
    direction_id: str = "bo-en",
    source_id: int = 1,
    gloss_lang: str = "en",
    truncated: bool = False,
    senses: list[ParsedSense] | None = None,
    related: list[RelatedCandidate] | None = None,
) -> EntryDraft:
    if senses is None:
        senses = [ParsedSense(gloss=gloss, gloss_lang=gloss_lang, source_id=source_id, is_truncated=truncated)]
    return EntryDraft(
        direction_id=direction_id,
        source_id=source_id,
        entry_id=(DDC if "dz" in direction_id else MONLAM).entry_id_for(direction_id, source_id),
        headword=headword,
        group_key=headword,
        senses=senses,
        related=related or [],
    )


SEE_ALSO = [RelatedCandidate("ཁ་པགས་", "see_also", "kha pags")]


def test_eligible_entry() -> None:
    assert is_eligible(entry())  # two syllables
    assert is_eligible(entry("རྒྱ་མཚོ་ཆེན་པོ་"))  # four syllables
    assert is_eligible(entry(gloss="sea"))  # three characters
    assert is_eligible(entry(gloss="x" * 100))
    assert is_eligible(entry(gloss="pillar, column; post"))  # punctuation other than / [ ( is fine
    assert is_eligible(entry(gloss="rub"))  # short English is often valid Wylie too
    assert is_eligible(entry(gloss="the hide of a yak", related=SEE_ALSO))


@pytest.mark.parametrize(
    "candidate",
    [
        entry(direction_id="bo-bo"),
        entry(direction_id="en-bo", headword="fortune"),
        entry("ཀ་"),  # one syllable
        entry("ཀ"),  # one syllable without a tsheg
        entry("རྒྱ་མཚོ་ཆེན་པོ་ཞིག་"),  # five syllables
        entry("བསྒྲུབས་བསྒྲུབས་བསྒྲུབས་བསྒྲུབས་"),  # over 24 code points
        entry("ཀ་༡་"),  # Tibetan digit
        entry("ཀ།"),  # shad is not a letter
        entry("བཀྲ་ཤིས\u0f85་"),  # U+0F85 paluta is a mark, not a letter
        entry("ཀaཁ་"),  # Latin letter
        entry(senses=[]),
        entry(gloss="ཀ་བ།", gloss_lang="bo"),
        entry(gloss="ab"),
        entry(gloss="x" * 101),
        entry(gloss="the 2nd month"),  # digit
        entry(gloss="to see/look"),  # slash
        entry(gloss="pillar [of a house]"),  # square bracket
        entry(gloss="pillar (of a house)"),  # parenthesis
        entry(gloss="pags", related=SEE_ALSO),  # Wylie left over from an SA list
        entry(gloss="bzo ba", related=SEE_ALSO),
        entry(truncated=True),
    ],
)
def test_ineligible_entries(candidate: EntryDraft) -> None:
    assert not is_eligible(candidate)


def test_eligibility_uses_pack_direction() -> None:
    draft = entry("རྫོང་ཁ་", "language", direction_id="dz-en", source_id=7)

    assert _is_eligible(draft, None, None, wotd_direction_id="dz-en")
    assert not _is_eligible(draft, None, None, wotd_direction_id="bo-en")
    assert not _is_eligible(entry(direction_id="dz-dz"), None, None, wotd_direction_id="dz-en")
    assert _assign_ranks([draft], 1, None, None, wotd_direction_id="dz-en") == {draft.entry_id: 0}
    assert _assign_ranks([draft], 1, None, None, wotd_direction_id="bo-en") == {}


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


# First glosses of bo-en entries without see-also links that are Tibetan written in Wylie (full build).
WYLIE_GLOSSES = [
    "'jam dbyangs", "red dbal man", "bsnyad pa", "bkag sdom dam po byed pa", "rnam rgyal rgya mtsho",
    "tshad ma'i skyes bu", "dkyil 'khor", "shes rab snying po", "rdo rje 'chang", "gya gya gya",
    "ris su bris dang 'bur du btod lugs su blug pa", "sman btsun chen mo", "dpal ldan dus kyi 'khor lo", "rdo rje",
    "lug sha byed bshags", "rma rnams 'drub pa'i rtsi", "gteg byed kyi nya ga", "red sgrol ma",
    "skabs thog thabs cha byung ba",
    "chom rkun ma, rab rmong ba, ro lang ma, gtum mo, spos ma, sme sha chan, dur khrod ma",
    "spros med, shin tu spros med)", "gza' dug, gnyan dug",
    "tsun tsum pa ti, zla ba'i shug pa, chu bzang po, kun tu 'dres pa)",
    "sngon 'gro gtor ma, bza' ba zas, gtung ba skom, spyod lam rgyun)",
    "rang bzhin lhun gyis grub pa'i dkyil 'khor, rtsa ba byang chub sems kyi dkyil 'khor)",
    "khro ba med pa, mtshang bru med pa, rdeg pa med pa)", "shing rta, rkang thang, rta dmag)",
    "chos sku, stong pa, 'chi kha bar do", "lha mo mkhar zhes pa ste, bod lho phyogs rtsa ri'i gnas kyi ming",
    "'dul ba'i klu 'bum khra bo, gtsang ma'i glu 'bum dkar po)", "sur kar, stag pa, spen dkar, nya sbrid)",
    "bsnos spag 'dam, mnan pa rkang pas brdzis, zan brdzis",
    "mo rlung, ma ning, srog rlung, rtsol rlung, tsha grang rlung, phyi rlung, nang rlung)",
    "ri bong mkhris pa, 'phyi ba'i mkhris pa, nya mkhris",
    "gzugs bzang, longs spyod che, shes rab phun tshogs, dbang phyug ldan, nad med, shin tu tshe ring ba)",
    "gsungs mtshungs pa, chos mtshungs pa, sku mtshungs pa)", "lus kyis 'gro ba, yid ltar mgyogs pas)",
]  # fmt: skip
# English first glosses whose words are also well-formed Wylie (same build): they stay eligible.
ENGLISH_GLOSSES = [
    "rub", "pen", "sand", "he has", "mad dog", "tram car", "we two", "I am", "we can", "two legs", "yes i can",
    "he, she", "dog, god", "pig, hog", "slug, snail", "pass, pass on",
]  # fmt: skip
# The en-bo headwords of the build that those English words are (or are plurals of).
ENGLISH_INDEX = HeadwordIndex(
    english={
        word: 3_000_000 + number
        for number, word in enumerate(
            "rub pen sand he has mad dog tram car we two i am can leg yes she god pig hog slug snail pass on".split()
        )
    }
)
# Words of the Wylie glosses that are English headwords too: one Tibetan word is enough to reject.
ENGLISH_INDEX_WITH_WYLIE_LOOKALIKES = HeadwordIndex(
    english={word: 3_000_100 + number for number, word in enumerate("red man dam shes lug lugs lo".split())}
)


@pytest.fixture(scope="module")
def pyewts() -> WylieConverter:
    if importlib.util.find_spec("pyewts") is None:
        pytest.skip("pyewts not installed")
    return PyewtsConverter()


@pytest.mark.parametrize("gloss", WYLIE_GLOSSES)
def test_first_gloss_in_wylie_is_rejected_without_links(pyewts: WylieConverter, gloss: str) -> None:
    assert is_eligible(entry(gloss=gloss))  # eligible but for the Wylie rule
    assert not is_eligible(entry(gloss=gloss), pyewts, ENGLISH_INDEX_WITH_WYLIE_LOOKALIKES)
    assert not is_eligible(entry(gloss=gloss), pyewts)


@pytest.mark.parametrize("gloss", ENGLISH_GLOSSES)
def test_english_first_gloss_that_is_also_wylie_stays_eligible(pyewts: WylieConverter, gloss: str) -> None:
    assert is_eligible(entry(gloss=gloss), pyewts, ENGLISH_INDEX)


@pytest.mark.parametrize("gloss", ["rub", "pen", "sand"])
def test_short_english_is_not_a_wylie_term(pyewts: WylieConverter, gloss: str) -> None:
    assert is_eligible(entry(gloss=gloss), pyewts)  # no index needed: one syllable without a Tibetan shape


def test_ranks_leave_out_wylie_glosses(pyewts: WylieConverter) -> None:
    entries = [entry(source_id=1, gloss="rdo rje"), entry(source_id=2, gloss="mad dog"), entry(source_id=3)]

    assert sorted(assign_ranks(entries, seed=1, converter=pyewts, index=ENGLISH_INDEX)) == [1_000_002, 1_000_003]
    assert sorted(assign_ranks(entries, seed=1)) == [1_000_001, 1_000_002, 1_000_003]
