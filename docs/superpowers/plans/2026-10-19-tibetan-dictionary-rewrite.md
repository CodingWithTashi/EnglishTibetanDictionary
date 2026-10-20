# Tibetan Dictionary 3.0 — Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. On approval, copy this file to `docs/superpowers/plans/2026-10-19-tibetan-dictionary-rewrite.md` as the first action.

**Goal:** Replace the current app wholesale with a Compose + Material 3 MVVM app that implements the new design and ships the Monlam dictionary (~182k entries) offline, with every design element present in code and hidden only when its data is absent.

**Architecture:** Single `:app` module, package-by-layer (`core`, `data`, `domain`, `navigation`, `ui`). Two Room databases: a **prebuilt read-only `dictionary.db`** (built by a Python pipeline from Monlam's SQLite, copied from assets with progress) and a **`user.db`** for saved words, folders, recents and activity. Unidirectional data flow: stateless `XScreen(state, onEvent)` composables, `@HiltViewModel` with `StateFlow<XUiState>` + `sealed interface XEvent/XEffect`, repositories behind interfaces. Visibility of data-dependent UI derives from `DictionaryCapabilities` (per-direction counts stored in the DB) and nullable fields — never from build flags. Whole features not in release 1 sit behind `object Features`.

**Tech stack:** Kotlin 2.3.21, AGP 8.13.2, KSP 2.3.12, Compose BOM 2026.06.01 + Material 3, Navigation Compose 2.9.8 (typed routes), Hilt 2.58, Room 2.8.5 (FTS4), DataStore 1.2.1, core-splashscreen 1.2.0, Firebase Crashlytics + Analytics (BoM 34.19.0), JUnit4/Truth/MockK/Turbine/Robolectric; Python 3 + pytest + `pyewts` for the data pipeline. Versions are copied from the owner's `~/dev/android/Tibetan-Prayer-App/gradle/libs.versions.toml`, which already builds on this machine.

**Spec:** the design handoff `docs/tibetan-dictionary-ui-revamp/project/Tibetan Dictionary Revamp.dc.html` (+ `android-frame.jsx`); the extracted values are reproduced in §6–§7 of this plan so implementers do not need to open the HTML. Data source: `/Users/kunchoktashi/Downloads/monlam-dictionary-master/app/src/main/assets/MLDic.ml` (read-only).

---

## 1. Context

The current app (`com.kharagedition.tibetandictionary` v2.1.0, XML + Fragments, 36k English→Tibetan rows, camera OCR, Firestore feedback, ads removed) has low engagement and no meaningful saved data. The owner wants a fresh app: new design, new content (Monlam: 38,271 EN→BO, 88,938 BO→EN, 55,124 BO→BO rows), clean scalable MVVM, and a database that already has **slots for data that does not exist yet** (audio, part of speech, see-also, examples, extra languages such as Chinese↔Tibetan) so that adding data later makes the corresponding UI appear with no code change.

**Decisions taken with the owner (2026-10-19):** Compose + M3 · release 1 = core app (Home/Search, Search results, Entry, Saved, Profile/Settings, splash, onboarding, all states, light + dark) · drop camera search and feedback form · keep only Crashlytics + Analytics · no migration from the old app.

**Defaults chosen in this plan (override at review if you disagree):**
| # | Default | Why |
|---|---|---|
| D1 | Release 1 shows **3 tabs** (Search, Saved, Profile); the Learn tab, Home stats card and "flashcards" copy appear when `Features.LEARN` flips on | Learn is a later release; an empty tab would mislead |
| D2 | Direction switcher: no "detected" chip; the **selected segment animates** to the auto-detected direction, and tapping a segment pins it for the current query | 1g's chip was superseded by 1d in rounds 2–3 |
| D3 | Saved words go to "All" by default (toast "Saved བཀྲ་ཤིས།" + **Change**); once folders exist, the last-used folder is the default and the toast names it | Design's "Daily" folder is sample content |
| D4 | The `dictionary.db` asset (~40 MB) is **committed** to the repo (Git LFS optional) | Solo-dev workflow; APK deflates it to ~15–18 MB |
| D5 | ~535 truncated BO→EN definitions are shown as-is with a one-line "incomplete in source data" note; `{C}`-style source tags are stored but not shown | Fixable in a data release without UI changes |
| D6 | minSdk 24 (was 21) | Matches the Prayer app and current Compose |
| D7 | Footer links: Rate → Play listing, More apps → Daily Prayer + Tibetan Calendar, Support → `developer.kharag@gmail.com`; **Privacy is hidden until a URL exists** | No privacy URL exists in either app |
| D8 | Wylie (EWTS) for every Tibetan headword is **generated at build time** with `pyewts`, so Wylie search ships in release 1 | pyewts verified: `pyewts.pyewts().toWylie()/toUnicode(text, warns)` |
| D9 | Learn's spaced-repetition tables arrive with the Learn release as `user.db` migration 1→2 | Keeps release 1 lean; Room migrations are routine |

**Owner action items (not blocking):** confirm the Monlam data license for redistribution (repo ships `gpl.txt`; README says "completely open-source"); provide a privacy-policy URL when available.

---

## 2. Global Constraints

- applicationId & namespace `com.kharagedition.tibetandictionary`; `versionCode = 21`, `versionName = "3.0.0"`; minSdk 24; compileSdk/targetSdk 36; JVM toolchain 17.
- **Never commit or push** (owner rule). Each task ends with a green test run, not a commit. No AI attribution anywhere.
- Every Tibetan run renders in Jomolhari; every Latin run in Noto Sans (400/500/600, italic 400). No other fonts.
- Colours only from `DictColors`; spacing on an 8pt grid; radii from `DictShapes`; touch targets ≥48dp; no elevation shadows (tonal surfaces + 1dp borders).
- `dictionary.db` is never written at runtime; user state lives only in `user.db` and DataStore.
- Visibility rules: data-dependent elements check capabilities/null fields (§7.4 tables); only whole unreleased features use `Features`.
- Parallel agents share one working tree: **each lane edits only the files it owns** (§8). Shared files (`Routes.kt`, `AppNavHost.kt`, `RepositoryModule.kt`, `values/strings.xml`, catalog) are completed by L0 before other lanes start.
- Build + test command for every task: `./gradlew :app:testDebugUnitTest :app:lintDebug` (plus `pytest` in `tools/dictionary` for L1).

## 3. Review Focus

Inputs the spec implies but no mockup shows; each has a pinned test in the owning task:
1. Mixed/odd queries — `"བཀྲ shis"`, trailing shad `"བཀྲ་ཤིས།"`, double spaces, 1-char input — must search without crashing and never record a blank recent (L1 T1.9 `QueryNormalizerTest`, L2 T2.2 `SearchViewModelTest.oddQueries`).
2. Entry with zero glosses (a BO→EN row that was only `SA x`) — Entry screen shows the see-also chips and no empty sense card (L1 T1.7 `entryDetailRedirectOnly`, L3 T3.1 `EntryViewModelTest.redirectOnlyEntry`).
3. Stored default direction missing after a data release (direction removed or `entry_count = 0`) — falls back to the first visible direction everywhere (L2 T2.1 `DirectionSlotsTest.fallbackWhenDefaultHidden`, L4 T4.1 `ProfileViewModelTest.defaultDirectionFallback`).
4. Saved row whose entry no longer exists in a newer `dictionary.db` — row still renders from its snapshot; tapping it shows a toast instead of crashing (L3 T3.2 `SavedViewModelTest.missingEntryToast`).
5. Accessibility extremes — `AppTextSize.LARGEST` × line spacing 2.3 × system font scale 2.0: rows/cards grow (min heights, no fixed heights), nothing clips (L0 T0.4 preview `ExtremeScalePreview`; L6 T6.3 device check).

---

## 4. Architecture

```
com.kharagedition.tibetandictionary
  DictionaryApplication.kt   MainActivity.kt   Features.kt
  core/   di/ init/ locale/ error/ time/ text/ audio/ speech/ Links.kt ContextExt.kt
  data/   local/{DictionaryDatabase,UserDatabase,entity,dao,relation}  model/  prefs/  repository/  search/  install/
  domain/ InputScript DetectScript ResolveDirection DirectionSlots Greeting StreakCalculator SavedGrouping
  navigation/ Routes.kt AppNavHost.kt NavigationEvents.kt
  ui/     theme/ components/ app/ main/ home/ search/ entry/ saved/ profile/ onboarding/ about/ learn/ preview/
tools/dictionary/   Python build pipeline (§5.3)
app/schemas/        exported Room schemas (source of truth for the asset DDL)
```

**Start-up:** `MainActivity.installSplashScreen()` keeps the system splash while `AppRootState.Loading`; `AppInitializer` runs `LegacyCleanup` → `DictionaryInstaller.ensureInstalled()` (copies/upgrades the asset with progress; `InstallingScreen` shows the 3a splash with the bar) → `ready`. `AppRootViewModel` combines installer state + prefs + capabilities into `AppRootState.Ready(onboarded, appearance, textSize, lineSpacing, language)`; `AppRoot` applies `DictTheme` and hosts `AppNavHost` (+ toast host).

**Search flow:** `SearchViewModel` debounces (150 ms) → `DetectScript` → `ResolveDirection` (runs the prefix search in the candidate directions and picks the one with hits; animates the switcher) → `DictionaryRepository.search()` (exact → prefix in Monlam order → FTS headword → FTS gloss; highlights; did-you-mean when empty) → `SearchUiState`.

**Capabilities:** `directions` rows carry build-time counts (`entry_count`, `wylie_count`, `audio_count`, `pos_count`, `label_count`, `example_count`, `related_count`, `wotd_count`, `truncated_count`); `Direction.features` is `count > 0`. A direction with `entry_count = 0` is never shown; a 4th direction appears in the switcher's "More" slot. Adding Chinese↔Tibetan later = Python emits a `directions` row + entries; adding audio = fill `audio_file`.

---

## 5. Data layer

### 5.1 `dictionary.db` schema (Room entities, package `data/local/entity`; DDL generated by Room, executed verbatim by Python from `app/schemas/.../DictionaryDatabase/<v>.json`)

| Table | Columns (snake_case `@ColumnInfo`) | Indices / notes |
|---|---|---|
| `directions` | `id TEXT PK` ('bo-en','bo-bo','en-bo'…), `source_lang`, `target_lang`, `source_script` ('tibt'/'latn'/'hani'), `label_en`, `label_bo`, `short_label` ("བོད→EN"), `position INT` (0–2 fixed, ≥3 overflow), `id_base INT` (1 000 000 / 2 000 000 / 3 000 000), `entry_count`, `sense_count`, `example_count`, `related_count`, `wylie_count`, `audio_count`, `pos_count`, `label_count`, `truncated_count`, `wotd_count` (all INT NOT NULL DEFAULT 0) | |
| `entries` | `id INT PK` (= `id_base + source_id`, stable across data releases), `direction_id FK`, `source_id INT`, `headword TEXT NOT NULL COLLATE NOCASE` (Tibetan ends with tsheg), `wylie TEXT COLLATE NOCASE` (nullable), `gloss_preview TEXT NOT NULL` (≤120 chars), `part_of_speech TEXT` ('n,vt'), `phonetic TEXT` (nullable slot, e.g. "tashi"; no source yet), `audio_file TEXT`, `sense_count INT`, `is_truncated INT`, `sort_order INT` (= source_id; Monlam order = Tibetan collation), `wotd_rank INT` (NULL = not eligible) | UNIQUE(`direction_id`,`headword`); (`direction_id`,`wylie`); (`direction_id`,`sort_order`); (`wotd_rank`) |
| `senses` | `id INT PK`, `entry_id FK CASCADE`, `sense_no INT` (1-based, continuous), `sub_no INT` (ཀཿ/ཁཿ or 1>/2>; NULL), `part_of_speech TEXT` ('n','adj','adv','v','vt','vi','interj','prep','pron','conj','num','part','aux','prefix','suffix','abbr','comb','pred','pl'), `label TEXT` (canonical keys, comma-joined), `gloss TEXT NOT NULL`, `gloss_lang TEXT` ('bo'/'en'), `note TEXT`, `source_tag TEXT` ('C;TGP-77'), `is_truncated INT`, `source_id INT` | (`entry_id`,`sense_no`,`sub_no`) |
| `examples` | `id PK`, `sense_id FK CASCADE`, `entry_id INT` (denormalised), `position INT`, `text TEXT NOT NULL`, `translation TEXT`, `audio_file TEXT` | (`sense_id`); (`entry_id`) |
| `related_entries` | `id PK`, `source_entry_id FK CASCADE`, `target_entry_id FK SET NULL` (NULL = unresolved; chip taps run a search), `target_headword TEXT NOT NULL`, `target_wylie TEXT`, `relation TEXT` ('see_also'/'synonym'), `position INT` | (`source_entry_id`); (`target_entry_id`) |
| `meta` | `key TEXT PK`, `value TEXT` — keys: `schema_version`, `data_version`, `built_at`, `source_name`, `source_author`, `source_url`, `source_license`, `attribution_text`, `wylie_converter`, `wotd_seed`, `wotd_count`, `wotd_direction_id`, `build_tool_version`, `row_counts_json` | |
| `entries_fts` | `@Fts4(contentEntity = EntryEntity, tokenizer = UNICODE61, tokenizerArgs = [TibetanTokenChars.ARG])` on `headword`, `wylie` | external content |
| `senses_fts` | same tokenizer, on `gloss` | external content |

`TibetanTokenChars.ARG = "tokenchars=" + 77 code points` (U+0F18, U+0F19, U+0F35, U+0F37, U+0F39, U+0F3E, U+0F3F, U+0F71–U+0F84, U+0F86, U+0F87, U+0F8D–U+0F97, U+0F99–U+0FBC, U+0FC6) — without it unicode61 splits inside Tibetan syllables (verified). Tsheg/shad stay separators so one token = one syllable.

**Search mechanics:** prefix search = B-tree range scan `col >= :prefix AND col < :prefix || U+10FFFF` on `headword`/`wylie` (NOCASE), ordered exact-first then `sort_order`; "contains syllable" and reverse lookup use the FTS tables. FTS4 because Room has no `@Fts5`.

**Versioning:** `DictionaryDatabase.VERSION` (const) = `@Database(version)` = asset `PRAGMA user_version`; bump on every data release. No `room_master_table` in the asset. Builder: `createFromAsset("dictionary/dictionary.db")`, `fallbackToDestructiveMigration(dropAllTables = true)`, `JournalMode.TRUNCATE`.

**`DictionaryInstaller`** (`data/install`): `state: StateFlow<InstallState>` with `Checking | Copying(progress: Float) | Ready(totalEntries: Int) | Failed(cause)`; `ensureInstalled()` compares the on-device file's header bytes 60–63 (`user_version`) with `VERSION`; if missing/mismatched it deletes the file (+`-journal/-wal/-shm`) and stream-copies the asset reporting progress, then opens Room (which finds a matching file) and reads `meta.data_version`/entry totals. Room's own copy path remains the fallback.

### 5.2 `user.db` v1 (Room, `exportSchema = true`, normal migrations)

| Table | Columns |
|---|---|
| `folders` | `id PK AUTOINCREMENT`, `name UNIQUE`, `position`, `created_at`, `updated_at` |
| `saved_entries` | `entry_id PK`, `direction_id`, `headword`, `wylie`, `gloss_preview` (snapshots: Saved never joins across DBs), `folder_id FK folders SET NULL`, `note`, `saved_at`, `updated_at` — indices (`folder_id`), (`saved_at`) |
| `recent_searches` | `id PK`, `query`, `direction_id`, `entry_id`, `headword`, `searched_at` — UNIQUE(`query`,`direction_id`), trimmed to 50 |
| `activity_days` | `date TEXT PK` ('yyyy-MM-dd' local), `searches INT`, `entries_opened INT` — feeds the streak |

Learn (`review_states`, `review_logs`, SM-2) = migration 1→2 in the Learn release (D9). Preferences (DataStore `user_prefs`): `UserPrefs(onboarded=false, appLanguage=EN, appearance=SYSTEM, textSize=AppTextSize.DEFAULT, lineSpacing=1.9f, defaultDirectionId="bo-en", lastUsedFolderId: Long?=null, wotdNotificationEnabled=false, wotdNotificationMinute=540, legacyCleanupDone=false)`.

### 5.3 Python pipeline `tools/dictionary/`

```
build_db.py  --source MLDic.ml --schema-dir app/schemas/<fqcn>/ --out app/src/main/assets/dictionary/dictionary.db
             --data-version 2026.10.1 [--wotd-seed 20261019] [--require-wylie] [--report out.json] [--limit N]
dictbuild/{schema,source,text,wylie,model,grouping,related,wotd,stats,writer,report}.py
dictbuild/parsers/{common,entotb,labels_en,tben,tbtb,labels_bo}.py      tests/ (pytest)   requirements.txt (pyewts, pytest)
```
Steps: load highest `<v>.json` → execute its `createSql`/indices/FTS triggers → parse each table → group rows into entries → resolve related links across directions → assign WOTD ranks → compute direction counts + meta → bulk insert, `INSERT INTO entries_fts(entries_fts) VALUES('rebuild')` (same for senses), `PRAGMA user_version = v`, `journal_mode=DELETE`, `VACUUM` → report; exit non-zero if counts leave the ranges in the table below. Run with `python3 -I` inside a venv.

**Grammar (the decisions; full regexes live in the parser tests):**
- Common: NBSP→space, drop U+200B/U+FEFF, U+0F0C→U+0F0B, collapse whitespace; strip CSV quotes (`"…"`); numbered senses `1. 2. 3.` accepted only as a sequence starting at 1 (`4.དགའ་ཞེན།` glued form allowed; `run a wolf 2 miles` is text); sub-senses `ཀཿ ཁཿ གཿ ངཿ ཅཿ` → `sub_no` 1–5; `1> 2>` → `sub_no`; numbering is continuous per entry across POS blocks and merged rows.
- **entotb (en-bo):** headword homonym digit `lead1` → strip, group by lower-cased headword (not always adjacent); POS header tokens map `n./n→n, adj.→adj, adv.→adv, v.→v, v.t./v.t/vt.→vt, v.i./vi.→vi, interj./int.→interj, prep.→prep, pron.→pron, conj.→conj, pl.→pl, prefix, suffix, abbr., combining form→comb, pred.→pred, aux.→aux, num.→num`; `adj. & n.` → `adj,n`; `(A)`/`(to)` prefix → `note`; `*` → note; verb inflections `ran, run` → note; domain/register labels from a whitelist (`adm med bot phys astrol pol law com chem edu econ … sl colloq fig archaic obs …`) at block start or after a sense number; English example phrases (≥2 Latin words) with following Tibetan → `examples(text, translation)` and removed from the gloss (single Latin words stay); `= lead pencil` → `related(synonym)`; all glosses `gloss_lang='bo'`.
- **tben (bo-en):** headword suffix ` ༡`/`༌༡`/`1`/`༠` → strip + group (groups are consecutive; a plain `Def.:` row precedes); ensure trailing tsheg; `wylie = toWylie(headword)`; `{TAG}` anywhere → `source_tag`; `Def.:`/`Definition:`/`Div.:` and Wylie prefixes `mtshan nyid/`, `dbye ba/` → `label`; ASCII-only definitions containing `/` → `toUnicode()`; accept if no warnings and ≥80 % Tibetan (`gloss_lang='bo'`), else keep Latin; `SA a, b, c` → see-also candidates resolved by longest Wylie-prefix match against bo-en then bo-bo headwords (matched text removed from gloss; unresolved kept with `target_entry_id NULL`); `is_truncated = len ≥ 250 and not ending in .!?)]།"` (expect ≈535).
- **tbtb (bo-bo):** ensure trailing tsheg; `wylie = toWylie`; leading `(label)` from the 37-key whitelist with aliases (`(བྱེད་འབྲེལ་ལས་ཚིག)`→`vt`, `(བྱེད་མེད་ལས་ཚིག)`→`vi`, `(ཚབ་ཚིག)`→`pron`, `(སྦྲེལ་ཚིག)`→`conj`, `(མངོན་བརྗོད།)`→label poetic, `(བརྡ་རྙིང་།)`→archaic, `(ཡུལ་སྐད།)`→dialect, `(ལེགས་སྦྱར།)`→Sanskrit, `(རྒྱ་སྐད།)`→Chinese, `(སོག་སྐད།)`→Mongolian…); entry-level label applies to senses without their own; `(དཔེར་ན།) …` → `examples`.
- **WOTD eligibility:** bo-en, 1–4 syllables, ≤24 code points, pure Tibetan letters, ≥1 sense, first gloss English 3–100 chars, not truncated; `wotd_rank` = index in a seeded shuffle; day index = `floorMod(epochDay + wotd_seed, wotd_count)`.

| Expected after build | Value |
|---|---|
| directions | 3 |
| entries bo-en / bo-bo / en-bo | 76,828 / 55,124 / 35,887 |
| senses | bo-en 88,000–88,938 · bo-bo 65,000–80,000 · en-bo 55,000–75,000 |
| examples | bo-bo 18,000–19,000 · en-bo 2,000–5,000 |
| related_entries | see_also 4,000–5,000 (≥70 % resolved) · synonym 1,000–3,500 |
| entries.wylie non-null | 131,952 (0 without pyewts) |
| senses.is_truncated | 480–600 |
| wotd_rank non-null | 25,000–60,000 |

### 5.4 Kotlin data contracts (used by every UI lane)

```kotlin
// data/model
enum class Script { TIBETAN, LATIN, HAN }
data class DirectionFeatures(val hasWylie: Boolean, val hasAudio: Boolean, val hasPartOfSpeech: Boolean, val hasLabels: Boolean,
    val hasExamples: Boolean, val hasRelated: Boolean, val hasWordOfTheDay: Boolean, val hasTruncated: Boolean)
data class Direction(val id: String, val sourceLang: String, val targetLang: String, val sourceScript: Script, val labelEn: String,
    val labelBo: String, val shortLabel: String, val position: Int, val entryCount: Int, val features: DirectionFeatures)
data class Attribution(val sourceName: String, val author: String, val url: String, val license: String, val text: String)
data class DictionaryCapabilities(val directions: List<Direction>, val dataVersion: String, val builtAt: Instant?,
    val attribution: Attribution, val wotdDirectionId: String?) {
  val totalEntries: Int; val primary: List<Direction> /*position<=2*/; val overflow: List<Direction>; val hasOverflow: Boolean
  val hasWylie: Boolean /*any direction*/ }
data class EntrySummary(val id: Long, val directionId: String, val headword: String, val wylie: String?, val glossPreview: String,
    val partOfSpeech: List<String>, val phonetic: String?, val audioFile: String?, val isTruncated: Boolean, val senseCount: Int) { val hasAudio get() = audioFile != null }
data class Example(val text: String, val translation: String?, val audioFile: String?)
data class SenseItem(val subNumber: Int?, val gloss: String, val glossLang: String, val isTruncated: Boolean, val examples: List<Example>)
data class Sense(val number: Int, val partOfSpeech: String?, val labels: List<String>, val note: String?, val sourceTags: List<String>, val items: List<SenseItem>)
enum class Relation { SEE_ALSO, SYNONYM }
data class RelatedEntry(val targetEntryId: Long?, val headword: String, val wylie: String?, val relation: Relation)
data class Entry(val summary: EntrySummary, val senses: List<Sense>, val related: List<RelatedEntry>)
enum class MatchField { HEADWORD, WYLIE, GLOSS }
data class SearchHit(val entry: EntrySummary, val field: MatchField, val headwordHighlight: IntRange?, val wylieHighlight: IntRange?, val glossHighlight: IntRange?)
data class Suggestion(val hit: SearchHit, val directionId: String)
data class SearchResult(val query: String, val directionId: String, val script: Script, val hits: List<SearchHit>, val hasMore: Boolean, val suggestions: List<Suggestion>)
data class SavedEntry(val entryId: Long, val directionId: String, val headword: String, val wylie: String?, val glossPreview: String, val folderId: Long?, val note: String?, val savedAt: Long)
data class Folder(val id: Long, val name: String, val position: Int, val count: Int)
data class RecentSearch(val id: Long, val query: String, val directionId: String, val entryId: Long?, val headword: String?, val searchedAt: Long)
enum class AppTextSize(val scale: Float) { SMALL(0.85f), DEFAULT(1f), LARGE(1.15f), LARGEST(1.3f) }
enum class Appearance { SYSTEM, LIGHT, DARK }   // core/locale: enum class AppLanguage(val tag) { EN("en"), BO("bo") }

// data/repository (interfaces; Impl classes bound in core/di/RepositoryModule)
interface DictionaryRepository {
  val capabilities: Flow<DictionaryCapabilities>
  suspend fun direction(id: String): Direction?
  suspend fun search(query: String, directionId: String, limit: Int = 50, offset: Int = 0): SearchResult
  suspend fun suggestionsForEmptyQuery(directionId: String, date: LocalDate, count: Int = 6): List<EntrySummary>
  fun observeEntry(id: Long): Flow<Entry?>;  suspend fun entry(id: Long): Entry?
  suspend fun summaries(ids: List<Long>): List<EntrySummary>
  suspend fun closest(text: String, directionId: String?, limit: Int = 5): List<Suggestion> }
interface SavedRepository {
  fun observeAll(): Flow<List<SavedEntry>>;  fun observeFolder(folderId: Long?): Flow<List<SavedEntry>>;  fun search(query: String): Flow<List<SavedEntry>>
  fun observeIsSaved(entryId: Long): Flow<Boolean>;  fun observeSavedIds(): Flow<Set<Long>>;  fun observeCount(): Flow<Int>
  suspend fun save(entry: EntrySummary, folderId: Long?);  suspend fun unsave(entryId: Long);  suspend fun setFolder(entryId: Long, folderId: Long?)
  fun observeFolders(): Flow<List<Folder>>;  suspend fun createFolder(name: String): Long;  suspend fun renameFolder(id: Long, name: String);  suspend fun deleteFolder(id: Long) }
interface RecentSearchesRepository { fun observe(limit: Int = 10): Flow<List<RecentSearch>>; suspend fun record(query: String, directionId: String, opened: EntrySummary? = null); suspend fun remove(id: Long); suspend fun clear() }
interface ActivityRepository { suspend fun markSearch(); suspend fun markEntryOpened(); fun observeActiveDays(limit: Int = 60): Flow<List<LocalDate>> }
interface WordOfTheDayRepository { fun observe(date: LocalDate): Flow<Entry?>; suspend fun forDate(date: LocalDate): Entry? }
interface UserPreferencesRepository { val prefs: Flow<UserPrefs>; suspend fun snapshot(): UserPrefs; suspend fun setOnboarded(Boolean); setAppLanguage(AppLanguage); setAppearance(Appearance); setTextSize(AppTextSize); setLineSpacing(Float); setDefaultDirectionId(String); setLastUsedFolderId(Long?); setWotdNotification(enabled: Boolean, minuteOfDay: Int); setLegacyCleanupDone(Boolean) }
// data/install
sealed interface InstallState { data object Checking; data class Copying(val progress: Float); data class Ready(val totalEntries: Int); data class Failed(val cause: Throwable) }
interface DictionaryInstaller { val state: StateFlow<InstallState>; suspend fun ensureInstalled() }
```

Search algorithm (`DictionaryRepositoryImpl.search`): normalise (`QueryNormalizer`: trim, collapse ws, U+0F0C→U+0F0B, strip trailing `།༎`, lowercase Latin) → `ScriptDetector` → mode by (`direction.sourceScript`, script): tibt+TIBETAN → HEADWORD; tibt+LATIN → WYLIE (if `features.hasWylie`) then GLOSS; latn+LATIN → HEADWORD; latn+TIBETAN → GLOSS; hani → HEADWORD (+GLOSS for LATIN) → prefix query (`limit+1` → `hasMore`) → page 0: FTS headword phrase, then FTS gloss (20) → highlights (HEADWORD: prefix range or first-token index; WYLIE: `0 until q.length` on wylie + first *k* syllables on headword; GLOSS: first-token index in preview) → did-you-mean only when empty: progressive prefix shortening (Tibetan: to previous tsheg first) ranked by Damerau-Levenshtein then `sort_order`, then the other visible direction with the same script (tagged with its `directionId`), then FTS gloss any-term; cap 5.

---

## 6. Design system (from the handoff)

### 6.1 `DictColors` (`@Immutable`, `Light`/`Dark`)
| Field | Light | Dark | Use |
|---|---|---|---|
| background | `#FBF8F3` | `#1B1715` | screens, sheets |
| surface1 | `#F3EDE3` | `#262019` | fields, switcher track, cards, nav bar, sense cards, pressed row |
| surface2 | `#EADFCF` | `#332B26` | unfilled dots, slider track, skeleton strong |
| accentContainer / onAccentContainer | `#F6DDD2` / `#4A1409` | `#5C2416` / `#F6DDD2` | nav indicator, tonal button/chip/badge, streak pill, selected card |
| accent / onAccent | `#8A2E1C` / `#FFFFFF` | `#E8A58C` / `#2E1109` | filled buttons, selected segment, links, sense numbers, caret, match highlight, saved heart |
| ink | `#241E19` | `#F1E9E0` | primary text/icons |
| muted | `#6B5F54` | `#B5A89A` | secondary text, Wylie, placeholders, inactive nav |
| faint | `#B5A89A` | `#8C8075` | version line, history icon |
| outline | `#D8CDBB` | `#4A403A` | chip/button borders, dashed boxes, outlined badges, sheet handle |
| cardBorder | `#E7DECF` | `#332B26` | stats/benefit cards |
| divider | `#EFE7DA` | `#332B26` | list separators |
| scrim | 42 % `#241E19` | 50 % `#000` | |
| inverseSurface / inverseOnSurface / inverseAccent | `#241E19` / `#F1E9E0` / `#E8A58C` | `#F1E9E0` / `#241E19` / `#8A2E1C` | toast |
| success | `#3F8F5E` | same | sync dot (later) |
| danger | `#A8322D` | `#E08A86` | M3 error bridge |
| flagLine | `#B5A89A` @ .6 | @ .5 | strand line |
| flagBlue/White/Red/Green/Yellow | `#3F6FB5` `#EDE8DC` `#C8402F` `#3F8F5E` `#E2B232` | same | prayer-flag strand |

M3 bridge: `primary=accent, onPrimary=onAccent, primaryContainer=accentContainer, onPrimaryContainer=onAccentContainer, secondary=muted, background/surface=background, onBackground/onSurface=ink, surfaceVariant=surface1, onSurfaceVariant=muted, surfaceContainerLow/Container=surface1, surfaceContainerHigh/Highest=surface2, outline=outline, outlineVariant=divider, error=danger, inverseSurface/inverseOnSurface, inversePrimary=inverseAccent`; ripple = accent. Dark-mode selected segment weight 600 (light 500).

### 6.2 `DictShapes` / `DictDimens`
Shapes: extraSmall 6 · small 12 · medium 16 · large 20 · extraLarge 28; `sheet` = top 28; `pill` = Circle. Dimens: minTouch 48; search field Home 56 / Focused 52 / Compact 48 (radius = height/2); switcher 40 (pad 4, segment radius 16), compact 36; chips 36 / 32; buttons CTA 52 / 48 / 44 / 40 / 36; icon buttons 48 / 40 / 36 (icons 24 / 24 / 20); nav bar 72, indicator 56×28; rows: entry 12×20 pad, saved 10×20, see-also 52, settings 52 (56 in bo), history 48; screen padding 20 (onboarding 24); gaps section 20 / block 12 / chip 8; card padding 14×16, WOTD body 10/20/14 under the strand; badge pad 3×7 (tonal) / 3×6 (outline), radius 6; streak pill pad 4×10; toast 48, 12 above nav, 16 sides; slider track 4 / thumb 20; page dash 20×4 / dot 8×4; progress dots 8; skeleton bars 22 / 12 (radius 6); splash icon 84; empty-state circles 72 / 64 / 40.

### 6.3 `DictTypography(textScale, lineSpacing)`
Fonts `res/font`: `jomolhari_regular.ttf` (copy from the Prayer app's `res/font`, known-good), `noto_sans_{regular,medium,semibold,italic}.ttf` (download OFL files from Google Fonts at implementation). Every size is sp × `textScale`; Latin line-heights = size × ratio; **Tibetan line-heights = size × ratio × (lineSpacing / 1.9)**; `includeFontPadding = false`, `LineHeightStyle(Center, Trim.None)`, Tibetan uses `LineBreak.Paragraph`.

Latin (Noto Sans): `screenTitle` 24/600 (ls −.01em) · `onboardingTitle` 26/600 ×1.25 · `noResultsTitle` 17/600 · `fieldText` 17 (16 on Home) · `fieldPlaceholder` 16 · `headwordLatin` 16/500 ×1.5 · `body` 15 ×1.6 (WOTD gloss ×1.5) · `wylieEntry` 15 italic · `bodySmall` 14 ×1.5 (result gloss ×1.45) · `bodySmallMedium` 14/500 · `buttonLarge` 15/600 · `button` 14/600 (outlined 14/500) · `label` 13/500 · `labelSemibold` 13/600 · `wylie` 13 italic (match prefix 600 + accent) · `secondary` 13 · `caption` 12 · `captionMedium` 12/500 · `sectionLabel` 12/600 · `overline` 11/600 ls .06em caps (WOTD variant .08em accent) · `navLabel` 11 (active 600) · `tiny` 11 · `badge` 10/600 ls .06em · `dayLabel` 10 · `navGlossLatin` 9.

Tibetan (Jomolhari, size / line ratio): `tibetanEntryHeadword` 46/1.8 · `tibetanWotd` 34/1.9 · `tibetanSplash` 26/1.8 (onboarding greeting 26/1.7 accent; bo screen title 26/1.7) · `tibetanRow` 24/1.8 · `tibetanSaved` 22/1.8 · `tibetanPreviewSense` 21/1.9 · `tibetanExample` 19/1.6 · `tibetanGloss` 18/1.6 · `tibetanChip` 17/1.5 · `tibetanInline16` 16/1.5 (toast 1.0) · `tibetanInline15` 15/1.4 ("བོད" in 13sp segments) · `tibetanInline14` 14/1.4 · `tibetanNavPrimary` 13/1.3 · `tibetanBadge` 11/1.2 ls 0 · `tibetanNavGloss` 10/1.2. `MixedText(text, latinStyle, tibetanStyle)` splits runs by `TibetanScript.isTibetan` (greeting, placeholder, toast, direction labels).

### 6.4 `DictIcons`
`ImageVector`s from the spec paths (24 viewBox, stroke 1.75 — 2 when active, 1.5 in empty states — round caps, `currentColor`): Search (circle r7 @11,11 + `M20 20l-3.5-3.5`), SearchOff (+ `M8.5 11h5`), Mic, Speaker (`M4 9v6h4l5 4V5L8 9H4z` + `M16 9a4 4 0 010 6`), Heart (`M12 20s-7-4.4-7-10a4 4 0 017-2.6A4 4 0 0119 10c0 5.6-7 10-7 10z`; filled = saved), Share (3 circles r2.5 @18,5/6,12/18,19 + lines), Copy, Learn (rect 3,5 13×15 rx2 + `M8 5V3h13v15h-2`), Person, Back (`M15 5l-7 7 7 7`), Close, ChevronRight/Down/Left, Check, Plus, History, OpenExternal. No `material-icons-extended`.

### 6.5 App icon (5a) → adaptive icon
160-unit tile, radial gradient cx 25 % cy 15 % r 110 % `#A63D27→#6A1F12`; left page `M30 44 Q56 36 80 50 L80 122 Q56 108 30 116 Z` (gradient `#FBF8F3→#F3EDE3@.85→#D9CDB9`), right page mirrored (reverse gradient), gutter rect 72,46 16×78 `#4A1409` α 0→.45→0, base `M26 118 Q54 108 80 122 Q106 108 134 118 L134 126 Q106 116 80 130 Q54 116 26 126 Z` `#3E1109` @ .8, glyphs "བོད" (Jomolhari 30 @54,92) and "En" (Noto Sans 600 26 @106,90) `#8A2E1C` converted to path outlines (fontTools). Map to the 108dp canvas at scale 0.45, offset 18 (book stays inside the 33dp safe radius); monochrome layer = pages + gutter silhouette. Splash uses the same icon at 84dp with glyphs; the drop shadow `0 8px 18px rgba(74,20,9,.2)`.

---

## 7. Presentation layer

### 7.1 `Features`
```kotlin
object Features { const val LEARN = false; const val ACCOUNTS = false; const val SHARE_IMAGE = false; const val WIDGETS = false
  const val PROCESS_TEXT = false; const val TABLET_TWO_PANE = false; const val WOTD_NOTIFICATION = false; const val VOICE_SEARCH = true }
```
LEARN → Learn tab, `LearnScreen`, Home `StatsCard`, Saved-empty second sentence. ACCOUNTS → onboarding step 3, Profile `AccountCard`. SHARE_IMAGE → share card (else plain-text share). WIDGETS/PROCESS_TEXT → manifest components (not declared in R1). WOTD_NOTIFICATION → Profile row + scheduler. VOICE_SEARCH → mic, AND `SpeechRecognizer.isRecognitionAvailable()`.

### 7.2 Components (`ui/components`, all stateless, `modifier` last, `@PreviewLightDark` + `locale="bo"` previews)
Buttons.kt (`FilledButton/TonalButton/OutlinedButton(accentBorder, trailing)/TextButton` with `ButtonSize { Cta, Default, Medium, Small, Xs }`, `DictIconButton(icon, cd, onClick, size, iconSize, tint, background, filled)`) · Chips.kt (`DictChip(selected, size Large|Small, leading, tonal)`, `TextChip`, `TibetanChip`, `DetectedChip`, `AddChip`) · SearchField.kt (`SearchField(value, onValueChange, variant Home|Focused|Compact, placeholder, focused, showMic, onMic, onClear, leadingSearchIcon, focusRequester)` — rest: surface1 + icon 24 + placeholder muted; focused: 2dp accent inner border + accent caret; `SearchHeader(onBack, field, switcher)`) · DirectionSwitcher.kt (`DirectionSwitcher(slots, selected, onSelect, compact, autoSelected)` 3 equal segments, thumb `animateDpAsState` 180 ms, third segment = `More ▾` + `DropdownMenu` when `slots.overflow` non-empty; `DirectionLabel(direction)` renders "བོད→ EN" / "བོད→བོད" / "EN →བོད" (bo→"བོད", en→"EN", zh→"中"); `DirectionBadge(direction, tonal)`) · EntryRow.kt (`EntryRow(result: SearchResultUi, onClick, highlighted)`: headword 24/1.8 Tibetan or 16/500 Latin, `WylieLine(wylie, matchRange)` −6 top margin, gloss 14 single-line ellipsis, outline badge when `showBadge`; `highlighted` = surface1 + no divider) · SenseCard.kt (`SenseCard(number?, partOfSpeech?, text, examples, label?, note?)`: number column 16 wide omitted when null, "(n.)" inline prefix, usage label as an inline outline badge after it, " — e.g. " muted + Tibetan 19 before every example, note on a muted 13sp line under the text — *amended after the final review (I3)*) · SeeAlso.kt (`SeeAlsoSection(items, onClick)`: label 12/600 + rows 52 with outline badge + headword + chevron) · Cards.kt (`WordOfTheDayCard(wotd, onPlay, onToggleSave, onShare, onOpen)` with `FlagStrand(Card)` flush top, overline accent + date, headword 34/1.9, Wylie, gloss, actions speaker/heart/share + "Open"; `StatsCard`, `AccountCard`, `TipBox`, `PreviewCard(textSize, lineSpacing)`) · FlagStrand.kt (`FlagStrandSize { Card(fill,12×8,gap 3,pad 6), Onboarding(140), Splash(160,14×10), Widget(50,7×5), ShareCard(300,30×20) }`, 1dp line + 5 flags radius 0/0/2/2) · Navigation.kt (`BottomNavBar(tabs, selected, onSelect, language)`: 72 surface1, indicator 56×28 accentContainer, icon 22 stroke 2 active, en = Latin 11 + Tibetan gloss 10/1.2, bo = Tibetan 13/1.3 + Latin 9; heart filled on Saved) · Toast.kt (`Toaster.show(message: CharSequence, action: String? = null, long = false, onAction)`, `AppToastHost` anchored 12dp above nav) · Skeleton.kt (`SkeletonEntryRow` ×2 presets 120/80/240 and 160/100/200) · EmptyState.kt (`InlineEmptyState(icon, title, body)` dashed; `FullEmptyState(icon, title, body, action)`) · Settings.kt (`SettingsGroup(title)`, `SettingsRow(label, value, valueContent, onClick, trailing Chevron|Switch|None, subLabel, divider, content)`, `SegmentedControl3`) · Sliders.kt (`TextSizeSlider` 4 steps with "A"/"A" ends, `LineSpacingSlider` 1.5–2.3 step .1) · Indicators.kt (`PageIndicator`, `StreakPill(days)`, `ProgressDots(flags)`, `ProgressBar(progress?)`) · Text.kt (`TibetanText`, `LatinText`, `MixedText`, `ScreenTitle(title, gloss, trailing)`, `Overline`, `Greeting(timeOfDay)`) · Sheets.kt (`DictModalSheet`, `FolderPickerSheet(folders, selected, onPick, onCreate)`, `DirectionPickerSheet`, `LanguagePickerSheet`, `TextSizeSheet` using 2b-style option cards 64 high, selected = accentContainer + 2dp accent border + check 22).

### 7.3 Navigation & shell
`Routes.kt`: `@Serializable data object Onboarding`, `Main`, `data class Search(val query: String = "", val voice: Boolean = false)`, `data class Entry(val entryId: Long)`, `data object About`, `data object Licences`; `enum class MainTab { SEARCH, SAVED, LEARN, PROFILE }`. Start = `Main` if onboarded else `Onboarding` (popUpTo inclusive). `Search`/`Entry`/`About`/`Licences` are pushes without the bottom bar (push: slide 200 ms + fade; Search: fade). `Entry → Entry` (see-also) pushes again. Deep links declared now for later features: `app://entry/{entryId}`, `app://search?query=`; `NavigationEventBus` for widget/process-text. `MainScaffold` + activity-scoped `MainViewModel(tab)`; back on a non-SEARCH tab → SEARCH, then exit. `enableOnBackInvokedCallback=true`.

Shell: `MainActivity` (`AppCompatActivity`, splash keep-condition, edge-to-edge, night mode from `appearance`), `AppRoot` (`DictTheme` → `LocalToaster`/`LocalAppLanguage` → `AppNavHost` + `AppToastHost`, `SystemBarIcons`), `AppRootViewModel` (`AppRootState.Loading | Installing(progress?, totalEntries?) | Ready(...)`), `InstallingScreen` (3a: `FlagStrand(Splash)`, icon 84 + shadow, "བོད་ཡིག་ཚིག་མཛོད།" 26/1.8, "Tibetan Dictionary" 15/600 ls .02em, `ProgressBar` 120×3 + "Preparing {N} entries · offline" 11 muted; `Failed` → `FullEmptyState` + "Try again"), `DictionaryApplication` (`@HiltAndroidApp`, Crashlytics/Analytics collection `= !BuildConfig.DEBUG`, `AppInitializer.start()`), `AppInitializer` (IO: `LegacyCleanup.runOnce()` → `installer.ensureInstalled()` → `ready` → `syncLanguage()`), `LegacyCleanup` (`deleteDatabase("dictionarydb.db")` + `-shm/-wal`, `deleteSharedPreferences("com.kharagedition.dictionary")`, `"${packageName}_preferences"`, cancel notifications; once via prefs). Manifest: `INTERNET` only; remove CAMERA/READ_EXTERNAL_STORAGE/`uses-feature camera`/ads/ML Kit meta-data/`SettingActivity`/`SplashScreenActivity`; one exported `MainActivity` with `Theme.Dict.Splash` (`windowSplashScreenBackground` `#FBF8F3` / night `#1B1715`, icon drawable, `postSplashScreenTheme=Theme.Dict`), `windowSoftInputMode=adjustResize`, `localeConfig` (en, bo), `dataExtractionRules` excluding `dictionary.db`.

### 7.4 Screens (release 1) — state, events, visibility

**Onboarding** — `OnboardingUiState(step, stepCount /*2, 3 if ACCOUNTS*/, directions, selectedDirection, textSize, lineSpacing)`; events `Next, Skip, SelectDirection, SetTextSize, SetLineSpacing, ContinueWithoutAccount, SignIn`; effect `Finished`. Step 1 (2b): `FlagStrand(Onboarding)`, greeting "བཀྲ་ཤིས་བདེ་ལེགས།" 26/1.7 accent, title "Which way do you look up words most?", body "Sets the default direction. You can switch any time with one tap.", option cards per visible direction, "Continue". Step 2 (2c, no strand): "Comfortable to read?", "Tibetan is shown larger than English so stacked letters stay clear.", `PreviewCard` live, "Text size" + `TextSizeSlider`, "Line spacing" + `LineSpacingSlider`. Skip hidden on the last step; persists on each Continue.

**Home (SEARCH tab, 1a + 2a)** — `HomeUiState(greeting: TimeOfDay, streakDays: Int?, slots: DirectionSlots, selectedDirection, wotd: WordOfTheDayUi?, recents: List<RecentChipUi>, stats: LearnStatsUi?, micAvailable)`; events `OpenSearch, VoiceSearch, SelectDirection, WotdPlay, WotdToggleSave, WotdShare, WotdOpen, RecentTap(query), StatsTap`; effects `NavigateSearch(query?, voice), NavigateEntry(id), Share(text), Toast`. Layout: greeting row + `StreakPill`; `SearchField(Home)` (tap → Search) + `DirectionSwitcher`; `WordOfTheDayCard`; "Recent" + horizontal chip row; `StatsCard`.
| Element | Visible when |
|---|---|
| StreakPill | `streakDays != null && streakDays >= 2` |
| Mic | `Features.VOICE_SEARCH && micAvailable` |
| "More" slot | `slots.overflow.isNotEmpty()` |
| WOTD card / Wylie / speaker | `wotd != null` / `wotd.wylie != null` / `wotd.hasAudio` |
| Recent section | `recents.isNotEmpty()` |
| StatsCard / due sub-line | `Features.LEARN && stats != null` / `stats.dueCount != null` |

**Search (route, 1g/3b/1k)** — `SearchUiState(query: TextFieldValue, script: InputScript, slots, selectedDirection, autoSelected, phase: Suggestions|Loading|Results|NoResults, results: List<SearchResultUi>, resultCount, hasMore, recents: List<RecentRowUi>, showTip, suggestions: List<SuggestionUi>, alternateDirection: Direction?, noMatchHint: Wylie|English|Tibetan, micAvailable)`; `SearchResultUi(entryId, direction, headword, headwordIsTibetan, wylie?, matchRange?, headwordMatchRange?, gloss, showBadge)`; events `QueryChanged, Clear, Back, SelectDirection, OpenResult(id), RecentTap, ClearRecents, SuggestionTap(id, directionId), TryAlternateDirection, VoiceSearch, LoadMore`; effects `NavigateEntry, NavigateBack, StartVoiceSearch, FocusField`. Pipeline: `debounce(150)` → `distinctUntilChanged` → `mapLatest { detect → resolveDirection → search }`; skeleton only if >250 ms; `record()` on `OpenResult`; `markSearch()` on first non-empty result per query.
| Element | Visible when |
|---|---|
| Recent list + Clear | `phase == Suggestions && recents.isNotEmpty()` |
| TipBox (Wylie tip) | `phase == Suggestions && capabilities.hasWylie` |
| Skeleton | `phase == Loading` |
| "N results" | `phase == Results` |
| Row Wylie / badge | `wylie != null` / `showBadge` (hit from another direction) |
| First row highlighted | `results.first().field == exact prefix match` |
| No-results card + rows | `phase == NoResults && suggestions.isNotEmpty()` |
| "Try X instead" | `alternateDirection != null` |
| Mic | `Features.VOICE_SEARCH && micAvailable && query.text.isEmpty()` |

**Entry (route, 1h/1l)** — `EntryUiState(loading, entry: EntryUi?, isSaved, savedFolderName?, folderPicker: FolderPickerUi?, missing: Boolean)`; `EntryUi(id, direction, headword, headwordIsTibetan, wylie?, phonetic?, partsOfSpeech, label?, senses: List<SenseUi(number?, partOfSpeech?, text, examples: List<ExampleUi>, label?: canonical keys, note?)>, related: List<RelatedUi(entryId?, direction, headword, isTibetan)>, hasAudio, audioFile?, isTruncated)`; events `Back, ToggleSave, ChangeFolder, PickFolder(id?), CreateFolder(name), DismissFolderPicker, Copy, Share, PlayAudio, OpenRelated(RelatedUi), ReportCorrection`; effects `NavigateBack, NavigateEntry(id), NavigateSearch(query), Toast(msg, action?, onAction?), CopyToClipboard, ShareText, OpenEmail`. Save: `save(summary, lastUsedFolderId)` → toast "Saved {headword}[ to {folder}]" + "Change" → `FolderPickerSheet`; unsave → "Removed" + "Undo"; `markEntryOpened()` on load. Share text: "{headword} ({wylie}) — {senses}". Report: mailto `Links.DEV_EMAIL`, subject "Correction: {headword} (#{id})".
| Element | Visible when |
|---|---|
| POS "adj. · n." / Wylie / phonetic / audio | `partsOfSpeech.isNotEmpty()` / `wylie != null` / `phonetic != null` / `hasAudio` |
| Sense number | `senses.size > 1` |
| Per-sense "(n.)" | `sense.partOfSpeech != null && partsOfSpeech.size > 1` |
| Examples (all) | `sense.examples.isNotEmpty()` |
| Per-sense label badge (amended, I3) | `sense.label != null`: the sense's label keys not shared by every sense (shared keys stay the entry's `label` after the POS); keys → text in `ui/components/UsageLabels.kt` (en + bo strings) |
| Sense note (amended, I3) | `sense.note != null` (notes that are only "*" are dropped) |
| Truncated note | `isTruncated` |
| See also | `related.isNotEmpty()`; chip navigates when `entryId != null`, else searches `headword` |
| Report a correction | always |

**Saved (tab, 1i/3c)** — `SavedUiState(totalCount, folders: List<FolderUi>, selectedFolderId?, query, groups: List<SavedGroupUi(label: Today|ThisWeek|ThisMonth|Earlier, items)>, isEmpty, wotd: WordOfTheDayUi?)`; `SavedItemUi(entryId, direction, headword, headwordIsTibetan, secondary: AnnotatedString, folderId?)`; events `QueryChanged, SelectFolder, AddFolder(name), Unsave(id), UndoUnsave(id, folderId?), OpenEntry(id), SaveWotd`; effects `Removed(id, folderId?) → toast "Removed" + Undo`, `NavigateEntry`. Populated when `!isEmpty`; folder chip row = "All N" + folders + `AddChip`; search-within field when `totalCount >= 8`; empty state (72 circle + heart, "Nothing saved yet", "Tap the heart on any entry to keep it here.[ Saved words become your flashcards in Learn.]", CTA "Save today's word {headword}" when `wotd != null && !wotd.isSaved`); inline "No matches" when a filter empties the list.

**Profile (tab, 2f/3d)** — `ProfileUiState(account: AccountUi?, textSize, lineSpacing, appearance, defaultDirection: Direction, directions, language, wotdNotification: WotdNotifUi?, versionName, entryCount: Int?, sheet: TextSize|Direction|Language|null)`; events `OpenSheet, DismissSheet, SetTextSize, SetLineSpacing, SetAppearance, SetDefaultDirection, SetLanguage, ToggleWotdNotification, OpenLink(Link), SignIn, OpenAccount`; effects `OpenUrl, ShareApp(text), NavigateAbout`. Layout: `ScreenTitle("Profile","སྒེར།")`; `AccountCard` (ACCOUNTS); group "READING": "Text size & spacing" value "{Default} · {1.9}" + preview box → `TextSizeSheet`; "Theme" `SegmentedControl3(Light/Dark/System)`; "Default direction" value + chevron → `DirectionPickerSheet`; "App language" value English/བོད་ཡིག → `LanguagePickerSheet` (applies `AppLocale.apply` immediately; nav/labels relabel via `LocalAppLanguage`); "Word of the day notification" (WOTD_NOTIFICATION); footer links About · Rate · Share app · More apps · Support & donate · Privacy (each hidden when its `Links` value is blank); version line "Tibetan Dictionary {version} · {N} entries · offline" (count omitted when null; Tibetan digits in bo). **About** route: app name/version, "Dictionary data: Monlam Tibetan Dictionary by Lobsang Monlam (GPL-3)" + `attribution_text`, fonts (OFL), a "Licences" row (→ **Licences** route: the full GPL-3.0 and OFL texts from `assets/licenses`, reflowed, in a lazy list), correction e-mail.

### 7.5 Domain units (pure Kotlin, JUnit + Truth)
| Unit | Signature | Rule / pinned cases |
|---|---|---|
| `DetectScript` | `detect(q): InputScript {TIBETAN, LATIN, WYLIE, HAN}` | any U+0F00–0FFF → TIBETAN; CJK → HAN; lower-cased tokens all `^[a-z'+.~-]+$` and (any token starts with a Tibetan onset cluster `bk,bg,bd,bs,bz,br,bl,dk,dg,db,dm,dp,dr,gc,gn,gt,gd,gz,gy,gs,rk,rg,rng,rj,rny,rt,rd,rn,rb,rm,rts,rdz,sk,sg,sng,sny,st,sd,sn,sp,sb,sm,sts,lk,lg,lng,lc,lj,lt,ld,lp,lb,lh,mkh,mg,mng,mch,mj,mny,mth,md,mn,mtsh,mdz,'kh,'g,'ch,'j,'d,'ph,'b,'tsh,'dz,tsh,dz,zh,ng,ny` OR contains `'` OR ≥2 tokens from `{pa,ba,ma,po,bo,mo,pa'i,ba'i,kyi,gyi,gi,las,la,na,dang,yin,red,'dug,yod}`) → WYLIE; else LATIN. Cases: "བཀྲ"→T, "bkra"→W, "bkra shis"→W, "snying rje"→W, "'jam dpal"→W, "compassion"→L, "pa"→L, "Love"→L, ""→L |
| `ResolveDirection` | `resolve(script, selected, pinned, directions, hits: Map<String,Int>): Resolution(direction, autoSelected, alternate?)` | pinned → selected; TIBETAN → selected if its source is tibt else first tibt direction; LATIN/WYLIE → candidates {latn-source direction (HEADWORD), tibt-source with `hasWylie` (WYLIE)}; pick the one with hits (prefer exact); tie → selected if candidate, else latn for LATIN / tibt for WYLIE; `alternate` = other candidate with hits, or the other candidate when all zero. Cases: WYLIE hits{bo-en:38}→bo-en auto; LATIN "pa" hits{en-bo:3, bo-en:12}→en-bo, alt bo-en; TIBETAN selected en-bo→bo-en auto |
| `DirectionSlots` | `data class DirectionSlots(fixed, overflow, moreLabel?)` with `companion fun compute(all, defaultId, selectedId): DirectionSlots` | canonical order by `position`; ≤3 → fixed=all; else fixed=[default, its reverse ?: next canonical], overflow=rest; selected∈overflow → `moreLabel`; default not in `all` → first visible (Review Focus 3). Cases: 3 dirs → 3 fixed; 4 dirs default bo-en → fixed [bo-en, en-bo], overflow [bo-bo, zh-bo]; selected zh-bo → more label "中→བོད" |
| `Greeting` | `timeOfDay(hour): MORNING(5–11) \| AFTERNOON(12–16) \| EVENING(17–4)` | 9→M, 14→A, 20→E, 2→E |
| `StreakCalculator` | `streak(activeDays: Set<LocalDate>, today): Int` | consecutive days ending today or yesterday: {0,−1,−2}→3; {−1,−2}→2; {−2}→0 |
| `SavedGrouping` | `group(items, today): List<SavedGroup>` | Today / ThisWeek (≤7 d) / ThisMonth / Earlier, newest first |

---

## 8. Tasks

Lanes: **L0 Foundation** runs first and alone. Then **L1 Data**, **L2 Home+Search**, **L3 Entry+Saved**, **L4 Profile+Onboarding+Locale**, **L5 Shell+Release** run in parallel (one subagent each, disjoint file ownership). **L6 Integration** last. Every task: write the test → see it fail → implement → see it pass → `./gradlew :app:testDebugUnitTest :app:lintDebug` green. No commits.

### L0 Foundation (owner: one agent; everything below must land before L1–L5)

**T0.1 Project skeleton**
Files: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `gradle.properties`, delete `build.gradle`/`app/build.gradle`/`settings.gradle`, delete old `app/src/main/{java,res/layout,res/navigation,res/menu,res/xml/root_preferences.xml,res/anim}` and `app/src/main/assets/dictionarydb.sqlite.db`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/{strings,themes,colors}.xml`, `.github/workflows/main.yml`.
- [ ] Copy the Prayer app's catalog/plugins/settings verbatim; drop media3/play-update/serialization-if-unused/messaging; keep room, datastore, splashscreen, adaptive, firebase analytics+crashlytics, test libs.
- [ ] `app/build.gradle.kts`: namespace/applicationId, minSdk 24, versionCode 21, versionName "3.0.0", compose, buildConfig, `room { schemaDirectory("$projectDir/schemas") }`, `ksp { arg("room.generateKotlin","true") }`, `testOptions.unitTests.isIncludeAndroidResources = true`, lint abortOnError, no flavors (single production build type pair).
- [ ] Manifest per §7.3 (placeholder `MainActivity`), `Theme.Dict`/`Theme.Dict.Splash` styles, `values/strings.xml` with **every key** listed in T0.6, `xml/locales_config.xml`, `xml/data_extraction_rules.xml`.
- [ ] `src/test/.../SmokeTest.kt`; `src/androidTest/.../HiltTestRunner.kt`; `src/test/resources/robolectric.properties` (`sdk=35`, `sqlite.mode=NATIVE`).
- [ ] CI `main.yml`: JDK 17 temurin, `gradle/actions/setup-gradle@v4`, write `google-services.json` from secret, `./gradlew assembleDebug testDebugUnitTest lintDebug`, upload APK.
- [ ] Verify: `./gradlew :app:assembleDebug :app:testDebugUnitTest` passes with the empty app.

**T0.2 Core + contracts**
Files: `Features.kt`, `core/{Links,ContextExt}.kt`, `core/di/{AppModule,CoroutinesModule,DataStoreModule,RepositoryModule}.kt`, `core/locale/{AppLanguage,AppLocale}.kt`, `core/error/ErrorReporter.kt`, `core/time/AppClock.kt`, `core/text/{TibetanScript,TibetanNumerals}.kt`, `core/audio/AudioPlayer.kt` (interface + `AudioSource`), `core/speech/SpeechAvailability.kt`, `data/model/*` (§5.4), `data/repository/*` **interfaces only**, `data/install/DictionaryInstaller.kt` (interface + `InstallState`), `data/prefs/{UserPrefs,UserPreferencesRepository,UserPreferencesRepositoryImpl}.kt`, `src/test/.../fakes/{FakeDictionaryRepository,FakeSavedRepository,FakeRecentSearchesRepository,FakeActivityRepository,FakeWordOfTheDayRepository,FakeUserPreferencesRepository,FakeInstaller,FakeClock}.kt`, `src/test/.../SampleData.kt`.
- [ ] Tests first: `TibetanScriptTest` (`isTibetan`, `ensureTsheg("ཀ་བ")=="ཀ་བ་"`, `stripTrailingTsheg`), `TibetanNumeralsTest` ("42"→"༤༢"), `UserPreferencesRepositoryImplTest` (defaults; round-trips; `lineSpacing` clamped 1.5–2.3).
- [ ] Implement; `RepositoryModule` binds `UserPreferencesRepositoryImpl` now and leaves `@Binds` stubs for the L1 impls (compile with `TODO()` impl classes named exactly `DictionaryRepositoryImpl`, `SavedRepositoryImpl`, `RecentSearchesRepositoryImpl`, `ActivityRepositoryImpl`, `WordOfTheDayRepositoryImpl`, `DictionaryInstallerImpl` so L1 replaces bodies only).

**T0.3 Theme + icons**
Files: `ui/theme/{DictColors,DictTypography,DictDimens,DictTheme,DictIcons,SystemBarIcons}.kt`, `res/font/*` (Jomolhari from the Prayer app; Noto Sans downloaded), `ui/preview/Previews.kt`.
- [ ] Test: `DictTypographyTest` (Tibetan line height scales with `lineSpacing/1.9`; Latin does not; `textScale` multiplies both).
- [ ] Implement §6.1–6.4; `DictTheme(darkTheme, textScale, lineSpacing, language, content)` exposes `DictTheme.colors/type/dimens`; M3 bridge; `LocalAppLanguage`.
- [ ] `TokensPreview` showing both palettes and the type scale (light/dark).

**T0.4 Component library**
Files: `ui/components/*.kt` per §7.2.
- [ ] For each file: write the composables with previews covering every state listed in §7.2 (light, dark, `locale="bo"`), plus `ExtremeScalePreview` (`textScale 1.3`, `lineSpacing 2.3`, `fontScale 2f`) for `EntryRow`, `SenseCard`, `WordOfTheDayCard`, `SettingsRow`, `BottomNavBar` — nothing may clip (Review Focus 5).
- [ ] Compose unit tests (Robolectric, `createComposeRule`): `DirectionSwitcherTest` (3 segments; 4 directions → "More" opens menu; `autoSelected` moves selection), `SearchFieldTest` (clear visible only with text; mic only when `showMic` and empty), `EntryRowTest` (bold accent span over `matchRange`), `BottomNavBarTest` (bo locale → Tibetan primary label), `ToastTest` (action callback).

**T0.5 Navigation, shell scaffolding, screen stubs**
Files: `navigation/{Routes,AppNavHost,NavigationEvents}.kt`, `ui/app/{AppRoot,AppRootViewModel,InstallingScreen}.kt`, `ui/main/{MainScaffold,MainViewModel}.kt`, stub `ui/{home,search,entry,saved,profile,onboarding,about,learn}/XScreen.kt` (each a `Text(route name)`), `MainActivity.kt`, `DictionaryApplication.kt`, `core/init/{AppInitializer,LegacyCleanup}.kt`.
- [ ] Tests: `AppRootViewModelTest` (Installing while `Copying`; Ready carries prefs), `MainViewModelTest` (LEARN tab hidden when `Features.LEARN` false), `LegacyCleanupTest` (creates `dictionarydb.db` + two prefs files → gone; second run no-op).
- [ ] Implement §7.3; `AppNavHost` already routes every destination to its stub so later lanes only replace stub bodies.

**T0.6 Strings**
Files: `res/values/strings.xml` (English + Tibetan gloss keys), `res/values-bo/strings.xml` (empty skeleton; L4 fills).
- [ ] Keys: `app_name`, `app_name_tibetan`, `tab_search/saved/learn/profile`, `tab_*_gloss`, `greeting_morning/afternoon/evening` (both scripts in one string), `streak_days` (plural), `search_placeholder` ("Search a word · ཚིག་འཚོལ།"), `search_placeholder_saved`, `wotd_overline`, `wotd_open`, `recent`, `clear`, `results_count` (plural), `no_match_title`, `no_match_hint_wylie/english/tibetan`, `try_direction_instead`, `wylie_tip`, `see_also`, `example_abbr`, `report_correction`, `report_correction_prefix`, `entry_truncated`, `entry_missing`, `saved_title`, `saved_gloss`, `saved_count` (plural), `saved_to_folder`, `saved_simple`, `change`, `undo`, `removed`, `all_folder`, `new_folder`, `saved_empty_title/body/body_learn`, `save_todays_word`, `no_matches`, `profile_title/gloss`, `reading`, `text_size_spacing`, `theme`, `theme_light/dark/system`, `default_direction`, `app_language`, `language_en/bo`, `wotd_notification`, `about/rate/share_app/more_apps/support/privacy`, `version_line`, `continue`, `skip`, `onboarding_direction_title/body`, `onboarding_textsize_title/body`, `onboarding_account_title/body`, `continue_without_account`, `sign_in_google`, `sign_in_later_note`, `text_size`, `line_spacing`, `text_size_small/default/large/largest`, `installing_entries`, `install_failed`, `try_again`, `voice_search`, `play_audio`, `save`, `saved_cd`, `copy`, `share`, `back`, `close`, `more`, `direction_desc_bo_en/bo_bo/en_bo`, `about_data`, `about_fonts`, `share_app_text`.

### L1 Data (owner: one agent; files: `tools/dictionary/**`, `app/schemas/**`, `data/local/**`, `data/search/**`, `data/repository/*Impl.kt`, `data/install/DictionaryInstallerImpl.kt`, `core/di/DatabaseModule.kt`, `app/src/main/assets/dictionary/`)

**T1.1 Room entities, DAOs, databases, schema export**
- [ ] Tests (Robolectric, in-memory, fixture rows via a test-only `@Insert` DAO): `EntryDaoTest.prefixOrdersExactFirstThenMonlam`, `.englishPrefixIsCaseInsensitive`, `.wyliePrefixCaseInsensitiveAndNullSafe`, `.ftsHeadwordMatchesSyllableInsideWord` (`ཤིས` hits `བཀྲ་ཤིས་`, `ཤ` does not), `.ftsGlossReverseLookup`, `.wotdRankLookup`; `SavedEntryDaoTest`, `RecentSearchDaoTest.recordUpsertsAndTrims`, `ActivityDayDaoTest`.
- [ ] Implement §5.1/§5.2 entities (`TibetanTokenChars.ARG` literal), DAOs (SQL in §5.1 notes), `DictionaryDatabase` (`VERSION = 1`, `exportSchema = true`), `UserDatabase`, relations, `DatabaseModule` (builder per §5.1).
- [ ] `./gradlew :app:kspDebugKotlin` → commit-ready `app/schemas/.../DictionaryDatabase/1.json` and `UserDatabase/1.json`.

**T1.2 Python pipeline: schema + text + common parsers**
- [ ] `tests/test_schema.py` (`test_schema_roundtrip`, `test_fts_tokenizer_keeps_tibetan_syllables`, `test_user_version_set`), `tests/test_text.py` (`strip_csv_quotes`, Tibetan/English headword normalisation incl. ` ༡`, `༌༡`, `1`, `༠`, `lead1`, `Adam &  Eve`; `is_truncated` 257-char `to perv` → True, ending `.` → False), `tests/test_common.py` (`split_numbered` sequence rule, `split_subsenses_bo`, `split_subsenses_gt`).
- [ ] Implement `dictbuild/{schema,source,text,wylie,model,parsers/common}.py` per §5.3 (`WylieConverter` protocol with `PyewtsConverter`/`NullConverter`, `load_converter(require)`).

**T1.3 Python parsers: entotb / tben / tbtb + grouping/related/wotd/writer**
- [ ] `tests/test_entotb.py`: `simple_pos` (`abandon` → vt + n), `house_blocks_subsenses_label` (5 senses; sense 4 label `astrol`; sense 5 vt), `water` (8 senses n,n,n,n,vt,vt,vt,vi), `love_trailing_example_without_translation`, `pound_of_butter_example`, `good_combined_pos`, `run_inflection_note`, `sloppy_pos_without_dot`, `undotted_domain_tags`, `parenthetical_prefix`, `asterisk_note`, `synonym_marker`, `single_latin_word_not_example`, `tibetan_numeral_subpoints_kept`.
- [ ] `tests/test_tben.py`: `quotes_and_tags`, `trailing_citation_tag`, `multi_tags`, `def_prefix`, `wylie_only_conversion` (skip without pyewts), `wylie_only_fallback_without_converter`, `sa_whole`, `sa_inline_comma`, `sa_list`, `sa_longest_prefix_resolution`, `truncated_flag`, `homonym_group_merge` (rows 114–117 → one `ཀ་བ་` entry, 4 senses), `numbered_group_without_plain_row`.
- [ ] `tests/test_tbtb.py`: `numbered_with_examples`, `entry_label_applies_to_all_senses`, `sense_level_label_pos`, `subsenses_gt`, `label_alias_and_unknown_parenthetical`, `double_spaces_collapsed`. `tests/test_wotd.py`: eligibility + deterministic ranks. `tests/test_build_smoke.py` (`--limit 300`, FK check empty, no `room_master_table`).
- [ ] Implement parsers, `grouping.py`, `related.py`, `wotd.py`, `stats.py`, `writer.py`, `report.py`, `build_db.py`, `README.md`, `requirements.txt`.

**T1.4 Build the asset**
- [ ] `python3 -m venv tools/dictionary/.venv && … pip install -r requirements.txt`; run `build_db.py` with `--require-wylie`; check the report against §5.3 counts; file lands at `app/src/main/assets/dictionary/dictionary.db` (D4).
- [ ] JVM test `DictionaryAssetVersionTest` (header bytes 60–63 == `VERSION` == schema JSON version); Robolectric `DictionaryAssetOpenTest` (opens the real asset with the production builder: 3 directions; `ཀ་བ་` ≥4 senses with a resolved see-also; `house` 5 senses, sense 4 `astrol`; `wotd_count > 0`; `search("bkra","bo-en")` first hit starts with `བཀྲ`).

**T1.5 Search internals**
- [ ] Tests: `ScriptDetectorTest`, `QueryNormalizerTest` (Review Focus 1 inputs), `PrefixBoundsTest`, `FtsQueryBuilderTest` (`བཀྲ་ཤི` → `"བཀྲ ཤི*"`, operators stripped), `HighlighterTest` (Wylie `bkra` → wylie 0..4, headword syllable 1; `bkra sh` → syllables 1–2), `EditDistanceTest`.
- [ ] Implement `data/search/{QueryNormalizer,ScriptDetector,PrefixBounds,FtsQueryBuilder,Highlighter,EditDistance}.kt`.

**T1.6 `DictionaryRepositoryImpl` + `WordOfTheDayRepositoryImpl`**
- [ ] Tests (Robolectric + Turbine): `capabilitiesHideDirectionsWithZeroEntries`, `capabilitiesFeaturesFollowCounts` (audio_count 0 → false; insert audio + count 1 → true, no code change), `searchTibetanInBoEnUsesHeadword`, `searchLatinInBoEnUsesWylieThenGloss`, `searchLatinInEnBoUsesHeadword`, `searchTibetanInEnBoUsesGloss`, `didYouMeanShortensPrefix` (`བཀྲ་ཤིསས` → `བཀྲ་ཤིས་`), `didYouMeanCrossDirection`, `pagingHasMore`, `entryDetailGroupsSubSensesAndExamples`, `summariesPreserveIdOrder`, `WordOfTheDayRepositoryImplTest` (same date → same entry; dates differ; `wotd_count = 0` → null).
- [ ] Implement per §5.4 algorithm + mappers.

**T1.7 `SavedRepositoryImpl`, `RecentSearchesRepositoryImpl`, `ActivityRepositoryImpl`**
- [ ] Tests: save/unsave; folder delete → `folderId` null; `search` matches wylie; `observeSavedIds` updates; recent upsert keeps count 1 and bumps timestamp; blank/1-char ignored; trims to 50; `activity_days` increments and `observeActiveDays` returns dates; `entryDetailRedirectOnly` (entry with 0 senses and 1 related → `Entry.senses` empty, `related` size 1).
- [ ] Implement.

**T1.8 `DictionaryInstallerImpl`**
- [ ] Tests (Robolectric): fresh install → `Copying` progress reaches 1.0 → `Ready(totalEntries > 0)`; stale `user_version` file → deleted and re-copied; corrupted copy → `Failed`, retry succeeds.
- [ ] Implement per §5.1; wire into `AppInitializer`.

### L2 Home + Search (owner: one agent; files: `domain/**`, `ui/home/**`, `ui/search/**`)

**T2.1 Domain** — tests then code for `DetectScript`, `ResolveDirection`, `DirectionSlots` (incl. `fallbackWhenDefaultHidden`), `Greeting`, `StreakCalculator` per §7.5.

**T2.2 `SearchViewModel` + `SearchScreen`/`SearchContent`**
- [ ] `SearchViewModelTest` (Turbine, fakes): debounce; auto-direction for Wylie (`autoSelected = true`); tapped segment pins; empty query → Suggestions with recents; tip hidden when `!hasWylie`; no results → suggestions + alternate; `oddQueries` (Review Focus 1: `"བཀྲ shis"`, `"བཀྲ་ཤིས།"`, `" a "`, `""` never record a recent, never throw); `OpenResult` records recent + `markSearch`.
- [ ] Screen per §7.4 with testTags `search_field`, `direction_segment_{id}`, `result_row_{id}`, `suggestion_row_{id}`; previews for each phase, light/dark.

**T2.3 `HomeViewModel` + `HomeScreen`**
- [ ] `HomeViewModelTest`: WOTD null hides card; streak <2 hides pill; stats null hides card; `SelectDirection` persists `defaultDirectionId`; `WotdToggleSave` toggles via `SavedRepository` and emits toast; `RecentTap` → `NavigateSearch(query)`.
- [ ] Screen per §7.4; previews light/dark/bo.

### L3 Entry + Saved (owner: one agent; files: `ui/entry/**`, `ui/saved/**`, `domain/SavedGrouping.kt`, `core/audio/MediaPlayerAudioPlayer.kt`)

**T3.1 `EntryViewModel` + `EntryScreen`**
- [ ] `EntryViewModelTest`: save → toast "Saved … to {folder}" with "Change"; unsave → "Removed" + Undo; audio hidden when `audioFile == null`; related empty hides section; `redirectOnlyEntry` (0 senses + related → no sense cards, chips shown); missing entry → `missing = true` + toast; `OpenRelated` with null id → `NavigateSearch(headword)`.
- [ ] Screen per §7.4 (toolbar heart/copy/share 48; header; sense cards; see-also; footer); testTags `entry_save`, `entry_audio`, `entry_related_{id}`.

**T3.2 `SavedViewModel` + `SavedScreen`/`SavedEmpty`**
- [ ] `SavedGroupingTest`; `SavedViewModelTest`: grouping; folder filter; undo restores folder; `missingEntryToast` (Review Focus 4); empty state when no rows; CTA hidden when WOTD already saved; search-within filters.
- [ ] Screens per §7.4; `FolderPickerSheet` usage; testTags `saved_row_{id}`, `folder_chip_{id}`.

**T3.3 `MediaPlayerAudioPlayer`** — plays `AudioSource.Asset/File`, stops on new play; unit test with a fake `MediaPlayer` factory.

### L4 Profile + Onboarding + Locale (owner: one agent; files: `ui/profile/**`, `ui/onboarding/**`, `ui/about/**`, `res/values-bo/strings.xml`)

**T4.1 `ProfileViewModel` + `ProfileScreen` + sheets**
- [ ] `ProfileViewModelTest`: language change applies `AppLocale` and persists; version line omits count when null; `defaultDirectionFallback` (stored id not visible → first visible); Privacy link hidden when `Links.PRIVACY_URL` blank; theme change persists.
- [ ] Screen per §7.4 + `AboutScreen`.

**T4.2 `OnboardingViewModel` + `OnboardingScreen`/`OnboardingSteps`**
- [ ] `OnboardingViewModelTest`: `stepCount` 2 when `!Features.ACCOUNTS`; Skip persists defaults + onboarded; Continue persists per step; last step hides Skip.
- [ ] Screens per §7.4 with `PageIndicator`, option cards, live `PreviewCard`.

**T4.3 Tibetan strings** — fill `values-bo/strings.xml` for every key in T0.6 (Tibetan primary; English gloss keys mirrored), run `lint` with `MissingTranslation` enabled for `bo`; verify `BottomNavBar`/`ScreenTitle`/`SettingsRow` in `locale="bo"` previews match 3d.

### L5 Shell + Release (owner: one agent; files: `MainActivity.kt`, `DictionaryApplication.kt`, `core/init/**`, `ui/app/InstallingScreen.kt`, `res/drawable/ic_launcher_*`, `res/mipmap-*`, `res/drawable/ic_splash_icon.xml`, `res/values*/themes.xml`, `app/proguard-rules.pro`, `fastlane/**`, `.github/workflows/release.yml`, `app/src/main/res/xml/*`)

**T5.1 Adaptive icon + splash** — vectors per §6.5 (glyph outlines via fontTools), monochrome layer, legacy PNG mipmaps, `Theme.Dict.Splash`; verify on device (icon, splash, dark splash).
**T5.2 `InstallingScreen`** — 3a layout bound to `AppRootState.Installing`; preview at progress 0.6; `Failed` state with retry.
**T5.3 Firebase + proguard + backup rules** — Crashlytics/Analytics on in release only; `google-services` plugin; proguard keeps for `navigation.**` + Room; `data_extraction_rules.xml` excludes `dictionary.db`.
**T5.4 Release pipeline** — `release.yml` + `fastlane/Fastfile` adapted from the Prayer app (package id, task `bundleRelease`, upload-cert SHA to be provided by the owner), `versionCode 21`.

### L6 Integration (owner: one agent, after L1–L5)

**T6.1 Wire real bindings** — remove `TODO()` stubs, run the full unit suite and `DictionaryAssetOpenTest`.
**T6.2 Instrumented flows (connected Pixel 8)** — `SearchToEntryToSaveFlowTest` (type "bkra" → row "བཀྲ་ཤིས།" with bold prefix → tap → Entry headword → heart → toast "Saved" → Saved tab lists it), `OnboardingFlowTest`, `DarkThemeSmokeTest`, `BoLocaleSmokeTest`.
**T6.3 Visual pass** — side-by-side with mockups 2a, 3b, 1g, 1h, 1i, 3c, 2f, 3d, 2b, 2c, 3a in light and dark at 360dp, plus Review Focus 5 extremes; fix deviations.
**T6.4 Cleanup** — delete remaining old resources/drawables, `demo/` screenshots refreshed, README updated with data-build instructions, `.gitignore` for `tools/dictionary/.venv`.

---

## 9. Verification (end-to-end)

1. `cd tools/dictionary && .venv/bin/python -I -m pytest` → all green; `build_db.py --report` counts within §5.3 ranges.
2. `./gradlew :app:testDebugUnitTest :app:lintDebug` → green, including `DictionaryAssetOpenTest` (real asset opens under Room validation) and `DictionaryAssetVersionTest`.
3. `./gradlew :app:installDebug` on the connected Pixel 8 (Android 17): first launch shows the splash with a determinate bar, then onboarding (2 steps) → Home with Word of the Day; typing `bkra` in Search highlights "bkra" and lists "བཀྲ་ཤིས།" first; typing `compassion` auto-switches to EN→བོད; `bkrashi` shows "did you mean"; Entry shows numbered senses for `house` (5) and `ཀ་བ་` (≥4) with see-also chips; saving shows the toast with "Change"; Saved groups by date; Profile language → བོད་ཡིག relabels the nav immediately; theme Dark matches 1j/1k/1l; text size LARGEST + spacing 2.3 does not clip.
4. Hidden-by-data proof: in a scratch copy of the asset set `audio_file` on one entry and `audio_count = 1`, reinstall → the speaker button appears on that entry with no code change; set `entry_count = 0` on `bo-bo` → the switcher shows two directions; add a `zh-bo` row with entries → "More" appears.
5. `./gradlew :app:connectedDebugAndroidTest` → `SearchToEntryToSaveFlowTest`, `OnboardingFlowTest` pass on the device.
6. Reinstall over the old 2.1.0 build: `dictionarydb.db` and old prefs are deleted; no crash.
7. CI `main.yml` green on push (JDK 17).

## 10. Risks

- Room prepackaged-DB validation is strict (column order, DEFAULT spelling, index names, FTS options): mitigated by executing Room's exported `createSql` in Python and `DictionaryAssetOpenTest` in CI; never ship `room_master_table`.
- `user_version` mismatch silently yields an empty DB under destructive fallback: `DictionaryInstaller` + `DictionaryAssetVersionTest` guard it; bump `VERSION` on every data release.
- FTS + Tibetan tokenisation depends on `tokenchars`; guarded by a pytest and a Robolectric test (`sqlite.mode=NATIVE`).
- Asset ~40 MB raw / ~15–18 MB in APK; first-open copy 0.5–1.5 s shown on the splash, never lazily.
- Parser over-reach on free-text definitions: whitelists + sequence checks + build report (unresolved `SA`, unknown labels) keep regressions visible.
- Noto Sans download and `pyewts` install need network at implementation time.
