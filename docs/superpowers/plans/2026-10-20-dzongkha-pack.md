# Dzongkha Dictionary Pack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the DDC Dzongkha dictionaries as a second, separate database "pack" next to the Monlam Tibetan one, defaulted by SIM country (`mcc402` = Bhutan), chosen in onboarding, switchable in Profile, with nothing of the two packs ever mixing.

**Architecture:** A `DictionaryPack` enum names the two packs; `DictionaryDatabases` holds one Room instance per pack and the active pack; the installer copies a pack's asset on demand and activates it; `DictionarySwitcher` installs → activates → persists the choice; repositories read the active database through `flatMapLatest`; `user.db` v2 scopes folders, saved words and recent searches by `pack_id`; the shell shows an installing overlay during a switch and pops the back stack to Home when it completes. The Python pipeline gains `--pack bo|dz` with a StarDict reader and three DDC parsers.

**Tech Stack:** Kotlin 2.3, Compose + M3, Hilt 2.58, Room 2.8.5, DataStore 1.2.1, navigation-compose 2.9.8, Robolectric 4.17, Turbine, Truth, MockK; Python 3.10+/3.13, pytest 9.1.1, pyewts 0.2.0.

**Spec:** `docs/superpowers/specs/2026-10-20-dzongkha-pack-design.md` (read it first; section numbers below cite it).

## Global Constraints

- Branch `feature/v3-rewrite`. One commit per task under the owner's git identity. **No AI/Claude/co-author/session text anywhere in git history. Never push.**
- Per task, before its commit: `./gradlew :app:testDebugUnitTest :app:lintDebug` green (L1 tasks: `cd tools/dictionary && .venv/bin/python -I -m pytest -q` green). No new Gradle or Python dependencies.
- `DictionaryDatabase.VERSION` stays 1 (the schema is unchanged); `UserDatabase.VERSION` becomes 2 with a hand-written migration.
- Entry ids: Monlam `id_base` 1,000,000 / 2,000,000 / 3,000,000; DDC 4,000,000 / 5,000,000 / 6,000,000. Direction ids `bo-en`, `bo-bo`, `en-bo`, `dz-en`, `dz-dz`, `en-dz`. Pack ids `bo`, `dz`.
- Every new user-facing string exists in `values/strings.xml` **and** `values-bo/strings.xml`; Tibetan wording is a draft for the owner to review (list the keys in the ledger). `values-mcc402/strings.xml` holds only `app_name` and `app_name_tibetan`.
- Tibetan-script headwords in both packs end with one tsheg (`normalise_tibetan_headword`); the search engine depends on it.
- Nothing but the installer may call `DictionaryDatabases.get()` for a pack that is not active.
- The pack pref is written only after the pack is installed and activated.
- `activity_days` stays shared; only the chosen pack is copied at first launch.
- Rebuilding `bo.db` with `--built-at 2026-10-19T00:00:00Z --wotd-seed 20261019 --data-version 2026.10.1` must give bytes identical to today's `dictionary.db`.

## Review Focus

1. A Bhutan SIM with the English UI must still show English labels; only the app name and the default pack may change (T0.1 `PackResourcesTest.mcc402KeepsEnglishStringsAndDefaultsToDzongkha`).
2. Typing a Dzongkha word with its final tsheg ("ཆུ་") must find the headword exactly like a Tibetan one (T1.3 `test_headword_gets_final_tsheg`; T4.1 `DictionaryAssetOpenTest.exactMatchWithTshegInDzongkha`).
3. Process death or a failure mid-switch must leave the stored pack and the old database untouched (T0.6 `DictionarySwitcherImplTest.failedSwitchLeavesPrefAndActivePack`; T0.5 `DictionaryInstallerImplTest.failedCopyOfSecondPackLeavesFirstPackInstalled`).
4. A saved word, folder or recent search of the other pack must never appear, and a stale `lastUsedFolderId` must never file a word into the other pack's folder (T0.3 `FolderDaoTest.existsIsScopedToPack`, `RecentSearchDaoTest.trimKeepsOtherPacksRows`; T2.2 `SavedRepositoryImplTest.saveIgnoresFolderOfOtherPack`).
5. Two switch requests in a row, or a deep link arriving during a switch, must not navigate on the old pack's data (T0.6 `DictionarySwitcherImplTest.secondRequestWhileRunningIsRejected`; T3.1 `AppNavHostTest.deepLinkDuringSwitchIsHeldUntilReady`).

---

## Lanes

**L0 Foundation** runs first and alone (T0.1 → T0.6, in order). Then **L1 Pipeline**, **L2 Data** and **L3 UI** run in parallel, one agent each with disjoint files. **L4 Integration** runs last. File ownership:

- L0: everything it names below (model, prefs, user.db, `DictionaryDatabases`, installer, switcher, DI, strings, fakes).
- L1: `tools/dictionary/**`, `app/src/main/assets/dictionary/**`, `app/src/main/assets/licenses/DDC-permission.txt`, `app/src/main/res/values/dictionary_stats_*.xml`, `tools/dictionary/README.md`.
- L2: `data/repository/**`, `data/model/Entry.kt`, their tests and `fakes/FakeDictionaryRepository.kt` if needed.
- L3: `ui/**`, `navigation/**`, `MainActivity.kt`, their tests. L3 does not edit `res/values*/strings.xml` (L0 adds every string); a missing string is a ledger ruling, not an edit.
- L4: `app/src/androidTest/**`, `DictionaryAssetVersionTest`, `DictionaryAssetOpenTest`, `AppNavHostTest`.

Package root: `app/src/main/java/com/kharagedition/tibetandictionary/` (abbreviated below as `…/`); unit tests under `app/src/test/java/com/kharagedition/tibetandictionary/` (`test:…/`).

---

### L0 Foundation

### Task T0.1: Pack model, resources, strings

**Files:**
- Create: `…/data/model/DictionaryPack.kt`, `…/data/model/DictionaryPackRes.kt`
- Create: `app/src/main/res/values/packs.xml`, `app/src/main/res/values-mcc402/packs.xml`
- Replace: `app/src/main/res/values-mcc402/strings.xml` (the staged and working copies are both wrong; `git add` the new content)
- Create by hand (regenerated by T1.6): `app/src/main/res/values/dictionary_stats_bo.xml` (`dictionary_entry_count_bo` 167839, `dictionary_asset_bytes_bo` 86753280), `app/src/main/res/values/dictionary_stats_dz.xml` (`dictionary_entry_count_dz` 0, `dictionary_asset_bytes_dz` 0, with a comment that T1.6 fills it); delete `dictionary_stats.xml`
- Modify: `…/ui/app/InstallingScreen.kt:68` and `…/core/di/DatabaseModule.kt:52` (use `R.integer.dictionary_entry_count_bo` / `dictionary_asset_bytes_bo` for now); `test:…/data/local/DictionaryAssetVersionTest.kt:63,70`, `DictionaryAssetOpenTest.kt:54` likewise
- `git mv app/src/main/assets/dictionary/dictionary.db app/src/main/assets/dictionary/bo.db`; `DictionaryDatabase.ASSET_PATH = "dictionary/bo.db"` (T0.4 removes the constant)
- Modify: `app/src/main/res/values/strings.xml`, `values-bo/strings.xml` (new keys below)
- Test: `test:…/data/model/DictionaryPackTest.kt`, `test:…/PackResourcesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class DictionaryPack(val id: String, val dbName: String, val assetPath: String, val defaultDirectionId: String, val licenceAsset: String) {
      TIBETAN("bo", "dictionary-bo.db", "dictionary/bo.db", "bo-en", "licenses/GPL-3.0.txt"),
      DZONGKHA("dz", "dictionary-dz.db", "dictionary/dz.db", "dz-en", "licenses/DDC-permission.txt");
      companion object { fun fromId(id: String?): DictionaryPack? }   // exact, case-sensitive match on id
  }
  // DictionaryPackRes.kt (top-level extension functions, a `when` per pack, no getIdentifier):
  @StringRes fun DictionaryPack.nameRes(): Int          // pack_name_bo / pack_name_dz
  @StringRes fun DictionaryPack.glossRes(): Int         // pack_gloss_bo / pack_gloss_dz
  @StringRes fun DictionaryPack.descriptionRes(): Int   // pack_desc_bo / pack_desc_dz (one %1$s: the entry count)
  @StringRes fun DictionaryPack.licenceTitleRes(): Int  // licence_data / licence_data_dz
  @IntegerRes fun DictionaryPack.entryCountRes(): Int   // dictionary_entry_count_bo / _dz
  @IntegerRes fun DictionaryPack.assetBytesRes(): Int   // dictionary_asset_bytes_bo / _dz
  ```
