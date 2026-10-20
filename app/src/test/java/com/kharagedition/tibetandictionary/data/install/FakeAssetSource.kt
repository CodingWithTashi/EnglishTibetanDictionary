package com.kharagedition.tibetandictionary.data.install

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [AssetSource] backed by [file]. Tests can:
 * - swap [file] to change the asset;
 * - set [openFailure] to make [open] throw;
 * - set [gate] to hold [open] until the test releases it ([entered] counts down once [open] has been called);
 * - set [declaredLength] to make [length] report a size other than the file's;
 * - set [failAfterBytes] to make a read throw once at least that many bytes have been delivered;
 * - set [pauseAfterBytes] to make a read wait, once that many bytes have been delivered, until [resume] is released
 *   ([midStream] counts down when it starts waiting).
 */
class FakeAssetSource(
    @Volatile var file: File,
    private val knownLength: Boolean = true,
) : AssetSource {
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

    override fun open(): InputStream {
        opens.incrementAndGet()
        entered.countDown()
        gate?.let { check(it.await(10, TimeUnit.SECONDS)) { "gate was never released" } }
        openFailure?.let { throw it }
        return ScriptedStream(file.inputStream())
    }

    override fun length(): Long? = declaredLength ?: if (knownLength) file.length() else null

    private inner class ScriptedStream(delegate: InputStream) : FilterInputStream(delegate) {
        private var delivered = 0L
        private var paused = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            failAfterBytes?.let { if (delivered >= it) throw IOException("asset read failed after $delivered bytes") }
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
