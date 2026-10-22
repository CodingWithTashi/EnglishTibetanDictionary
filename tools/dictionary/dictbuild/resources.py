"""The Android resource file that carries facts about a built asset (`res/values/dictionary_stats_<pack_id>.xml`).

The app reads `R.integer.dictionary_entry_count_<pack_id>` to show the bundled entry count while it
installs the pack's asset, and `R.integer.dictionary_asset_bytes_<pack_id>` as the asset's length: the
asset ships compressed in the APK, so the installer cannot ask the APK for its size, yet needs it for
copy progress and to check that the copy is complete. The file is written by the same build that writes
`<pack_id>.db`, before the database is renamed into place, and never by hand.
"""

from __future__ import annotations

import os
from pathlib import Path

ENTRY_COUNT_NAME = "dictionary_entry_count"  # the pack id is appended: dictionary_entry_count_bo
ASSET_BYTES_NAME = "dictionary_asset_bytes"

# An Android <integer> resource is a 32-bit signed int.
MAX_RESOURCE_INTEGER = 2**31 - 1

# XML comments may not contain "--", so the option is named without its dashes.
_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!--
    Facts about the bundled dictionary asset (app/src/main/assets/dictionary/{pack_id}.db). Written by the
    data build (tools/dictionary/build_db.py, option stats-xml) together with the asset; do not edit by hand.
    The installing screen shows the entry count. The installer takes the asset's length in bytes from
    {bytes_name}, because the asset is stored compressed in the APK. 0 means unknown.
-->
<resources>
    <integer name="{count_name}">{count}</integer>
    <integer name="{bytes_name}">{asset_bytes}</integer>
</resources>
"""


def stats_xml(entry_count: int, asset_bytes: int, pack_id: str) -> str:
    """The resource file for pack `pack_id`'s asset of `asset_bytes` bytes with `entry_count` entries across all directions."""
    count_name = f"{ENTRY_COUNT_NAME}_{pack_id}"
    bytes_name = f"{ASSET_BYTES_NAME}_{pack_id}"
    for name, value in ((count_name, entry_count), (bytes_name, asset_bytes)):
        if not 0 <= value <= MAX_RESOURCE_INTEGER:
            raise ValueError(f"{name} must be between 0 and {MAX_RESOURCE_INTEGER}: {value}")
    return _TEMPLATE.format(
        pack_id=pack_id, count_name=count_name, count=entry_count, bytes_name=bytes_name, asset_bytes=asset_bytes
    )


def write_stats_xml(path: Path, entry_count: int, asset_bytes: int, pack_id: str) -> None:
    """Write the resource file to `path` through a temporary file, so a failure leaves `path` as it was."""
    write_text_atomically(path, stats_xml(entry_count, asset_bytes, pack_id))


def write_text_atomically(path: Path, text: str) -> None:
    """Replace `path` with `text` (UTF-8) through a temporary file beside it."""
    temporary = path.with_name(f".{path.name}.tmp")
    try:
        temporary.write_text(text, encoding="utf-8")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)