- Strings (en / bo): `pack_name_bo` "Tibetan Dictionary" / "བོད་ཡིག་ཚིག་མཛོད།"; `pack_name_dz` "Dzongkha Dictionary" / "རྫོང་ཁའི་ཚིག་མཛོད།"; `pack_gloss_bo` "བོད་ཡིག་ཚིག་མཛོད།" / "Tibetan Dictionary"; `pack_gloss_dz` "རྫོང་ཁའི་ཚིག་མཛོད།" / "Dzongkha Dictionary"; `pack_desc_bo` "Monlam · Tibetan ↔ English · %1$s entries" / "སྨོན་ལམ། · བོད་ཡིག ↔ དབྱིན་ཡིག · ཚིག %1$s"; `pack_desc_dz` "DDC · Dzongkha ↔ English · %1$s entries" / "DDC · རྫོང་ཁ ↔ དབྱིན་ཡིག · ཚིག %1$s"; `dictionary` "Dictionary" / "ཚིག་མཛོད།"; `onboarding_dictionary_title` "Which dictionary do you want?" / "ཚིག་མཛོད་གང་དགོས་སམ།"; `onboarding_dictionary_body` "The app opens with this dictionary. You can switch any time in Profile." / "མཉེན་ཆས་འདི་ཚིག་མཛོད་འདིས་འགོ་འཛུགས། སྒེར་གྱི་སྒྲིག་འགོད་ནས་དུས་ནམ་ཡང་བརྗེ་ཆོག"; `switching_dictionary` "Preparing %1$s · offline" / "%1$s གྲ་སྒྲིག་བྱེད་བཞིན། · དྲ་རྒྱ་མི་དགོས།"; `keep_current_dictionary` "Keep current dictionary" / "ད་ལྟའི་ཚིག་མཛོད་ཉར།"; `direction_desc_dz_en` "Dzongkha to English" / "རྫོང་ཁ་ནས་དབྱིན་ཡིག"; `direction_desc_dz_dz` "Dzongkha definitions" / "རྫོང་ཁའི་ཚིག་འགྲེལ།"; `direction_desc_en_dz` "English to Dzongkha" / "དབྱིན་ཡིག་ནས་རྫོང་ཁ"; `licence_data_dz` "Dictionary data · Dzongkha Development Commission, used with permission" / "ཚིག་མཛོད་ཀྱི་གཞི་གྲངས། · རྫོང་ཁ་གོང་འཕེལ་ལྷན་ཚོགས། ཆོག་མཆན་ཐོབ།". Changed formats: `version_line` "%1$s %2$s · %3$s entries · offline" / "%1$s %2$s · ཚིག %3$s · དྲ་རྒྱ་མི་དགོས།"; `version_line_no_count` "%1$s %2$s · offline" / "%1$s %2$s · དྲ་རྒྱ་མི་དགོས།"; `share_app_text` "Look up words offline with %1$s: %2$s" / "%1$s ཐོག་དྲ་རྒྱ་མེད་པར་ཚིག་འཚོལ། %2$s"; `about_data` "Dictionary data: %1$s by %2$s (%3$s)" / "ཚིག་མཛོད་ཀྱི་གཞི་གྲངས། %2$s ཀྱིས་བསྒྲིགས་པའི་ %1$s (%3$s)". Callers of the three changed formats (`VersionLine.kt`, `ProfileScreen.kt:146`, `AboutScreen.kt:129`) are updated in this task to pass `stringResource(R.string.pack_name_bo)` and, for `about_data`, the fixed Monlam values, so the build stays green until T3.4 makes them pack-aware. `values-mcc402/strings.xml`: `app_name` "Dzongkha Dictionary", `app_name_tibetan` "རྫོང་ཁའི་ཚིག་མཛོད།". `packs.xml`: `<string name="default_dictionary_pack" translatable="false">bo</string>`, mcc402 → `dz`.

- [ ] **Step 1: Write the failing tests**
  - `DictionaryPackTest`: `fromIdMatchesExactIds` (`"bo"` → TIBETAN, `"dz"` → DZONGKHA, `"BO"`, `""`, `null` → null); `fileNamesAreDistinct` (dbName, assetPath, licenceAsset, defaultDirectionId all distinct across entries); `resourceMappingsAreDistinct` (each `*Res()` gives different ids per pack).
  - `PackResourcesTest` (`@RunWith(AndroidJUnit4::class)`): `defaultQualifiersDefaultToTibetan` (`R.string.default_dictionary_pack` == "bo", `R.string.app_name` == "Tibetan Dictionary"); `mcc402KeepsEnglishStringsAndDefaultsToDzongkha` with `@Config(qualifiers = "mcc402")`: `default_dictionary_pack` == "dz", `app_name` == "Dzongkha Dictionary", `app_name_tibetan` == "རྫོང་ཁའི་ཚིག་མཛོད།", `R.string.tab_search` == "Search"; `boLocaleStillTibetan` with `qualifiers = "bo"`: `tab_search` == "འཚོལ།", `default_dictionary_pack` == "bo".
- [ ] **Step 2: Run them**: `./gradlew :app:testDebugUnitTest --tests '*DictionaryPackTest' --tests '*PackResourcesTest'` → FAIL (unresolved references).
- [ ] **Step 3: Implement** the enum, the `*Res()` functions, the resource files, the string keys (en + bo), the `git mv`, the four consumer edits, and the three changed-format call sites.
- [ ] **Step 4: Run** the two tests → PASS; `./gradlew :app:testDebugUnitTest :app:lintDebug` → green (the asset tests still open `dictionary/bo.db` through the renamed constant).
- [ ] **Step 5: Commit** `git add -A app/src/main/res app/src/main/assets/dictionary app/src/main/java app/src/test && git commit -m "Add the dictionary pack model, its resources and strings"`.

### Task T0.2: Preferences: resolved pack, per-pack direction and folder

**Files:**
- Modify: `…/data/prefs/UserPrefs.kt`, `UserPreferencesRepository.kt`, `UserPreferencesRepositoryImpl.kt`, `…/core/di/DataStoreModule.kt`
- Modify: `test:…/fakes/FakeUserPreferencesRepository.kt`, `test:…/data/prefs/UserPreferencesRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `DictionaryPack` (T0.1).
- Produces:
  ```kotlin
  // UserPrefs
  val dictionaryPack: DictionaryPack = DictionaryPack.TIBETAN   // resolved: stored, else the resource default
  val dictionaryPackChosen: Boolean = false                     // true once setDictionaryPack ran
  // UserPreferencesRepository
  suspend fun setDictionaryPack(pack: DictionaryPack)
  // DataStoreModule
  @Qualifier annotation class DefaultDictionaryPack
  @Provides @DefaultDictionaryPack fun provideDefaultDictionaryPack(@ApplicationContext context: Context): DictionaryPack
      // = DictionaryPack.fromId(context.getString(R.string.default_dictionary_pack)) ?: DictionaryPack.TIBETAN
  // UserPreferencesRepositoryImpl(store: DataStore<Preferences>, @DefaultDictionaryPack defaultPack: DictionaryPack)
  ```
  Keys: `dictionary_pack` (string); `default_direction_id_<pack.id>` (string); `last_used_folder_id_<pack.id>` (long). Reading `defaultDirectionId`: per-pack key, else (bo only) the old `default_direction_id`, else `pack.defaultDirectionId`. `lastUsedFolderId`: per-pack key, else (bo only) the old `last_used_folder_id`, else null. `setDefaultDirectionId` / `setLastUsedFolderId` write the key of the pack stored in the same edit (`it[DICTIONARY_PACK]` resolved as above).
- Fake: holds `directionByPack: MutableMap<DictionaryPack, String>` and `folderByPack: MutableMap<DictionaryPack, Long?>`; `setDictionaryPack` sets `dictionaryPack`, `dictionaryPackChosen = true` and swaps `defaultDirectionId` / `lastUsedFolderId` in the exposed `UserPrefs` to that pack's stored values (default `pack.defaultDirectionId` / null); the two setters store under the current pack. Constructor keeps `initial: UserPrefs = UserPrefs()`.

- [ ] **Step 1: Write the failing tests** in `UserPreferencesRepositoryImplTest` (existing DataStore test setup; construct the Impl with `defaultPack = DictionaryPack.TIBETAN` unless stated): `unsetPackResolvesToInjectedDefault` (Impl with `DZONGKHA` → `prefs.first().dictionaryPack == DZONGKHA`, `dictionaryPackChosen == false`); `setDictionaryPackPersistsAndMarksChosen`; `defaultDirectionIsPerPack` (set `en-bo` under bo, switch to dz → `dz-en`; set `en-dz`; switch back → `en-bo`); `lastUsedFolderIsPerPack` (same shape, 7L / null / 9L); `legacySingleKeysReadAsTibetanValues` (write `default_direction_id` = "bo-bo" and `last_used_folder_id` = 3L directly into the store → read under bo as those values, under dz as `dz-en` / null); `unknownStoredPackFallsBackToDefault` (store `"xx"`). Add `FakeUserPreferencesRepositoryTest.switchingPackSwapsDirectionAndFolder`.
- [ ] **Step 2: Run** `--tests '*UserPreferencesRepositoryImplTest' --tests '*FakeUserPreferencesRepositoryTest'` → FAIL.
- [ ] **Step 3: Implement** as in Interfaces; update `UserPrefs` KDoc; every existing `UserPrefs(...)` construction in tests still compiles (defaults).
- [ ] **Step 4: Run** → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git commit -am "Resolve the dictionary pack in prefs and keep direction and folder per pack"`.

### Task T0.3: user.db v2: pack_id on folders, saved entries, recent searches

**Files:**
- Modify: `…/data/local/entity/FolderEntity.kt`, `SavedEntryEntity.kt`, `RecentSearchEntity.kt`, `…/data/local/UserDatabase.kt` (VERSION 2), `…/data/local/dao/FolderDao.kt`, `SavedEntryDao.kt`, `RecentSearchDao.kt`, `…/core/di/DatabaseModule.kt` (`.addMigrations(UserDatabaseMigrations.MIGRATION_1_2)`), `…/data/model/SavedEntry.kt` (+ `packId: String`), `…/data/repository/UserMappers.kt`
- Create: `…/data/local/UserDatabaseMigrations.kt`; `app/schemas/com.kharagedition.tibetandictionary.data.local.UserDatabase/2.json` (exported by the build)
- Modify (compile only, minimal): `…/data/repository/SavedRepositoryImpl.kt`, `RecentSearchesRepositoryImpl.kt` pass `DictionaryPack.TIBETAN.id` for now (L2 T2.2 makes them pack-aware)
- Test: `test:…/data/local/UserDatabaseMigrationTest.kt` (new), `FolderDaoTest.kt`, `SavedEntryDaoTest.kt`, `RecentSearchDaoTest.kt`, `test:…/data/local/LocalDbFixtures.kt`, `SampleData.kt` (saved entries get `packId = "bo"`)

