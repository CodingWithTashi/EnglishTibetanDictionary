package com.kharagedition.tibetandictionary.data.model

/**
 * A dictionary the app can ship: its own database file, bundled asset, default direction and licence text.
 * [id] is the stable key stored in preferences.
 */
enum class DictionaryPack(
    val id: String,
    val dbName: String,
    val assetPath: String,
    val defaultDirectionId: String,
    val licenceAsset: String,
) {
    TIBETAN("bo", "dictionary-bo.db", "dictionary/bo.db", "bo-en", "licenses/GPL-3.0.txt"),
    DZONGKHA("dz", "dictionary-dz.db", "dictionary/dz.db", "dz-en", "licenses/DDC-permission.txt"),
    ;

    companion object {
        /** The pack with exactly this [id] (case-sensitive), or null. */
        fun fromId(id: String?): DictionaryPack? = entries.firstOrNull { it.id == id }
    }
}
