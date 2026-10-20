package com.kharagedition.tibetandictionary.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.Links
import com.kharagedition.tibetandictionary.core.audio.AudioPlayer
import com.kharagedition.tibetandictionary.core.audio.AudioSource
import com.kharagedition.tibetandictionary.core.error.ErrorReporter
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.model.SavedEntry
import com.kharagedition.tibetandictionary.data.repository.ActivityRepository
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import com.kharagedition.tibetandictionary.ui.model.FolderPickerUi
import com.kharagedition.tibetandictionary.ui.model.FolderUi
import com.kharagedition.tibetandictionary.ui.model.SavedWordActions
import com.kharagedition.tibetandictionary.ui.model.SavedWordActions.Companion.savedMessage
import com.kharagedition.tibetandictionary.ui.model.UiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Optional
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One dictionary entry (1h/1l): reads [entryId] once, follows whether it is saved and in which folder, and turns
 * user actions into [SavedWordActions] calls and one-shot [effects]. Failures are recorded through [errors]: a
 * failed read shows the entry as missing, a failed save shows a generic error toast. [audioPlayer] is present when
 * core/audio binds `MediaPlayerAudioPlayer` (`AudioModule`); without a binding Play does nothing.
 */
@HiltViewModel(assistedFactory = EntryViewModel.Factory::class)
class EntryViewModel @AssistedInject constructor(
    @Assisted private val entryId: Long,
    private val dictionary: DictionaryRepository,
    private val saved: SavedRepository,
    private val saveActions: SavedWordActions,
    private val activity: ActivityRepository,
    private val errors: ErrorReporter,
    audioPlayer: Optional<AudioPlayer>,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(entryId: Long): EntryViewModel
    }

    /** What has been read from the dictionary. */
    private sealed interface Content {
        data object Loading : Content
        data object Missing : Content
        data class Loaded(val entry: Entry, val ui: EntryUi) : Content
    }

    /** The open folder picker's own state; folders and the selection come from the repository. */
    private data class PickerDraft(val creating: Boolean = false, val name: String = "", val error: UiText? = null)

    private val audio: AudioPlayer? = audioPlayer.orElse(null)
    private var playedAudio = false

    private val content = MutableStateFlow<Content>(Content.Loading)
    private val picker = MutableStateFlow<PickerDraft?>(null)

    private val effectChannel = Channel<EntryEffect>(Channel.BUFFERED)
    val effects: Flow<EntryEffect> = effectChannel.receiveAsFlow()

    /** Folder of the saved row (null when unsaved or unfiled). */
    private val savedFolderId: Flow<Long?> =
        saved.observeAll().map { rows -> rows.firstOrNull { it.entryId == entryId }?.folderId }.distinctUntilChanged()

    val uiState: StateFlow<EntryUiState> = combine(
        content,
        saved.observeIsSaved(entryId),
        savedFolderId,
        saved.observeFolders(),
        picker,
    ) { content, isSaved, folderId, folders, draft ->
        val filedIn = folderId.takeIf { isSaved }
        EntryUiState(
            loading = content == Content.Loading,
            entry = (content as? Content.Loaded)?.ui,
            isSaved = isSaved,
            savedFolderName = filedIn?.let { id -> folders.firstOrNull { it.id == id }?.name },
            folderPicker = draft?.let {
                FolderPickerUi(
                    folders = folders.map { folder -> FolderUi(folder.id, folder.name, folder.count) },
                    selectedId = filedIn,
                    creating = it.creating,
                    newFolderName = it.name,
                    createError = it.error,
                )
            },
            missing = content == Content.Missing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EntryUiState())

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: EntryEvent) {
        when (event) {
            EntryEvent.Back -> send(EntryEffect.NavigateBack)
            EntryEvent.ToggleSave -> withEntry { toggleSave(it) }
            EntryEvent.ChangeFolder -> if (loaded() != null) picker.value = PickerDraft()
            is EntryEvent.PickFolder -> withEntry { pickFolder(it, event.folderId) }
            EntryEvent.StartCreateFolder -> picker.update { it?.copy(creating = true, name = "", error = null) }
            EntryEvent.CancelCreateFolder -> picker.update { it?.copy(creating = false, name = "", error = null) }
            is EntryEvent.NewFolderNameChanged -> picker.update { it?.copy(name = event.name, error = null) }
            is EntryEvent.CreateFolder -> withEntry { createFolder(it, event.name) }
            EntryEvent.DismissFolderPicker -> picker.value = null
            EntryEvent.Copy -> loaded()?.let {
                send(EntryEffect.CopyToClipboard(label = it.ui.headword, text = it.ui.headwordWithWylie()))
            }
            EntryEvent.Share -> loaded()?.let { send(EntryEffect.ShareText(it.ui.shareText())) }
            EntryEvent.PlayAudio -> playAudio()
            is EntryEvent.OpenRelated -> {
                val related = event.related
                send(related.entryId?.let(EntryEffect::NavigateEntry) ?: EntryEffect.NavigateSearch(related.headword))
            }
            EntryEvent.ReportCorrection -> loaded()?.let { send(it.ui.correctionEmail(Links.DEV_EMAIL)) }
        }
    }

    override fun onCleared() {
        if (playedAudio) audio?.stop()
    }

    private suspend fun load() {
        // A failed read is recorded and shown like a missing entry.
        val loaded = recorded { read() }
        if (loaded == null) {
            content.value = Content.Missing
            effectChannel.send(EntryEffect.Toast(UiText(R.string.entry_missing)))
            return
        }
        content.value = loaded
        // Activity stats only: a failure is recorded but does not hide the entry.
        recorded { activity.markEntryOpened() }
    }

    /** Reads the entry and its see-also targets; null when the entry or its direction is not installed. */
    private suspend fun read(): Content.Loaded? {
        val entry = dictionary.entry(entryId) ?: return null
        val direction = dictionary.direction(entry.summary.directionId) ?: return null
        val targetIds = entry.related.mapNotNull { it.targetEntryId }.distinct()
        val targetDirections = if (targetIds.isEmpty()) {
            emptyMap()
        } else {
            dictionary.summaries(targetIds).associate { it.id to it.directionId }
        }
        val directions = (targetDirections.values.toSet() - direction.id)
            .mapNotNull { dictionary.direction(it) }
            .plus(direction)
            .associateBy(Direction::id)
        return Content.Loaded(entry, entry.toUi(direction, directions, targetDirections))
    }

    private suspend fun toggleSave(loaded: Content.Loaded) {
        val summary = loaded.entry.summary
        when (val result = saveActions.toggle(summary)) {
            // A second tap while the first is still saving or removing.
            null -> Unit
            is SavedWordActions.Toggle.Removed -> {
                // Undo puts the row back unchanged (folder, note and save time).
                val undo = { undoUnsave(result.row) }
                effectChannel.send(EntryEffect.Toast(UiText(R.string.removed), R.string.undo, undo))
            }
            is SavedWordActions.Toggle.Saved -> {
                val message = savedMessage(summary.headword, result.folder?.name)
                effectChannel.send(EntryEffect.Toast(message, R.string.change) { onEvent(EntryEvent.ChangeFolder) })
            }
        }
    }

    private fun undoUnsave(row: SavedEntry) {
        viewModelScope.launch { guarded { saveActions.restore(row) } }
    }

    /** Files the word in [folderId], saving it there when it is not saved yet. */
    private suspend fun pickFolder(loaded: Content.Loaded, folderId: Long?) {
        val summary = loaded.entry.summary
        val filed = saveActions.fileIn(entryId, folderId, unsaved = summary) ?: return
        picker.value = null
        effectChannel.send(EntryEffect.Toast(savedMessage(summary.headword, filed.folderName)))
    }

    private suspend fun createFolder(loaded: Content.Loaded, name: String) {
        when (val created = saveActions.createFolder(name)) {
            // A blank name.
            null -> Unit
            is SavedWordActions.NewFolder.Rejected -> picker.update { it?.copy(error = created.error) }
            is SavedWordActions.NewFolder.Created -> pickFolder(loaded, created.id)
        }
    }

    private fun playAudio() {
        val file = loaded()?.ui?.audioFile ?: return
        val player = audio ?: return
        player.play(AudioSource.Asset(file))
        playedAudio = true
    }

    private fun loaded(): Content.Loaded? = content.value as? Content.Loaded

    /** Runs [block] with the loaded entry; ignored while loading or when the entry is missing. */
    private fun withEntry(block: suspend (Content.Loaded) -> Unit) {
        val loaded = loaded() ?: return
        viewModelScope.launch { guarded { block(loaded) } }
    }

    /** Runs a change to the saved words; a failure is recorded and shown as a generic error toast. */
    private suspend fun guarded(block: suspend () -> Unit) {
        if (recorded { block() } == null) effectChannel.send(EntryEffect.Toast(UiText(R.string.entry_action_failed)))
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

    private fun send(effect: EntryEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