**Interfaces:**
- Produces (entity columns `pack_id TEXT NOT NULL`; indices: folders unique `(pack_id, name)`; saved_entries `(pack_id, saved_at)`; recent_searches unique `(pack_id, query, direction_id)` and `(pack_id, searched_at)`):
  ```kotlin
  object UserDatabaseMigrations { val MIGRATION_1_2: Migration }
  // FolderDao
  fun observeAll(pack: String): Flow<List<FolderEntity>>
  fun observeCounts(pack: String): Flow<List<FolderCount>>      // JOIN folders f ON f.id = folder_id WHERE f.pack_id = :pack
  suspend fun existsByNameIgnoreCase(pack: String, name: String, exceptId: Long?): Boolean
  suspend fun exists(id: Long, pack: String): Boolean
  suspend fun nextPosition(pack: String): Int
  // insert / rename / delete unchanged
  // SavedEntryDao
  fun observeAll(pack: String); fun observeInFolder(pack: String, folderId: Long); fun observeUnfiled(pack: String)
  fun search(pack: String, query: String); fun observeIds(pack: String); fun observeCount(pack: String)
  // observeIsSaved(entryId), get(entryId), insert, delete(entryId), setFolder, setNote unchanged (ids are global)
  // RecentSearchDao
  fun observeRecent(pack: String, limit: Int); suspend fun clear(pack: String)
  open suspend fun record(entity: RecentSearchEntity, keep: Int = MAX_ROWS)   // find/trim scoped by entity.packId
  // UserMappers
  fun EntrySummary.toSavedEntity(packId: String, now: Long, folderId: Long?, note: String?): SavedEntryEntity
  fun SavedEntry.toEntity(folderId: Long?): SavedEntryEntity   // carries entry.packId
  ```
  Migration SQL: `ALTER TABLE folders ADD COLUMN pack_id TEXT NOT NULL DEFAULT 'bo'` (same for `saved_entries`, `recent_searches`); `DROP INDEX index_folders_name`; `CREATE UNIQUE INDEX index_folders_pack_id_name ON folders (pack_id, name)`; `DROP INDEX index_recent_searches_query_direction_id`; `CREATE UNIQUE INDEX index_recent_searches_pack_id_query_direction_id ON recent_searches (pack_id, `query`, direction_id)`; `CREATE INDEX index_saved_entries_pack_id_saved_at ON saved_entries (pack_id, saved_at)`; `CREATE INDEX index_recent_searches_pack_id_searched_at ON recent_searches (pack_id, searched_at)`. Index names must match what Room generates for the annotations (check `2.json`).

- [ ] **Step 1: Write the failing tests**
  - `UserDatabaseMigrationTest` (Robolectric, `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UserDatabase::class.java)`): `migrate1To2KeepsRowsAndTagsThemTibetan` (create v1, insert one folder "Verbs", one saved entry 1_000_001 in it, one recent search; `runMigrationsAndValidate(…, 2, true, MIGRATION_1_2)`; every row has `pack_id = 'bo'`); `migrate1To2AllowsSameFolderNameInOtherPack` (after migration insert folder "Verbs" with pack `dz` → no constraint error).
  - `FolderDaoTest`: `observeAllIsScopedToPack`, `existsIsScopedToPack` (folder of dz → `exists(id, "bo")` false), `nameUniquePerPackOnly` (same name in both packs inserts; same pack throws), `nextPositionIsPerPack`.
  - `SavedEntryDaoTest`: `listsCountsAndIdsAreScopedToPack`, `searchIsScopedToPack`.
  - `RecentSearchDaoTest`: `observeRecentIsScopedToPack`, `trimKeepsOtherPacksRows` (51 bo rows + 1 dz row → dz row survives), `clearIsScopedToPack`, `sameQueryAndDirectionInBothPacksAreTwoRows`.
- [ ] **Step 2: Run** `--tests '*UserDatabaseMigrationTest' --tests '*FolderDaoTest' --tests '*SavedEntryDaoTest' --tests '*RecentSearchDaoTest'` → FAIL.
- [ ] **Step 3: Implement**; run `./gradlew :app:compileDebugKotlin` to export `2.json`; write the migration so `runMigrationsAndValidate` passes (index names from `2.json`).
- [ ] **Step 4: Run** → PASS; full unit tests + lint green (fixtures and `SampleData` updated).
- [ ] **Step 5: Commit** `git add -A app/schemas app/src && git commit -m "Scope folders, saved words and recent searches by dictionary pack (user.db v2)"`.

### Task T0.4: DictionaryDatabases and DI

**Files:**
- Create: `…/data/local/DictionaryDatabases.kt`
- Modify: `…/core/di/DatabaseModule.kt` (remove the `DictionaryDatabase` and the five dictionary-DAO `@Provides`; add the `DictionaryDatabases` provider); `…/data/local/DictionaryDatabase.kt` (remove `DB_NAME`, `ASSET_PATH`)
- Modify (compile only): the three injectors `…/data/repository/DictionaryRepositoryImpl.kt`, `WordOfTheDayRepositoryImpl.kt`, `…/data/install/DictionaryInstallerImpl.kt` take `databases: DictionaryDatabases` and use `databases.activeDb.xDao()` / `databases.get(pack)`; their tests construct `DictionaryDatabases { db }.apply { activate(TIBETAN) }` (L2 makes the repositories switch-aware; T0.5 the installer)
- Test: `test:…/data/local/DictionaryDatabasesTest.kt`, `test:…/data/local/LocalDbFixtures.kt` (+ `fun databasesOf(vararg packs: Pair<DictionaryPack, DictionaryDatabase>): DictionaryDatabases`)

**Interfaces:**
- Produces:
  ```kotlin
  /** One Room instance per pack, opened lazily; see spec §4 for the rules. */
  class DictionaryDatabases(private val open: (DictionaryPack) -> DictionaryDatabase) {
      val active: StateFlow<DictionaryPack?>                 // null until activate()
      val activeDb: DictionaryDatabase                       // get() = get(checkNotNull(active.value) { "No active dictionary" })
      fun get(pack: DictionaryPack): DictionaryDatabase       // @Synchronized lazy per pack, kept open
      fun activate(pack: DictionaryPack)
      fun isOpen(pack: DictionaryPack): Boolean               // for tests and the installer's short-circuit
  }
  // DatabaseModule: @Provides @Singleton fun provideDictionaryDatabases(@ApplicationContext context: Context) =
  //   DictionaryDatabases { pack -> Room.databaseBuilder(context, DictionaryDatabase::class.java, pack.dbName)
  //       .createFromAsset(pack.assetPath).fallbackToDestructiveMigration(dropAllTables = true)
  //       .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build() }
  ```

- [ ] **Step 1: Write the failing tests** `DictionaryDatabasesTest`: `activeDbThrowsBeforeActivation` (IllegalStateException), `getOpensEachPackOnceAndKeepsBoth` (counting `open` lambda: two `get(TIBETAN)` → one open; `get(DZONGKHA)` → second instance, both `isOpen`), `activateSwitchesActiveDb` (Turbine on `active`: null → TIBETAN → DZONGKHA; `activeDb` is the matching instance), `providerBuildsDistinctFiles` (Robolectric: `DatabaseModule.provideDictionaryDatabases(context)`, open both packs without assets → each `openHelper.databaseName` equals `pack.dbName`; close them).
- [ ] **Step 2: Run** `--tests '*DictionaryDatabasesTest'` → FAIL.
- [ ] **Step 3: Implement**; wire the three injectors; `DictionaryAssetOpenTest` switches from `provideDictionaryDatabase` to `provideDictionaryDatabases(context).get(TIBETAN)`.
- [ ] **Step 4: Run** → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git commit -am "Hold one dictionary database per pack behind DictionaryDatabases"`.

### Task T0.5: Installer per pack

**Files:**
- Modify: `…/data/install/DictionaryInstaller.kt`, `DictionaryInstallerImpl.kt`, `AssetSource.kt`, `…/core/di/DatabaseModule.kt` (`provideAssetSizeProvider` reads `context.resources.getInteger(pack.assetBytesRes())`), `…/core/init/AppInitializer.kt` (`installer.ensureInstalled(prefs.snapshot().dictionaryPack)`), `…/ui/app/AppRootViewModel.kt` (compile only: `retryInstall` passes `prefsRepository.snapshot().dictionaryPack`; `when` arms use the new shapes; T3.1 reworks it)
- Modify: `test:…/fakes/FakeInstaller.kt`, `test:…/data/install/FakeAssetSource.kt`, `DictionaryInstallerImplTest.kt`, `test:…/ui/app/AppRootViewModelTest.kt` (compile only)

**Interfaces:**
- Produces:
  ```kotlin
  interface DictionaryInstaller { val state: StateFlow<InstallState>; suspend fun ensureInstalled(pack: DictionaryPack) }
  sealed interface InstallState {
      data object Checking : InstallState
      data class Copying(val pack: DictionaryPack, val progress: Float) : InstallState
      data class Ready(val pack: DictionaryPack, val totalEntries: Int) : InstallState
      data class Failed(val pack: DictionaryPack, val cause: Throwable) : InstallState
  }
  interface AssetSource { fun open(pack: DictionaryPack): InputStream; fun length(pack: DictionaryPack): Long? }
  fun interface AssetSizeProvider { fun expectedBytes(pack: DictionaryPack): Long? }
  class DictionaryInstallerImpl @Inject constructor(context, assetSource, recordedAssetSize, databases: DictionaryDatabases, io, errors)
  ```
  Behaviour: `ensureInstalled(pack)` returns at once when `state.value is Ready && it.pack == pack` (and `databases.active.value == pack`); otherwise, under the mutex: on the first call of the process delete `context.getDatabasePath("dictionary.db")` with its `-journal`/`-wal`/`-shm` and every `getDatabasePath(p.dbName + ".tmp")` for both packs; then today's copy/verify flow on `pack.dbName` / `pack.assetPath`; `openAndCount(pack)` uses `databases.get(pack)` and ends with `databases.activate(pack)` before `_state.value = Ready(pack, count)`. A failure of pack B never touches pack A's file. `FakeInstaller(outcome: (DictionaryPack) -> InstallState = { Ready(it, SampleData.capabilities.totalEntries) })` records `ensureInstalledCalls: List<DictionaryPack>` and, when a `databases` is given, activates the pack on Ready. `FakeAssetSource(files: Map<DictionaryPack, File>, …)`.

- [ ] **Step 1: Write the failing tests** (adapt every existing `DictionaryInstallerImplTest` case to `ensureInstalled(TIBETAN)` and the pack-carrying states; add): `installsTheRequestedPackOnly` (dz → `dictionary-dz.db` exists, `dictionary-bo.db` does not, `databases.active.value == DZONGKHA`, state `Ready(DZONGKHA, 3)`); `secondPackInstallsNextToTheFirst` (bo then dz: both files exist, active == DZONGKHA, bo untouched: same mtime/length); `failedCopyOfSecondPackLeavesFirstPackInstalled` (dz asset fails after 100 KB → `Failed(DZONGKHA, …)`, active stays TIBETAN, bo file intact, no `dictionary-dz.db`, no `.tmp`); `alreadyActivePackReturnsWithoutReopening` (second `ensureInstalled(TIBETAN)` does not call `databases.get` again and keeps the file); `firstRunSweepsLegacyAndTempFiles` (pre-create `dictionary.db`, `dictionary.db-journal`, `dictionary-dz.db.tmp` → all gone after the first call). `AppInitializer` test (`AppRootViewModelTest` or a new `AppInitializerTest`): `startInstallsTheStoredPack` (prefs dz → `ensureInstalledCalls == [DZONGKHA]`).
- [ ] **Step 2: Run** `--tests '*DictionaryInstallerImplTest' --tests '*AppRootViewModelTest'` → FAIL.
- [ ] **Step 3: Implement**.
- [ ] **Step 4: Run** → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git commit -am "Install one dictionary pack at a time and activate it when verified"`.

