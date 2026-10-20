"""Word-of-the-day eligibility and ranks (plan §5.3 "WOTD eligibility").

An eligible entry gets `wotd_rank` = its position in a shuffle seeded by `wotd_seed`; the app shows
the entry whose rank is `floorMod(epochDay + wotd_seed, wotd_count)`. The shuffle orders entries by
SHA-256 of "<seed>:<entry id>", so it is reproducible across Python versions and machines, and an
entry's place relative to another never depends on which other entries are eligible.
"""

from __future__ import annotations

import hashlib
import re
from collections.abc import Iterable

from dictbuild.model import GLOSS_LANG_EN, EntryDraft
from dictbuild.related import HeadwordIndex
from dictbuild.text import syllables
from dictbuild.wylie import WylieConverter, is_wylie_term, is_wylie_text

WOTD_DIRECTION_ID = "bo-en"
DEFAULT_WOTD_SEED = 20261019

MIN_SYLLABLES = 2
MAX_SYLLABLES = 4
MAX_CODE_POINTS = 24
MIN_GLOSS_LENGTH = 3
MAX_GLOSS_LENGTH = 100

# Rule "pure Tibetan letters": consonants, vowel signs and marks, subjoined letters and the tsheg;
# no digits, shad marks, symbols (U+0F85 paluta) or other scripts.
_TIBETAN_LETTERS = re.compile(r"[\u0f40-\u0f6c\u0f71-\u0f84\u0f86\u0f87\u0f8d-\u0fbc\u0f0b]+")
# A first gloss with a digit, "/", "[" or "(" is a reference, a list of alternatives or carries an
# aside ("pillar (of a house)", "see 2"): it reads badly on its own as the word of the day.
_UNSHOWABLE_GLOSS = re.compile(r"[\d/\[(]")
# A first gloss that is Tibetan in Wylie may be a list ("chos sku, stong pa, 'chi kha bar do"); a stray
# closing bracket ends some ("shing rta, rkang thang, rta dmag)").
_LIST_SEPARATOR = re.compile(r"[,;]")
_ITEM_TRIM = " )"


def is_eligible(
    entry: EntryDraft, converter: WylieConverter | None = None, index: HeadwordIndex | None = None
) -> bool:
    """bo-en, 2–4 syllables, ≤24 code points, pure Tibetan letters, first gloss English 3–100 chars
    with no digit, "/", "[" or "(", not truncated, and not Tibetan written in Wylie:
    - on an entry with see-also links, a first gloss of Wylie syllables only ("pags", "bzo ba": the rest
      of an `SA` list) does not count;
    - on any entry, with a `converter`, neither does a first gloss whose every comma-separated item is
      a Wylie term (`is_wylie_term`: "rdo rje", "dkyil 'khor", "gza' dug, gnyan dug"), unless every word
      of it is an English headword of `index` ("mad dog", "we two": short English words are often
      well-formed Wylie)."""
    headword = entry.headword
    if entry.direction_id != WOTD_DIRECTION_ID or len(headword) > MAX_CODE_POINTS:
        return False
    if not _TIBETAN_LETTERS.fullmatch(headword) or not MIN_SYLLABLES <= len(syllables(headword)) <= MAX_SYLLABLES:
        return False
    if not entry.senses or entry.is_truncated:
        return False
    first = entry.senses[0]
    if first.gloss_lang != GLOSS_LANG_EN or not MIN_GLOSS_LENGTH <= len(first.gloss) <= MAX_GLOSS_LENGTH:
        return False
    if _UNSHOWABLE_GLOSS.search(first.gloss):
        return False
    # Short English glosses are often valid Wylie too ("rub", "pen", "sand"), so the loose Wylie test only
    # applies where an `SA` list can have left Wylie behind.
    if entry.related and is_wylie_text(first.gloss):
        return False
    return converter is None or not _is_wylie_gloss(first.gloss, converter, index)


def assign_ranks(
    entries: Iterable[EntryDraft],
    seed: int,
    converter: WylieConverter | None = None,
    index: HeadwordIndex | None = None,
) -> dict[int, int]:
    """Entry id → 0-based rank for every eligible entry, in the seeded shuffle order."""
    eligible = {entry.entry_id for entry in entries if is_eligible(entry, converter, index)}
    ordered = sorted(eligible, key=lambda entry_id: (_shuffle_key(seed, entry_id), entry_id))
    return {entry_id: rank for rank, entry_id in enumerate(ordered)}


def _is_wylie_gloss(gloss: str, converter: WylieConverter, index: HeadwordIndex | None) -> bool:
    """Every comma- or semicolon-separated item is a Wylie term ("rdo rje", "gza' dug, gnyan dug"),
    and not every word is English."""
    items = [item.strip(_ITEM_TRIM) for item in _LIST_SEPARATOR.split(gloss)]
    if not all(items) or not all(is_wylie_term(item, converter) for item in items):
        return False
    return index is None or not all(_is_english_word(word, index) for item in items for word in item.split())


def _is_english_word(word: str, index: HeadwordIndex) -> bool:
    """An en-bo headword, or the plural of one ("legs")."""
    word = word.lower()
    return index.lookup_english(word) is not None or (
        word.endswith("s") and index.lookup_english(word[:-1]) is not None
    )


def _shuffle_key(seed: int, entry_id: int) -> bytes:
    return hashlib.sha256(f"{seed}:{entry_id}".encode("ascii")).digest()
