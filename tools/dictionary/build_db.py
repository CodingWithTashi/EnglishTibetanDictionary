"""Build the app's prebuilt dictionary.db from the Monlam source database (plan §5.3).

The build steps land in T1.3; this entry point already accepts the final command line:

    .venv/bin/python -I build_db.py --source MLDic.ml \\
        --schema-dir ../../app/schemas/com.kharagedition.tibetandictionary.data.local.DictionaryDatabase \\
        --out ../../app/src/main/assets/dictionary/dictionary.db --data-version 2026.10.1
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

# Isolated mode (-I) keeps this script's directory off sys.path; the dictbuild package lives next to it.
sys.path.insert(0, str(Path(__file__).resolve().parent))

import dictbuild  # noqa: E402


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build dictionary.db from the Monlam source database.")
    parser.add_argument("--version", action="version", version=f"dictbuild {dictbuild.__version__}")
    parser.add_argument("--source", type=Path, required=True, help="Monlam source database (MLDic.ml), read-only")
    parser.add_argument("--schema-dir", type=Path, required=True, help="app/schemas/<DictionaryDatabase fqcn>/")
    parser.add_argument("--out", type=Path, required=True, help="dictionary.db to write")
    parser.add_argument("--data-version", required=True, help="data release, e.g. 2026.10.1")
    parser.add_argument("--wotd-seed", type=int, help="seed for the word-of-the-day shuffle")
    parser.add_argument("--require-wylie", action="store_true", help="fail when pyewts is not installed")
    parser.add_argument("--report", type=Path, help="write a JSON build report here")
    parser.add_argument("--limit", type=int, help="parse only the first N rows of each source table")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    parse_args(argv)
    print("build_db: not implemented yet (the build steps land in T1.3)", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
