package com.kharagedition.tibetandictionary.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.IntegerRes
import androidx.annotation.StringRes
import com.kharagedition.tibetandictionary.R

/**
 * Every resource the app shows for one pack. A new pack adds one value to [DictionaryPack.resources] (the `when` is
 * exhaustive, so the build fails until it exists) and the resources it names. `docs/adding-a-dictionary-pack.md` lists
 * the other places a pack touches.
 *
 * @property name the pack's name in the UI language ("Tibetan Dictionary").
 * @property gloss the name in the other UI language, shown beside [name].
 * @property title the name in Tibetan script in every UI language (installing screen, About).
 * @property titleLatin the name in Latin letters in every UI language, under [title] on the installing screen.
 * @property language the dictionary's language in the UI language ("Tibetan"), for copy that names it.
 * @property description one line about the pack; takes the entry count as its only `%1$s`.
 * @property welcome the greeting at the top of onboarding, in the pack's language in every UI language.
 * @property greetingMorning the Home greeting before noon ("Good morning · …").
 * @property greetingAfternoon the Home greeting from noon.
 * @property greetingEvening the Home greeting in the evening.
 * @property licenceTitle heading of the pack's data licence on the Licences screen.
 * @property tile the app-icon tile with its shadow, drawn by the installing screen while the pack is prepared.
 * @property entryCount entry count of the bundled asset, as the data build recorded it.
 * @property assetBytes size in bytes of the bundled asset, as the data build recorded it; 0 means unknown.
 */
data class PackResources(
    @param:StringRes val name: Int,
    @param:StringRes val gloss: Int,
    @param:StringRes val title: Int,
    @param:StringRes val titleLatin: Int,
    @param:StringRes val language: Int,
    @param:StringRes val description: Int,
    @param:StringRes val welcome: Int,
    @param:StringRes val greetingMorning: Int,
    @param:StringRes val greetingAfternoon: Int,
    @param:StringRes val greetingEvening: Int,
    @param:StringRes val licenceTitle: Int,
    @param:DrawableRes val tile: Int,
    @param:IntegerRes val entryCount: Int,
    @param:IntegerRes val assetBytes: Int,
)

/** The resources of this pack; see [PackResources]. */
val DictionaryPack.resources: PackResources
    get() = when (this) {
        DictionaryPack.TIBETAN -> TibetanResources
        DictionaryPack.DZONGKHA -> DzongkhaResources
    }

private val TibetanResources = PackResources(
    name = R.string.pack_name_bo,
    gloss = R.string.pack_gloss_bo,
    title = R.string.pack_title_bo,
    titleLatin = R.string.pack_title_latin_bo,
    language = R.string.pack_language_bo,
    description = R.string.pack_desc_bo,
    welcome = R.string.pack_welcome_bo,
    greetingMorning = R.string.greeting_morning_bo,
    greetingAfternoon = R.string.greeting_afternoon_bo,
    greetingEvening = R.string.greeting_evening_bo,
    licenceTitle = R.string.licence_data,
    tile = R.drawable.ic_splash_tile,
    entryCount = R.integer.dictionary_entry_count_bo,
    assetBytes = R.integer.dictionary_asset_bytes_bo,
)

private val DzongkhaResources = PackResources(
    name = R.string.pack_name_dz,
    gloss = R.string.pack_gloss_dz,
    title = R.string.pack_title_dz,
    titleLatin = R.string.pack_title_latin_dz,
    language = R.string.pack_language_dz,
    description = R.string.pack_desc_dz,
    welcome = R.string.pack_welcome_dz,
    greetingMorning = R.string.greeting_morning_dz,
    greetingAfternoon = R.string.greeting_afternoon_dz,
    greetingEvening = R.string.greeting_evening_dz,
    licenceTitle = R.string.licence_data_dz,
    tile = R.drawable.ic_splash_tile_dz,
    entryCount = R.integer.dictionary_entry_count_dz,
    assetBytes = R.integer.dictionary_asset_bytes_dz,
)
