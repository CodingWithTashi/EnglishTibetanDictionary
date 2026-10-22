package com.kharagedition.tibetandictionary.data.install

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.core.di.DatabaseModule
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabases
import com.kharagedition.tibetandictionary.data.local.direction
import com.kharagedition.tibetandictionary.data.local.entity.MetaEntity
import com.kharagedition.tibetandictionary.data.local.entry
import com.kharagedition.tibetandictionary.data.model.DictionaryPack
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryInstallerImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val target: File get() = context.getDatabasePath(DictionaryPack.TIBETAN.dbName)
    private val tmpCopy: File get() = File(target.path + ".tmp")
    private val errors = RecordingErrorReporter()
    private val openDbs = mutableListOf<DictionaryDatabase>()
    private lateinit var lastDatabases: DictionaryDatabases
    private val roomOpens = CopyOnWriteArrayList<DictionaryPack>()

    private fun fileOf(pack: DictionaryPack): File = context.getDatabasePath(pack.dbName)

    /** A valid pack database written by Room (so its user_version is [DictionaryDatabase.VERSION]): 3 entries. */
    private lateinit var goodAsset: File

    @Before
    fun setUp() {
        goodAsset = buildFixtureAsset()
    }

    @After
    fun tearDown() {
        openDbs.forEach { it.close() }
    }

    @Test
    fun freshInstallCopiesWithProgressAndBecomesReady() = runTest {
        // Progress is reported every 64 KB, so the fixture must span more than one step.
        assertThat(goodAsset.length()).isGreaterThan(64L * 1024)
        val installer = installer(FakeAssetSource(goodAsset), StandardTestDispatcher(testScheduler))

        installer.state.test(timeout = 10.seconds) {
            assertThat(awaitItem()).isEqualTo(InstallState.Checking)
            launch { installer.ensureInstalled(DictionaryPack.TIBETAN) }

            val states = awaitSettled()
            val progress = states.filterIsInstance<InstallState.Copying>().map { it.progress }
            assertThat(progress.first()).isEqualTo(0f)
            assertThat(progress.last()).isEqualTo(1f)
            assertThat(progress.size).isAtLeast(3)
            assertThat(progress).isInStrictOrder()
            assertThat(states.last()).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
        }
        assertThat(userVersion(target)).isEqualTo(DictionaryDatabase.VERSION)
        assertThat(tmpCopy.exists()).isFalse()
        assertThat(errors.recorded).isEmpty()
    }

    @Test
    fun unknownLengthWithoutARecordedSizeReportsOnlyStartAndCompletion() = runTest {
        val installer = installer(FakeAssetSource(goodAsset, knownLength = false), StandardTestDispatcher(testScheduler))

        installer.state.test(timeout = 10.seconds) {
            assertThat(awaitItem()).isEqualTo(InstallState.Checking)
            launch { installer.ensureInstalled(DictionaryPack.TIBETAN) }

            assertThat(awaitSettled()).containsExactly(
                InstallState.Copying(DictionaryPack.TIBETAN, 0f),
                InstallState.Copying(DictionaryPack.TIBETAN, 1f),
                InstallState.Ready(DictionaryPack.TIBETAN, 3),
            ).inOrder()
        }
    }

    @Test
    fun aRecordedSizeOfZeroCountsAsUnknown() = runTest {
        // The recorded asset size of 0 means "unknown": no intermediate progress, and the install still completes.
        val asset = FakeAssetSource(goodAsset, knownLength = false)
        val installer = installer(asset, StandardTestDispatcher(testScheduler), AssetSizeProvider { 0L })

        installer.state.test(timeout = 10.seconds) {
            assertThat(awaitItem()).isEqualTo(InstallState.Checking)
            launch { installer.ensureInstalled(DictionaryPack.TIBETAN) }

            assertThat(awaitSettled()).containsExactly(
                InstallState.Copying(DictionaryPack.TIBETAN, 0f),
                InstallState.Copying(DictionaryPack.TIBETAN, 1f),
                InstallState.Ready(DictionaryPack.TIBETAN, 3),
            ).inOrder()
        }
    }

    @Test
    fun unknownLengthUsesTheRecordedSizeForProgress() = runTest {
        // The asset ships compressed, so openFd cannot size it; the build recorded its size in dictionary_stats_bo.xml.
        val asset = FakeAssetSource(goodAsset, knownLength = false)
        val recorded = AssetSizeProvider { goodAsset.length() }
        val installer = installer(asset, StandardTestDispatcher(testScheduler), recorded)

        installer.state.test(timeout = 10.seconds) {
            assertThat(awaitItem()).isEqualTo(InstallState.Checking)
            launch { installer.ensureInstalled(DictionaryPack.TIBETAN) }

            val states = awaitSettled()
            val progress = states.filterIsInstance<InstallState.Copying>().map { it.progress }
            assertThat(progress.first()).isEqualTo(0f)
            assertThat(progress.last()).isEqualTo(1f)
            assertThat(progress.size).isAtLeast(3)
            assertThat(progress).isInStrictOrder()
            assertThat(states.last()).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
        }
        assertThat(errors.recorded).isEmpty()
    }

    @Test
    fun aRecordedSizeThatIsOffIsReportedButTheInstallSucceeds() = runTest {
        // A stale recorded asset size only skews the progress; the SQLite header check guards the copy.
        for (recordedBytes in listOf(goodAsset.length() - 3, goodAsset.length() + 5)) {
            closeOpenDbs()
            context.deleteDatabase(DictionaryPack.TIBETAN.dbName)
            errors.recorded.clear()
            val asset = FakeAssetSource(goodAsset, knownLength = false)
            val installer = installer(asset, StandardTestDispatcher(testScheduler), AssetSizeProvider { recordedBytes })

            installer.ensureInstalled(DictionaryPack.TIBETAN)

            assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
            assertThat(target.length()).isEqualTo(goodAsset.length())
            assertThat(errors.recorded.map { it.message }).containsExactly(
                "dictionary_asset_bytes_bo is $recordedBytes but the asset has ${goodAsset.length()} bytes",
            )
        }
    }

    @Test
    fun aTruncatedCopyFailsOnTheHeaderCheckWhenTheLengthIsOnlyRecorded() = runTest {
        val truncated = tmp.newFile("truncated-recorded.db").apply {
            writeBytes(goodAsset.readBytes().copyOf(goodAsset.length().toInt() / 2))
        }
        val asset = FakeAssetSource(truncated, knownLength = false)
        val recorded = AssetSizeProvider { goodAsset.length() } // the build recorded the full size
        val installer = installer(asset, StandardTestDispatcher(testScheduler), recorded)

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        val state = installer.state.value
        assertThat(state).isInstanceOf(InstallState.Failed::class.java)
        assertThat((state as InstallState.Failed).cause).hasMessageThat().startsWith("Copied dictionary is truncated")
        assertThat(target.exists()).isFalse()
        assertThat(tmpCopy.exists()).isFalse()
    }

    @Test
    fun aKnownDescriptorLengthWinsOverTheRecordedSize() = runTest {
        val installer =
            installer(FakeAssetSource(goodAsset), StandardTestDispatcher(testScheduler), AssetSizeProvider { 1L })

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
    }

    @Test
    fun matchingVersionSkipsCopy() = runTest {
        val asset = FakeAssetSource(goodAsset)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = installer(asset, dispatcher)
        first.ensureInstalled(DictionaryPack.TIBETAN)
        assertThat(first.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))

        // Calling again on the same instance is a no-op.
        first.ensureInstalled(DictionaryPack.TIBETAN)
        assertThat(asset.openCount).isEqualTo(1)

        // Next launch: a new installer finds the matching file and goes straight to Ready.
        closeOpenDbs()
        val second = installer(asset, dispatcher)
        second.state.test(timeout = 10.seconds) {
            assertThat(awaitItem()).isEqualTo(InstallState.Checking)
            launch { second.ensureInstalled(DictionaryPack.TIBETAN) }

            assertThat(awaitSettled()).containsExactly(InstallState.Ready(DictionaryPack.TIBETAN, 3))
        }
        assertThat(asset.openCount).isEqualTo(1)
    }

    @Test
    fun staleVersionIsDeletedAndRecopied() = runTest {
        checkNotNull(target.parentFile).mkdirs()
        withUserVersion(goodAsset, DictionaryDatabase.VERSION + 1).copyTo(target)
        val siblings = listOf("-journal", "-wal", "-shm").map { File(target.path + it) }
        siblings.forEach { it.writeText("stale") }
        val asset = FakeAssetSource(goodAsset)
        var siblingsLeftWhenRoomOpened: List<String>? = null
        val installer = installer(asset, StandardTestDispatcher(testScheduler)) {
            siblingsLeftWhenRoomOpened = siblings.filter { it.exists() }.map { it.name }
        }

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
        assertThat(asset.openCount).isEqualTo(1)
        assertThat(siblingsLeftWhenRoomOpened).isEmpty()
        assertThat(userVersion(target)).isEqualTo(DictionaryDatabase.VERSION)
    }

    @Test
    fun readyActivatesTheTibetanPackAndAFailedInstallActivatesNothing() = runTest {
        val truncated = tmp.newFile("truncated.db").apply {
            writeBytes(goodAsset.readBytes().copyOf(goodAsset.length().toInt() / 2))
        }
        val asset = FakeAssetSource(truncated)
        val installer = installer(asset, StandardTestDispatcher(testScheduler))

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        assertThat(installer.state.value).isInstanceOf(InstallState.Failed::class.java)
        assertThat(lastDatabases.active.value).isNull()

        asset.file = goodAsset
        installer.ensureInstalled(DictionaryPack.TIBETAN)

        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
        assertThat(lastDatabases.active.value).isEqualTo(DictionaryPack.TIBETAN)
    }

    @Test
    fun corruptAssetFailsThenRetrySucceeds() = runTest {
        val truncated = tmp.newFile("truncated.db").apply {
            writeBytes(goodAsset.readBytes().copyOf(goodAsset.length().toInt() / 2))
        }
        val asset = FakeAssetSource(truncated)
        val installer = installer(asset, StandardTestDispatcher(testScheduler))

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        val failed = installer.state.value
        assertThat(failed).isInstanceOf(InstallState.Failed::class.java)
        assertThat(errors.recorded).containsExactly((failed as InstallState.Failed).cause)
        assertThat(tmpCopy.exists()).isFalse()
        assertThat(target.exists()).isFalse()

        asset.file = goodAsset
        installer.state.test(timeout = 10.seconds) {
            assertThat(awaitItem()).isEqualTo(failed)
            launch { installer.ensureInstalled(DictionaryPack.TIBETAN) }

            // A retry keeps Failed until the copy starts: no Checking in between (the shell would show it as
            // "installer did not start").
            val states = awaitSettled()
            assertThat(states.first()).isEqualTo(InstallState.Copying(DictionaryPack.TIBETAN, 0f))
            assertThat(states).doesNotContain(InstallState.Checking)
            assertThat(states.last()).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
        }
        assertThat(tmpCopy.exists()).isFalse()
    }

    @Test
    fun midStreamReadFailureFailsThenRetrySucceeds() = runTest {
        val asset = FakeAssetSource(goodAsset).apply { failAfterBytes = 1 } // throws on the read after the first chunk
        val installer = installer(asset, StandardTestDispatcher(testScheduler))

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        val state = installer.state.value
        assertThat(state).isInstanceOf(InstallState.Failed::class.java)
        val cause = (state as InstallState.Failed).cause
        assertThat(cause).hasMessageThat().contains("asset read failed")
        assertThat(errors.recorded).containsExactly(cause)
        assertThat(tmpCopy.exists()).isFalse()
        assertThat(target.exists()).isFalse()

        asset.failAfterBytes = null
        installer.ensureInstalled(DictionaryPack.TIBETAN)
        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
        assertThat(asset.openCount).isEqualTo(2)
    }

    @OptIn(ExperimentalCoroutinesApi::class) // runCurrent
    @Test
    fun cancelledInstallBecomesFailedAndCleansUp() = runTest {
        val asset = FakeAssetSource(goodAsset).apply { pauseAfterBytes = 1 } // waits on the read after the first chunk
        val installer = installer(asset, Dispatchers.IO)

        val job = launch { installer.ensureInstalled(DictionaryPack.TIBETAN) }
        runCurrent()
        assertThat(asset.midStream.await(10, TimeUnit.SECONDS)).isTrue()
        assertThat(installer.state.value).isInstanceOf(InstallState.Copying::class.java)
        assertThat(tmpCopy.exists()).isTrue()

        // E.g. a retry in the viewModelScope of an activity that finishes during the copy.
        job.cancel()
        asset.resume.countDown()
        job.join()

        val state = installer.state.value
        assertThat(state).isInstanceOf(InstallState.Failed::class.java)
        assertThat((state as InstallState.Failed).cause).isInstanceOf(CancellationException::class.java)
        assertThat(tmpCopy.exists()).isFalse()
        assertThat(target.exists()).isFalse()
        assertThat(errors.recorded).isEmpty()

        // The retry screen's "Try again" then installs normally.
        installer.ensureInstalled(DictionaryPack.TIBETAN)
        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
    }

    @Test
    fun strayCancellationExceptionIsAFailure() = runTest {
        // Room can throw a java.util.concurrent.CancellationException while the caller is still active.
        val stray = CancellationException("stray")
        val installer = installer(FakeAssetSource(goodAsset), StandardTestDispatcher(testScheduler)) { throw stray }

        installer.ensureInstalled(DictionaryPack.TIBETAN) // returns normally: the caller was not cancelled

        assertThat(installer.state.value).isEqualTo(InstallState.Failed(DictionaryPack.TIBETAN, stray))
        assertThat(errors.recorded).containsExactly(stray)
    }

    @Test
    fun declaredLengthMismatchFails() = runTest {
        val asset = FakeAssetSource(goodAsset).apply { declaredLength = goodAsset.length() + 1 }
        val installer = installer(asset, StandardTestDispatcher(testScheduler))

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        val state = installer.state.value
        assertThat(state).isInstanceOf(InstallState.Failed::class.java)
        assertThat((state as InstallState.Failed).cause).hasMessageThat()
            .isEqualTo("Copied ${goodAsset.length()} of ${goodAsset.length() + 1} bytes")
        assertThat(target.exists()).isFalse()
        assertThat(tmpCopy.exists()).isFalse()
    }

    @Test
    fun roomOpenFailureDeletesTheCopySoRetryRecopies() = runTest {
        val boom = IOException("disk I/O error")
        var failOpen = true
        val asset = FakeAssetSource(goodAsset)
        val installer = installer(asset, StandardTestDispatcher(testScheduler)) { if (failOpen) throw boom }

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        assertThat(installer.state.value).isEqualTo(InstallState.Failed(DictionaryPack.TIBETAN, boom))
        assertThat(errors.recorded).containsExactly(boom)
        val leftovers = listOf("", "-journal", "-wal", "-shm", ".tmp").map { File(target.path + it) }
        assertThat(leftovers.filter { it.exists() }).isEmpty()

        failOpen = false
        installer.ensureInstalled(DictionaryPack.TIBETAN)
        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
        assertThat(asset.openCount).isEqualTo(2)
    }

    @Test
    fun blankDataVersionFailsAndDiscardsTheCopy() = runTest {
        val asset = FakeAssetSource(buildFixtureAsset("blank-version.db", dataVersion = " "))
        val installer = installer(asset, StandardTestDispatcher(testScheduler))

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        val state = installer.state.value
        assertThat(state).isInstanceOf(InstallState.Failed::class.java)
        val cause = (state as InstallState.Failed).cause
        assertThat(cause).isInstanceOf(IllegalStateException::class.java)
        assertThat(cause).hasMessageThat().contains("data_version")
        assertThat(errors.recorded).containsExactly(cause)
        assertThat(target.exists()).isFalse()
        assertThat(tmpCopy.exists()).isFalse()
    }

    @Test
    fun assetWithWrongVersionFails() = runTest {
        val stale = withUserVersion(goodAsset, DictionaryDatabase.VERSION + 1)
        val installer = installer(FakeAssetSource(stale), StandardTestDispatcher(testScheduler))

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        val state = installer.state.value
        assertThat(state).isInstanceOf(InstallState.Failed::class.java)
        assertThat((state as InstallState.Failed).cause).hasMessageThat().contains("user_version")
        assertThat(target.exists()).isFalse()
        assertThat(tmpCopy.exists()).isFalse()
    }

    @Test
    fun openFailureYieldsFailed() = runTest {
        val boom = IOException("asset missing")
        val asset = FakeAssetSource(goodAsset).apply { openFailure = boom }
        val installer = installer(asset, StandardTestDispatcher(testScheduler))

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        val state = installer.state.value
        assertThat(state).isInstanceOf(InstallState.Failed::class.java)
        val cause = (state as InstallState.Failed).cause
        assertThat(cause).isInstanceOf(IOException::class.java)
        assertThat(cause).hasMessageThat().isEqualTo("asset missing")
        assertThat(errors.recorded).containsExactly(cause)
        assertThat(target.exists()).isFalse()
        assertThat(tmpCopy.exists()).isFalse()

        asset.openFailure = null
        installer.ensureInstalled(DictionaryPack.TIBETAN)
        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
    }

    @Test
    fun installsTheRequestedPackOnly() = runTest {
        val asset = FakeAssetSource(mapOf(DictionaryPack.DZONGKHA to goodAsset))
        val installer = installer(asset, StandardTestDispatcher(testScheduler))

        installer.ensureInstalled(DictionaryPack.DZONGKHA)

        assertThat(fileOf(DictionaryPack.DZONGKHA).exists()).isTrue()
        assertThat(fileOf(DictionaryPack.TIBETAN).exists()).isFalse()
        assertThat(lastDatabases.active.value).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.DZONGKHA, 3))
        assertThat(asset.openedPacks).containsExactly(DictionaryPack.DZONGKHA)
    }

    @Test
    fun secondPackInstallsNextToTheFirst() = runTest {
        val asset = FakeAssetSource(mapOf(DictionaryPack.TIBETAN to goodAsset, DictionaryPack.DZONGKHA to goodAsset))
        val installer = installer(asset, StandardTestDispatcher(testScheduler))
        installer.ensureInstalled(DictionaryPack.TIBETAN)
        val bo = fileOf(DictionaryPack.TIBETAN)
        val (modified, length) = bo.lastModified() to bo.length()

        installer.ensureInstalled(DictionaryPack.DZONGKHA)

        assertThat(fileOf(DictionaryPack.DZONGKHA).exists()).isTrue()
        assertThat(lastDatabases.active.value).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(bo.lastModified()).isEqualTo(modified)
        assertThat(bo.length()).isEqualTo(length)
        assertThat(asset.openedPacks).containsExactly(DictionaryPack.TIBETAN, DictionaryPack.DZONGKHA).inOrder()
    }

    @Test
    fun failedCopyOfSecondPackLeavesFirstPackInstalled() = runTest {
        val asset = FakeAssetSource(mapOf(DictionaryPack.TIBETAN to goodAsset, DictionaryPack.DZONGKHA to goodAsset))
        val installer = installer(asset, StandardTestDispatcher(testScheduler))
        installer.ensureInstalled(DictionaryPack.TIBETAN)
        val bo = fileOf(DictionaryPack.TIBETAN)
        val (modified, length) = bo.lastModified() to bo.length()
        val boDb = lastDatabases.get(DictionaryPack.TIBETAN)
        asset.failingPack = DictionaryPack.DZONGKHA
        asset.failAfterBytes = 100L * 1024

        installer.ensureInstalled(DictionaryPack.DZONGKHA)

        val state = installer.state.value
        assertThat(state).isInstanceOf(InstallState.Failed::class.java)
        assertThat((state as InstallState.Failed).pack).isEqualTo(DictionaryPack.DZONGKHA)
        assertThat(lastDatabases.active.value).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(lastDatabases.get(DictionaryPack.TIBETAN)).isSameInstanceAs(boDb)
        assertThat(bo.lastModified()).isEqualTo(modified)
        assertThat(bo.length()).isEqualTo(length)
        assertThat(fileOf(DictionaryPack.DZONGKHA).exists()).isFalse()
        assertThat(File(fileOf(DictionaryPack.DZONGKHA).path + ".tmp").exists()).isFalse()
    }

    @Test
    fun alreadyActivePackReturnsWithoutReopening() = runTest {
        val asset = FakeAssetSource(goodAsset)
        val installer = installer(asset, StandardTestDispatcher(testScheduler))
        installer.ensureInstalled(DictionaryPack.TIBETAN)
        val opensAfterFirst = roomOpens.size
        val (modified, length) = target.lastModified() to target.length()

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        assertThat(roomOpens).hasSize(opensAfterFirst)
        assertThat(asset.openCount).isEqualTo(1)
        assertThat(target.lastModified()).isEqualTo(modified)
        assertThat(target.length()).isEqualTo(length)
        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
    }

    @Test
    fun retryOfTheActivePackAfterAFailedOtherPackKeepsTheLiveFile() = runTest {
        val asset = FakeAssetSource(mapOf(DictionaryPack.TIBETAN to goodAsset, DictionaryPack.DZONGKHA to goodAsset))
        val installer = installer(asset, StandardTestDispatcher(testScheduler))
        installer.ensureInstalled(DictionaryPack.TIBETAN)
        asset.failingPack = DictionaryPack.DZONGKHA
        asset.failAfterBytes = 1
        installer.ensureInstalled(DictionaryPack.DZONGKHA)
        assertThat(installer.state.value).isInstanceOf(InstallState.Failed::class.java)
        val opens = roomOpens.size

        installer.ensureInstalled(DictionaryPack.TIBETAN) // "keep current"

        assertThat(roomOpens).hasSize(opens)
        assertThat(target.exists()).isTrue()
        assertThat(lastDatabases.active.value).isEqualTo(DictionaryPack.TIBETAN)
        assertThat(asset.openedPacks).containsExactly(DictionaryPack.TIBETAN, DictionaryPack.DZONGKHA).inOrder()
    }

    @Test
    fun firstRunSweepsLegacyAndTempFiles() = runTest {
        val dir = checkNotNull(target.parentFile).also { it.mkdirs() }
        val leftovers = listOf("dictionary.db", "dictionary.db-journal", "dictionary-dz.db.tmp", "dictionary-bo.db.tmp")
            .map { File(dir, it).apply { writeText("old") } }
        val installer = installer(FakeAssetSource(goodAsset), StandardTestDispatcher(testScheduler))

        installer.ensureInstalled(DictionaryPack.TIBETAN)

        assertThat(leftovers.filter { it.exists() }).isEmpty()
        assertThat(target.exists()).isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class) // runCurrent
    @Test
    fun concurrentCallsRunOnce() = runTest {
        val asset = FakeAssetSource(goodAsset).apply { gate = CountDownLatch(1) }
        val installer = installer(asset, Dispatchers.IO)

        val first = launch { installer.ensureInstalled(DictionaryPack.TIBETAN) }
        runCurrent()
        assertThat(asset.entered.await(10, TimeUnit.SECONDS)).isTrue()
        val second = launch { installer.ensureInstalled(DictionaryPack.TIBETAN) }
        runCurrent()
        assertThat(second.isActive).isTrue()

        checkNotNull(asset.gate).countDown()
        joinAll(first, second)

        assertThat(asset.openCount).isEqualTo(1)
        assertThat(installer.state.value).isEqualTo(InstallState.Ready(DictionaryPack.TIBETAN, 3))
    }

    private fun installer(
        asset: AssetSource,
        io: CoroutineDispatcher,
        recordedSize: AssetSizeProvider = AssetSizeProvider { null },
        beforeRoomOpens: () -> Unit = {},
    ): DictionaryInstallerImpl {
        // The production builder (createFromAsset + destructive fallback): there is no asset under Robolectric, so a
        // test passes only if Room finds the installed file and skips its own copy.
        val production = DatabaseModule.provideDictionaryDatabases(context)
        lastDatabases = DictionaryDatabases { pack ->
            roomOpens += pack
            beforeRoomOpens()
            production.get(pack).also { openDbs += it }
        }
        return DictionaryInstallerImpl(context, asset, recordedSize, lastDatabases, io, errors)
    }

    private fun closeOpenDbs() {
        openDbs.forEach { it.close() }
        openDbs.clear()
    }

    /** Collects states up to and including the first [InstallState.Ready] or [InstallState.Failed]. */
    private suspend fun ReceiveTurbine<InstallState>.awaitSettled(): List<InstallState> {
        val states = mutableListOf<InstallState>()
        while (true) {
            val state = awaitItem()
            states += state
            if (state is InstallState.Ready || state is InstallState.Failed) return states
        }
    }

    /** Writes the fixture database with Room and returns a copy of it named [assetName]; null [dataVersion] omits it. */
    private fun buildFixtureAsset(assetName: String = "asset.db", dataVersion: String? = "2026.10.1"): File {
        val name = "fixture.db"
        val db = Room.databaseBuilder(context, DictionaryDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        runBlocking {
            val write = db.testWriteDao()
            write.insertDirections(
                listOf(
                    direction("bo-en", position = 0, entryCount = 2),
                    direction("bo-bo", position = 1, entryCount = 0),
                    direction("en-bo", position = 2, sourceScript = "latn", entryCount = 1),
                ),
            )
            write.insertEntries(
                listOf(
                    entry(1_000_001, "bo-en", "བཀྲ་ཤིས་", sortOrder = 1),
                    entry(1_000_002, "bo-en", "སྙིང་རྗེ་", sortOrder = 2),
                    entry(3_000_001, "en-bo", "luck", sortOrder = 1),
                ),
            )
            write.insertMeta(
                listOfNotNull(dataVersion?.let { MetaEntity("data_version", it) }, MetaEntity("schema_version", "1")),
            )
        }
        db.close()
        val asset = File(tmp.root, assetName)
        context.getDatabasePath(name).copyTo(asset)
        context.deleteDatabase(name)
        return asset
    }

    /** A copy of [source] whose header claims `user_version = [version]`. */
    private fun withUserVersion(source: File, version: Int): File {
        val copy = File(tmp.root, "v$version-${System.nanoTime()}.db")
        source.copyTo(copy)
        RandomAccessFile(copy, "rw").use {
            it.seek(USER_VERSION_OFFSET)
            it.writeInt(version)
        }
        return copy
    }

    private fun userVersion(file: File): Int = RandomAccessFile(file, "r").use {
        it.seek(USER_VERSION_OFFSET)
        it.readInt()
    }

    private class RecordingErrorReporter : ErrorReporter {
        val recorded = CopyOnWriteArrayList<Throwable>()

        override fun record(t: Throwable) {
            recorded += t
        }
    }

    private companion object {
        const val USER_VERSION_OFFSET = 60L
    }
}
