package com.kharagedition.tibetandictionary.data.install

import android.content.Context
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** The bundled `dictionary.db` bytes that [DictionaryInstallerImpl] copies onto the device. */
interface AssetSource {
    /** Opens a new stream over the asset; the caller closes it. */
    fun open(): InputStream

    /**
     * The asset's size in bytes, or null when it cannot be known without reading it all (an asset stored compressed
     * in the APK, as `dictionary.db` is). The installer then falls back to [AssetSizeProvider].
     */
    fun length(): Long?
}

/**
 * The asset's size in bytes as the data build recorded it (`R.integer.dictionary_asset_bytes` in
 * `res/values/dictionary_stats_bo.xml`), or null when unknown. The installer uses it when [AssetSource.length] is null,
 * for copy progress and the final length check.
 */
fun interface AssetSizeProvider {
    fun expectedBytes(): Long?
}

/** Reads [DictionaryPack.TIBETAN]'s asset from the APK. */
@Singleton
class ApkAssetSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AssetSource {
    override fun open(): InputStream = context.assets.open(DictionaryPack.TIBETAN.assetPath)

    /** `openFd` works only for assets stored uncompressed; a compressed one throws, which means "unknown". */
    override fun length(): Long? = try {
        context.assets.openFd(DictionaryPack.TIBETAN.assetPath).use { fd -> fd.length.takeIf { it >= 0 } }
    } catch (_: IOException) {
        null
    }
}
