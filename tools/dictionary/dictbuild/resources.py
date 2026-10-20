"""The Android resource file that carries facts about the built asset (`res/values/dictionary_stats.xml`).

The app reads `R.integer.dictionary_entry_count` to show the bundled entry count while it installs the
asset, so the file is written by the same build that writes `dictionary.db` and never by hand.
"""

from __future__ import annotations

import os
from pathlib import Path

ENTRY_COUNT_NAME = "dictionary_entry_count"

# XML comments may not contain "--", so the option is named without its dashes.
_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!--
    Facts about the bundled dictionary asset (app/src/main/assets/dictionary/dictionary.db). Written by the
    data build (tools/dictionary/build_db.py, option stats-xml) together with the asset; do not edit by hand.
    The installing screen shows the entry count; 0 means unknown.
-->
<resources>
    <integer name="{name}">{count}</integer>
</resources>
"""


def stats_xml(entry_count: int) -> str:
    """The resource file for an asset with `entry_count` entries across all directions."""
    if entry_count < 0:
        raise ValueError(f"entry count must not be negative: {entry_count}")
    return _TEMPLATE.format(name=ENTRY_COUNT_NAME, count=entry_count)


def write_stats_xml(path: Path, entry_count: int) -> None:
    """Write `stats_xml(entry_count)` to `path` through a temporary file, so a failure leaves it as it was."""
    temporary = path.with_name(f".{path.name}.tmp")
    try:
        temporary.write_text(stats_xml(entry_count), encoding="utf-8")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)
