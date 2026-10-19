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

The `slow` smoke test (`tests/test_build_smoke.py`) builds a database from the first 300 rows of
each table of the real source, and skips when the source file is absent. Leave it out with:

```sh
.venv/bin/python -I -m pytest -q -m "not slow"
```

Tests that need `pyewts` skip without it.

## Build

```sh
.venv/bin/python -I build_db.py \
  --source /path/to/MLDic.ml \
  --schema-dir ../../app/schemas/com.kharagedition.tibetandictionary.data.local.DictionaryDatabase \
  --out ../../app/src/main/assets/dictionary/dictionary.db \
  --data-version 2026.10.1 [--wotd-seed 20261019] [--require-wylie] [--report out.json] [--limit N]
```

The steps, in order:

1. Load the highest `<version>.json` schema.
2. Read the three source tables.
3. Parse each row.
4. Group rows into entries.
5. Resolve related links across directions.
6. Assign word-of-the-day ranks.
7. Compute the direction counts and `meta`.
8. Write the database.
9. Write the report.

The database is built in memory, compacted with `VACUUM INTO` a temporary file next to `--out`
(with `journal_mode=DELETE`), and renamed into place. A failed build leaves no file behind.

**Exit status**

| Code | Meaning |
|---|---|
| 0 | Success. |
| 1 | The schema or source cannot be read, or the database cannot be written. The message names the path. |
| 2 | A full build's counts leave the expected ranges in `dictbuild/stats.py`. The database and report are still written, and each failing count is printed. |

`--limit N` parses only the first N rows of each table, for quick checks. It skips the range
checks, so it never exits 2.

**Report** (`--report out.json`)
- `inputs`: the command-line inputs, and `limit`.
- `table_counts`: rows per table.
- `directions`: each direction's `directions` row (the capability counts).
- `related`: total and resolved links per relation, and the self-links dropped.
- `unresolved_see_also`: the count, plus up to 20 sample `{entry, target}` pairs.
- `unknown_labels`: label-position abbreviations that are not whitelisted (count, distinct, top 20),
  so the whitelists can grow.
- `warnings`: parser warnings by kind, with up to 10 sample source ids each.
- `range_checks`: every expected range with its value, or `"skipped (--limit)"`.

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
| `dictbuild/parsers/common.py` | Split numbered senses and sub-senses; `tidy`; the binding-particle rule |
| `dictbuild/parsers/labels_en.py` | English part-of-speech headers and the domain/register label whitelist |
| `dictbuild/parsers/entotb.py` | Parse `entotb` (English → Tibetan) |
| `dictbuild/parsers/labels_bo.py` | Whitelist of the Tibetan leading label parentheticals |
| `dictbuild/parsers/tbtb.py` | Parse `tbtb` (Tibetan → Tibetan) |
| `dictbuild/parsers/tben.py` | Parse `tben` (Tibetan → English), including `SA` see-also lists |
| `dictbuild/grouping.py` | Merge homonym rows into entries with continuous sense numbers |
| `dictbuild/related.py` | Index headwords and resolve see-also and synonym links |
| `dictbuild/wotd.py` | Word-of-the-day eligibility and seeded ranks |
| `dictbuild/writer.py` | Assemble rows, build `meta`, write the database |
| `dictbuild/stats.py` | Direction capability counts and the expected ranges |
| `dictbuild/report.py` | The JSON build report |
| `build_db.py` | Command line |

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

**`split_subsenses`**
- Uses `ཀཿ` markers when the text has them, otherwise `1>` markers.

**`starts_with_particle`**
- Some Tibetan syllables never start a word: `ཞེས`, `ཅེས`, genitive and instrumental particles,
  `སོགས`, `བཅས`, `ལའང`, `ལྟར`. Listed phrases count too: `ལ་ལྟོས`, `ཡང་ཟེར`, `ཟེར་བ`,
  `ཁོངས་སུ་ལྟོས`, `ཡི་འདས་ཚིག`, ….