### Task T0.6: DictionarySwitcher

**Files:**
- Create: `…/data/install/DictionarySwitcher.kt` (interface, `SwitchState`, `DictionarySwitcherImpl`)
- Modify: `…/core/di/RepositoryModule.kt` (`@Binds @Singleton DictionarySwitcherImpl → DictionarySwitcher`)
- Create: `test:…/fakes/FakeDictionarySwitcher.kt`, `test:…/data/install/DictionarySwitcherImplTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface SwitchState {
      data object Idle : SwitchState
      data class InProgress(val pack: DictionaryPack, val progress: Float?) : SwitchState
      data class Failed(val pack: DictionaryPack, val cause: Throwable) : SwitchState
  }
  interface DictionarySwitcher {
      val state: StateFlow<SwitchState>
      /** Installs, activates, then persists [pack]; completes when the switch ended (success or Failed). */
      fun switchTo(pack: DictionaryPack): Job
      fun retry()      // the Failed pack; no-op otherwise
      fun dismiss()    // Failed → Idle; no-op otherwise
  }
  @Singleton class DictionarySwitcherImpl @Inject constructor(installer: DictionaryInstaller, databases: DictionaryDatabases,
      prefs: UserPreferencesRepository, errors: ErrorReporter, @ApplicationScope scope: CoroutineScope) : DictionarySwitcher
  ```
  Behaviour: `switchTo` launches on `scope`. If `databases.active.value == pack`: `prefs.setDictionaryPack(pack)` and finish (state stays Idle). If a switch is `InProgress`: return its job when it is for the same pack, else a completed job (rejected, state unchanged). Else state `InProgress(pack, null)`; while running mirror `installer.state` `Copying(pack, p)` into `InProgress(pack, p)`; `installer.ensureInstalled(pack)`; on `installer.state` `Ready(pack)`: `prefs.setDictionaryPack(pack)`, state Idle; on `Failed(pack, cause)` (or a throw, recorded): state `Failed(pack, cause)`, pref untouched. `retry()` = `switchTo` of the failed pack. `FakeDictionarySwitcher`: `state` is a `MutableStateFlow`, `switchTo` records `requests`, by default completes at once and sets `activeOverride`/calls a given `onSwitch: (DictionaryPack) -> Unit` (tests wire it to `FakeUserPreferencesRepository.setDictionaryPack`).

