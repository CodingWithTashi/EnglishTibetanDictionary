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
from dictbuild.text import syllables

WOTD_DIRECTION_ID = "bo-en"
DEFAULT_WOTD_SEED = 20261019

MIN_SYLLABLES = 2
MAX_SYLLABLES = 4
MAX_CODE_POINTS = 24
MIN_GLOSS_LENGTH = 3
MAX_GLOSS_LENGTH = 100

# Rule "pure Tibetan letters": consonants, vowel signs and marks, subjoined letters and the tsheg;
# no digits, shad marks, symbols or other scripts.
_TIBETAN_LETTERS = re.compile(r"[\u0f40-\u0f6c\u0f71-\u0f87\u0f8d-\u0fbc\u0f0b]+")
# A first gloss with a digit, "/", "[" or "(" is a reference, a list of alternatives or carries an
# aside ("pillar (of a house)", "see 2"): it reads badly on its own as the word of the day.
_UNSHOWABLE_GLOSS = re.compile(r"[\d/\[(]")


def is_eligible(entry: EntryDraft) -> bool:
    """bo-en, 2–4 syllables, ≤24 code points, pure Tibetan letters, first gloss English 3–100 chars
    with no digit, "/", "[" or "(", not truncated."""
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
    return not _UNSHOWABLE_GLOSS.search(first.gloss)


def assign_ranks(entries: Iterable[EntryDraft], seed: int) -> dict[int, int]:
    """Entry id → 0-based rank for every eligible entry, in the seeded shuffle order."""
    eligible = {entry.entry_id for entry in entries if is_eligible(entry)}
    ordered = sorted(eligible, key=lambda entry_id: (_shuffle_key(seed, entry_id), entry_id))
    return {entry_id: rank for rank, entry_id in enumerate(ordered)}


def _shuffle_key(seed: int, entry_id: int) -> bytes:
    return hashlib.sha256(f"{seed}:{entry_id}".encode("ascii")).digest()
