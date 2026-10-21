# Dzongkha dictionary pack — design

Status: approved by the owner on 2026-10-20 (plan-mode review). Implementation plan:
`docs/superpowers/plans/2026-10-20-dzongkha-pack.md`.

## 1. Context and goal

Tibetan Dictionary 3.0 (`feature/v3-rewrite`) ships one read-only Room database built from the Monlam source
(`app/src/main/assets/dictionary/dictionary.db`, 86.8 MB raw, 27.7 MB deflated; directions `bo-en`, `bo-bo`,
`en-bo`). The owner has the three DDC Dzongkha StarDict dictionaries (`~/Downloads/dzongkha/`: dz-en 35,543,
en-dz 23,237, dz-dz 22,787 entries; dated 2014-02-03) and written permission from the DCDD to publish them.
Profiled 2026-10-20: every file is complete (index counts match the `.ifo` word counts, no empty definitions, no
missing letters), 99–100 % of entries carry a part of speech, and only 19 % of Dzongkha headwords exist in Monlam.

Goal: ship both dictionaries as two **separate** databases ("packs"). A Bhutan SIM (resource qualifier `mcc402`)
defaults the app to the Dzongkha pack; everyone else defaults to Tibetan. The user confirms or changes the pack in
onboarding and can switch later in Profile. The two packs never mix: directions, entries, word of the day, saved
words, folders and recent searches are all scoped to the active pack. Search, entry, saved, profile, theming and
the rest of the app are unchanged.

Owner decisions (2026-10-20):
- Two database files, not one combined file.
- Default by SIM country via `values-mcc402`; switch in onboarding and in Profile.
- No mixing of the two dictionaries anywhere.
- The learning streak (`activity_days`) stays shared across packs.
- At first launch only the chosen pack is copied; the other is copied on the first switch.
- DCDD permission is in hand; the licence text for the app comes from the owner.
- Per-task commits on `feature/v3-rewrite` (no AI attribution anywhere, never push).

Out of scope (stated): a Dzongkha UI translation (`values-dz`); Play Asset Delivery; the fourth DDC set (bo-dz);
auto-switching packs for a deep link into the inactive pack (it shows the existing `entry_missing` state).

## 2. The pack model

```kotlin
enum class DictionaryPack(val id: String, val dbName: String, val assetPath: String, val defaultDirectionId: String,
                          val licenceAsset: String) {
    TIBETAN("bo", "dictionary-bo.db", "dictionary/bo.db", "bo-en", "licenses/GPL-3.0.txt"),
    DZONGKHA("dz", "dictionary-dz.db", "dictionary/dz.db", "dz-en", "licenses/DDC-permission.txt");
}
```

Both packs share `DictionaryDatabase` (one Room schema, one `VERSION`, one pipeline DDL). Only the file, the asset
and the rows differ. Entry ids are globally unique across both files: the Dzongkha build uses `id_base`
4,000,000 / 5,000,000 / 6,000,000 (Monlam keeps 1M / 2M / 3M). `saved_entries.entry_id` is the primary key of
`user.db`, so this is required; a pipeline test pins that pack id bases and direction ids never overlap.

## 3. Default pack and preferences

- `res/values/packs.xml`: `default_dictionary_pack` = `bo`; `res/values-mcc402/packs.xml` overrides it with `dz`.
  The resource system does the SIM-country detection (no telephony permission). Wi-Fi-only devices and foreign
  SIMs in Bhutan get Tibetan; the Profile switch covers them.