- A Latin word followed by one of these belongs to the Tibetan sentence, so it is neither an example
  nor a label: `auction ལ་ལྟོས།`, `charwoman ཡང་ཟེར།`.
- `ཡི`, `ལ`, `ལས`, `དང` and `ཟེར` also open ordinary words (`ཡི་གེ`, `ལ་ཉག`, `ཟེར་ཐིག`), so on
  their own they do not bind.

## Per-table grammar

**`entotb` (English → Tibetan; glosses are Tibetan)**
- A definition is a run of part-of-speech blocks: `n.`, `v.t.`, `adj. & n.`, `combining form`, ….
  - Undotted forms count only at the start of the definition.
  - A header right after a sense number applies to that sense only: `2. pl. …`.
  - Senses are numbered per block, and `sense_no` continues across blocks.
- Labels from the whitelist in `labels_en.py` are read in three places: at the start of a block, after
  a sense number, and after a sub-sense marker. Examples: `adm,law`, `chem.`, `psychol.` → `psych`.
- An unknown dotted abbreviation in a label position stays in the gloss and goes into the report.
- Notes:
  - a Latin parenthetical such as `(to)` or `(fem. authoress)`;
  - verb inflections after a verb header: `ran, run`;
  - `*` (a coined term), stored as the note `*`;
  - a trailing header followed only by English (`pl. aides`), which becomes a note on the previous sense.
- `= English term` (at most five words) becomes a `synonym` link and leaves the gloss.
  - A Tibetan `= …` is an equivalent, so it stays in the gloss.
  - A sense that is nothing but `= X` keeps X as its gloss.
- An English phrase of two or more words becomes an example. The Tibetan that follows, up to the
  next example, is its translation. A lone `ལྟ་བུ།` means "for example": no translation.
- A sense whose gloss is empty once its examples are taken out is dropped, and its examples move to
  the previous sense.

**`tbtb` (Tibetan → Tibetan)**
- A leading parenthetical from the `labels_bo.py` whitelist (35 spellings, typos included, compared
  without tsheg or shad) gives a part of speech (`vt`, `vi`, `adj`, …) or a label (`poetic`,
  `archaic`, `dialect`, `sanskrit`, `chinese`, `mongolian`).
- Entry-level labels apply to every sense without its own, separately for part of speech and label.
- `1>` sub-senses.
- Each `(དཔེར་ན།)` starts one example.
- An unknown leading parenthetical stays in the gloss and goes into the report.

**`tben` (Tibetan → English; glosses are English)**
- `{TAG}` becomes the sense's `source_tag`, as written.
- `Def.:` and `Definition:` become the label `definition`; `Div.:` becomes `division`.
- Numbered senses are split.
- `SA x, y`: the first item takes the longest Wylie prefix that is a headword, looking in bo-en first,
  then bo-bo.
  - Later items count only when they resolve as a whole.
  - An unresolved target stops at the first English stopword or non-Wylie token, and is kept as Wylie.
- A definition that is only Wylie is converted to Tibetan. It must convert without warnings, and every
  Latin token must be a well-formed Wylie syllable.
- A truncated source field flags the last sense.

**Entries and links**
- Rows with the same group key merge into one entry: the first row's id, headword and Wylie, senses
  renumbered continuously, links de-duplicated.
- `entries.id` = the direction's id base + the first row's `_id`.
- `entries.sense_count` counts distinct sense numbers.
- Self-links are dropped.

**Word of the day**
- An entry qualifies when all of these hold:
  - it is bo-en;
  - the headword has 2–4 syllables, at most 24 code points, and only Tibetan letters;
  - the first gloss is English, 3–100 characters, with no digit and no `/`, `[` or `(`;
  - the entry is not truncated.
- Ranks follow SHA-256 of `"<seed>:<entry id>"`. They are reproducible, and one entry's rank never
  depends on which other entries qualify.
