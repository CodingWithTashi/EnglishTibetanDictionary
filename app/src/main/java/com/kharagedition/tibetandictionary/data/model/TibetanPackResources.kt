package com.kharagedition.tibetandictionary.data.model

import com.kharagedition.tibetandictionary.R

/** The Tibetan pack ([DictionaryPack.TIBETAN], Monlam data); strings in `strings_pack_bo.xml`. */
internal val TibetanPackResources = PackResources(
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
    licenceTitle = R.string.licence_data_bo,
    directionDescriptions = mapOf(
        "bo-en" to R.string.direction_desc_bo_en,
        "bo-bo" to R.string.direction_desc_bo_bo,
        "en-bo" to R.string.direction_desc_en_bo,
    ),
    tile = R.drawable.ic_splash_tile,
    entryCount = R.integer.dictionary_entry_count_bo,
    assetBytes = R.integer.dictionary_asset_bytes_bo,
    sample = PackSample(
        headword = "བཀྲ་ཤིས།",
        wylie = "bkra shis",
        glosses = listOf("auspicious, fortunate", "good luck, blessing"),
        definition = "བདེ་ལེགས་དང་ལེགས་པའི་མཚན་མ།",
        shortGloss = "auspicious",
    ),
    translateSamples = listOf("ཁྱེད་རང་སྐུ་གཟུགས་བདེ་པོ་ཡིན་པས།", "ང་བོད་པ་ཡིན།"),
)
