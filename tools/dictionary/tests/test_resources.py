"""The Android stats resource file: names carry the pack id."""

from __future__ import annotations

import pytest

from dictbuild.resources import stats_xml


@pytest.mark.parametrize("pack_id", ["bo", "dz"])
def test_stats_xml_names_carry_pack_id(pack_id: str) -> None:
    text = stats_xml(12, 3456, pack_id)

    assert f'<integer name="dictionary_entry_count_{pack_id}">12</integer>' in text
    assert f'<integer name="dictionary_asset_bytes_{pack_id}">3456</integer>' in text
    assert f"assets/dictionary/{pack_id}.db" in text
    assert "dictionary_asset_bytes_" + pack_id + "," in text  # the comment names the resource too
    assert 'name="dictionary_entry_count"' not in text


def test_stats_xml_rejects_values_outside_an_android_integer() -> None:
    with pytest.raises(ValueError, match="dictionary_entry_count_bo"):
        stats_xml(-1, 0, "bo")
    with pytest.raises(ValueError, match="dictionary_asset_bytes_dz"):
        stats_xml(0, 2**31, "dz")