- `values-mcc402/strings.xml` holds **only** `app_name` ("Dzongkha Dictionary") and `app_name_tibetan`
  ("རྫོང་ཁའི་ཚིག་མཛོད།"). The MCC qualifier outranks language, so any other key there would override the UI
  language for every Bhutan-SIM user. The launcher label follows the SIM (owner's choice); the data follows the
  setting. (The staged copy of this file came from the Dzongkha Keyboard app and the working copy was a full copy of
  `values-bo`; both are replaced.)
- `UserPrefs` gains `dictionaryPack: DictionaryPack` (resolved, never null) and `dictionaryPackChosen: Boolean`.
  `UserPreferencesRepositoryImpl` receives the resource default (`@DefaultDictionaryPack`) and resolves
  `stored ?: default`.
- `defaultDirectionId` and `lastUsedFolderId` are stored per pack inside the Impl (`default_direction_id_<pack>`,
  `last_used_folder_id_<pack>`), read for the stored pack in the same snapshot, falling back to
  `pack.defaultDirectionId` / null; the old single keys are read through as the `bo` values. `UserPrefs` fields and
  setters keep their names, so no caller changes. Switching packs and back keeps each pack's own choices.
- The pack pref is written **only after** a pack has been installed and activated (§5), so the stored pack and the
  active pack never disagree.

## 4. Two database files, one active

- **`DictionaryDatabases`** (`data/local`, `@Singleton`; replaces the `DictionaryDatabase` and dictionary-DAO
  providers): `get(pack)` builds Room lazily and synchronised per pack
  (`Room.databaseBuilder(context, DictionaryDatabase::class.java, pack.dbName).createFromAsset(pack.assetPath)
  .fallbackToDestructiveMigration(dropAllTables = true).setJournalMode(TRUNCATE)`) and keeps both open (Room 2.8
  forbids reopening a closed instance; each builder is bound to its own file + asset, so the `createFromAsset`
  fallback can never copy the wrong asset). `active: StateFlow<DictionaryPack?>` (null until the first activation),
  `activeDb` (throws `IllegalStateException` before activation), `activate(pack)`. Rule, pinned in KDoc and tests:
  nothing but the installer calls `get()` for a pack that is not active.
- **Repositories** inject `DictionaryDatabases` instead of DAOs. `DictionaryRepositoryImpl.capabilities =
  active.filterNotNull().flatMapLatest { combine(db.directionDao().observeVisible(), db.metaDao().observeAll()) }`;
  one-shot calls use `activeDb`; `SearchEngine` is stateless and built per call. `WordOfTheDayRepositoryImpl` has
  the same shape; `wotd_direction_id` comes from each file's own `meta`.
- **Installer**: `ensureInstalled(pack)`; `InstallState.Checking | Copying(pack, progress) | Ready(pack,
  totalEntries) | Failed(pack, cause)`; target `pack.dbName`, asset `pack.assetPath`, recorded size
  `R.integer.dictionary_asset_bytes_<pack>`; `AssetSource.open(pack)` / `length(pack)`;
  `AssetSizeProvider.expectedBytes(pack)`. It short-circuits when the pack is already active (a retry or
  "keep current" can never re-open-and-delete the live file), calls `activate(pack)` just before emitting `Ready`,
  never deletes the other pack's file (switching back is instant), and on its first run deletes
  `dictionary-*.db.tmp` for both packs and the pre-pack `dictionary.db` with its `-journal`/`-wal`/`-shm` siblings.
- **`DictionarySwitcher`** (`data/install`, `@Singleton`, runs on `@ApplicationScope` so a popped view model cannot
  cancel it; one switch at a time): `switchTo(pack): Job` = if the pack is already active, persist the pref and
  return; else `installer.ensureInstalled(pack)` (which activates on success) **then** `prefs.setDictionaryPack
  (pack)`. `state: StateFlow<SwitchState>` = `Idle | InProgress(pack, progress) | Failed(pack, cause)`;
  `retry()`; `dismiss()` ("keep current dictionary"). Process death mid-copy therefore lands on the old pack.
  Cold start keeps its shape: `AppInitializer.start()` calls `installer.ensureInstalled(prefs.snapshot()
  .dictionaryPack)`; `AppRootViewModel.retryInstall()` does the same.

## 5. User data scoped per pack (`user.db` v2)

`pack_id TEXT NOT NULL` on `folders`, `saved_entries`, `recent_searches` (`activity_days` stays shared).
`UserDatabase.VERSION = 2`; `Migration(1, 2)`: `ALTER TABLE … ADD COLUMN pack_id TEXT NOT NULL DEFAULT 'bo'`
×3, drop `index_folders_name`, create unique `(pack_id, name)`, drop the `(query, direction_id)` unique index and
create unique `(pack_id, query, direction_id)`, add indices `(pack_id, saved_at)` and `(pack_id, searched_at)`;
export `2.json`; a `MigrationTestHelper` test. List queries, `trim`, `clear`, `nextPosition`,
`existsByNameIgnoreCase` and `exists` take the pack, so one pack's history never evicts the other's and a stale
`lastUsedFolderId` can never file a word into the other pack's folder; id-keyed single-row queries (`get`,
`delete`, `setFolder`, `setNote`, `observeIsSaved`) stay id-only because ids are global. `SavedRepositoryImpl` and
`RecentSearchesRepositoryImpl` inject `DictionaryDatabases`, pass the active pack id, and wrap observe flows in
`active.filterNotNull().flatMapLatest`. `SavedEntry` (domain) carries `packId` so Undo restores into the same pack.

