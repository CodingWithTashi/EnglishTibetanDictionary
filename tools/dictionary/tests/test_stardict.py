"""StarDict reader: `.ifo` header checks and `.idx`/`.dict` records."""

from __future__ import annotations

import os
import struct
from pathlib import Path

import pytest

from dictbuild.source import SourceRow
from dictbuild.stardict import StarDictError, read_stardict

SOURCE_ENV = "DZONGKHA_SOURCE"
SOURCE = Path(os.environ[SOURCE_ENV]) if os.environ.get(SOURCE_ENV) else None

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


def test_reads_entries_in_idx_order(tmp_path: Path) -> None:
    rows = read_stardict(write_stardict(tmp_path))

    assert rows == [SourceRow(i, word, definition) for i, (word, definition) in enumerate(ENTRIES, start=1)]


def test_wordcount_mismatch_raises(tmp_path: Path) -> None:
    prefix = write_stardict(tmp_path, wordcount=4)

    with pytest.raises(StarDictError, match=r"t\.ifo.*wordcount.*4.*3"):
        read_stardict(prefix)


def test_idxfilesize_mismatch_raises(tmp_path: Path) -> None:
    prefix = write_stardict(tmp_path, idxfilesize=5)

    with pytest.raises(StarDictError, match=r"idxfilesize"):
        read_stardict(prefix)


def test_sametypesequence_must_be_x(tmp_path: Path) -> None:
    with pytest.raises(StarDictError, match=r"sametypesequence.*'m'"):
        read_stardict(write_stardict(tmp_path, sametypesequence="m"))


def test_missing_magic_raises(tmp_path: Path) -> None:
    with pytest.raises(StarDictError, match=r"t\.ifo.*magic"):
        read_stardict(write_stardict(tmp_path, magic="nope"))


def test_record_past_end_of_dict_raises(tmp_path: Path) -> None:
    with pytest.raises(StarDictError, match=r"t\.dict"):
        read_stardict(write_stardict(tmp_path, dict_cut=2))


def test_truncated_idx_record_raises(tmp_path: Path) -> None:
    # idxfilesize matches the file, but the last record has no room for its offset and size.
    prefix = write_stardict(tmp_path, wordcount=4, idx_extra=b"abc\0\0\0")

    with pytest.raises(StarDictError, match=r"t\.idx"):
        read_stardict(prefix)


def test_invalid_utf8_raises(tmp_path: Path) -> None:
    prefix = write_stardict(tmp_path)
    prefix.with_suffix(".dict").write_bytes(b"\xff" * len(prefix.with_suffix(".dict").read_bytes()))

    with pytest.raises(StarDictError, match=r"t\.dict.*UTF-8"):
        read_stardict(prefix)


def test_invalid_utf8_in_idx_word_raises(tmp_path: Path) -> None:
    prefix = write_stardict(tmp_path)
    idx = prefix.with_suffix(".idx")
    idx.write_bytes(b"\xff" + idx.read_bytes()[1:])

    with pytest.raises(StarDictError, match=r"t\.idx.*record 1.*UTF-8"):
        read_stardict(prefix)


@pytest.mark.parametrize("suffix", [".ifo", ".idx", ".dict"])
def test_missing_file_raises(tmp_path: Path, suffix: str) -> None:
    prefix = write_stardict(tmp_path)
    prefix.with_suffix(suffix).unlink()

    with pytest.raises(StarDictError, match=rf"t\{suffix}"):
        read_stardict(prefix)


def test_empty_key_is_returned(tmp_path: Path) -> None:
    rows = read_stardict(write_stardict(tmp_path, [("", "orphan"), ("a", "b")]))

    assert rows == [SourceRow(1, "", "orphan"), SourceRow(2, "a", "b")]


def test_limit_reads_first_n(tmp_path: Path) -> None:
    prefix = write_stardict(tmp_path, wordcount=99)  # wordcount is not checked with a limit

    assert read_stardict(prefix, limit=2) == [SourceRow(1, *ENTRIES[0]), SourceRow(2, *ENTRIES[1])]


def test_limit_still_checks_idxfilesize(tmp_path: Path) -> None:
    with pytest.raises(StarDictError, match=r"idxfilesize"):
        read_stardict(write_stardict(tmp_path, idxfilesize=5), limit=1)


@pytest.mark.slow
@pytest.mark.skipif(
    SOURCE is None or not SOURCE.is_dir(),
    reason=f"set {SOURCE_ENV} to the folder holding the three DDC stardict-* folders to read the real files",
)
def test_reads_real_ddc_files() -> None:
    assert SOURCE is not None
    expected = {"dz-en": 35543, "en-dz": 23237, "dz-dz": 22787}
    for name, count in expected.items():
        rows = read_stardict(SOURCE / f"stardict-{name}" / name)
        assert len(rows) == count, name
        assert [row.source_id for row in rows] == list(range(1, count + 1))
