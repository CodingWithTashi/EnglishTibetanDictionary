package com.kharagedition.tibetandictionary.data.install

import android.content.Context
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
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
     * in the APK). Without a length the installer reports no intermediate copy progress.
     */
    fun length(): Long?
}

/** Reads [DictionaryDatabase.ASSET_PATH] from the APK. */
@Singleton
class ApkAssetSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AssetSource {
    override fun open(): InputStream = context.assets.open(DictionaryDatabase.ASSET_PATH)

    /** `openFd` works only for assets stored uncompressed; a compressed one throws, which means "unknown". */
    override fun length(): Long? = try {
        context.assets.openFd(DictionaryDatabase.ASSET_PATH).use { fd -> fd.length.takeIf { it >= 0 } }
    } catch (_: IOException) {
        null
    }
}
