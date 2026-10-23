# Adding a dictionary pack

A pack is one bundled dictionary database with its own directions, word of the day, saved words, folders and
recents. The app ships two: Tibetan (`bo`, Monlam) and Dzongkha (`dz`, DDC). Search, the installer, the switcher, the
user database and the screens are generic: they take everything from the pack's database and from its entry in
`DictionaryPack` and `PackResources`. This page lists what a new pack needs, using `xx` as its id and `NEWLANG` as
its enum value.

Steps marked **build fails** are enforced by an exhaustive `when`: the app does not compile until they are done.
Steps marked **test fails** are checked for every pack by the named test, so a forgotten one fails
`./gradlew :app:testDebugUnitTest`.

## 1. Data (`tools/dictionary`)

1. Write the source reader and one parser per source table under `dictbuild/parsers/`, with tests under `tests/`.
   The DDC parsers (`dz_en.py`, `dz_dz.py`, `en_dz.py`, sharing `ddc_common.py`) are the closest model for
   StarDict sources; the Monlam ones (`tben.py`, `tbtb.py`, `entotb.py`) for an SQLite source.
2. Add `dictbuild/packs/xx.py` defining the pack's `Pack` (copy `ddc.py`) and register it in `PACKS` in
   `dictbuild/packs/__init__.py`. The `Pack` sets:
   - the directions, in switcher order. Each direction needs an `id` and an `id_base` that no direction of any pack
     uses (the next free `id_base` is `7_000_000`): entry ids are `id_base` plus the source row id, and saved words
     and folders store them. Its `short_label` is two language tokens around one arrow (`"རྫོང→EN"`): the app's
     direction switcher shows exactly these tokens, so use the same token for a language in every direction
     (`tests/test_packs.py` checks both);
   - the source tables and their parsers, the word-of-the-day direction and the headword lookup order;
   - the `meta` strings shown on About (`source_name`, `source_author`, `source_url`, `source_license`,
     `attribution_text`);
   - the `expected` count ranges of a full build (a build outside them exits with code 2).
3. Build the asset and its stats file as `tools/dictionary/README.md` describes ("Shipping a data release"):
   `--pack xx`, `--out app/src/main/assets/dictionary/xx.db`,
   `--stats-xml app/src/main/res/values/dictionary_stats_xx.xml`.
4. Put the data licence text in `app/src/main/assets/licenses/`.

## 2. App

1. `data/model/DictionaryPack.kt`: add `NEWLANG("xx", "<default direction id>", "licenses/<licence file>")`.
   The id is stored in preferences and in the user database, so never change it once shipped. The file names
   follow from it (`dictionary-xx.db` on the device, `assets/dictionary/xx.db` in the APK).
2. `data/model/NewlangPackResources.kt` (**build fails**): copy `DzongkhaPackResources.kt`, point every resource at
   the new pack's, list a description for each of its directions in `directionDescriptions` and give it a `sample`
   entry taken from its own data (the onboarding reading step, the text-size sheet and Profile draw it). Then map
   `NEWLANG` to it in `DictionaryPack.resources` (`data/model/PackResources.kt`).
   - `DictionaryPackTest` (**test fails**) checks that no two packs share a resource, a direction or a description.
   - `DictionaryAssetOpenTest` (**test fails**) opens the real asset and checks that every direction it ships has
     a description and a switcher label, and that no id band overlaps another pack's.
3. Strings: copy `res/values/strings_pack_dz.xml` and `res/values-bo/strings_pack_dz.xml` to `strings_pack_xx.xml`
   and replace the `_dz` suffixes and the text. The default file says what each string is for. `pack_title_xx`,
   `pack_title_latin_xx` and `pack_welcome_xx` are the same in every UI language, so only the default file has them.
4. Icon tile: `PackResources.tile` is the tile the installing screen shows while the pack is prepared. Reuse
   `R.drawable.ic_splash_tile`, or add a variant to `tools/icon/gen.py` as the Dzongkha one is
   (`tools/icon/README.md`).
5. `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` (**test fails**, `BackupRulesTest`): exclude
   `dictionary-xx.db` and its `-journal`, `-shm`, `-wal` and `.tmp` files in every section, as for the other packs.
   Android's backup rules take no wildcards, and a 37–87 MB database would use up the 25 MB Auto Backup quota.
6. Optional, `ui/profile/ProfileUiState.kt`: add the pack's entries to `OtherApp` (the developer's other apps,
   listed under "More apps"). A pack without any hides "More apps".
7. Translation (**build fails**): give the pack's `translateSamples`, one or two short sentences in its language
   for the Translate tab's "Try" chips, and add it to `DictionaryPack.translationPairs()`
   (`data/translation/TranslationModels.kt`) as its language ⇄ English, its own language first. A language other
   than `bo`, `dz` or `en` also needs a `Lang` entry there (check `usesTibetanScript`), and on the backend an entry
   in `LANG_CODES` (`backend/functions/src/domain/languages.ts`) and a route for each direction in
   `config/translation` (`backend/README.md`). `npm run check-providers` shows which provider really handles it.
   Until the backend offers a direction, the Translate tab and the Home card leave it out.

The direction switcher's labels, the direction list and its features (Wylie, examples, word of the day…) all come
from the asset, so they need no app change.

## 3. Optional: a country default

To make the pack the first choice for a country's SIM cards, add `res/values-mcc<code>/packs.xml` with
`default_dictionary_pack` set to `xx`, as `values-mcc402` does for Bhutan. The same folder can rename the app
(`app_name`), and `drawable-mcc<code>` and `mipmap-mcc<code>-*` can give it its own launcher and splash icon.
The launcher icon, app name and system splash follow the SIM, not the active pack: Android reads them before
the app runs.

## 4. Check

- `./gradlew :app:testDebugUnitTest :app:lintDebug`. `DictionaryAssetVersionTest` and `DictionaryAssetOpenTest`
  open the real asset; `DictionaryPackTest`, `BackupRulesTest`, `SheetsTest`, `PackOptionCardTest` and
  `PackResourcesTest` cover every pack.
- `tools/dictionary`: `.venv/bin/python -I -m pytest -q`.
- On a device: onboarding shows the new pack and its greeting; Profile → Dictionary switches to it and back;
  saved words, folders and recents stay with their pack; About and Licences name the pack's source.
