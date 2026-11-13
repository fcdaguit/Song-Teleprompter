package com.chordflow.prompter.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chordflow.prompter.core.AudioChordDetector
import com.chordflow.prompter.core.ChordDetection
import com.chordflow.prompter.core.ChordParser
import com.chordflow.prompter.core.ChordTransposer
import com.chordflow.prompter.core.LineupSong
import com.chordflow.prompter.core.PromptItem
import com.chordflow.prompter.data.ChordFlowDatabase
import com.chordflow.prompter.data.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the teleprompter screen draws, in one immutable snapshot. */
data class PrompterUiState(
    val loading: Boolean = true,
    val lineupName: String = "",
    val lineupId: Long = -1L,
    val songs: List<LineupSong> = emptyList(),
    val items: List<PromptItem> = emptyList(),
    val activeIndex: Int = 0,
    val autoScroll: Boolean = true,
    val micGate: Float = 0.30f,
    val confidence: Float = 0.78f,
    val fontScalePercent: Int = 100,
    val micRunning: Boolean = false,
    val micError: String? = null,
    val detected: ChordDetection? = null,
    val inputLevel: Float = 0f,
    val emptyMessage: String? = null
) {
    val currentSongIndex: Int
        get() = items.getOrNull(activeIndex)?.songIndex ?: 0

    val currentSong: LineupSong?
        get() = songs.getOrNull(currentSongIndex)

    val nextSong: LineupSong?
        get() = songs.getOrNull(currentSongIndex + 1)

    val currentKey: String
        get() = currentSong?.targetKey ?: "-"

    val positionLabel: String
        get() = if (songs.isEmpty()) "0/0" else "${currentSongIndex + 1}/${songs.size}"

    /** Chord the engine is waiting for, spelled in the current song's key. */
    val awaitedChord: String?
        get() {
            val index = nextTriggerIndexFrom(activeIndex) ?: return null
            return (items.getOrNull(index) as? PromptItem.Lyric)?.triggers?.firstOrNull()
        }

    fun nextTriggerIndexFrom(from: Int): Int? {
        var i = from + 1
        while (i < items.size) {
            val item = items[i]
            if (item is PromptItem.Lyric && item.hasTrigger) return i
            i++
        }
        return null
    }

    fun firstIndexOfSong(songIndex: Int): Int {
        val index = items.indexOfFirst { it.songIndex == songIndex }
        return if (index >= 0) index else 0
    }
}

class TeleprompterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SongRepository(
        application,
        ChordFlowDatabase.get(application).songDao()
    )
    private val detector = AudioChordDetector()

    private val _state = MutableStateFlow(PrompterUiState())
    val state: StateFlow<PrompterUiState> = _state.asStateFlow()

    private var loadedKey: String? = null
    private var lastAdvanceAtMs = 0L
    private var lastTriggerLabel: String? = null

    /** Minimum time between chord-driven line advances. */
    private val minAdvanceGapMs = 320L

    /** Longer gap when the same chord would fire again, so repeats do not race. */
    private val repeatAdvanceGapMs = 1_600L

    init {
        viewModelScope.launch {
            detector.detection.collect { detection ->
                _state.value = _state.value.copy(detected = detection)
                handleDetection(detection)
            }
        }
        viewModelScope.launch {
            detector.inputLevel.collect { level ->
                _state.value = _state.value.copy(inputLevel = level)
            }
        }
        viewModelScope.launch {
            detector.running.collect { running ->
                _state.value = _state.value.copy(micRunning = running)
            }
        }
        viewModelScope.launch {
            detector.error.collect { error ->
                _state.value = _state.value.copy(micError = error)
            }
        }
        viewModelScope.launch {
            repository.observeSettings().collect { settings ->
                detector.gate = settings.micGate
                detector.minConfidence = settings.confidence
                _state.value = _state.value.copy(
                    micGate = settings.micGate,
                    confidence = settings.confidence,
                    fontScalePercent = settings.fontScalePercent,
                    autoScroll = settings.autoScrollEnabled
                )
            }
        }
    }

    /**
     * Loads either a whole lineup or a single song. Re-loading the same target
     * is a no-op so that a rotation does not restart the service.
     */
    fun load(lineupId: Long, singleSongId: Long?) {
        val key = "$lineupId/$singleSongId"
        if (loadedKey == key) return
        loadedKey = key

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, emptyMessage = null)
            val songs = if (singleSongId != null) {
                repository.loadSingleSong(singleSongId)
            } else {
                repository.loadLineupSongs(lineupId)
            }
            val name = when {
                singleSongId != null -> songs.firstOrNull()?.title ?: "Song"
                else -> repository.dao.getLineup(lineupId)?.name ?: "Service Lineup"
            }

            if (songs.isEmpty()) {
                _state.value = _state.value.copy(
                    loading = false,
                    lineupName = name,
                    lineupId = lineupId,
                    songs = emptyList(),
                    items = emptyList(),
                    emptyMessage = "There is nothing to display. Add songs to the lineup first."
                )
                return@launch
            }

            val items = ChordParser.flattenLineup(songs)
            _state.value = _state.value.copy(
                loading = false,
                lineupName = name,
                lineupId = lineupId,
                songs = songs,
                items = items,
                activeIndex = 0,
                emptyMessage = null
            )
        }
    }

    // -------------------------------------------------------------- microphone

    fun startListening() {
        detector.gate = _state.value.micGate
        detector.minConfidence = _state.value.confidence
        detector.start(viewModelScope)
    }

    fun stopListening() {
        detector.stop()
    }

    fun setMicGate(value: Float) {
        detector.gate = value
        _state.value = _state.value.copy(micGate = value)
        viewModelScope.launch { repository.setMicGate(value) }
    }

    fun setConfidence(value: Float) {
        detector.minConfidence = value
        _state.value = _state.value.copy(confidence = value)
        viewModelScope.launch { repository.setConfidence(value) }
    }

    fun setAutoScroll(enabled: Boolean) {
        _state.value = _state.value.copy(autoScroll = enabled)
        viewModelScope.launch { repository.setAutoScroll(enabled) }
    }

    fun setFontScale(percent: Int) {
        val clamped = percent.coerceIn(70, 220)
        _state.value = _state.value.copy(fontScalePercent = clamped)
        viewModelScope.launch { repository.setFontScale(clamped) }
    }

    // ------------------------------------------------------------- navigation

    fun setActiveIndex(index: Int) {
        val items = _state.value.items
        if (index !in items.indices) return
        _state.value = _state.value.copy(activeIndex = index)
    }

    fun stepLine(delta: Int) {
        val state = _state.value
        if (state.items.isEmpty()) return
        var index = state.activeIndex + delta
        // Skip blank spacer lines so manual stepping always lands on content.
        while (index in state.items.indices) {
            val item = state.items[index]
            if (item is PromptItem.SongBreak) break
            if (item is PromptItem.Lyric && !item.isBlank) break
            index += delta
        }
        if (index in state.items.indices) {
            _state.value = state.copy(activeIndex = index)
            lastAdvanceAtMs = System.currentTimeMillis()
        }
    }

    fun jumpToSong(songIndex: Int) {
        val state = _state.value
        if (songIndex !in state.songs.indices) return
        _state.value = state.copy(activeIndex = state.firstIndexOfSong(songIndex))
        lastAdvanceAtMs = System.currentTimeMillis()
        lastTriggerLabel = null
    }

    fun nextSong() = jumpToSong(_state.value.currentSongIndex + 1)

    fun previousSong() = jumpToSong(_state.value.currentSongIndex - 1)

    fun restartLineup() {
        _state.value = _state.value.copy(activeIndex = 0)
        lastTriggerLabel = null
    }

    // ---------------------------------------------------------- live key change

    /** Live HUD key nudge. Updates the hidden triggers, never the lyrics. */
    fun nudgeKey(semitones: Int) {
        val state = _state.value
        val songIndex = state.currentSongIndex
        val song = state.songs.getOrNull(songIndex) ?: return
        applyKey(songIndex, ChordTransposer.transposeKeyName(song.targetKey, semitones))
    }

    fun setKey(key: String) {
        applyKey(_state.value.currentSongIndex, key)
    }

    private fun applyKey(songIndex: Int, newKey: String) {
        val state = _state.value
        val song = state.songs.getOrNull(songIndex) ?: return
        val songs = state.songs.toMutableList()
        songs[songIndex] = song.copy(targetKey = newKey)

        // Re-flattening produces the same number of items in the same order -
        // only the invisible triggers change - so the active line is preserved.
        val items = ChordParser.flattenLineup(songs)
        _state.value = state.copy(
            songs = songs,
            items = items,
            activeIndex = state.activeIndex.coerceIn(0, maxOf(0, items.size - 1))
        )
        lastTriggerLabel = null

        // Remember the choice for next time, if this song came from a lineup.
        val lineupItemId = song.lineupItemId
        if (lineupItemId > 0L) {
            viewModelScope.launch {
                val items2 = repository.dao.getLineupItems(state.lineupId)
                items2.firstOrNull { it.id == lineupItemId }?.let { slot ->
                    repository.setSlotKey(slot, newKey)
                }
            }
        }
    }

    // ------------------------------------------------------------- trigger logic

    private fun handleDetection(detection: ChordDetection?) {
        if (detection == null) return
        val state = _state.value
        if (!state.autoScroll || state.items.isEmpty()) return
        if (detection.confidence < state.confidence) return

        val targetIndex = state.nextTriggerIndexFrom(state.activeIndex) ?: return
        val target = state.items.getOrNull(targetIndex) as? PromptItem.Lyric ?: return
        if (detection.rootPc !in target.triggerPitchClasses) return

        val now = System.currentTimeMillis()
        val label = target.triggers.firstOrNull()
        val gap = if (label != null && label == lastTriggerLabel) {
            repeatAdvanceGapMs
        } else {
            minAdvanceGapMs
        }
        if (now - lastAdvanceAtMs < gap) return

        lastAdvanceAtMs = now
        lastTriggerLabel = label
        _state.value = state.copy(activeIndex = targetIndex)
    }

    override fun onCleared() {
        detector.stop()
        super.onCleared()
    }
}
