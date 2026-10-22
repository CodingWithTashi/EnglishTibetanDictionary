package com.kharagedition.tibetandictionary.data.install

import android.content.Context
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** The bundled pack databases (`assets/dictionary/<id>.db`) that [DictionaryInstallerImpl] copies onto the device. */
interface AssetSource {
    /** Opens a new stream over [pack]'s asset; the caller closes it. */
    fun open(pack: DictionaryPack): InputStream

    /**
     * [pack]'s asset size in bytes, or null when it cannot be known without reading it all (an asset stored
     * compressed in the APK, as the dictionary assets are). The installer then falls back to [AssetSizeProvider].
     */
    fun length(pack: DictionaryPack): Long?
}

/**
 * [pack]'s asset size in bytes as the data build recorded it (`R.integer.dictionary_asset_bytes_<id>` in
 * `res/values/dictionary_stats_<id>.xml`), or null when unknown. The installer uses it when [AssetSource.length] is
 * null, for copy progress and the final length check.
 */
fun interface AssetSizeProvider {
    fun expectedBytes(pack: DictionaryPack): Long?
}

/** Reads each pack's asset from the APK. */
@Singleton
class ApkAssetSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AssetSource {
    override fun open(pack: DictionaryPack): InputStream = context.assets.open(pack.assetPath)

    /** `openFd` works only for assets stored uncompressed; a compressed one throws, which means "unknown". */
    override fun length(pack: DictionaryPack): Long? = try {
        context.assets.openFd(pack.assetPath).use { fd -> fd.length.takeIf { it >= 0 } }
    } catch (_: IOException) {
        null
    }
}
