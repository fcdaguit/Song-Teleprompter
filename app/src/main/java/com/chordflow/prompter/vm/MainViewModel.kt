package com.chordflow.prompter.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chordflow.prompter.core.ChordParser
import com.chordflow.prompter.data.ChordFlowDatabase
import com.chordflow.prompter.data.LineupBoard
import com.chordflow.prompter.data.LineupItemEntity
import com.chordflow.prompter.data.OnlineSong
import com.chordflow.prompter.data.OnlineSongRepository
import com.chordflow.prompter.data.SongCatalog
import com.chordflow.prompter.data.SongEntity
import com.chordflow.prompter.data.SongRepository
import com.chordflow.prompter.data.SongSource
import com.chordflow.prompter.data.StageSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which screen is on top. Kept in the ViewModel so it survives rotation. */
sealed interface Screen {
    data object Home : Screen
    data object Library : Screen
    data object Downloader : Screen
    data class Editor(val songId: Long) : Screen
    data class Prompter(val lineupId: Long, val singleSongId: Long? = null) : Screen
}

/** State of the optional online song browser. */
data class DownloaderState(
    val catalogs: List<SongCatalog> = OnlineSongRepository.DEFAULT_CATALOGS,
    val selectedCatalogIndex: Int = 0,
    val query: String = "",
    val results: List<OnlineSong> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val lastDownloadedTitle: String? = null,
    val customOwner: String = "",
    val customRepo: String = "",
    val customBranch: String = "main",
    val rawUrl: String = ""
) {
    val filteredResults: List<OnlineSong>
        get() = if (query.isBlank()) {
            results
        } else {
            val needle = query.trim().lowercase()
            results.filter {
                it.title.lowercase().contains(needle) || it.path.lowercase().contains(needle)
            }
        }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ChordFlowDatabase.get(application).songDao()
    val repository = SongRepository(application, dao)
    private val online = OnlineSongRepository(application)

    val board: StateFlow<LineupBoard> = repository.observeBoard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LineupBoard())

    val settings: StateFlow<StageSettings> = repository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StageSettings())

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _downloader = MutableStateFlow(DownloaderState())
    val downloader: StateFlow<DownloaderState> = _downloader.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.seedIfFirstRun() }
                .onFailure { _status.value = "Could not set up the starter library: ${it.message}" }
        }
    }

    // ------------------------------------------------------------- navigation

    fun go(screen: Screen) {
        _screen.value = screen
    }

    fun goHome() {
        _screen.value = Screen.Home
    }

    /** True when there is a screen to go back to. */
    fun onBackPressed(): Boolean {
        if (_screen.value == Screen.Home) return false
        _screen.value = Screen.Home
        return true
    }

    fun clearStatus() {
        _status.value = null
    }

    // ----------------------------------------------------------------- lineup

    fun launchActiveLineup() {
        val lineup = board.value.activeLineup
        if (lineup == null) {
            _status.value = "Create a lineup and add at least one song first."
            return
        }
        if (board.value.activeSlots.isEmpty()) {
            _status.value = "\"${lineup.name}\" has no songs in it yet."
            return
        }
        _screen.value = Screen.Prompter(lineup.id)
    }

    fun playSingleSong(song: SongEntity) {
        val lineupId = board.value.activeLineup?.id ?: -1L
        _screen.value = Screen.Prompter(lineupId, singleSongId = song.id)
    }

    fun createLineup(name: String) = viewModelScope.launch {
        val trimmed = name.trim().ifBlank { "New Service Lineup" }
        repository.createLineup(trimmed)
        _status.value = "Created \"$trimmed\" and made it the active lineup."
    }

    fun setActiveLineup(lineupId: Long) = viewModelScope.launch {
        repository.setActiveLineup(lineupId)
    }

    fun deleteLineup(lineupId: Long) = viewModelScope.launch {
        repository.deleteLineup(lineupId)
    }

    fun renameActiveLineup(name: String) = viewModelScope.launch {
        board.value.activeLineup?.let { repository.renameLineup(it, name.trim()) }
    }

    fun addSongToActiveLineup(song: SongEntity) = viewModelScope.launch {
        val lineup = board.value.activeLineup
        if (lineup == null) {
            val id = repository.createLineup("Sunday Service Lineup")
            repository.addSongToLineup(id, song)
        } else {
            repository.addSongToLineup(lineup.id, song)
        }
        _status.value = "Added \"${song.title}\" to the lineup."
    }

    fun removeSlot(item: LineupItemEntity) = viewModelScope.launch {
        repository.removeSlot(item)
    }

    fun moveSlot(fromIndex: Int, toIndex: Int) = viewModelScope.launch {
        val lineup = board.value.activeLineup ?: return@launch
        repository.moveSlot(lineup.id, fromIndex, toIndex)
    }

    fun setSlotKey(item: LineupItemEntity, key: String) = viewModelScope.launch {
        repository.setSlotKey(item, key)
    }

    fun nudgeSlotKey(item: LineupItemEntity, song: SongEntity, semitones: Int) =
        viewModelScope.launch {
            repository.nudgeSlotKey(item, song, semitones)
        }

    fun setSlotLabel(item: LineupItemEntity, label: String) = viewModelScope.launch {
        repository.setSlotLabel(item, label)
    }

    // ------------------------------------------------------------------ songs

    fun saveSongEdits(song: SongEntity, title: String, key: String?, content: String) =
        viewModelScope.launch {
            repository.updateSongContent(song, content, title, key)
            _status.value = "Saved \"$title\"."
        }

    fun createBlankSong() = viewModelScope.launch {
        val template = buildString {
            appendLine("{title: New Song}")
            appendLine("{key: G}")
            appendLine()
            appendLine("[G]Type your lyrics here with [C]chords in [D]brackets")
        }
        val id = repository.saveSongFromText(template, "New Song", SongSource.LOCAL)
        _screen.value = Screen.Editor(id)
    }

    fun deleteSong(song: SongEntity) = viewModelScope.launch {
        repository.deleteSong(song.id)
        _status.value = "Deleted \"${song.title}\"."
    }

    /** Import a .cho / .onsong / .pro / .txt chord sheet from local storage. */
    fun importFromUri(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val result = runCatching {
            withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                } ?: error("That file could not be opened.")
            }
        }
        val text = result.getOrElse { error ->
            _status.value = "Import failed: ${error.message}"
            return@launch
        }
        if (text.isBlank()) {
            _status.value = "That file is empty."
            return@launch
        }
        val fallback = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.replace('_', ' ')
            ?.trim()
            ?.ifBlank { "Imported Song" }
            ?: "Imported Song"
        val id = repository.saveSongFromText(text, fallback, SongSource.IMPORT)
        val parsed = ChordParser.parse(text)
        _status.value = "Imported \"${parsed.title ?: fallback}\"."
        _screen.value = Screen.Editor(id)
    }

    // ------------------------------------------------------------- downloader

    fun setDownloaderQuery(query: String) {
        _downloader.value = _downloader.value.copy(query = query)
    }

    fun selectCatalog(index: Int) {
        _downloader.value = _downloader.value.copy(
            selectedCatalogIndex = index,
            results = emptyList(),
            error = null
        )
    }

    fun setCustomCatalogField(owner: String?, repo: String?, branch: String?) {
        val current = _downloader.value
        _downloader.value = current.copy(
            customOwner = owner ?: current.customOwner,
            customRepo = repo ?: current.customRepo,
            customBranch = branch ?: current.customBranch
        )
    }

    fun setRawUrl(url: String) {
        _downloader.value = _downloader.value.copy(rawUrl = url)
    }

    fun browseSelectedCatalog() = viewModelScope.launch {
        val state = _downloader.value
        val catalog = state.catalogs.getOrNull(state.selectedCatalogIndex) ?: return@launch
        loadCatalog(catalog)
    }

    fun browseCustomCatalog() = viewModelScope.launch {
        val state = _downloader.value
        if (state.customOwner.isBlank() || state.customRepo.isBlank()) {
            _downloader.value = state.copy(error = "Enter both a GitHub owner and a repository name.")
            return@launch
        }
        loadCatalog(
            SongCatalog(
                label = "${state.customOwner}/${state.customRepo}",
                owner = state.customOwner.trim(),
                repo = state.customRepo.trim(),
                branch = state.customBranch.trim().ifBlank { "main" }
            )
        )
    }

    private suspend fun loadCatalog(catalog: SongCatalog) {
        _downloader.value = _downloader.value.copy(loading = true, error = null)
        val result = online.listCatalog(catalog)
        _downloader.value = result.fold(
            onSuccess = { songs ->
                _downloader.value.copy(loading = false, results = songs, error = null)
            },
            onFailure = { t ->
                _downloader.value.copy(
                    loading = false,
                    results = emptyList(),
                    error = t.message ?: "Could not reach GitHub."
                )
            }
        )
    }

    fun downloadSong(song: OnlineSong) = viewModelScope.launch {
        _downloader.value = _downloader.value.copy(loading = true, error = null)
        val result = online.downloadSongText(song.rawUrl)
        result.fold(
            onSuccess = { text ->
                repository.saveSongFromText(text, song.title, SongSource.DOWNLOAD)
                _downloader.value = _downloader.value.copy(
                    loading = false,
                    lastDownloadedTitle = song.title,
                    error = null
                )
                _status.value = "Downloaded \"${song.title}\" into your offline library."
            },
            onFailure = { t ->
                _downloader.value = _downloader.value.copy(
                    loading = false,
                    error = t.message ?: "Download failed."
                )
            }
        )
    }

    fun downloadFromRawUrl() = viewModelScope.launch {
        val raw = _downloader.value.rawUrl.trim()
        if (raw.isEmpty()) {
            _downloader.value = _downloader.value.copy(error = "Paste a raw song file URL first.")
            return@launch
        }
        val url = online.normaliseRawUrl(raw)
        _downloader.value = _downloader.value.copy(loading = true, error = null)
        val result = online.downloadSongText(url)
        result.fold(
            onSuccess = { text ->
                val fallback = online.titleFromPath(url)
                val id = repository.saveSongFromText(text, fallback, SongSource.DOWNLOAD)
                _downloader.value = _downloader.value.copy(
                    loading = false,
                    lastDownloadedTitle = fallback,
                    rawUrl = "",
                    error = null
                )
                _status.value = "Downloaded \"$fallback\"."
                _screen.value = Screen.Editor(id)
            },
            onFailure = { t ->
                _downloader.value = _downloader.value.copy(
                    loading = false,
                    error = t.message ?: "Download failed."
                )
            }
        )
    }

    // --------------------------------------------------------------- settings

    fun setMicGate(value: Float) = viewModelScope.launch { repository.setMicGate(value) }
    fun setConfidence(value: Float) = viewModelScope.launch { repository.setConfidence(value) }
    fun setFontScale(percent: Int) = viewModelScope.launch { repository.setFontScale(percent) }
    fun setAutoScroll(enabled: Boolean) = viewModelScope.launch { repository.setAutoScroll(enabled) }
}
