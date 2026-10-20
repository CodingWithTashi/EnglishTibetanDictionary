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
each table of the real source. Point `MLDIC_SOURCE` at `MLDic.ml` to run it; without it the test
skips and says why. Leave it out with:

```sh
MLDIC_SOURCE=/path/to/MLDic.ml .venv/bin/python -I -m pytest -q
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
6. Drop empty entries: no sense and no link (one in the current source, `དཔལ་གྱི་ཁབ་`). Links that
   resolved to them become unresolved, and the report warns `build:empty_entry_dropped`.
7. Assign word-of-the-day ranks.
8. Compute the direction counts and `meta`.
9. Write the database.
10. Write the report.

The database is built in memory, compacted with `VACUUM INTO` a temporary file next to `--out`
(with `journal_mode=DELETE`), and renamed into place. A failed build leaves no file behind.

**Exit status**

| Code | Meaning |
|---|---|
| 0 | Success. |
| 1 | The schema or source cannot be read, or the database cannot be written. The message names the path. |
| 2 | A full build's counts leave the expected ranges in `dictbuild/stats.py`. Each failing count is printed. `--out` is not replaced: the database goes to a new `dictionary-out-of-range-*` temporary directory (the path is printed), and the report is still written. |

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
- With this rule en-bo has 35,888 groups. A full build expects 35,887–35,888 entries.

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
  - Headers may be joined by `&`, `,`, `and` or stray dots: `n. & v.t.`, `adj. , n.`.
  - A header right before a "see" phrase (`cox n. ལ་ལྟོས།`, `escape n. 3. ལ་ལྟོས།`) is part of the
    cross-reference and does not open a block.
  - Spaced and misspelled headers are listed in `POS_CODES`: `v. t.`, `n.t.`, `b.t.`, `phep.`. They count only
    at the start of a definition.
- Labels from the whitelist in `labels_en.py` are read in three places: at the start of a block, after
  a sense number, and after a sub-sense marker. Examples: `adm,law`, `chem.`, `psychol.` → `psych`.
  - A label may span several tokens: `compt. sc.`, `us .sl.`, `com ,law`. A trailing `etc.` is
    ignored.
  - A dotted label is taken even when an English phrase follows it (`sl. chuck out …`). An undotted
    one is not (`law and order` stays text).
- An unknown dotted abbreviation in a label position stays in the gloss and goes into the report.
- Every sense has a Tibetan gloss. English text with no Tibetan never becomes a sense; it is a *lead*:
  - It is the text before the first sense number or `ཀཿ` of a block (`n. revision n. 1.`), or a
    numbered item without Tibetan (`1. mat 2. ལ་ལྟོས།`, `2. Congress ཀཿ …`).
  - A lead is held for the next sense of the row. If that sense's gloss opens with a "see" particle,
    the lead joins the gloss (`arbiter 1. ལ་ལྟོས།` → `arbiter ལ་ལྟོས།`). Otherwise it becomes a note,
    with the warning `lead_kept_as_note`.
  - A lead with no word of two letters (`n. & .`, `adj. a, 1.`) is a header leftover. It is dropped,
    with the warning `header_leftover_dropped`.
  - A lead still held at the end of the row goes to the last sense.
  - A numbered item that is only `= X` is a synonym link (`2. = absorbent cotton`).
- Notes:
  - a Latin parenthetical such as `(to)` or `(fem. authoress)`;
  - verb inflections after a verb header, up to the first sense number, Tibetan, `*`, `=` or bracket
    holding Tibetan, at most six words: `v.i. ran, run 1.`, `v. gave, given . 1.`,
    `v.t. -sook, -saken ཀཿ`. Forms bound to the Tibetan after them stay text (`v. get ཡི་འདས་ཚིག`);
  - plurals after a `pl.` header, the same way. They become the note `pl. mice`, and the block loses
    the part of speech `pl`;
  - a block without Tibetan that has a part of speech (`pl. aides`), which becomes a note on the
    previous sense;
  - `*` (a coined term), stored as the note `*`.
- Notes and leads that open a block go to its first sense only.
- `= English term` (at most five words) becomes a `synonym` link and leaves the gloss.
  - A Tibetan `= …` is an equivalent, so it stays in the gloss.
  - An equation inside English text is no marker: an `=` right after a Latin letter, digit, `+` or
    apostrophe stays text (`_EQUATION_LEFT`: `libere=library`, `it’s = it is`, `H2 SO4 = …`). So does
    a term with digits or other punctuation (`_HEADWORD_LIKE`).
- An English phrase of two or more words becomes an example. The Tibetan that follows, up to the
  next example, is its translation. A lone `ལྟ་བུ།` means "for example": no translation.
  - A `(` between the phrase and its Tibetan opens the translation: `We played three games.
    (རྩེད་མོ།)རྩེ་སྟངས།` keeps the bracket in the translation.
  - So does a sense number that ends the phrase, which numbers its translations: `to black out 1.
    མུན་ནག་བཟོ་བ། 2. ནག་འཐོམ་ལང་བ།` has the translation `1. … 2. …`.
- A sense that opens with an English phrase (`5. in bond ཀཿ …`, `4. sl. chuck out ཕྱིར་འབུད་པ།`)
  keeps the phrase's Tibetan as its gloss and the phrase as a note.

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
  - A prefix never cuts a run of Wylie syllables. `SA kha pags` resolves `kha pags` or nothing, never
    `kha` with the gloss "pags".
  - An unresolved target is the whole Wylie run: it stops at the first English stopword or non-Wylie
    token, and is kept as Wylie.
  - Later items count when they resolve as a whole, or when they look like Tibetan in Wylie (kept
    unresolved: `SA kha sten, tshig sten`). Short English words are often valid Wylie too (`SA grang
    ba, cold`, `spin, send`), so such an item must:
    - be only Wylie syllables that convert without warnings;
    - use no `ai`/`au` vowel and no da drag (`cold`, `bend`), which modern spelling does not have;
    - have two syllables, an apostrophe, or an onset English never has (`mgo`, `rta`, `lha`).
  - The first later item that does not count ends the list; it and the rest are gloss.
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
  - the headword has 2–4 syllables, at most 24 code points, and only Tibetan letters (U+0F85
    paluta is a mark, not a letter);
  - the first gloss is English, 3–100 characters, with no digit and no `/`, `[` or `(`;
  - when the entry has see-also links, the first gloss is not all Wylie syllables (left over from an
    `SA` list: `pags`). Without links the rule does not apply, since short English words are often
    valid Wylie too (`rub`, `pen`, `sand`);
  - the entry is not truncated.
- Ranks follow SHA-256 of `"<seed>:<entry id>"`. They are reproducible, and one entry's rank never
  depends on which other entries qualify.
