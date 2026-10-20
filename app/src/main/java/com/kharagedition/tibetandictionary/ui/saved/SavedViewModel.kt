package com.kharagedition.tibetandictionary.ui.saved

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.core.time.AppClock
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import com.kharagedition.tibetandictionary.data.repository.WordOfTheDayRepository
import com.kharagedition.tibetandictionary.domain.SavedGrouping
import com.kharagedition.tibetandictionary.ui.model.FolderPickerUi
import com.kharagedition.tibetandictionary.ui.model.FolderUi
import com.kharagedition.tibetandictionary.ui.model.SavedWordActions
import com.kharagedition.tibetandictionary.ui.model.SavedWordActions.Companion.savedMessage
import com.kharagedition.tibetandictionary.ui.model.UiText
import com.kharagedition.tibetandictionary.ui.model.WordOfTheDayUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Saved tab (1i/3c): every saved word, filtered by folder and by the search-within query, in sections by when
 * it was saved; today's word for the empty state. Rows come from the snapshots in user.db, so a word whose entry a
 * newer dictionary dropped still shows, and opening it explains instead of navigating. Changes go through
 * [SavedWordActions] (one at a time); a failure is recorded through [errors] and shown as a generic error toast.
 * The typed query and the chosen folder live in [savedState], so they survive process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val saved: SavedRepository,
    private val saveActions: SavedWordActions,
    private val dictionary: DictionaryRepository,
    private val wordOfTheDay: WordOfTheDayRepository,
    private val clock: AppClock,
    private val errors: ErrorReporter,
) : ViewModel() {

    /** The filter in effect: [query] (trimmed) only while the search field shows, [folderId] only while it exists. */
    private data class Filter(val query: String, val folderId: Long?)

    /** The open sheet's own state; folders and the word's current folder come from the repository. */
    private sealed interface Sheet {
        data class NewFolder(val name: String = "", val error: UiText? = null) : Sheet

        /** The folder picker for the saved word [entryId], with its optional new-folder field. */
        data class Picker(
            val entryId: Long,
            val creating: Boolean = false,
            val name: String = "",
            val error: UiText? = null,
        ) : Sheet
    }

    private val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")
    private val selectedFolderId: StateFlow<Long?> = savedState.getStateFlow(KEY_FOLDER, null)
    private val sheet = MutableStateFlow<Sheet?>(null)

    /** Words removed while the tab is open, so Undo can save them again. */
    private val removed = mutableMapOf<Long, SavedEntry>()

    private val effectChannel = Channel<SavedEffect>(Channel.BUFFERED)
    val effects: Flow<SavedEffect> = effectChannel.receiveAsFlow()

    private val folders = saved.observeFolders()

    private val filter: Flow<Filter> = combine(query, selectedFolderId, saved.observeCount(), folders) {
            typed, folderId, count, folders ->
        Filter(
            query = if (count >= SEARCH_MIN_COUNT) typed.trim() else "",
            folderId = folderId?.takeIf { id -> folders.any { it.id == id } },
        )
    }.distinctUntilChanged()

    /** The words that pass the filter, newest first, with the filter they passed. */
    private val visible: Flow<Pair<Filter, List<SavedEntry>>> =
        filter.flatMapLatest { filter -> rows(filter).map { filter to it } }

    /**
     * Today's word (for the day the tab is shown) and whether it is saved. It is null until read, so the list never
     * waits for the dictionary, and when reading fails (recorded).
     */
    private val wotd: Flow<WordOfTheDayUi?> = combine(
        flow { emitAll(wordOfTheDay.observe(clock.today())) }
            .onStart { emit(null) }
            .catch { t ->
                errors.record(t)
                emit(null)
            },
        saved.observeSavedIds(),
    ) { entry, savedIds -> entry?.toWotdUi(clock.today(), isSaved = entry.summary.id in savedIds) }

    private val typedAndSheet = combine(query, sheet) { typed, sheet -> typed to sheet }

    val uiState: StateFlow<SavedUiState> = combine(
        saved.observeAll(),
        folders,
        visible,
        wotd,
        typedAndSheet,
    ) { all, folders, (filter, rows), wotd, (typed, sheet) ->
        val folderUis = folders.map { FolderUi(it.id, it.name, it.count) }
        SavedUiState(
            loading = false,
            totalCount = all.size,
            folders = folderUis,
            selectedFolderId = filter.folderId,
            query = typed,
            groups = SavedGrouping.group(rows, clock.today(), clock.zone()).map { it.toUi() },
            wotd = wotd,
            folderPicker = (sheet as? Sheet.Picker)?.let { picker ->
                FolderPickerUi(
                    folders = folderUis,
                    selectedId = all.firstOrNull { it.entryId == picker.entryId }?.folderId,
                    creating = picker.creating,
                    newFolderName = picker.name,
                    createError = picker.error,
                )
            },
            newFolder = (sheet as? Sheet.NewFolder)?.let { NewFolderUi(it.name, it.error) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SavedUiState())

    fun onEvent(event: SavedEvent) {
        when (event) {
            is SavedEvent.QueryChanged -> savedState[KEY_QUERY] = event.query
            is SavedEvent.SelectFolder -> savedState[KEY_FOLDER] = event.folderId
            SavedEvent.StartAddFolder -> sheet.value = Sheet.NewFolder()
            is SavedEvent.NewFolderNameChanged -> sheet.update { it?.withName(event.name) }
            is SavedEvent.AddFolder -> launchGuarded { addFolder(event.name) }
            SavedEvent.DismissAddFolder -> sheet.update { if (it is Sheet.NewFolder) null else it }
            is SavedEvent.Unsave -> launchGuarded { unsave(event.entryId) }
            is SavedEvent.UndoUnsave -> launchGuarded { undoUnsave(event.entryId, event.folderId) }
            is SavedEvent.OpenEntry -> viewModelScope.launch { openEntry(event.entryId) }
            SavedEvent.SaveWotd -> launchGuarded { saveWotd() }
            is SavedEvent.ChangeFolder -> launchGuarded { openPicker(event.entryId) }
            is SavedEvent.PickFolder -> withPicker { pickFolder(it.entryId, event.folderId) }
            SavedEvent.StartCreateFolder -> updatePicker { it.copy(creating = true, name = "", error = null) }
            SavedEvent.CancelCreateFolder -> updatePicker { it.copy(creating = false, name = "", error = null) }
            is SavedEvent.CreateFolder -> withPicker { createFolderFor(it.entryId, event.name) }
            SavedEvent.DismissFolderPicker -> sheet.update { if (it is Sheet.Picker) null else it }
        }
    }

    private fun rows(filter: Filter): Flow<List<SavedEntry>> = when {
        filter.query.isNotEmpty() -> saved.search(filter.query).map { rows ->
            if (filter.folderId == null) rows else rows.filter { it.folderId == filter.folderId }
        }
        filter.folderId != null -> saved.observeFolder(filter.folderId)
        else -> saved.observeAll()
    }

    private suspend fun addFolder(name: String) {
        when (val created = saveActions.createFolder(name)) {
            // A blank name.
            null -> Unit
            is SavedWordActions.NewFolder.Rejected ->
                sheet.update { if (it is Sheet.NewFolder) it.copy(error = created.error) else it }
            is SavedWordActions.NewFolder.Created -> sheet.update { if (it is Sheet.NewFolder) null else it }
        }
    }

    private suspend fun unsave(entryId: Long) {
        // Null when the word is not saved (any more), or a first tap is still removing it.
        val row = saveActions.unsave(entryId) ?: return
        removed[entryId] = row
        effectChannel.send(SavedEffect.Removed(entryId, row.folderId))
    }

    /** Puts the removed row back as it was (note and save time kept), in [folderId]. */
    private suspend fun undoUnsave(entryId: Long, folderId: Long?) {
        val row = removed.remove(entryId) ?: return
        saveActions.restore(row.copy(folderId = folderId))
    }

    /**
     * Review Focus 4: an entry a newer dictionary dropped toasts instead of opening; a failed lookup is recorded and
     * shows the generic error.
     */
    private suspend fun openEntry(entryId: Long) {
        val found = recorded { dictionary.summaries(listOf(entryId)).isNotEmpty() }
        val effect = when (found) {
            true -> SavedEffect.NavigateEntry(entryId)
            false -> SavedEffect.Toast(UiText(R.string.entry_missing))
            null -> SavedEffect.Toast(UiText(R.string.entry_action_failed))
        }
        effectChannel.send(effect)
    }

    /**
     * Saves today's word to the last-used folder (none if it was deleted), as the entry screen does; nothing happens
     * when it is already saved, or while a first tap is still saving it.
     */
    private suspend fun saveWotd() {
        val summary = wordOfTheDay.forDate(clock.today())?.summary ?: return
        val result = saveActions.saveIfAbsent(summary) ?: return
        effectChannel.send(
            SavedEffect.Toast(
                savedMessage(summary.headword, result.folder?.name),
                R.string.change,
                SavedEvent.ChangeFolder(summary.id),
            ),
        )
    }

    private suspend fun openPicker(entryId: Long) {
        if (saved.observeIsSaved(entryId).first()) sheet.value = Sheet.Picker(entryId)
    }

    /** Files the saved word [entryId] in [folderId]; a word no longer saved only closes the picker. */
    private suspend fun pickFolder(entryId: Long, folderId: Long?) {
        val filed = saveActions.fileIn(entryId, folderId)
        sheet.update { if (it is Sheet.Picker) null else it }
        if (filed == null) return
        effectChannel.send(SavedEffect.Toast(savedMessage(filed.headword, filed.folderName)))
    }

    private suspend fun createFolderFor(entryId: Long, name: String) {
        when (val created = saveActions.createFolder(name)) {
            // A blank name.
            null -> Unit
            is SavedWordActions.NewFolder.Rejected -> updatePicker { it.copy(error = created.error) }
            is SavedWordActions.NewFolder.Created -> pickFolder(entryId, created.id)
        }
    }

    private fun Sheet.withName(name: String): Sheet = when (this) {
        is Sheet.NewFolder -> copy(name = name, error = null)
        is Sheet.Picker -> copy(name = name, error = null)
    }

    private fun updatePicker(change: (Sheet.Picker) -> Sheet.Picker) {
        sheet.update { if (it is Sheet.Picker) change(it) else it }
    }

    /** Runs [block] with the open picker; ignored when it is closed. */
    private fun withPicker(block: suspend (Sheet.Picker) -> Unit) {
        val picker = sheet.value as? Sheet.Picker ?: return
        launchGuarded { block(picker) }
    }

    /** Launches a change to the saved words; a failure is recorded and shown as a generic error toast. */
    private fun launchGuarded(block: suspend () -> Unit): Job =
        viewModelScope.launch {
            if (recorded { block() } == null) effectChannel.send(SavedEffect.Toast(UiText(R.string.entry_action_failed)))
        }

    /** What [block] returns, or null when it fails; any failure but cancellation is recorded. */
    private inline fun <T> recorded(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        errors.record(t)
        null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** [savedState] keys of the search-within query and the selected folder. */
        const val KEY_QUERY = "saved_query"
        const val KEY_FOLDER = "saved_folder_id"
    }
}