- [ ] **Step 1: Write the failing tests** `DictionarySwitcherImplTest` (real `DictionaryDatabases { inMemoryDictionaryDb() }`, `FakeInstaller` with `databases`, `FakeUserPreferencesRepository`, `TestScope`): `alreadyActivePackOnlyPersistsPref`; `switchInstallsActivatesThenPersists` (order: `ensureInstalledCalls == [DZONGKHA]`, active DZONGKHA, then pref dz and `dictionaryPackChosen`; state Idle → InProgress → Idle); `progressMirrorsInstaller` (installer emitting `Copying(DZONGKHA, 0.5f)` → `InProgress(DZONGKHA, 0.5f)`); `failedSwitchLeavesPrefAndActivePack` (installer outcome Failed → state `Failed`, pref still bo, active still TIBETAN); `retryRunsTheFailedPackAgain`; `dismissReturnsToIdle`; `secondRequestWhileRunningIsRejected` (gate the installer; `switchTo(TIBETAN)` during a dz switch returns a completed job and `ensureInstalledCalls` stays `[DZONGKHA]`); `sameRequestWhileRunningReturnsSameJob`.
- [ ] **Step 2: Run** `--tests '*DictionarySwitcherImplTest'` → FAIL.
- [ ] **Step 3: Implement** + bind + fake.
- [ ] **Step 4: Run** → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git add -A app/src && git commit -m "Add DictionarySwitcher: install, activate, then persist the chosen pack"`.

---

### L1 Pipeline (`tools/dictionary`; commands run from there with `.venv/bin/python -I`)

### Task T1.1: Packs: make the Monlam setup one pack

**Files:**
- Create: `dictbuild/packs.py`
- Modify: `dictbuild/model.py` (`EntryDraft.entry_id` becomes a field; remove `DIRECTIONS`, `DIRECTION_BY_ID`, `entry_id_for` globals; add `RELATION_ANTONYM = "antonym"`, `GLOSS_LANG_DZ = "dz"`), `dictbuild/grouping.py`, `dictbuild/related.py`, `dictbuild/wotd.py`, `dictbuild/stats.py`, `dictbuild/source.py` (keep `read_rows`; move `DIRECTION_BY_TABLE` into the Monlam pack), `build_db.py` (`--pack` option required; `build(pack, …)`; meta from `pack.meta`)
- Modify tests: `tests/test_stats.py`, `tests/test_build_smoke.py`, `tests/test_related.py`, `tests/test_wotd.py`, `tests/test_grouping.py` (use `MONLAM`)
- Create: `tests/test_packs.py`

**Interfaces:**
- Produces:
  ```python
  @dataclass(frozen=True, slots=True, kw_only=True)
  class Expected:  # ranges of a full build
      directions: int; entries: dict[str, tuple[int, int]]; senses: dict[str, tuple[int, int]]
      examples: dict[str, tuple[int, int]]; see_also: tuple[int, int]; see_also_resolved_percent: tuple[float, float]
      synonyms: tuple[int, int]; antonyms: tuple[int, int]; wylie: int; truncated: tuple[int, int]; wotd: tuple[int, int]
  @dataclass(frozen=True, slots=True, kw_only=True)
  class Pack:
      id: str; directions: tuple[DirectionRow, ...]; tables: tuple[str, ...]; direction_by_table: dict[str, str]
      tibetan_lookup_order: tuple[str, ...]; english_direction: str; wotd_direction_id: str
      meta: dict[str, str]  # source_name, source_author, source_url, source_license, attribution_text
      expected: Expected
      read_source: Callable[[Path, int | None], dict[str, list[SourceRow]]]
      parse: Callable[[str, SourceRow, WylieConverter, HeadwordIndex], ParsedRow]  # dispatch on table
      def direction(self, direction_id: str) -> DirectionRow
      def entry_id_for(self, direction_id: str, source_id: int) -> int
  MONLAM: Pack; PACKS: dict[str, Pack]   # DDC is added by T1.5
  # changed signatures
  group_rows(pack: Pack, direction_id: str, rows: Iterable[ParsedRow]) -> list[EntryDraft]
  HeadwordIndex.from_source_rows(pack: Pack, rows_by_table) -> HeadwordIndex   # stores lookup order + english direction
  resolve_related(entries, index, converter) -> RelatedStats                   # _lookup uses index.english_direction
  is_eligible(entry, converter=None, index=None, *, wotd_direction_id: str) -> bool
  assign_ranks(entries, seed, converter=None, index=None, *, wotd_direction_id: str) -> dict[int, int]
  direction_rows(pack: Pack, rows: ContentRows) -> list[DirectionRow]
  check_ranges(pack: Pack, directions, rows, *, wylie_available: bool) -> list[RangeCheck]  # + "related antonym" check
  build(pack: Pack, rows_by_table, schema, converter, wotd_seed, data_version, built_at, report)
  ```

- [ ] **Step 1: Write the failing tests** `tests/test_packs.py`: `test_monlam_pack_matches_previous_constants` (ids, id bases 1M/2M/3M, positions, tables, lookup order `("bo-en", "bo-bo")`, english `en-bo`, wotd `bo-en`, meta values equal to the strings now in `build_db.py`); `test_entry_id_for_adds_id_base`; `test_packs_have_disjoint_direction_ids_and_id_bases` (over `PACKS.values()`; with one pack it passes trivially and guards T1.5); `test_unknown_direction_raises_key_error`. Update the existing tests to the new signatures.
- [ ] **Step 2: Run** `pytest -q` → FAIL (imports).
- [ ] **Step 3: Implement** the refactor; `parse_args` gains `--pack` (`choices=sorted(PACKS)`, required); `main` picks `PACKS[args.pack]`; `read_source` becomes `pack.read_source(args.source, args.limit)`.
- [ ] **Step 4: Run** `pytest -q` → PASS. Regression guard: `build_db.py --pack bo --source <MLDic.ml> --schema-dir ../../app/schemas/com.kharagedition.tibetandictionary.data.local.DictionaryDatabase --out /tmp/bo-check.db --data-version 2026.10.1 --wotd-seed 20261019 --built-at 2026-10-19T00:00:00Z --require-wylie` → exit 0 and `cmp /tmp/bo-check.db ../../app/src/main/assets/dictionary/bo.db` prints nothing (if the owner's `MLDic.ml` path is unknown, ask through the ledger; do not skip this check).
- [ ] **Step 5: Commit** `git add -A tools/dictionary && git commit -m "Describe the Monlam build as a dictionary pack"`.

### Task T1.2: StarDict reader and Tibetan-numbered senses

**Files:**
- Create: `dictbuild/stardict.py`, `tests/test_stardict.py`
- Modify: `dictbuild/parsers/common.py` (+ `split_numbered_tibetan`), `tests/test_common.py`

**Interfaces:**
- Produces:
  ```python
  class StarDictError(ValueError): ...
  def read_stardict(prefix: Path, *, limit: int | None = None) -> list[SourceRow]
      # prefix = path without extension (…/stardict-dz-en/dz-en); checks .ifo wordcount == entries read and
      # idxfilesize == .idx size and sametypesequence == "x"; SourceRow(source_id = 1-based ordinal, word, definition)
  def split_numbered_tibetan(text: str) -> list[Segment] | None
      # markers ༡ ༢ ༣ … (U+0F21–U+0F29 sequences, incl. ༡༠) as a run starting at ༡, each followed by a space or Tibetan
      # text; a Tibetan digit inside a word or a lone ༢ is text; None when there is no ༡ marker
  ```

- [ ] **Step 1: Write the failing tests** `test_stardict.py`: `test_reads_entries_in_idx_order` (the test writes a 3-entry `.ifo/.idx/.dict` fixture into `tmp_path`); `test_wordcount_mismatch_raises`; `test_limit_reads_first_n`. `test_common.py`: `test_split_numbered_tibetan_sequence` ("༡ རིལ་རི། ༢ སྒོར་སྒོ། ༣ ཞིབ་བཤལ།" → three segments 1..3); `test_split_numbered_tibetan_requires_one_first` ("༢ x" → None); `test_split_numbered_tibetan_keeps_lead_text` ("noun ༡ ཨའི། ༢ ཨ་ཎེམོ།" → `Segment(None, "noun")` first); `test_split_numbered_tibetan_ten` (…༩ … ༡༠ …).
- [ ] **Step 2: Run** → FAIL.  **Step 3: Implement.**  **Step 4: Run** `pytest -q` → PASS.
- [ ] **Step 5: Commit** `git add -A tools/dictionary && git commit -m "Read StarDict sources and split senses numbered with Tibetan digits"`.

### Task T1.3: dz-en parser

**Files:**
- Create: `dictbuild/parsers/ddc_common.py`, `dictbuild/parsers/dz_en.py`, `tests/test_dz_en.py`

**Interfaces:**
- Produces:
  ```python
  # ddc_common.py
  DDC_POS_CODES: dict[str, str]   # "noun"→"n", "adj."→"adj", "verb"→"v", "adv."→"adv", "pron."→"pron", "prep."→"prep",
                                  # "excl."→"interj", "conj."→"conj", "pron:"→"pron", "conj:"→"conj", "n:"→"n", "v:"→"v",
                                  # "n."→"n", "v."→"v", "adv:"→"adv", "modal v."→"aux", "combining form"→"comb",
                                  # "combining form n."→"comb", "PHR V."→"v", "IDM"→None(phrase), "prefix"→"prefix", "suffix"→"suffix"
  def pos_code(text: str) -> tuple[str | None, bool]      # (code, known): unknown non-empty → (None, False)
  def strip_tags(markup: str) -> str                        # removes <…> tags, html.unescape, collapses whitespace
  def split_k_blocks(body: str) -> list[tuple[str, str]]    # [(key text, rest)] per "<k>…</k>" block, in order
  # dz_en.py
  def parse_dz_en_row(row: SourceRow, converter: WylieConverter, index: HeadwordIndex) -> ParsedRow
  ```
  Rules (spec §7): headword = `normalise_tibetan_headword(key)[0]` (trailing space and tsheg handled; homonym digits as marker) and `group_key` = that headword; `wylie = converter.to_wylie(headword)`; `<def>` body per `<k>` block: `<pos>` → `part_of_speech` via `pos_code` (unknown → `unknown_labels`); a leading `( fut., prs., pst., imp.)` list → `notes` entry "forms: fut., prs., pst., imp."; `hon. X` / `opp. X` / `short X` → notes "hon. X", "opp. X", "short X"; `<kref type="syn">X</kref>` → `RelatedCandidate(X-normalised, RELATION_SYNONYM)`, `type="ant"` → `RELATION_ANTONYM`; `syn.` marker text dropped; `<b>N</b>` numbers → senses (ASCII numbers starting at 1; otherwise one sense); bracketed Dzongkha glosses stay in the gloss; gloss `tidy()`ed, `gloss_lang = GLOSS_LANG_EN`; each further `<k>` block continues the sense numbering; empty gloss → sense dropped with warning `sense_without_gloss`.

- [ ] **Step 1: Write the failing tests** (`parse(source_id, word, definition)` helper; rows copied from the real file): `test_headword_gets_final_tsheg` ("ཀ" → headword "ཀ་"); `test_pos_and_single_gloss` (row 'ཀ་ཀུ་རུ': pos "n", gloss "pumpkin, squash"); `test_synonym_krefs_become_links` ('ཀ་ཀུ': three `synonym` candidates "ཀ་སྨད་", "ཀ་མད་", "ཀ་ཁ་", gloss "alphabet"); `test_numbered_senses_and_bracketed_dzongkha` ('ཀ་ཀྲ་ཀོ་ཀྲ': senses 1 "curl, winding, cock-eyed, crooked (གཡོ་གཡོཝ།)" and 2 "devious (གཡོ་སྒྱུ་ཅན།)", pos adj on both); `test_verb_forms_and_honorific_become_notes` ('འགྱོ': pos "v", notes contain "forms: fut., prs., pst., imp." and "hon. འབྱོན།", gloss starts "go, proceed"); `test_opposite_becomes_antonym_link_and_note` ('ཆུ': sense 1 "fluid, water, liquid (གཤེར་རིགས།)", note "hon. ཆབ།", antonym link "མེ་"); `test_apostrophe_entity_unescaped` ('ཕ་མིང': gloss "father's name"); `test_unknown_pos_reported`; `test_wylie_from_converter` (pyewts fixture, skip without).
- [ ] **Step 2: Run** → FAIL.  **Step 3: Implement.**  **Step 4: Run** `pytest -q` → PASS.
- [ ] **Step 5: Commit** `git add -A tools/dictionary && git commit -m "Parse the DDC Dzongkha-English dictionary"`.

### Task T1.4: en-dz parser

**Files:**
- Create: `dictbuild/parsers/en_dz.py`, `tests/test_en_dz.py`

**Interfaces:**
- Produces: `def parse_en_dz_row(row: SourceRow) -> ParsedRow` and `ddc_common.sort_case_duplicates(rows: list[SourceRow]) -> list[SourceRow]`. Rules: the key is trimmed and kept as written as `headword`; `normalise_english_headword` gives the homonym digit and `group_key` (lower-cased), so `A`/`a` and `Cancer`/`cancer` merge in `group_rows`, which keeps the first row's headword. `sort_case_duplicates` is a stable sort that moves, among rows sharing a lower-cased key, the all-lower-case row first; T1.5's `read_source` applies it to en-dz, so the lower-case spelling is the display headword. Each `<k>` block: `<pos>` → `pos_code` (unknown → `unknown_labels`; `IDM`/`PHR V.` known); body split with `split_numbered_tibetan` → senses, else one sense; `gloss_lang = GLOSS_LANG_DZ`; empty key → `group_key = ""` (grouping skips it); keys like "(the court)" kept as written (trimmed).

- [ ] **Step 1: Write the failing tests**: `test_single_block` ('locust': pos n, one sense "ཅིའུ་དབ།"); `test_tibetan_numbered_senses` ('mother': senses "ཨའི། ཨ་མ།" and "ཨ་ཎེམོ་སློབ་དཔོན་མོའིི་གོ་གནས།"); `test_pos_blocks_continue_senses` ('round': blocks noun(6)/verb(2)/adv(3)/prep(4)/adj(…) → sense numbers continue, pos per sense); `test_empty_key_has_empty_group_key`; `test_messy_pos_variants` ('daughter-in-law' "n:" → n; 'bring about' "PHR V." → v; a row with `<pos></pos>` → None and not reported); `test_case_duplicates_sort_lower_first` (`sort_case_duplicates` on rows `A`, `a`, `b` → `a`, `A`, `b`).
- [ ] **Step 2: Run** → FAIL.  **Step 3: Implement.**  **Step 4: Run** `pytest -q` → PASS.
- [ ] **Step 5: Commit** `git add -A tools/dictionary && git commit -m "Parse the DDC English-Dzongkha dictionary"`.

### Task T1.5: dz-dz parser, the DDC pack, related/WOTD/stats per pack

**Files:**
- Create: `dictbuild/parsers/dz_dz.py`, `tests/test_dz_dz.py`
- Modify: `dictbuild/packs.py` (+ `DDC`, `PACKS["dz"]`), `tests/test_packs.py`, `tests/test_stats.py`, `tests/test_wotd.py`, `tests/test_related.py` (parametrise over packs where a rule is pack-specific)

**Interfaces:**
- Produces: `def parse_dz_dz_row(row: SourceRow, converter: WylieConverter) -> ParsedRow` (key via `normalise_tibetan_headword`; body after the `<k>` block is running Dzongkha, `split_numbered_tibetan` → senses, `gloss_lang = GLOSS_LANG_DZ`, no pos; empty key → empty group key). `DDC = Pack(id="dz", directions=(dz-en 4_000_000 pos 0 "Dzongkha → English"/"རྫོང་ཁ → དབྱིན་ཡིག"/"རྫོང→EN" tibt; dz-dz 5_000_000 pos 1 "Dzongkha → Dzongkha"/"རྫོང་ཁ → རྫོང་ཁ"/"རྫོང→རྫོང" tibt; en-dz 6_000_000 pos 2 "English → Dzongkha"/"དབྱིན་ཡིག → རྫོང་ཁ"/"EN→རྫོང" latn), tables=("dz-en","dz-dz","en-dz"), tibetan_lookup_order=("dz-en","dz-dz"), english_direction="en-dz", wotd_direction_id="dz-en", meta={source_name: "DDC Dzongkha Dictionaries (StarDict, 2014-02-03)", source_author: "Dzongkha Development Commission", source_url: "https://www.dzongkha.gov.bt/", source_license: "Used with permission of the DCDD", attribution_text: "Dictionary data from the Dzongkha–English, English–Dzongkha and Dzongkha dictionaries of the Dzongkha Development Commission (2014 digital edition), used with the permission of the Dzongkha Development and Culture Division." (OWNER WORDING: ledger item)}, expected=Expected(directions=3, entries={"dz-en": (35_543, 35_543), "dz-dz": (22_786, 22_786), "en-dz": (23_100, 23_237)}, senses={"dz-en": (36_000, 42_000), "dz-dz": (26_000, 32_000), "en-dz": (30_000, 42_000)}, examples={}, see_also=(0, 0), see_also_resolved_percent=(0.0, 100.0), synonyms=(900, 940), antonyms=(440, 460), wylie=58_329, truncated=(0, 0), wotd=(5_000, 30_000)), read_source=lambda directory, limit: {name: read_stardict(directory / f"stardict-{name}" / name, limit=limit) for name in tables} with `sort_case_duplicates` applied to "en-dz")`. `check_ranges` skips the resolved-% check when `see_also` is `(0, 0)`.

- [ ] **Step 1: Write the failing tests**: `test_dz_dz.py`: `test_running_definition` ('ཀ ' key with trailing space → "ཀ་", one sense starting "གསལ་བྱེད་སུམ་ཅུའི་དང་པོ་ཀ།"); `test_numbered_senses` ('མི་ཆོས་གཙང་མ་བཅུ་དྲུག' → 16 senses); `test_empty_key`. `test_packs.py`: `test_ddc_pack_shape` (ids, id bases 4M/5M/6M, positions, latn for en-dz, lookup order, wotd direction) and the existing disjointness test now covering two packs. `test_wotd.py`: `test_eligibility_uses_pack_direction` (a dz-en draft with 2 syllables and an English gloss is eligible with `wotd_direction_id="dz-en"`, not with `"bo-en"`). `test_related.py`: `test_ddc_index_resolves_in_dz_en_then_dz_dz`. `test_stats.py`: `test_ddc_ranges_include_antonyms`.
- [ ] **Step 2: Run** → FAIL.  **Step 3: Implement.**  **Step 4: Run** `pytest -q` → PASS.
- [ ] **Step 5: Commit** `git add -A tools/dictionary && git commit -m "Add the DDC Dzongkha pack to the build pipeline"`.

### Task T1.6: CLI, stats files, README, full builds and assets

**Files:**
- Modify: `build_db.py` (help text; summary lines name the pack), `dictbuild/resources.py` (`stats_xml(entry_count, asset_bytes, pack_id)`, names `dictionary_entry_count_<pack_id>` / `dictionary_asset_bytes_<pack_id>`, comment names `assets/dictionary/<pack_id>.db`), `tests/test_resources.py`, `tests/test_build_smoke.py` (+ `DZONGKHA_SOURCE` slow test with `--limit 300`), `README.md`
- Build outputs: `app/src/main/assets/dictionary/bo.db` (rebuilt, byte-identical), `app/src/main/assets/dictionary/dz.db` (new), `app/src/main/res/values/dictionary_stats_bo.xml`, `dictionary_stats_dz.xml`
- Create: `app/src/main/assets/licenses/DDC-permission.txt` (OWNER: the permission text; until supplied, the file holds the `attribution_text` sentence and a line "Full permission text to be supplied by the owner." and the ledger lists it)

- [ ] **Step 1: Write the failing tests**: `test_resources.py`: `test_stats_xml_names_carry_pack_id`; `test_build_smoke.py`: `test_build_requires_pack` (no `--pack` → argparse error exit 2), `test_tiny_dz_build` (3-row StarDict fixture per table via the T1.2 fixture writer, `--pack dz --limit 10` → exit 0, `directions` rows 3, `PRAGMA user_version` 1, `meta.wotd_direction_id == "dz-en"`), the slow `test_build_dz_limit_300` gated by `DZONGKHA_SOURCE`.
- [ ] **Step 2: Run** → FAIL.  **Step 3: Implement**; write the README sections (pack matrix, DDC source layout `<dir>/stardict-dz-en/dz-en.*` etc., both stats files, per-pack release steps).
- [ ] **Step 4: Run** `pytest -q` → PASS. Then the full builds, from `tools/dictionary`:
  ```sh
  .venv/bin/python -I build_db.py --pack bo --require-wylie --source <MLDic.ml> --schema-dir ../../app/schemas/com.kharagedition.tibetandictionary.data.local.DictionaryDatabase --out ../../app/src/main/assets/dictionary/bo.db --stats-xml ../../app/src/main/res/values/dictionary_stats_bo.xml --data-version 2026.10.1 --wotd-seed 20261019 --built-at 2026-10-19T00:00:00Z --report /tmp/bo-report.json
  .venv/bin/python -I build_db.py --pack dz --require-wylie --source ~/Downloads/dzongkha --schema-dir ../../app/schemas/com.kharagedition.tibetandictionary.data.local.DictionaryDatabase --out ../../app/src/main/assets/dictionary/dz.db --stats-xml ../../app/src/main/res/values/dictionary_stats_dz.xml --data-version 2026.10.1 --wotd-seed 20261019 --built-at 2026-10-20T00:00:00Z --report /tmp/dz-report.json
  ```
  Expected: both exit 0; `git status` shows `bo.db` unchanged; `dz.db` 30–40 MB; the dz report's `unknown_labels` only lists documented DDC variants (record the list in the ledger; tighten `Expected` to the actual counts ± 0 for entries and a ±5 % window elsewhere, re-run, commit the tightened ranges). Then `cd ../.. && ./gradlew :app:testDebugUnitTest :app:lintDebug` → green.
- [ ] **Step 5: Commit** `git add -A tools/dictionary app/src/main/assets app/src/main/res/values/dictionary_stats_bo.xml app/src/main/res/values/dictionary_stats_dz.xml && git commit -m "Build the Dzongkha pack and ship both dictionary assets"`.

---

### L2 Data

### Task T2.1: Dictionary and word-of-the-day repositories over the active pack

**Files:**
- Modify: `…/data/repository/DictionaryRepositoryImpl.kt`, `WordOfTheDayRepositoryImpl.kt`, `Mappers.kt` (nothing structural), `test:…/data/repository/DictionaryRepositoryImplTest.kt`, `WordOfTheDayRepositoryImplTest.kt`, `test:…/data/TestDb.kt` (+ `miniDzongkhaDictionary()` with directions dz-en/dz-dz/en-dz at id bases 4M/5M/6M, `wotd_direction_id = "dz-en"`, 3 entries incl. "ཆུ་")

**Interfaces:**
- Consumes: `DictionaryDatabases` (T0.4).
- Produces: `DictionaryRepositoryImpl @Inject constructor(databases: DictionaryDatabases, @IoDispatcher io)`: `capabilities = databases.active.filterNotNull().flatMapLatest { pack -> val db = databases.get(pack); combine(db.directionDao().observeVisible(), db.metaDao().observeAll()) { … } }.distinctUntilChanged().flowOn(io)`; `observeEntry` the same shape over `entryDao().observeWithDetails`; one-shot calls on `databases.activeDb`; `SearchEngine(db.entryDao(), db.directionDao())` built per call. `WordOfTheDayRepositoryImpl(databases, io)` likewise (`observe` flatMaps on the active pack; `forDate` uses `activeDb`).

- [ ] **Step 1: Write the failing tests** (existing ones switch to `DictionaryDatabases { … }` with `activate(TIBETAN)`; add): `DictionaryRepositoryImplTest.capabilitiesFollowTheActivePack` (databases over `miniDictionary()` and `miniDzongkhaDictionary()`; Turbine: bo directions, then `activate(DZONGKHA)` → dz directions, `wotdDirectionId == "dz-en"`); `searchUsesTheActivePack` (query "ཆུ" after activating dz hits the dz entry id in the 4M range); `observeEntryReEmitsOnSwitch` (an id present only in dz: null → entry after activation). `WordOfTheDayRepositoryImplTest.wordOfTheDayFollowsTheActivePack`.
- [ ] **Step 2: Run** `--tests '*DictionaryRepositoryImplTest' --tests '*WordOfTheDayRepositoryImplTest'` → FAIL.
- [ ] **Step 3: Implement.**  **Step 4: Run** → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git commit -am "Read dictionary data and the word of the day from the active pack"`.

