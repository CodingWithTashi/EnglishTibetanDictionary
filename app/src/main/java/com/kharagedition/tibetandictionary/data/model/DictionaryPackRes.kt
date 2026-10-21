package com.kharagedition.tibetandictionary.data.model

import androidx.annotation.IntegerRes
import androidx.annotation.StringRes
import com.kharagedition.tibetandictionary.R

/** The pack's name in the UI language. */
@StringRes
fun DictionaryPack.nameRes(): Int = when (this) {
    DictionaryPack.TIBETAN -> R.string.pack_name_bo
    DictionaryPack.DZONGKHA -> R.string.pack_name_dz
}

/** The pack's name in the other language, shown as a gloss. */
@StringRes
fun DictionaryPack.glossRes(): Int = when (this) {
    DictionaryPack.TIBETAN -> R.string.pack_gloss_bo
    DictionaryPack.DZONGKHA -> R.string.pack_gloss_dz
}

/** One-line description; takes the entry count as its only `%1$s`. */
@StringRes
fun DictionaryPack.descriptionRes(): Int = when (this) {
    DictionaryPack.TIBETAN -> R.string.pack_desc_bo
    DictionaryPack.DZONGKHA -> R.string.pack_desc_dz
}

/** Heading of the pack's data licence on the Licences screen. */
@StringRes
fun DictionaryPack.licenceTitleRes(): Int = when (this) {
    DictionaryPack.TIBETAN -> R.string.licence_data
    DictionaryPack.DZONGKHA -> R.string.licence_data_dz
}

/** Entry count of the bundled asset, as the data build recorded it. */
@IntegerRes
fun DictionaryPack.entryCountRes(): Int = when (this) {
    DictionaryPack.TIBETAN -> R.integer.dictionary_entry_count_bo
    DictionaryPack.DZONGKHA -> R.integer.dictionary_entry_count_dz
}

/** Size in bytes of the bundled asset, as the data build recorded it; 0 means unknown. */
@IntegerRes
fun DictionaryPack.assetBytesRes(): Int = when (this) {
    DictionaryPack.TIBETAN -> R.integer.dictionary_asset_bytes_bo
    DictionaryPack.DZONGKHA -> R.integer.dictionary_asset_bytes_dz
}
