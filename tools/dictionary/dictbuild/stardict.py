"""Reader for StarDict dictionaries (`.ifo`, `.idx`, uncompressed `.dict`) with `sametypesequence=x`."""

from __future__ import annotations

import struct
from pathlib import Path

from dictbuild.source import SourceRow

_MAGIC = "StarDict's dict ifo file"
_OFFSET_AND_SIZE = struct.Struct(">II")


class StarDictError(ValueError):
    """A StarDict file does not match its header or is cut short; the message names the file."""


def read_stardict(prefix: Path, *, limit: int | None = None) -> list[SourceRow]:
    """Entries of the StarDict at `prefix` (the path without extension) in `.idx` order.

    `source_id` is the 1-based ordinal in the `.idx`. The header's `wordcount` must equal the number of
    entries read, except with `limit` (only the first N entries are read); `idxfilesize` must always
    equal the size of the `.idx`. Entries with an empty key are returned as they are.
    """
    ifo_path, idx_path, dict_path = (prefix.parent / (prefix.name + suffix) for suffix in (".ifo", ".idx", ".dict"))
    header = _read_header(ifo_path)
    if header.get("sametypesequence") != "x":
        raise StarDictError(f"{ifo_path}: expected sametypesequence 'x', got {header.get('sametypesequence')!r}")
    if "idxoffsetbits" in header and header["idxoffsetbits"] != "32":
        raise StarDictError(f"{ifo_path}: expected 32-bit offsets, got idxoffsetbits {header['idxoffsetbits']!r}")
    wordcount = _int_field(ifo_path, header, "wordcount")
    idxfilesize = _int_field(ifo_path, header, "idxfilesize")

    idx = idx_path.read_bytes()
    if len(idx) != idxfilesize:
        raise StarDictError(f"{idx_path}: {ifo_path.name} says idxfilesize {idxfilesize}, the file has {len(idx)} bytes")
    data = dict_path.read_bytes()

    rows: list[SourceRow] = []
    position = 0
    while position < len(idx) and (limit is None or len(rows) < limit):
        ordinal = len(rows) + 1
        end = idx.find(b"\0", position)
        if end < 0 or end + 1 + _OFFSET_AND_SIZE.size > len(idx):
            raise StarDictError(f"{idx_path}: record {ordinal} at byte {position} runs past the end of the file")
        word = _decode(idx[position:end], idx_path, ordinal)
        offset, size = _OFFSET_AND_SIZE.unpack_from(idx, end + 1)
        position = end + 1 + _OFFSET_AND_SIZE.size
        if offset + size > len(data):
            raise StarDictError(
                f"{dict_path}: record {ordinal} ({offset}+{size}) runs past the end of the file ({len(data)} bytes)"
            )
        rows.append(SourceRow(ordinal, word, _decode(data[offset : offset + size], dict_path, ordinal)))

    if limit is None and len(rows) != wordcount:
        raise StarDictError(f"{ifo_path}: wordcount is {wordcount}, but {idx_path.name} has {len(rows)} records")
    return rows


def _read_header(path: Path) -> dict[str, str]:
    """`key=value` lines of an `.ifo`; a line without `=` continues the previous value."""
    lines = _decode(path.read_bytes(), path, None).splitlines()
    if not lines or lines[0].strip() != _MAGIC:
        raise StarDictError(f"{path}: the first line is not the magic line {_MAGIC!r}")
    header: dict[str, str] = {}
    last_key: str | None = None
    for line in lines[1:]:
        key, separator, value = line.partition("=")
        if separator:
            last_key = key.strip()
            header[last_key] = value.strip()
        elif last_key is not None and line.strip():
            header[last_key] += "\n" + line.strip()
    return header


def _int_field(path: Path, header: dict[str, str], key: str) -> int:
    value = header.get(key, "")
    if not value.isdecimal():
        raise StarDictError(f"{path}: {key} must be a number, got {value!r}")
    return int(value)


def _decode(raw: bytes, path: Path, ordinal: int | None) -> str:
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError as error:
        where = "" if ordinal is None else f" record {ordinal}"
        raise StarDictError(f"{path}:{where} is not valid UTF-8 ({error.reason} at byte {error.start})") from error
