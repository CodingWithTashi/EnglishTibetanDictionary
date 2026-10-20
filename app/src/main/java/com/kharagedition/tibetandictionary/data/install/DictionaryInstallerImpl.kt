package com.kharagedition.tibetandictionary.data.install

import android.content.Context
import com.kharagedition.tibetandictionary.core.di.IoDispatcher
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.data.local.DictionaryDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Puts the bundled `dictionary.db` in place before Room opens it, so the splash can show copy progress.
 *
 * The installer owns data upgrades: an on-device file whose header `user_version` differs from
 * [DictionaryDatabase.VERSION] (bumped on every data release) is deleted with its `-journal`/`-wal`/`-shm` siblings and
 * the asset is copied again. The copy goes to `dictionary.db.tmp`, is checked (SQLite magic, `user_version`, complete
 * length) and only then renamed over the target, so a failed or interrupted copy never leaves a half-written database.
 * Room then finds a file of the expected version and skips its own `createFromAsset` copy, which stays as the fallback
 * if the installer is ever bypassed. The installer must finish before anything else opens the database (start-up
 * runs it first): it deletes and replaces the file underneath.
 *
 * Opening checks `meta.data_version` (§5.1): a missing or blank value is a broken build and fails the install. When
 * opening or that check fails, the installed file is deleted so a retry copies the asset again. Room is not closed
 * (Room 2.8 forbids reusing a closed database), so if Room's own open had already succeeded, this process keeps
 * reading the deleted file and only the next launch reads the new copy; that case means the asset itself is bad.
 *
 * Progress: [InstallState.Copying] goes from 0 to 1, updated at most every [PROGRESS_STEP_BYTES]. When the asset's
 * length is unknown ([AssetSource.length] is null, an asset stored compressed) only 0 and then 1 are reported.
 *
 * The state never stays at [InstallState.Copying]: every failure, including cancelling the caller (a retry in a
 * finishing activity's `viewModelScope`), ends in [InstallState.Failed], which shows the retry screen. A retry keeps
 * the old [InstallState.Failed] until its first [InstallState.Copying], [InstallState.Ready] or [InstallState.Failed]
 * instead of going back to [InstallState.Checking], which the shell reads as "installer did not start".
 */
@Singleton
class DictionaryInstallerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val assetSource: AssetSource,
    private val dbProvider: Provider<DictionaryDatabase>,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val errors: ErrorReporter,
) : DictionaryInstaller {

    private val _state = MutableStateFlow<InstallState>(InstallState.Checking)
    override val state: StateFlow<InstallState> = _state.asStateFlow()

    private val mutex = Mutex()

    /**
     * Concurrent callers queue on [mutex]; a caller that finds [InstallState.Ready] returns at once. The state is not
     * reset here: it is [InstallState.Checking] on the first call and [InstallState.Failed] on a retry.
     */
    override suspend fun ensureInstalled() {
        mutex.withLock {
            if (_state.value is InstallState.Ready) return
            try {
                _state.value = withContext(io) { install() }
            } catch (t: Throwable) {
                // install() turns every failure into Failed, so this is the caller being cancelled. Leave Failed, not
                // the last Copying, so the shell shows the retry screen, then let the cancellation propagate.
                _state.value = InstallState.Failed(t)
                throw t
            }
        }
    }

    private suspend fun install(): InstallState = try {
        val target = context.getDatabasePath(DictionaryDatabase.DB_NAME)
        if (readUserVersion(target) != DictionaryDatabase.VERSION) {
            deleteDatabaseFiles(target)
            copyAsset(target)
        }
        InstallState.Ready(openAndCount(target))
    } catch (t: Throwable) {
        // A real cancellation propagates; a CancellationException while still active (Room can throw a stray
        // java.util.concurrent one) is an ordinary failure. Throwable, not Exception: the splash waits on this state.
        if (t is CancellationException) currentCoroutineContext().ensureActive()
        errors.record(t)
        InstallState.Failed(t)
    }

    /** The file's `user_version`, or null when it is missing, unreadable or not an SQLite database. */
    private fun readUserVersion(file: File): Int? = readHeader(file)?.userVersion

    private fun deleteDatabaseFiles(target: File) {
        for (suffix in DATABASE_FILE_SUFFIXES) {
            val file = File(target.path + suffix)
            if (file.exists() && !file.delete()) throw IOException("Could not delete ${file.name}")
        }
    }

    /** Streams the asset into a temp file next to [target], verifies it and renames it over [target]. */
    private suspend fun copyAsset(target: File) {
        val dir = checkNotNull(target.parentFile) { "No directory for $target" }
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create $dir")
        val temp = File(dir, target.name + TEMP_SUFFIX)
        try {
            val length = assetSource.length()?.takeIf { it > 0 }
            assetSource.open().use { input ->
                _state.value = InstallState.Copying(0f)
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var copied = 0L
                    var reported = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (length != null && copied - reported >= PROGRESS_STEP_BYTES) {
                            reported = copied
                            _state.value = InstallState.Copying((copied.toFloat() / length).coerceAtMost(1f))
                        }
                    }
                    output.fd.sync()
                }
            }
            _state.value = InstallState.Copying(1f)
            verifyHeader(temp, expectedLength = length)
            if (!temp.renameTo(target)) throw IOException("Could not rename ${temp.name} to ${target.name}")
        } finally {
            // No-op after a successful rename; otherwise removes the partial copy so a retry starts clean.
            temp.delete()
        }
    }

    /** Fails unless [file] is an SQLite database of [DictionaryDatabase.VERSION] that was copied in full. */
    private fun verifyHeader(file: File, expectedLength: Long?) {
        val header = readHeader(file) ?: throw IOException("Copied dictionary is not an SQLite database")
        if (header.userVersion != DictionaryDatabase.VERSION) {
            throw IOException(
                "Copied dictionary has user_version ${header.userVersion}, expected ${DictionaryDatabase.VERSION}",
            )
        }
        val actual = file.length()
        if (expectedLength != null && actual != expectedLength) {
            throw IOException("Copied $actual of $expectedLength bytes")
        }
        if (header.databaseSize != null && actual < header.databaseSize) {
            throw IOException("Copied dictionary is truncated: $actual of ${header.databaseSize} bytes")
        }
    }

    /**
     * Opens Room on the installed [target], checks `meta.data_version` and sums the visible directions' entry counts.
     * On failure (other than the caller being cancelled) it deletes [target] so a retry copies the asset again.
     */
    private suspend fun openAndCount(target: File): Int = try {
        val db = dbProvider.get()
        val dataVersion = db.metaDao().get(DATA_VERSION_KEY)
        check(!dataVersion.isNullOrBlank()) { "dictionary.db has no meta.$DATA_VERSION_KEY" }
        db.directionDao().observeVisible().first().sumOf { it.entryCount }
    } catch (t: Throwable) {
        if (t !is CancellationException || currentCoroutineContext().isActive) {
            try {
                deleteDatabaseFiles(target)
            } catch (e: IOException) {
                t.addSuppressed(e)
            }
        }
        throw t
    }

    /** The fields of the 100-byte SQLite header the installer checks; see https://sqlite.org/fileformat.html. */
    private class Header(val userVersion: Int, val databaseSize: Long?)

    /** Parses [file]'s header, or null when the file is missing, shorter than a header or lacks the SQLite magic. */
    private fun readHeader(file: File): Header? {
        if (!file.isFile) return null
        val bytes = ByteArray(HEADER_SIZE)
        try {
            RandomAccessFile(file, "r").use { it.readFully(bytes) }
        } catch (_: IOException) {
            return null
        }
        if (!bytes.copyOf(SQLITE_MAGIC.size).contentEquals(SQLITE_MAGIC)) return null
        val header = ByteBuffer.wrap(bytes) // big-endian
        val pageSize = (header.getShort(PAGE_SIZE_OFFSET).toInt() and 0xFFFF).let { if (it == 1) MAX_PAGE_SIZE else it }
        val pageCount = header.getInt(PAGE_COUNT_OFFSET).toLong() and 0xFFFF_FFFFL
        // The in-header page count is valid only when the change counter matches "version-valid-for".
        val sizeValid = pageCount > 0 && header.getInt(CHANGE_COUNTER_OFFSET) == header.getInt(VALID_FOR_OFFSET)
        return Header(
            userVersion = header.getInt(USER_VERSION_OFFSET),
            databaseSize = if (sizeValid) pageCount * pageSize else null,
        )
    }

    private companion object {
        const val TEMP_SUFFIX = ".tmp"
        const val DATA_VERSION_KEY = "data_version"
        val DATABASE_FILE_SUFFIXES = listOf("", "-journal", "-wal", "-shm")
        const val BUFFER_SIZE = 16 * 1024
        const val PROGRESS_STEP_BYTES = 64 * 1024

        const val HEADER_SIZE = 100
        val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        const val PAGE_SIZE_OFFSET = 16
        const val CHANGE_COUNTER_OFFSET = 24
        const val PAGE_COUNT_OFFSET = 28
        const val USER_VERSION_OFFSET = 60
        const val VALID_FOR_OFFSET = 92
        const val MAX_PAGE_SIZE = 65_536
    }
}
