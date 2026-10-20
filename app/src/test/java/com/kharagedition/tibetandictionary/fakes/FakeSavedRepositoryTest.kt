package com.kharagedition.tibetandictionary.fakes

import com.google.common.truth.Truth.assertThat
import com.kharagedition.tibetandictionary.SampleData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Pins the parts of [FakeSavedRepository] that must keep matching `SavedRepositoryImpl`. */
class FakeSavedRepositoryTest {

    private val repo = FakeSavedRepository()

    @Test
    fun folderNamesAreTrimmed() = runTest {
        val id = repo.createFolder("  Verbs ")
        repo.renameFolder(id, " Actions  ")

        assertThat(repo.observeFolders().first().map { it.name }).containsExactly("Actions")
    }

    @Test
    fun blankFolderNamesAreRejected() = runTest {
        val id = repo.createFolder("Verbs")

        assertThat(failureOf { repo.createFolder("  ") }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(failureOf { repo.renameFolder(id, "") }).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun duplicateFolderNamesAreRejectedIgnoringCaseAndSpaces() = runTest {
        val verbs = repo.createFolder("Verbs")
        repo.createFolder("Nouns")

        assertThat(failureOf { repo.createFolder(" VERBS ") }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(failureOf { repo.renameFolder(verbs, "nouns") }).isInstanceOf(IllegalArgumentException::class.java)
        repo.renameFolder(verbs, "VERBS")
        assertThat(repo.observeFolders().first().map { it.name }).containsExactly("VERBS", "Nouns").inOrder()
    }

    @Test
    fun saveAndSetFolderWithAnUnknownFolderIdFileTheEntryAsUnfiled() = runTest {
        val verbs = repo.createFolder("Verbs")
        repo.deleteFolder(verbs)

        repo.save(SampleData.tashi, folderId = verbs)
        repo.save(SampleData.nyingje, folderId = repo.createFolder("Nouns"))
        repo.setFolder(SampleData.nyingje.id, verbs)

        assertThat(repo.observeFolder(null).first().map { it.entryId })
            .containsExactly(SampleData.tashi.id, SampleData.nyingje.id)
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()
}
