package com.kharagedition.tibetandictionary.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharagedition.tibetandictionary.R
import com.kharagedition.tibetandictionary.core.Links
import com.kharagedition.tibetandictionary.core.audio.AudioPlayer
import com.kharagedition.tibetandictionary.core.audio.AudioSource
import com.kharagedition.tibetandictionary.data.model.Direction
import com.kharagedition.tibetandictionary.data.model.Entry
import com.kharagedition.tibetandictionary.data.prefs.UserPreferencesRepository
import com.kharagedition.tibetandictionary.data.repository.ActivityRepository
import com.kharagedition.tibetandictionary.data.repository.DictionaryRepository
import com.kharagedition.tibetandictionary.data.repository.SavedRepository
import com.kharagedition.tibetandictionary.ui.model.FolderUi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Optional
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One dictionary entry (1h/1l): reads [entryId] once, follows whether it is saved and in which folder, and turns
 * user actions into repository calls and one-shot [effects]. [audioPlayer] is absent until a player is bound.
 */
@HiltViewModel(assistedFactory = EntryViewModel.Factory::class)
class EntryViewModel @AssistedInject constructor(
    @Assisted private val entryId: Long,
    private val dictionary: DictionaryRepository,
    private val saved: SavedRepository,
    private val activity: ActivityRepository,
    private val prefs: UserPreferencesRepository,
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
    private data class PickerDraft(val creating: Boolean = false, val name: String = "", val error: String? = null)

    private val audio: AudioPlayer? = audioPlayer.orElse(null)
    private var playedAudio = false

    private val content = MutableStateFlow<Content>(Content.Loading)
    private val picker = MutableStateFlow<PickerDraft?>(null)

    /** Serialises save, unsave, undo and refiling so quick taps cannot interleave. */
    private val saveLock = Mutex()

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
            EntryEvent.Copy -> loaded()?.let { send(EntryEffect.CopyToClipboard(it.ui.headwordWithWylie())) }
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
        val entry = dictionary.entry(entryId)
        val direction = entry?.let { dictionary.direction(it.summary.directionId) }
        if (entry == null || direction == null) {
            content.value = Content.Missing
            effectChannel.send(EntryEffect.Toast(UiText(R.string.entry_missing)))
            return
        }
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
        content.value = Content.Loaded(entry, entry.toUi(direction, directions, targetDirections))
        activity.markEntryOpened()
    }

    private suspend fun toggleSave(loaded: Content.Loaded) {
        val summary = loaded.entry.summary
        saveLock.withLock {
            if (saved.observeIsSaved(entryId).first()) {
                val folderId = savedFolderId.first()
                saved.unsave(entryId)
                val undo = { undoUnsave(loaded, folderId) }
                effectChannel.send(EntryEffect.Toast(UiText(R.string.removed), R.string.undo, undo))
            } else {
                val folderId = prefs.snapshot().lastUsedFolderId
                saved.save(summary, folderId)
                val message = savedMessage(summary.headword, folderName(folderId))
                effectChannel.send(EntryEffect.Toast(message, R.string.change) { onEvent(EntryEvent.ChangeFolder) })
            }
        }
    }

    private fun undoUnsave(loaded: Content.Loaded, folderId: Long?) {
        viewModelScope.launch { saveLock.withLock { saved.save(loaded.entry.summary, folderId) } }
    }

    private suspend fun pickFolder(loaded: Content.Loaded, folderId: Long?) {
        saveLock.withLock {
            if (saved.observeIsSaved(entryId).first()) {
                saved.setFolder(entryId, folderId)
            } else {
                saved.save(loaded.entry.summary, folderId)
            }
        }
        prefs.setLastUsedFolderId(folderId)
        picker.value = null
        effectChannel.send(EntryEffect.Toast(savedMessage(loaded.entry.summary.headword, folderName(folderId))))
    }

    private suspend fun createFolder(loaded: Content.Loaded, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val folderId = try {
            saved.createFolder(trimmed)
        } catch (e: IllegalArgumentException) {
            // The repository rejects duplicate names; its message names the clash.
            picker.update { it?.copy(error = e.message?.takeIf(String::isNotBlank) ?: trimmed) }
            return
        }
        pickFolder(loaded, folderId)
    }

    private fun playAudio() {
        val file = loaded()?.ui?.audioFile ?: return
        val player = audio ?: return
        player.play(AudioSource.Asset(file))
        playedAudio = true
    }

    private suspend fun folderName(folderId: Long?): String? =
        folderId?.let { id -> saved.observeFolders().first().firstOrNull { it.id == id }?.name }

    private fun savedMessage(headword: String, folderName: String?): UiText =
        if (folderName != null) {
            UiText(R.string.saved_to_folder, listOf(headword, folderName))
        } else {
            UiText(R.string.saved_simple, listOf(headword))
        }

    private fun loaded(): Content.Loaded? = content.value as? Content.Loaded

    /** Runs [block] with the loaded entry; ignored while loading or when the entry is missing. */
    private fun withEntry(block: suspend (Content.Loaded) -> Unit) {
        val loaded = loaded() ?: return
        viewModelScope.launch { block(loaded) }
    }

    private fun send(effect: EntryEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
