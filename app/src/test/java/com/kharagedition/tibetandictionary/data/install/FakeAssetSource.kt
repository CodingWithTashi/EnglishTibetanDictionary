package com.kharagedition.tibetandictionary.data.install

import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [AssetSource] backed by [file]; tests swap [file] to change the asset, set [openFailure] to make [open] throw, and set
 * [gate] to hold [open] until the test releases it ([entered] counts down once [open] has been called).
 */
class FakeAssetSource(
    @Volatile var file: File,
    private val knownLength: Boolean = true,
) : AssetSource {
    @Volatile var openFailure: Exception? = null

    @Volatile var gate: CountDownLatch? = null

    val entered = CountDownLatch(1)

    private val opens = AtomicInteger()

    val openCount: Int get() = opens.get()

    override fun open(): InputStream {
        opens.incrementAndGet()
        entered.countDown()
        gate?.let { check(it.await(10, TimeUnit.SECONDS)) { "gate was never released" } }
        openFailure?.let { throw it }
        return file.inputStream()
    }

    override fun length(): Long? = if (knownLength) file.length() else null
}