### Task T2.2: Saved and recent repositories per pack

**Files:**
- Modify: `…/data/repository/SavedRepositoryImpl.kt`, `RecentSearchesRepositoryImpl.kt`, `UserMappers.kt`, `test:…/data/repository/SavedRepositoryImplTest.kt`, `RecentSearchesRepositoryImplTest.kt`

**Interfaces:**
- Produces: `SavedRepositoryImpl(db: UserDatabase, databases: DictionaryDatabases, clock, io)`, `RecentSearchesRepositoryImpl(db, databases, clock, io)`. `private val packId: String get() = checkNotNull(databases.active.value) { "No active dictionary" }.id`; observe flows: `databases.active.filterNotNull().flatMapLatest { savedDao.observeAll(it.id) }` (all list flows, `observeFolders`, `observeCount`, `observeSavedIds`, `search`, `observe(limit)`); writes use `packId` (`toSavedEntity(packId, …)`, `FolderEntity(packId = packId, …)`, `nextPosition(packId)`, `existsByNameIgnoreCase(packId, …)`, `exists(id, packId)`, `RecentSearchEntity(packId = packId, …)`, `clear(packId)`).

- [ ] **Step 1: Write the failing tests**: `SavedRepositoryImplTest.savedListsFollowTheActivePack` (save under bo, activate dz → `observeAll` empty, `observeCount` 0; back → one); `saveIgnoresFolderOfOtherPack` (folder created under dz; activate bo; `save(entry, folderId = dzFolder)` → stored with `folderId == null`); `restoreKeepsPack` (unsave/restore under bo while dz is active later still lists under bo only); `createFolderSameNameInBothPacks`. `RecentSearchesRepositoryImplTest.recentsFollowTheActivePack`, `clearOnlyClearsActivePack`.
- [ ] **Step 2: Run** → FAIL.  **Step 3: Implement.**  **Step 4: Run** → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git commit -am "Scope saved words, folders and recent searches to the active pack"`.

### Task T2.3: Antonym relation

**Files:**
- Modify: `…/data/model/Entry.kt` (`enum class Relation { SEE_ALSO, SYNONYM, ANTONYM }`), `…/data/repository/Mappers.kt:135` (`"synonym" → SYNONYM; "antonym" → ANTONYM; else SEE_ALSO`), `test:…/data/repository/MappersTest.kt` (or the nearest existing mapper test)

- [ ] **Step 1: Write the failing test** `relationAntonymMaps` (`RelatedEntryEntity(relation = "antonym")` → `Relation.ANTONYM`; `"anything"` → SEE_ALSO).
- [ ] **Step 2: Run** → FAIL.  **Step 3: Implement** (grep `Relation.` for exhaustive `when`s; the UI renders ANTONYM rows like SYNONYM rows).  **Step 4: Run** → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git commit -am "Map antonym links from the dictionary data"`.

