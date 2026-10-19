# dictionary.db build pipeline

Turns the Monlam source database (`MLDic.ml`, tables `entotb`, `tben`, `tbtb`) into the app's
prebuilt, read-only `app/src/main/assets/dictionary/dictionary.db`.

## Setup

```sh
cd tools/dictionary
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
```

`pyewts` (Wylie conversion) is optional at build time. Without it the build still succeeds, but
`entries.wylie` stays NULL. Pass `--require-wylie` to make a missing `pyewts` an error.

> **Note:** Homebrew's `python@3.14` (3.14.6) can fail to bootstrap pip with
> `Symbol not found: _XML_SetAllocTrackerActivationThreshold`. Its `pyexpat` needs a newer
> libexpat than the macOS one. If that happens, create the venv with `python3.13 -m venv .venv`
> instead. The pipeline needs only the standard library's `sqlite3` with FTS4, plus the packages
> in `requirements.txt`.

## Tests

```sh
.venv/bin/python -I -m pytest -q
```

`-I` (isolated mode) ignores `PYTHON*` variables and user site-packages. `pyproject.toml` puts the
package root on `sys.path` and turns warnings into errors.

## Build

```sh
.venv/bin/python -I build_db.py \
  --source /path/to/MLDic.ml \
  --schema-dir ../../app/schemas/com.kharagedition.tibetandictionary.data.local.DictionaryDatabase \
  --out ../../app/src/main/assets/dictionary/dictionary.db \
  --data-version 2026.10.1 [--wotd-seed 20261019] [--require-wylie] [--report out.json] [--limit N]
```

The build steps are not implemented yet (T1.3). `build_db.py` currently only parses its arguments.

## Schema

The DDL is never hand-written. `dictbuild.schema` loads the highest `<version>.json` that Room
exports to `app/schemas/…DictionaryDatabase/`. It then executes, verbatim:

1. every entity's `createSql`, with `${TABLE_NAME}` substituted;
2. each entity's `indices[].createSql`;
3. the FTS `contentSyncTriggers`.

After that it sets `PRAGMA user_version` to the schema version. The `INSERT` statements are generated
from `fields[].columnName`.

Room's `setupQueries` (`room_master_table`) are not run, so the asset carries no `room_master_table`.
The FTS tables use `unicode61` with Tibetan `tokenchars`, so one token is one syllable.

## Layout

| Module | Responsibility |
|---|---|
| `dictbuild/schema.py` | Load the Room schema export, run its DDL, set `user_version`, generate `INSERT`s |
| `dictbuild/source.py` | Open the source read-only and read rows in `_id` order |
| `dictbuild/text.py` | Shared text rules (see below) |
| `dictbuild/wylie.py` | `WylieConverter` protocol: `PyewtsConverter`, `NullConverter`, `load_converter(require)` |
| `dictbuild/model.py` | Row dataclasses that mirror the schema columns, plus parser output records |
| `dictbuild/parsers/common.py` | Split numbered senses and sub-senses |
| `build_db.py` | Command line (build steps in T1.3) |

T1.3 adds `parsers/{entotb,labels_en,tben,tbtb,labels_bo}.py` and `grouping`, `related`, `wotd`,
`stats`, `writer` and `report`.

## Shared grammar

**`preprocess`**
- NBSP → space.
- U+200B and U+FEFF are dropped.
- U+0F0C → U+0F0B.
- Whitespace runs collapse to one space, and the text is trimmed.

**`strip_csv_quotes`**
- Removes the `"…"` wrapper, or a lone leading `"`.
- Unescapes `""` → `"`.

**`normalise_tibetan_headword`**
- Returns `(headword, homonym marker)`.
- A trailing ` ༡`, `༡` after U+0F0C, ` ༡༡༡`, `1` or `༠` becomes the marker.
- The headword ends in exactly one tsheg: `ཀུང་ཧྲེ` → `ཀུང་ཧྲེ་`, `གཞའ།` → `གཞའ་`.

**`normalise_english_headword`**
- Returns `(display, homonym digit, lower-cased group key)`.
- A digit glued to a final letter is a homonym marker, even after a single letter: `lead1` → `lead`, `1`
  and `I1` → `I`, `1`.
- Headwords in `ENGLISH_HOMONYM_EXCEPTIONS` keep their digit: `K2` (the mountain) stays `K2`, with
  group key `k2`.
- Spaced numbers stay in the headword: `calender 1`.
- With this rule en-bo has 35,888 entries.

**`is_truncated(raw)`**
- True when the raw field is at least 250 characters long and its cleaned text does not end in
  `. ! ? ) ] ། "`.

**`split_numbered`**
- `1. 2. 3.` count as markers only as a sequence that starts at 1. Other numbers stay text.
- A glued marker is accepted: `4.དགའ་ཞེན།`.
- A digit without a dot is text: `run a wolf 2 miles`.
- Decimals, years and Tibetan numerals are never markers.

**`split_subsenses_bo`**
- `ཀཿ ཁཿ གཿ ངཿ ཅཿ` → sub numbers 1–5, but only at the start of a token.
- So `ཧེརུཀཿ` stays text.

**`split_subsenses_gt`**
- `1> 2>` → sub numbers.

All splitters return `(None, leading text)` first when there is text before the first marker.
