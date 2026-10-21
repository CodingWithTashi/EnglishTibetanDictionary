"""Group parsed rows into entries (plan §5.3: homonym rows merge; sense numbers continue across rows).

Rows sharing a group key form one entry, wherever they appear: English homonyms (`lead`, `lead1`,
`lead2`) are not always adjacent, and three bo-en headwords repeat after other rows. The entry keeps
the first row's `_id` (the lowest), headword and Wylie; senses are renumbered so that `sense_no`
runs 1, 2, 3, … over all of the entry's rows, sub-senses keeping their shared number.
"""

from __future__ import annotations

from collections.abc import Iterable
from typing import TYPE_CHECKING

from dictbuild.model import EntryDraft, ParsedRow, RelatedCandidate

if TYPE_CHECKING:
    from dictbuild.packs import Pack


def group_rows(pack: Pack, direction_id: str, rows: Iterable[ParsedRow]) -> list[EntryDraft]:
    """Merge rows by group key, in order of first appearance; rows without a headword are skipped."""
    entries: dict[str, EntryDraft] = {}
    for row in rows:
        if not row.group_key:
            continue
        entry = entries.get(row.group_key)
        if entry is None:
            entry = entries[row.group_key] = EntryDraft(
                direction_id=direction_id,
                source_id=row.source_id,
                entry_id=pack.entry_id_for(direction_id, row.source_id),
                headword=row.headword,
                group_key=row.group_key,
                wylie=row.wylie,
            )
        offset = entry.senses[-1].sense_no if entry.senses else 0
        for sense in row.senses:
            sense.sense_no += offset
            entry.senses.append(sense)
        for candidate in row.related:
            if not any(_same_link(candidate, kept) for kept in entry.related):
                entry.related.append(candidate)
        entry.source_ids.append(row.source_id)
    return list(entries.values())


def _same_link(a: RelatedCandidate, b: RelatedCandidate) -> bool:
    return (a.relation, a.headword) == (b.relation, b.headword)
