package com.kharagedition.tibetandictionary.data.model

/**
 * A dictionary the app can ship: one bundled database with its own directions, word of the day, saved words, folders
 * and recents. Everything the UI shows for a pack is in its [resources]; `docs/adding-a-dictionary-pack.md` lists
 * what a new pack needs.
 *
 * @property id the stable key stored in preferences and in the user database (`pack_id`); never change it once
 *   shipped. The pack's file names follow from it.
 * @property defaultDirectionId the direction a user of this pack starts on, until they choose another.
 * @property licenceAsset the data licence under `assets/`, shown on the Licences screen.
 */
enum class DictionaryPack(
    val id: String,
    val defaultDirectionId: String,
    val licenceAsset: String,
) {
    TIBETAN("bo", "bo-en", "licenses/GPL-3.0.txt"),
    DZONGKHA("dz", "dz-en", "licenses/DDC-permission.txt"),
    ;

    /** The installed copy in the app's databases directory (`dictionary-<id>.db`). */
    val dbName: String get() = "dictionary-$id.db"

    /** The bundled database the data build writes (`assets/dictionary/<id>.db`). */
    val assetPath: String get() = "dictionary/$id.db"

    companion object {
        /** The pack with exactly this [id] (case-sensitive), or null. */
        fun fromId(id: String?): DictionaryPack? = entries.firstOrNull { it.id == id }
    }
}
