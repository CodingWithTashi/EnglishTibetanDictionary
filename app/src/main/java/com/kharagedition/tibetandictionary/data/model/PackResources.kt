package com.kharagedition.tibetandictionary.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.IntegerRes
import androidx.annotation.StringRes

/**
 * Everything the app shows for one pack. Each pack defines its value in its own file ([TibetanPackResources],
 * [DzongkhaPackResources]); [DictionaryPack.resources] maps the packs to them, and its `when` is exhaustive, so the
 * build fails until a new pack has one. Its strings live in `res/values/strings_pack_<id>.xml` (and the `values-bo`
 * copy); `docs/adding-a-dictionary-pack.md` lists the rest.
 *
 * @property name the pack's name in the UI language ("Tibetan Dictionary").
 * @property gloss the name in the other UI language, shown beside [name].
 * @property title the name in the pack's own script in every UI language (installing screen, About).
 * @property titleLatin the name in Latin letters in every UI language, under [title] on the installing screen.
 * @property language the dictionary's language in the UI language ("Tibetan"), for copy that names it.
 * @property description one line about the pack; takes the entry count as its only `%1$s`.
 * @property welcome the greeting at the top of onboarding, in the pack's language in every UI language.
 * @property greetingMorning the Home greeting before noon ("Good morning · …").
 * @property greetingAfternoon the Home greeting from noon.
 * @property greetingEvening the Home greeting in the evening.
 * @property licenceTitle heading of the pack's data licence on the Licences screen.
 * @property directionDescriptions what each of the pack's directions is for (direction id to string resource), shown
 *   by the direction pickers. A direction without one shows its label from the data instead.
 * @property tile the app-icon tile with its shadow, drawn by the installing screen while the pack is prepared.
 * @property entryCount entry count of the bundled asset, as the data build recorded it.
 * @property assetBytes size in bytes of the bundled asset, as the data build recorded it; 0 means unknown.
 * @property sample the entry the reading previews draw; see [PackSample].
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
    val directionDescriptions: Map<String, Int>,
    @param:DrawableRes val tile: Int,
    @param:IntegerRes val entryCount: Int,
    @param:IntegerRes val assetBytes: Int,
    val sample: PackSample,
)

/**
 * The entry the reading preview draws for a pack: a headword with its Wylie, numbered English glosses and a line in
 * the pack's own language, taken from that pack's data so the preview reads as the dictionary does. [shortGloss] is
 * the one-word gloss beside the headword in Profile's reading row. The onboarding reading step, the text-size sheet
 * and Profile draw it.
 */
data class PackSample(
    val headword: String,
    val wylie: String,
    val glosses: List<String>,
    val definition: String,
    val shortGloss: String,
)

/** The resources of this pack; see [PackResources]. */
val DictionaryPack.resources: PackResources
    get() = when (this) {
        DictionaryPack.TIBETAN -> TibetanPackResources
        DictionaryPack.DZONGKHA -> DzongkhaPackResources
    }