## 6. Shell, onboarding, Profile

- **Gate** (`AppRootViewModel.uiState`): `combine(installer.state, initializer.ready, prefs, databases.active,
  switcher.state)`. While `active == null` (first install, including a Bhutan device whose default is `dz`):
  today's Loading / Installing / Ready logic, `MainActivity` replacing `AppRoot` with `InstallingScreen`. Once a pack
  is active: `Ready(pack = active, onboarded, appearance, textSize, lineSpacing, switching = switcher.state)`.
  `AppRoot` stays mounted during a switch and draws the installing content as a full-screen overlay while
  `switching != Idle` (pointer input consumed; its `BackHandler` composed after the NavHost so it wins; `Failed`
  offers "Try again" = `switcher.retry()` and "Keep current dictionary" = `switcher.dismiss()`). Deep links are not
  collected while an overlay is up (the bus keeps its replayed event).
- **After a completed switch** (`Ready.pack` changed): `AppNavHost` keeps a `rememberSaveable` last-seen pack; when
  it differs and the user is onboarded it selects the Search tab (`MainViewModel.selectTab`) and runs
  `navController.navigate(Main) { popUpTo(navController.graph.id) { inclusive = true } }`, which pops every entry so
  each nav-entry view model (Home/Saved/Profile in `Main`, Search, Entry) is recreated for the new pack. Nothing is
  keyed; `MainViewModel` (tab only) and `AppRootViewModel` hold no dictionary data. During onboarding the pack
  changes without popping: `OnboardingViewModel` observes `capabilities`, which re-emits the new pack's directions.
- **Onboarding** gets step 1 "Choose your dictionary" (`OnboardingStep.DICTIONARY = 1`, DIRECTION 2, READING 3,
  ACCOUNT 4): two `OptionCard`s (pack name + Tibetan-script gloss, description with the count from the per-pack
  stats resource, tags `onboarding_pack_bo` / `onboarding_pack_dz`), preselected from `prefs.dictionaryPack`; the
  flag strand + greeting move from the direction step to it. Continue on step 1 **awaits** `switcher.switchTo
  (selected)` before advancing; Skip awaits it before storing the other steps and `onboarded`, so `Finished` never
  races the switch and Main mounts directly on the chosen pack. If the switch fails the step stays put behind the
  overlay's Failed state; Continue tries again.
- **Profile**: a "Dictionary" row (value = active pack name) before "Default direction", `ProfileSheet.DICTIONARY`,
  `DictionaryPickerSheet` modelled on `LanguagePickerSheet`; `ProfileEvent.SetDictionary(pack)` closes the sheet
  and calls `switcher.switchTo(pack)` (fire-and-forget: the Profile view model is popped by the switch).
