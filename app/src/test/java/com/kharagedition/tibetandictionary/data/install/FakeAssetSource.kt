package com.kharagedition.tibetandictionary.data.install

import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [AssetSource] backed by one file per pack in [files]. Tests can:
 * - swap [file] (the Tibetan pack's) or put another entry in [files] to change an asset;
 * - set [openFailure] to make [open] throw;
 * - set [gate] to hold [open] until the test releases it ([entered] counts down once [open] has been called);
 * - set [declaredLength] to make [length] report a size other than the file's;
 * - set [failAfterBytes] to make a read throw once at least that many bytes have been delivered, only for
 *   [failingPack] when that is set;
 * - set [pauseAfterBytes] to make a read wait, once that many bytes have been delivered, until [resume] is released
 *   ([midStream] counts down when it starts waiting).
 */
class FakeAssetSource(
    files: Map<DictionaryPack, File>,
    private val knownLength: Boolean = true,
) : AssetSource {
    /** An asset for [DictionaryPack.TIBETAN] only. */
    constructor(file: File, knownLength: Boolean = true) : this(mapOf(DictionaryPack.TIBETAN to file), knownLength)

    val files = ConcurrentHashMap(files)

    /** The Tibetan pack's asset file. */
    var file: File
        get() = checkNotNull(files[DictionaryPack.TIBETAN])
        set(value) {
            files[DictionaryPack.TIBETAN] = value
        }

    @Volatile var failingPack: DictionaryPack? = null

    @Volatile var openFailure: Exception? = null

    @Volatile var gate: CountDownLatch? = null

    val entered = CountDownLatch(1)

    @Volatile var declaredLength: Long? = null

    @Volatile var failAfterBytes: Long? = null

    @Volatile var pauseAfterBytes: Long? = null

    val midStream = CountDownLatch(1)

    val resume = CountDownLatch(1)

    private val opens = AtomicInteger()

    val openCount: Int get() = opens.get()

    /** The packs [open] was called for, in order. */
    val openedPacks = java.util.concurrent.CopyOnWriteArrayList<DictionaryPack>()

    override fun open(pack: DictionaryPack): InputStream {
        openedPacks += pack
        opens.incrementAndGet()
        entered.countDown()
        gate?.let { check(it.await(10, TimeUnit.SECONDS)) { "gate was never released" } }
        openFailure?.let { throw it }
        return ScriptedStream(pack, checkNotNull(files[pack]) { "no asset for $pack" }.inputStream())
    }

    override fun length(pack: DictionaryPack): Long? =
        declaredLength ?: if (knownLength) checkNotNull(files[pack]).length() else null

    private inner class ScriptedStream(
        private val pack: DictionaryPack,
        delegate: InputStream,
    ) : FilterInputStream(delegate) {
        private var delivered = 0L
        private var paused = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            failAfterBytes?.let {
                if (delivered >= it && (failingPack ?: pack) == pack) {
                    throw IOException("asset read failed after $delivered bytes")
                }
            }
            pauseAfterBytes?.let {
                if (!paused && delivered >= it) {
                    paused = true
                    midStream.countDown()
                    check(resume.await(10, TimeUnit.SECONDS)) { "resume was never released" }
                }
            }
            return super.read(b, off, len).also { if (it > 0) delivered += it }
        }
    }
}
