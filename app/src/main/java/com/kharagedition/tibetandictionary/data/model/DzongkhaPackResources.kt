package com.kharagedition.tibetandictionary.data.model

import com.kharagedition.tibetandictionary.R

/** The Dzongkha pack ([DictionaryPack.DZONGKHA], DDC data); strings in `strings_pack_dz.xml`. */
internal val DzongkhaPackResources = PackResources(
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
    directionDescriptions = mapOf(
        "dz-en" to R.string.direction_desc_dz_en,
        "dz-dz" to R.string.direction_desc_dz_dz,
        "en-dz" to R.string.direction_desc_en_dz,
    ),
    tile = R.drawable.ic_splash_tile_dz,
    entryCount = R.integer.dictionary_entry_count_dz,
    assetBytes = R.integer.dictionary_asset_bytes_dz,
    // DDC dz-en and dz-dz entries for བཀྲ་ཤིས་ (the first sentence of the Dzongkha definition).
    sample = PackSample(
        headword = "བཀྲ་ཤིས།",
        wylie = "bkra shis",
        glosses = listOf("auspicious, good, prosperity"),
        definition = "ལེགས་ཤོམ། དགེ་བ་ཕུན་སུམ་ཚོགས་མི་ཅིག་ལུ་སླབཔ་ཨིན།",
        shortGloss = "auspicious",
    ),
)
