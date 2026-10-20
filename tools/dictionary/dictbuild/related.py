"""Headword index and cross-entry link resolution (plan §5.3 "SA" and "= synonym" rules).

The index maps every headword the build will write to its entry id, so links resolve without a
second pass over the database: Tibetan targets are looked up in bo-en first, then bo-bo; English
targets by their lower-cased group key in en-bo. Lookups are dictionary hits, never scans.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping
from dataclasses import dataclass, field

from dictbuild.model import EntryDraft, entry_id_for
from dictbuild.parsers.common import has_tibetan
from dictbuild.source import DIRECTION_BY_TABLE, SourceRow
from dictbuild.text import normalise_english_headword, normalise_tibetan_headword
from dictbuild.wylie import WylieConverter

# Rule "SA … resolved by longest Wylie-prefix match against bo-en then bo-bo headwords".
TIBETAN_LOOKUP_ORDER = ("bo-en", "bo-bo")
ENGLISH_DIRECTION = "en-bo"
UNRESOLVED_SAMPLE_SIZE = 20


class HeadwordIndex:
    """Normalised headword → entry id, per direction."""

    def __init__(
        self,
        tibetan: Mapping[str, Mapping[str, int]] | None = None,
        english: Mapping[str, int] | None = None,
    ) -> None:
        self._tibetan = {direction: dict(headwords) for direction, headwords in (tibetan or {}).items()}
        self._english = dict(english or {})

    @classmethod
    def from_source_rows(cls, rows_by_table: Mapping[str, Iterable[SourceRow]]) -> HeadwordIndex:
        """Index the rows the build groups into entries; a key's first row (lowest `_id`) gives the id."""
        tibetan: dict[str, dict[str, int]] = {}
        english: dict[str, int] = {}
        for table, rows in rows_by_table.items():
            direction = DIRECTION_BY_TABLE[table]
            if direction == ENGLISH_DIRECTION:
                for row in rows:
                    if key := normalise_english_headword(row.word)[2]:
                        english.setdefault(key, entry_id_for(direction, row.source_id))
            else:
                headwords = tibetan.setdefault(direction, {})
                for row in rows:
                    if headword := normalise_tibetan_headword(row.word)[0]:
                        headwords.setdefault(headword, entry_id_for(direction, row.source_id))
        return cls(tibetan, english)

    def lookup_tibetan(self, headword: str) -> int | None:
        """The entry id of a Tibetan headword in bo-en, else bo-bo, else None."""
        key = normalise_tibetan_headword(headword)[0]
        for direction in TIBETAN_LOOKUP_ORDER:
            if (entry_id := self._tibetan.get(direction, {}).get(key)) is not None:
                return entry_id
        return None

    def lookup_english(self, text: str) -> int | None:
        """The en-bo entry id of an English headword (case-insensitive, homonym digit ignored)."""
        return self._english.get(normalise_english_headword(text)[2])


@dataclass(slots=True)
class RelatedStats:
    resolved: int = 0
    unresolved: int = 0
    self_links: int = 0
    # relation → (resolved, total)
    by_relation: dict[str, tuple[int, int]] = field(default_factory=dict)
    # relation → (source headword, target headword) of its first unresolved links
    unresolved_samples: dict[str, list[tuple[str, str]]] = field(default_factory=dict)


def resolve_related(entries: Iterable[EntryDraft], index: HeadwordIndex, converter: WylieConverter) -> RelatedStats:
    """Fill `target_entry_id` (and the Wylie of Tibetan targets) and drop links to the entry itself."""
    stats = RelatedStats()
    for entry in entries:
        kept = []
        for candidate in entry.related:
            if candidate.target_entry_id is None:
                candidate.target_entry_id = _lookup(candidate.headword, entry.direction_id, index)
            if candidate.target_entry_id == entry.entry_id:
                stats.self_links += 1
                continue
            if candidate.wylie is None and has_tibetan(candidate.headword):
                candidate.wylie = converter.to_wylie(candidate.headword)
            kept.append(candidate)
            _count(stats, entry, candidate.relation, candidate.headword, candidate.target_entry_id is not None)
        entry.related = kept
    return stats


def _lookup(headword: str, direction_id: str, index: HeadwordIndex) -> int | None:
    if has_tibetan(headword):
        return index.lookup_tibetan(headword)
    if direction_id == ENGLISH_DIRECTION:
        return index.lookup_english(headword)
    return None  # a Wylie target that could not be converted (no converter): not resolvable


def _count(stats: RelatedStats, entry: EntryDraft, relation: str, target: str, resolved: bool) -> None:
    done, total = stats.by_relation.get(relation, (0, 0))
    stats.by_relation[relation] = (done + resolved, total + 1)
    if resolved:
        stats.resolved += 1
        return
    stats.unresolved += 1
    samples = stats.unresolved_samples.setdefault(relation, [])
    if len(samples) < UNRESOLVED_SAMPLE_SIZE:
        samples.append((entry.headword, target))
