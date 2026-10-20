"""Shared fixtures: the Room schema exported by the app is the DDL source of truth."""

from __future__ import annotations

from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[3]
DICTIONARY_SCHEMA_DIR = (
    REPO_ROOT / "app" / "schemas" / "com.kharagedition.tibetandictionary.data.local.DictionaryDatabase"
)


@pytest.fixture(scope="session")
def schema_dir() -> Path:
    assert DICTIONARY_SCHEMA_DIR.is_dir(), f"exported Room schema missing: {DICTIONARY_SCHEMA_DIR}"
    return DICTIONARY_SCHEMA_DIR