- **About / Licences / version line / share**: About builds its data line from the active pack's `Attribution`
  (source, author, licence); the version line and the share text take the pack name; Licences shows
  `pack.licenceAsset` under a per-pack title (`licenses/GPL-3.0.txt` / `licenses/DDC-permission.txt`).
- **Direction labels**: `languageToken("dz") = "རྫོང"` (non-Latin); `directionDescription` gains `dz-en`, `dz-dz`,
  `en-dz`. **Relation**: `Relation.ANTONYM`, mapped from `"antonym"` (today an unknown relation silently becomes
  SEE_ALSO).

## 7. Data pipeline: `build_db.py --pack bo|dz`

The Monlam setup is hard-coded in `build_db.py`, `source.py`, `model.py` (`DIRECTIONS`, `entry_id_for`),
`related.py` (lookup order, English direction), `wotd.py` (`WOTD_DIRECTION_ID`), `stats.py` (ranges, the `3, 3`
directions check) and `resources.py` (resource names). All of it becomes per pack through `dictbuild/packs.py`
(`Pack`: id, directions, source reader, table → parser map, lookup order, English direction, WOTD direction, meta,
expected ranges). `writer.py`, `schema.py`, `report.py`, `text.py`, `wylie.py` stay generic. The `bo` pack wraps
today's constants unchanged, and a rebuild of `bo.db` with the same `--built-at` is byte-identical to today's
`dictionary.db` (the regression guard).

DDC directions: `dz-en` (id_base 4,000,000, position 0, labels "Dzongkha → English" / "རྫོང་ཁ → དབྱིན་ཡིག" /
"རྫོང→EN"), `dz-dz` (5,000,000, 1), `en-dz` (6,000,000, 2, `latn`). New modules: `stardict.py` (reader),
`parsers/ddc_common.py`, `parsers/dz_en.py`, `parsers/en_dz.py`, `parsers/dz_dz.py`;
`common.split_numbered_tibetan` (senses numbered ༡ ༢ ༣). Rules that matter: Tibetan-script headwords get the final
tsheg through `normalise_tibetan_headword` (the search engine's exact/prefix matching relies on it); en-dz keys
that differ only by case merge into one entry (the unique NOCASE index requires it), the lower-case spelling being
the display headword; `<kref type="syn|ant">` become `synonym` / `antonym` links; POS abbreviations map through a
tolerant DDC table and unknown ones are reported; Wylie is generated as for Monlam; `gloss_lang = "dz"` for
Dzongkha glosses (the app only carries it through). Meta for dz: `source_name` "DDC Dzongkha Dictionaries (StarDict,
2014-02-03)", `source_author` "Dzongkha Development Commission", `source_license` "Used with permission of the
DCDD", `attribution_text` owner-approved, `wotd_direction_id` `dz-en`. Stats go to `res/values/dictionary_stats_bo.
xml` / `_dz.xml` (`dictionary_entry_count_<pack>`, `dictionary_asset_bytes_<pack>`). Assets:
`git mv dictionary.db bo.db` (no new blob), add `dz.db` (~36 MB raw, ~10 MB deflated), `licenses/DDC-permission.txt`.

## 8. Verification

1. Pipeline: pytest green; `--pack bo` reproduces today's bytes; `--pack dz` exits 0 inside its ranges; the report
   shows no `unknown_labels` beyond the documented DDC variants.
2. `./gradlew :app:testDebugUnitTest :app:lintDebug` green.
3. `./gradlew :app:connectedDebugAndroidTest`: onboarding with the pack step; `PackSwitchFlowTest`.
4. Device pass: fresh install without SIM → Tibetan preselected; with a Bhutan SIM or an AVD with `mcc=402` →
   Dzongkha preselected, launcher "Dzongkha Dictionary", Dzongkha splash; switch both ways in Profile and confirm
   Saved / Recent / word of the day / About / Licences follow the pack with nothing from the other pack; kill the app
   mid-copy of the second pack and relaunch → retry, no half-written file; release AAB grows by ≈10 MB.
