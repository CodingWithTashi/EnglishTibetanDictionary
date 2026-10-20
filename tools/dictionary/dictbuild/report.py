"""The JSON build report (plan §5.3: "whitelists + sequence checks + build report keep regressions visible").

It records the inputs, per-table and per-direction counts, link resolution (with a sample of
unresolved see-also targets), parentheticals in label positions that are not whitelisted labels,
parser warnings by kind, and the expected-range checks.
"""

from __future__ import annotations

import dataclasses
import json
from collections import Counter
from collections.abc import Iterable, Mapping, Sequence
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from dictbuild.model import RELATION_SEE_ALSO, DirectionRow, ParsedRow
from dictbuild.related import RelatedStats
from dictbuild.stats import RangeCheck

TOP_UNKNOWN_LABELS = 20
WARNING_SAMPLE_SIZE = 10
_DIRECTION_TEXT_FIELDS = {"id", "source_lang", "target_lang", "source_script", "label_en", "label_bo", "short_label"}


@dataclass(slots=True)
class BuildReport:
    inputs: dict[str, Any]
    table_counts: dict[str, int] = field(default_factory=dict)
    directions: dict[str, dict[str, int]] = field(default_factory=dict)
    related: dict[str, Any] = field(default_factory=dict)
    unresolved_see_also: dict[str, Any] = field(default_factory=dict)
    range_checks: list[dict[str, object]] | None = None
    _unknown_labels: Counter[str] = field(default_factory=Counter)
    _warnings: Counter[str] = field(default_factory=Counter)
    _warning_samples: dict[str, list[int]] = field(default_factory=dict)

    def add_parsed(self, table: str, rows: Iterable[ParsedRow]) -> None:
        """Collect a table's unknown label parentheticals and parser warnings."""
        for row in rows:
            self._unknown_labels.update(row.unknown_labels)
            for kind in dict.fromkeys(row.warnings):
                key = f"{table}:{kind}"
                self._warnings[key] += row.warnings.count(kind)
                sample = self._warning_samples.setdefault(key, [])
                if len(sample) < WARNING_SAMPLE_SIZE:
                    sample.append(row.source_id)

    def add_warning(self, key: str, source_ids: Sequence[int]) -> None:
        """Record a build-level warning (`key` such as "build:empty_entry_dropped") for these source rows."""
        if not source_ids:
            return
        self._warnings[key] += len(source_ids)
        sample = self._warning_samples.setdefault(key, [])
        sample.extend(source_ids[: max(WARNING_SAMPLE_SIZE - len(sample), 0)])

    def add_related(self, stats: RelatedStats) -> None:
        self.related = {
            relation: {"total": total, "resolved": resolved}
            for relation, (resolved, total) in sorted(stats.by_relation.items())
        }
        self.related["self_links_dropped"] = stats.self_links
        resolved, total = stats.by_relation.get(RELATION_SEE_ALSO, (0, 0))
        samples = stats.unresolved_samples.get(RELATION_SEE_ALSO, [])
        self.unresolved_see_also = {
            "count": total - resolved,
            "sample": [{"entry": source, "target": target} for source, target in samples],
        }

    def add_counts(self, table_counts: Mapping[str, int], directions: Sequence[DirectionRow]) -> None:
        self.table_counts = dict(table_counts)
        self.directions = {
            direction.id: {
                name: value
                for name, value in dataclasses.asdict(direction).items()
                if name not in _DIRECTION_TEXT_FIELDS
            }
            for direction in directions
        }

    def add_range_checks(self, checks: Sequence[RangeCheck]) -> None:
        self.range_checks = [check.as_dict() for check in checks]

    def to_dict(self) -> dict[str, Any]:
        return {
            "inputs": self.inputs,
            "limit": self.inputs.get("limit"),
            "table_counts": self.table_counts,
            "directions": self.directions,
            "related": self.related,
            "unresolved_see_also": self.unresolved_see_also,
            "unknown_labels": {
                "count": sum(self._unknown_labels.values()),
                "distinct": len(self._unknown_labels),
                "top": [[label, count] for label, count in self._unknown_labels.most_common(TOP_UNKNOWN_LABELS)],
            },
            "warnings": {
                key: {"count": count, "sample_source_ids": self._warning_samples[key]}
                for key, count in sorted(self._warnings.items())
            },
            "range_checks": self.range_checks if self.range_checks is not None else "skipped (--limit)",
        }

    def write(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.to_dict(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
