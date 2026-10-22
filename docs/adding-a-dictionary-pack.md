# Adding a dictionary pack

A pack is one bundled dictionary database with its own directions, word of the day, saved words, folders and
recents. The app ships two: Tibetan (`bo`, Monlam) and Dzongkha (`dz`, DDC). This page lists every place a new
pack touches, using `xx` as its id and `NEWLANG` as its enum value.

The steps marked **build fails** are enforced by an exhaustive `when`: the app does not compile until they are
done. The tests named under a step cover every pack, so they check the new one without changes.

## 1. Data

1. Write the source reader and one parser per source table under `tools/dictionary/dictbuild/parsers/`, with
   tests under `tools/dictionary/tests/`. The DDC parsers (`dz_en.py`, `dz_dz.py`, `en_dz.py`, sharing
   `ddc_common.py`) are the closest model for StarDict sources.
2. Add a `Pack` to `tools/dictionary/dictbuild/packs.py` and register it in `PACKS`. It sets the directions
   (in switcher order), the tables, the word-of-the-day direction, the `meta` strings shown on About
   (`source_name`, `source_author`, `source_url`, `source_license`, `attribution_text`) and the `expected`
   ranges of a full build.
   - Each direction needs an `id_base` no other direction of any pack uses (the next free one is `7_000_000`).
     Entry ids are `id_base` plus the source row id, and saved words and folders store them.
3. Build the asset and its stats file as `tools/dictionary/README.md` describes ("Shipping a data release"):
   `--pack xx`, `--out app/src/main/assets/dictionary/xx.db`,
   `--stats-xml app/src/main/res/values/dictionary_stats_xx.xml`.
4. Add the data licence to `app/src/main/assets/licenses/`.

## 2. App

1. `data/model/DictionaryPack.kt`: add
   `NEWLANG("xx", "dictionary-xx.db", "dictionary/xx.db", "xx-en", "licenses/<licence file>")`. The id is stored
   in preferences and in the user database, so never change it once shipped.
2. `data/model/DictionaryPackRes.kt` (**build fails**): add a `PackResources` value to `resources` and the
   resources it names (step 3).
3. Strings, in `res/values/strings.xml` and `res/values-bo/strings.xml`:
   - `pack_name_xx`, `pack_gloss_xx`, `pack_desc_xx` (takes the entry count), `pack_language_xx`
   - `pack_title_xx` and `pack_title_latin_xx` (non-translatable; values only)
   - `pack_welcome_xx` (non-translatable; the onboarding greeting in the pack's language)
   - `greeting_morning_xx`, `greeting_afternoon_xx`, `greeting_evening_xx`
   - `licence_data_xx`
   - `direction_desc_<direction>` for each direction, and the matching lines in `directionDescription`
     (`ui/components/Sheets.kt`). Without them the picker shows the direction's label instead.
4. `ui/components/PackContent.kt` (**build fails**): add a `PackSample` to `sample`, taken from the pack's
   own data. The onboarding reading step, the text-size preview and Profile's reading row draw it.
5. `ui/components/DirectionSwitcher.kt`: if the language is not written in Latin letters, add its token to
   `languageToken` and its code to `NonLatinTokens` (as `dz` → "རྫོང"). Otherwise the code is shown in capitals.
6. `ui/profile/ProfileUiState.kt`: add the pack's `OtherApp` entries, if any. A pack without any hides
   "More apps".
7. `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`: exclude `dictionary-xx.db` and its
   `-journal`, `-shm`, `-wal` and `.tmp` files, as for the other packs. `BackupRulesTest` checks every pack.
8. Icon tile: `PackResources.tile` is the tile the installing screen shows while the pack is prepared. Reuse
   `R.drawable.ic_splash_tile`, or add a variant to `tools/icon/gen.py` as the Dzongkha one is
   (`tools/icon/README.md`).

Nothing else picks behaviour by pack: the installer, the switcher, search, the user database and the screens
take the pack from `DictionaryPack` and `resources`.

## 3. Optional: a country default

To make the pack the first choice for a country's SIM cards, add `res/values-mcc<code>/packs.xml` with
`default_dictionary_pack` set to `xx`, as `values-mcc402` does for Bhutan. The same folder can rename the app
(`app_name`), and `drawable-mcc<code>` and `mipmap-mcc<code>-*` can give it its own launcher and splash icon.
The launcher icon, app name and system splash follow the SIM, not the active pack: Android reads them before
the app runs.

## 4. Check

- `./gradlew :app:testDebugUnitTest :app:lintDebug`. `DictionaryAssetVersionTest` and `DictionaryAssetOpenTest`
  open the real asset; `DictionaryPackTest` checks that each pack's resources are distinct;
  `PackOptionCardTest` and `PackResourcesTest` cover the names.
- `tools/dictionary`: `.venv/bin/python -I -m pytest -q`.
- On a device: onboarding shows the new pack and its greeting; Profile → Dictionary switches to it and back;
  saved words, folders and recents stay with their pack; About and Licences name the pack's source.