---

### L3 UI

### Task T3.1: Root gate, switching overlay, navigation reset, held deep links

**Files:**
- Modify: `…/ui/app/AppRootViewModel.kt`, `AppRoot.kt`, `InstallingScreen.kt`, `MainActivity.kt`, `…/navigation/AppNavHost.kt`
- Test: `test:…/ui/app/AppRootViewModelTest.kt`, `InstallingScreenTest.kt`, `test:…/navigation/AppNavHostTest.kt` (the switch and deep-link cases; L4 adds the end-to-end one)

**Interfaces:**
- Consumes: `DictionaryDatabases.active`, `DictionarySwitcher` + `SwitchState` (T0.4/T0.6), `InstallState` (T0.5), `DictionaryPackRes` (T0.1), `MainViewModel.selectTab(MainTab)`.
- Produces:
  ```kotlin
  sealed interface AppRootState {
      data object Loading : AppRootState
      data class Installing(val pack: DictionaryPack, val progress: Float?, val totalEntries: Int?, val failure: Throwable? = null) : AppRootState
      data class Ready(val pack: DictionaryPack, val onboarded: Boolean, val appearance: Appearance, val textSize: AppTextSize,
                       val lineSpacing: Float, val switching: SwitchState = SwitchState.Idle) : AppRootState
  }
  class AppRootViewModel(installer, initializer, prefsRepository, databases: DictionaryDatabases, switcher: DictionarySwitcher, errors, @ApplicationScope appScope)
      fun retryInstall()           // installer.ensureInstalled(prefsRepository.snapshot().dictionaryPack) on appScope
      fun retrySwitch()            // switcher.retry()
      fun keepCurrentDictionary()  // switcher.dismiss()
  @Composable fun InstallingScreen(state: AppRootState.Installing, onRetry: () -> Unit, modifier = Modifier,
                                   bundledEntryCount: Int = integerResource(state.pack.entryCountRes()))
  @Composable fun InstallingContent(title: String, progress: Float?, totalEntries: Int?, failure: Throwable?, onRetry: () -> Unit,
                                    secondaryActionLabel: String? = null, onSecondaryAction: (() -> Unit)? = null, modifier)  // shared body
  @Composable fun SwitchingOverlay(switching: SwitchState, onRetry: () -> Unit, onKeepCurrent: () -> Unit)             // in AppRoot.kt; tag "switching_overlay"
  @Composable fun AppRoot(state: AppRootState.Ready, onRetrySwitch: () -> Unit, onKeepCurrent: () -> Unit)
  @Composable fun AppNavHost(startDestination: Any, navController: NavHostController, activePack: DictionaryPack, onboarded: Boolean, holdLinks: Boolean, modifier = Modifier)
  ```
  Gate mapping: `combine(installer.state, initializer.ready, prefs, databases.active, switcher.state)`: `active == null` → `Checking` → Loading or (startupDone) Installing(pack = prefs.dictionaryPack, failure = installerDidNotStart); `Copying(p, x)` → Installing(p, x, null); `Failed(p, c)` → Installing(p, null, null, c); `Ready` with active still null → Loading (one frame). `active != null` → `Ready(active, prefs…, switching)`. `AppRoot`: `Box { AppNavHost(…, holdLinks = state.switching != Idle); AppToastHost(…); if (state.switching != Idle) SwitchingOverlay(…) }`; the overlay fills the parent, consumes pointer input, declares `BackHandler(true) {}`, shows `InstallingContent(title = stringResource(R.string.switching_dictionary, stringResource(pack.nameRes())), progress, totalEntries = null, failure, onRetry, secondaryActionLabel = stringResource(R.string.keep_current_dictionary) on Failed)`. `AppNavHost`: `var lastPack by rememberSaveable { mutableStateOf(activePack.id) }; LaunchedEffect(activePack, onboarded) { if (activePack.id != lastPack) { lastPack = activePack.id; if (onboarded) { mainViewModel.selectTab(MainTab.SEARCH); navController.navigate(Main) { popUpTo(navController.graph.id) { inclusive = true } } } } }`; the deep-link collector becomes `LaunchedEffect(navController, holdLinks) { if (!holdLinks) eventBus.events.collect { … } }`. `MainActivity`: `is AppRootState.Ready -> AppRoot(s, viewModel::retrySwitch, viewModel::keepCurrentDictionary)`.

- [ ] **Step 1: Write the failing tests**: `AppRootViewModelTest`: existing cases adapted; `firstInstallOfDzongkhaShowsInstallingForThatPack` (prefs dz, installer Copying(DZONGKHA, .3f) → `Installing(DZONGKHA, 0.3f, …)`); `switchShowsOverlayThenNewPack` (active TIBETAN; switcher state InProgress(DZONGKHA, .5f) → `Ready(TIBETAN, switching = InProgress)`; databases.activate(DZONGKHA) + Idle → `Ready(DZONGKHA, switching = Idle)`); `failedSwitchKeepsOldPackWithFailure`; `keepCurrentDismisses` (calls `switcher.dismiss()`); `retryInstallUsesStoredPack`. `InstallingScreenTest`: `overlayShowsKeepCurrentOnlyWhenFailed` (compose test of `SwitchingOverlay`). `AppNavHostTest` (Hilt + `@BindValue` fakes incl. `FakeDictionarySwitcher` and a real `DictionaryDatabases { inMemoryDictionaryDb() }`): `completedSwitchPopsToHomeOnSearchTab` (open Entry 1_000_001 over Main, then `databases.activate(DZONGKHA)` → `home_screen` shown, no entry route, tab Search); `switchDuringOnboardingDoesNotPop` (not onboarded; activate → still `onboarding_screen`); `deepLinkDuringSwitchIsHeldUntilReady` (switcher InProgress; post `app://entry/1000001` → no navigation; Idle → Entry screen shown).
- [ ] **Step 2: Run** `--tests '*AppRootViewModelTest' --tests '*InstallingScreenTest' --tests '*AppNavHostTest'` → FAIL.
- [ ] **Step 3: Implement.**  **Step 4: Run** → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git commit -am "Show a switching overlay and reset navigation when the dictionary pack changes"`.

### Task T3.2: Onboarding step 1: choose your dictionary

**Files:**
- Modify: `…/ui/onboarding/OnboardingUiState.kt`, `OnboardingViewModel.kt`, `OnboardingSteps.kt`, `OnboardingScreen.kt`
- Test: `test:…/ui/onboarding/OnboardingViewModelTest.kt`, `OnboardingScreenTest.kt`

**Interfaces:**
- Consumes: `DictionarySwitcher` (T0.6), `DictionaryPackRes` (T0.1), `OptionCard`, `MixedText`.
- Produces: `OnboardingStep.DICTIONARY = 1, DIRECTION = 2, READING = 3, ACCOUNT = 4`; `STEP_COUNT = if (Features.ACCOUNTS) 4 else 3` (and `STEP_COUNT_WITH_ACCOUNTS = 4`); `OnboardingUiState(step = DICTIONARY, …, packs: List<DictionaryPack> = DictionaryPack.entries, selectedPack: DictionaryPack = TIBETAN, switching: Boolean = false)`; `OnboardingEvent.SelectDictionary(pack: DictionaryPack)`; `OnboardingViewModel(savedState, prefs, dictionary, switcher: DictionarySwitcher, errors)`; draft key `onboarding_pack`; `DictionaryStep(state, onEvent, modifier)` with the flag strand, greeting and `StepHeader(onboarding_dictionary_title, onboarding_dictionary_body, TitleUnderGreeting)` moved from `DirectionStep` (which now uses `TitleTop`), cards `OptionCard(selected, onClick = SelectDictionary, Modifier.testTag("onboarding_pack_${pack.id}"), description = stringResource(pack.descriptionRes(), TibetanNumerals.formatCount(integerResource(pack.entryCountRes()), language))) { MixedText(stringResource(pack.nameRes()) + " · " + stringResource(pack.glossRes()), …) }`. `store(DICTIONARY, state)` = `switcher.switchTo(state.selectedPack).join()` then `check(switcher.state.value !is SwitchState.Failed)`; `next()` becomes `viewModelScope.launch { if (advancing) return@launch; advancing = true; try { val ok = recordedOk { prefs.store(step, state) }; if (ok) updateDraft { it.copy(step = step + 1) } } finally { advancing = false } }` where `recordedOk` returns false on a recorded failure; `finish()` returns without setting `onboarded` or sending `Finished` (and resets `finished = false`) when the DICTIONARY store fails. `BackHandler(enabled = state.step > OnboardingStep.DICTIONARY)`; `Back` coerces to DICTIONARY; `restoredDraft` coerces into `DICTIONARY..STEP_COUNT`.

- [ ] **Step 1: Write the failing tests**: `OnboardingViewModelTest`: `startsOnDictionaryStepWithStoredPackSelected` (prefs dz → selectedPack DZONGKHA); `continueOnDictionaryStepSwitchesThenAdvances` (`FakeDictionarySwitcher` wired to prefs: requests `[DZONGKHA]`, prefs pack dz, step 2); `continueWithSamePackStillPersistsChoice` (`dictionaryPackChosen` true); `failedSwitchStaysOnDictionaryStep` (fake switcher ending Failed → step 1, onboarded false); `skipOnDictionaryStepSwitchesBeforeFinishing` (order: request, then `onboarded`, then `Finished`); `directionStepShowsNewPackDirections` (fake dictionary capabilities swapped by the switcher hook → directions dz-en first); existing tests shifted by one step. `OnboardingScreenTest`: `dictionaryStepShowsBothPacksAndTags`, `backIsDisabledOnDictionaryStep`, step tags `onboarding_step_1..3`.
- [ ] **Step 2: Run** `--tests '*OnboardingViewModelTest' --tests '*OnboardingScreenTest'` → FAIL.
- [ ] **Step 3: Implement.**  **Step 4: Run** → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git commit -am "Let onboarding choose the dictionary pack first"`.

