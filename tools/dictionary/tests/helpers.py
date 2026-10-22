"""Helpers shared by test modules."""

from __future__ import annotations

import struct
from pathlib import Path

ENTRIES = [("ཀ", "<k>ཀ</k> first"), ("ཁ་བ", "second"), ("ག", "ཚིག་གི third")]


def write_stardict(
    folder: Path,
    entries: list[tuple[str, str]] = ENTRIES,
    *,
    wordcount: int | None = None,
    idxfilesize: int | None = None,
    sametypesequence: str = "x",
    magic: str = "StarDict's dict ifo file",
    idx_extra: bytes = b"",
    dict_cut: int = 0,
) -> Path:
    idx = b""
    dict_ = b""
    for word, definition in entries:
        body = definition.encode("utf-8")
        idx += word.encode("utf-8") + b"\0" + struct.pack(">II", len(dict_), len(body))
        dict_ += body
    idx += idx_extra
    ifo = (
        f"{magic}\nversion=2.4.2\nwordcount={len(entries) if wordcount is None else wordcount}\n"
        f"idxfilesize={len(idx) if idxfilesize is None else idxfilesize}\nbookname=Test\n"
        f"sametypesequence={sametypesequence}\ndescription=Line one\nline two without equals\n"
    )
    prefix = folder / "t"
    prefix.with_suffix(".ifo").write_text(ifo, encoding="utf-8")
    prefix.with_suffix(".idx").write_bytes(idx)
    prefix.with_suffix(".dict").write_bytes(dict_[: len(dict_) - dict_cut])
    return prefix
