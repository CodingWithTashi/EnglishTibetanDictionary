"""The dictionary packs: the Monlam setup that used to be module constants, now one `Pack`."""

from __future__ import annotations

import itertools

import pytest

from dictbuild.packs import DDC, MONLAM, PACKS


def test_monlam_pack_matches_previous_constants() -> None:
    assert MONLAM.id == "bo"
    assert PACKS == {"bo": MONLAM, "dz": DDC}
    assert [
        (d.id, d.source_lang, d.target_lang, d.source_script, d.position, d.id_base) for d in MONLAM.directions
    ] == [
        ("bo-en", "bo", "en", "tibt", 0, 1_000_000),
        ("bo-bo", "bo", "bo", "tibt", 1, 2_000_000),
        ("en-bo", "en", "bo", "latn", 2, 3_000_000),
    ]
    assert MONLAM.tables == ("tben", "tbtb", "entotb")
    assert MONLAM.direction_by_table == {"tben": "bo-en", "tbtb": "bo-bo", "entotb": "en-bo"}
    assert MONLAM.tibetan_lookup_order == ("bo-en", "bo-bo")
    assert MONLAM.english_direction == "en-bo"
    assert MONLAM.wotd_direction_id == "bo-en"
    assert MONLAM.meta == {
        "source_name": "Monlam Dictionary (MLDic.ml)",
        "source_author": "Lobsang Monlam",
        "source_url": "https://github.com/iamironrabbit/monlam-dictionary",
        "source_license": "GPL-3.0",
        "attribution_text": (
            "Dictionary data from the Monlam Tibetan Dictionary by Lobsang Monlam, as published in the "
            "open-source Monlam Dictionary app (https://github.com/iamironrabbit/monlam-dictionary) under the "
            "GNU General Public License, version 3 (GPL-3.0)."
        ),
    }
    assert MONLAM.expected.directions == 3
    assert MONLAM.expected.antonyms == (0, 0)


def test_ddc_pack_shape() -> None:
    assert DDC.id == "dz"
    assert [
        (d.id, d.source_lang, d.target_lang, d.source_script, d.position, d.id_base, d.short_label)
        for d in DDC.directions
    ] == [
        ("dz-en", "dz", "en", "tibt", 0, 4_000_000, "རྫོང→EN"),
        ("dz-dz", "dz", "dz", "tibt", 1, 5_000_000, "རྫོང→རྫོང"),
        ("en-dz", "en", "dz", "latn", 2, 6_000_000, "EN→རྫོང"),
    ]
    assert DDC.tables == ("dz-en", "dz-dz", "en-dz")
    assert DDC.direction_by_table == {"dz-en": "dz-en", "dz-dz": "dz-dz", "en-dz": "en-dz"}
    assert DDC.tibetan_lookup_order == ("dz-en", "dz-dz")
    assert DDC.english_direction == "en-dz"
    assert DDC.wotd_direction_id == "dz-en"
    assert DDC.expected.directions == 3
    assert DDC.expected.see_also == (0, 0)
    assert set(DDC.meta) == {"source_name", "source_author", "source_url", "source_license", "attribution_text"}


def test_ddc_parse_dispatches_on_the_table() -> None:
    from dictbuild.related import HeadwordIndex
    from dictbuild.source import SourceRow
    from dictbuild.wylie import NullConverter

    index = HeadwordIndex({}, {})
    rows = {
        "dz-en": SourceRow(1, "ཀ", "<k>ཀ</k> <def><pos>n.</pos> the letter ka</def>"),
        "dz-dz": SourceRow(2, "ཀ", "<k>ཀ </k>གསལ་བྱེད་ཀ།"),
        "en-dz": SourceRow(3, "ka", "<k>ka</k> <pos>n.</pos> ཀ"),
    }
    glosses = {t: DDC.parse(t, row, NullConverter(), index).senses[0].gloss_lang for t, row in rows.items()}
    assert glosses == {"dz-en": "en", "dz-dz": "dz", "en-dz": "dz"}
    with pytest.raises(ValueError):
        DDC.parse("tben", rows["dz-en"], NullConverter(), index)


def test_ddc_read_source_reads_stardict_and_orders_case_duplicates(tmp_path) -> None:
    from test_stardict import write_stardict

    for name, entries in {
        "dz-en": [("ཀ", "x")],
        "dz-dz": [("ཁ", "y")],
        "en-dz": [("A", "ཀ"), ("a", "ཁ")],
    }.items():
        folder = tmp_path / f"stardict-{name}"
        folder.mkdir()
        write_stardict(folder, entries)
        for suffix in (".ifo", ".idx", ".dict"):
            (folder / f"t{suffix}").rename(folder / f"{name}{suffix}")

    rows = DDC.read_source(tmp_path, None)

    assert list(rows) == ["dz-en", "dz-dz", "en-dz"]
    assert [row.word for row in rows["en-dz"]] == ["a", "A"]
    assert [row.word for row in DDC.read_source(tmp_path, 1)["en-dz"]] == ["A"]


def test_entry_id_for_adds_id_base() -> None:
    assert MONLAM.entry_id_for("bo-en", 114) == 1_000_114
    assert MONLAM.entry_id_for("bo-bo", 3) == 2_000_003
    assert MONLAM.entry_id_for("en-bo", 19_600) == 3_019_600


def test_packs_have_disjoint_direction_ids_and_id_bases() -> None:
    for first, second in itertools.combinations(PACKS.values(), 2):
        assert {d.id for d in first.directions}.isdisjoint(d.id for d in second.directions)
        assert {d.id_base for d in first.directions}.isdisjoint(d.id_base for d in second.directions)
    for pack in PACKS.values():
        assert len({d.id_base for d in pack.directions}) == len(pack.directions)
        assert set(pack.direction_by_table.values()) <= {d.id for d in pack.directions}


def test_unknown_direction_raises_key_error() -> None:
    assert MONLAM.direction("bo-en").id_base == 1_000_000
    with pytest.raises(KeyError):
        MONLAM.direction("dz-en")
    with pytest.raises(KeyError):
        MONLAM.entry_id_for("dz-en", 1)
    assert DDC.entry_id_for("dz-dz", 12_063) == 5_012_063


def test_pack_option_is_required_and_limited_to_known_packs(tmp_path, capsys) -> None:
    import build_db

    common = ["--source", "s", "--schema-dir", "d", "--out", str(tmp_path / "o.db"), "--data-version", "t"]
    for extra in ([], ["--pack", "xx"]):
        with pytest.raises(SystemExit) as stop:
            build_db.parse_args([*common, *extra])
        assert stop.value.code == 2
    assert "--pack" in capsys.readouterr().err
    assert build_db.parse_args([*common, "--pack", "bo"]).pack == "bo"