### Task T3.3: Profile: Dictionary row and sheet; direction labels

**Files:**
- Modify: `…/ui/profile/ProfileUiState.kt`, `ProfileViewModel.kt`, `ProfileScreen.kt`, `ProfileSheets.kt`, `…/ui/components/Sheets.kt` (`DictionaryPickerSheet`; `directionDescription` + dz ids), `…/ui/components/DirectionSwitcher.kt` (`"dz" -> "རྫོང"`, `NonLatinTokens += "dz"`)
- Test: `test:…/ui/profile/ProfileViewModelTest.kt`, `test:…/ui/components/DirectionSwitcherTest.kt` (`dzongkhaTokenIsTibetanScript`)

**Interfaces:**
- Produces: `ProfileUiState.dictionary: DictionaryPack = TIBETAN`; `ProfileSheet.DICTIONARY`; `ProfileEvent.SetDictionary(val pack: DictionaryPack)`; `ProfileViewModel(prefs, dictionary, databases: DictionaryDatabases, switcher: DictionarySwitcher, localeApplier, linkTargets, errors)` whose `uiState` combine adds `databases.active.filterNotNull()`; handler: `sheet.value = null; if (pack != state.dictionary) switcher.switchTo(pack)`; `@Composable fun DictionaryPickerSheet(selected: DictionaryPack, onPick: (DictionaryPack) -> Unit, onDismiss: () -> Unit)` (title `R.string.dictionary`, one `OptionCard` per pack with name + gloss and `descriptionRes`); the row `SettingsRow(label = stringResource(R.string.dictionary), Modifier.testTag("profile_row_dictionary"), value = stringResource(state.dictionary.nameRes()), onClick = OpenSheet(DICTIONARY), trailing = Chevron)` placed before the Default direction row.

- [ ] **Step 1: Write the failing tests**: `ProfileViewModelTest.dictionaryRowShowsActivePack`, `setDictionaryClosesSheetAndRequestsSwitch`, `setSameDictionaryDoesNotSwitch`; `DirectionSwitcherTest.dzongkhaTokenIsTibetanScript` (`directionLabelText(dzEn)` == "རྫོང → EN" following the existing spacing rule); a `SheetsTest`/existing picker test: `directionDescriptionCoversDdcIds`.
- [ ] **Step 2: Run** → FAIL.  **Step 3: Implement.**  **Step 4: Run** → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git commit -am "Add the Dictionary setting to Profile and Dzongkha direction labels"`.

### Task T3.4: About, Licences, version line and share text per pack

**Files:**
- Modify: `…/ui/about/AboutViewModel.kt`, `AboutUiState.kt`, `AboutScreen.kt`, `LicencesScreen.kt` (+ `LicencesViewModel`), `…/ui/profile/VersionLine.kt`, `ProfileViewModel.kt`/`ProfileScreen.kt` (share text and version line take the pack name), `ProfileUiState.kt` if needed
- Test: `test:…/ui/about/AboutViewModelTest.kt`, `LicencesScreenTest.kt` (existing), `test:…/ui/profile/VersionLineTest.kt`

**Interfaces:**
- Produces: `AboutUiState(versionName, entryCount, pack: DictionaryPack, sourceName: String, author: String, licence: String, attribution: String?)` from `capabilities.attribution` + `databases.active`; `AboutScreen` renders `stringResource(R.string.about_data, sourceName, author, licence)` and `versionLine(stringResource(pack.nameRes()), versionName, entryCount)`; `internal fun versionLine(packName: String, versionName: String, entryCount: Int?): String`; `@HiltViewModel class LicencesViewModel(databases: DictionaryDatabases) { val pack: StateFlow<DictionaryPack> }`; `internal fun bundledLicences(pack: DictionaryPack): List<Pair<String, Int>>` = `(pack.licenceAsset to pack.licenceTitleRes())` + the two fonts; `AssetManager.readLicences(pack)`; `ProfileEffect.ShareApp(listingUrl, packName: String)` and the screen formats `share_app_text` with both.

- [ ] **Step 1: Write the failing tests**: `AboutViewModelTest.aboutShowsActivePackAttribution`; `VersionLineTest.versionLineNamesThePack`; `LicencesScreenTest.dataLicenceFollowsPack` (`bundledLicences(DZONGKHA).first() == ("licenses/DDC-permission.txt" to R.string.licence_data_dz)`); `ProfileViewModelTest.shareNamesThePack`.
- [ ] **Step 2: Run** → FAIL.  **Step 3: Implement.**  **Step 4: Run** → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git commit -am "Show the active pack's attribution, licence and name on About and Profile"`.

---

### L4 Integration

### Task T4.1: Asset tests over both packs, nav host end-to-end switch

**Files:**
- Modify: `test:…/data/local/DictionaryAssetVersionTest.kt`, `DictionaryAssetOpenTest.kt` (JUnit `Parameterized` over `DictionaryPack.entries`; `assumeTrue(assetFile.exists())` so a missing `dz.db` skips, not fails), `test:…/navigation/AppNavHostTest.kt`

- [ ] **Step 1: Write the failing tests**: `DictionaryAssetVersionTest` per pack: header `user_version == 1`, `SUM(entry_count) == R.integer.dictionary_entry_count_<pack>`, file length == `dictionary_asset_bytes_<pack>`, `meta.wotd_direction_id` == `pack.defaultDirectionId`, every `directions.id` starts with the pack's language pair; `DictionaryAssetOpenTest` per pack: existing query-plan checks plus `exactMatchWithTshegInDzongkha` (`search("ཆུ་", "dz-en")` first hit headword "ཆུ་", id in 4,000,000..4,999,999) and `idRangesDoNotOverlap` (min/max `entries.id` per pack inside its bands). `AppNavHostTest.saveSwitchSaveSwitchBackShowsOnlyOwnPack` (fakes: save 1_000_001 under bo, switch to dz → Saved empty; back → one).
- [ ] **Step 2–4:** Run → FAIL on missing pieces → fix → PASS; full unit tests + lint green.
- [ ] **Step 5: Commit** `git commit -am "Guard both dictionary assets and the pack switch in tests"`.

### Task T4.2: Instrumented flows

**Files:**
- Modify: `app/src/androidTest/…/flows/OnboardingFlowTest.kt` (step 1 is the pack step: `onboarding_pack_bo` preselected without a Bhutan SIM, Continue, then the existing direction assertions on step 2, tags shifted)
- Create: `app/src/androidTest/…/flows/PackSwitchFlowTest.kt`

- [ ] **Step 1: Write the tests**: `PackSwitchFlowTest` (onboarded prefs, bo): search "ཆུ", open the entry, save it; Profile → `profile_row_dictionary` → pick Dzongkha → `switching_overlay` appears then goes → `home_screen` with `direction_segment_dz-en`; Saved tab empty; search "ཆུ" opens a Dzongkha entry; switch back → Saved shows the Tibetan word; `awaitPrefs { it.dictionaryPack == TIBETAN }`.
- [ ] **Step 2: Run** `./gradlew :app:connectedDebugAndroidTest` on a device/emulator → PASS (record device and duration in the ledger).
- [ ] **Step 3: Commit** `git add -A app/src/androidTest && git commit -m "Cover the dictionary pack step and switch in instrumented flows"`.

### Task T4.3: Device pass and close-out

- [ ] Run the spec §8.4 device pass (no-SIM default, `mcc=402` default via AVD cellular settings, both switches, kill mid-copy, AAB size) and record each result in the ledger.
- [ ] Whole-branch review (subagent-driven-development's final review) with fixes as further commits.
- [ ] Ledger close-out: owner items (Tibetan wording keys from T0.1; `attribution_text` and `DDC-permission.txt` wording; `source_url`; squash the `dz.db` blob before any push; Play listing copy for Bhutan).
