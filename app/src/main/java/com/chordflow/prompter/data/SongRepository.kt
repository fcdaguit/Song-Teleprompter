package com.chordflow.prompter.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chordflow.prompter.core.ChordParser
import com.chordflow.prompter.core.ChordTransposer
import com.chordflow.prompter.core.LineupSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "chordflow_settings")

/** One slot of a lineup, resolved against the song library. */
data class LineupSlot(
    val item: LineupItemEntity,
    val song: SongEntity,
    val effectiveKey: String
) {
    val position: Int get() = item.orderIndex
}

/** Everything the home screen needs in one snapshot. */
data class LineupBoard(
    val lineups: List<LineupEntity> = emptyList(),
    val activeLineup: LineupEntity? = null,
    val activeSlots: List<LineupSlot> = emptyList(),
    val library: List<SongEntity> = emptyList()
)

/** Stage settings that survive a restart. */
data class StageSettings(
    val micGate: Float = 0.30f,
    val confidence: Float = 0.78f,
    val fontScalePercent: Int = 100,
    val autoScrollEnabled: Boolean = true
)

class SongRepository(
    private val context: Context,
    val dao: SongDao
) {

    // ------------------------------------------------------------- observation

    fun observeBoard(): Flow<LineupBoard> = combine(
        dao.observeLineups(),
        dao.observeAllLineupItems(),
        dao.observeSongs()
    ) { lineups, items, songs ->
        val active = lineups.firstOrNull { it.isActive } ?: lineups.firstOrNull()
        val songsById = songs.associateBy { it.id }
        val slots = if (active == null) {
            emptyList()
        } else {
            items.asSequence()
                .filter { it.lineupId == active.id }
                .mapNotNull { item ->
                    songsById[item.songId]?.let { song ->
                        LineupSlot(item, song, effectiveKeyOf(item, song))
                    }
                }
                .toList()
        }
        LineupBoard(
            lineups = lineups,
            activeLineup = active,
            activeSlots = slots,
            library = songs
        )
    }

    fun observeSongs(): Flow<List<SongEntity>> = dao.observeSongs()

    // ---------------------------------------------------------------- lineups

    /**
     * The key a slot is actually played in: the lineup override if there is
     * one, otherwise the song's own key, then any extra manual offset.
     */
    fun effectiveKeyOf(item: LineupItemEntity, song: SongEntity): String {
        val base = item.lineupKey
            ?: song.originalKey?.takeIf { it.isNotBlank() }
            ?: "C"
        return if (item.transposeSemitones == 0) {
            base
        } else {
            ChordTransposer.transposeKeyName(base, item.transposeSemitones)
        }
    }

    /** Resolve and parse a lineup, ready for [ChordParser.flattenLineup]. */
    suspend fun loadLineupSongs(lineupId: Long): List<LineupSong> {
        val items = dao.getLineupItems(lineupId)
        val out = ArrayList<LineupSong>(items.size)
        for (item in items) {
            val song = dao.getSong(item.songId) ?: continue
            out.add(
                LineupSong(
                    lineupItemId = item.id,
                    songId = song.id,
                    title = song.title,
                    originalKey = song.originalKey,
                    targetKey = effectiveKeyOf(item, song),
                    parsed = ChordParser.parse(song.content)
                )
            )
        }
        return out
    }

    /** A single song treated as a one-entry lineup, for "play this song now". */
    suspend fun loadSingleSong(songId: Long, keyOverride: String? = null): List<LineupSong> {
        val song = dao.getSong(songId) ?: return emptyList()
        val key = keyOverride
            ?: song.originalKey?.takeIf { it.isNotBlank() }
            ?: "C"
        return listOf(
            LineupSong(
                lineupItemId = -1L,
                songId = song.id,
                title = song.title,
                originalKey = song.originalKey,
                targetKey = key,
                parsed = ChordParser.parse(song.content)
            )
        )
    }

    suspend fun createLineup(name: String, makeActive: Boolean = true): Long {
        val id = dao.insertLineup(LineupEntity(name = name, isActive = false))
        if (makeActive) dao.setActiveLineup(id)
        return id
    }

    suspend fun renameLineup(lineup: LineupEntity, name: String) {
        dao.updateLineup(lineup.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setActiveLineup(lineupId: Long) = dao.setActiveLineup(lineupId)

    suspend fun deleteLineup(lineupId: Long) = dao.deleteLineupById(lineupId)

    suspend fun addSongToLineup(lineupId: Long, song: SongEntity) {
        dao.appendSongToLineup(
            lineupId = lineupId,
            songId = song.id,
            lineupKey = song.originalKey?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun removeSlot(item: LineupItemEntity) {
        dao.deleteLineupItemById(item.id)
        dao.compactOrder(item.lineupId)
    }

    suspend fun moveSlot(lineupId: Long, fromIndex: Int, toIndex: Int) {
        dao.moveLineupItem(lineupId, fromIndex, toIndex)
    }

    suspend fun setSlotKey(item: LineupItemEntity, key: String) {
        dao.updateLineupItem(item.copy(lineupKey = key, transposeSemitones = 0))
    }

    suspend fun nudgeSlotKey(item: LineupItemEntity, song: SongEntity, semitones: Int) {
        val current = effectiveKeyOf(item, song)
        val next = ChordTransposer.transposeKeyName(current, semitones)
        dao.updateLineupItem(item.copy(lineupKey = next, transposeSemitones = 0))
    }

    suspend fun setSlotLabel(item: LineupItemEntity, label: String?) {
        dao.updateLineupItem(item.copy(slotLabel = label?.takeIf { it.isNotBlank() }))
    }

    // ------------------------------------------------------------------ songs

    /** Create or replace a song from raw ChordPro / chord-sheet text. */
    suspend fun saveSongFromText(
        raw: String,
        fallbackTitle: String,
        source: String
    ): Long {
        val parsed = ChordParser.parse(raw)
        val title = parsed.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val existing = dao.findSongByTitle(title)
        return if (existing == null) {
            dao.insertSong(
                SongEntity(
                    title = title,
                    artist = parsed.artist,
                    originalKey = parsed.key,
                    tempo = parsed.tempo,
                    content = raw,
                    source = source
                )
            )
        } else {
            dao.updateSong(
                existing.copy(
                    artist = parsed.artist ?: existing.artist,
                    originalKey = parsed.key ?: existing.originalKey,
                    tempo = parsed.tempo ?: existing.tempo,
                    content = raw,
                    source = source,
                    updatedAt = System.currentTimeMillis()
                )
            )
            existing.id
        }
    }

    suspend fun updateSongContent(song: SongEntity, raw: String, title: String, key: String?) {
        val parsed = ChordParser.parse(raw)
        dao.updateSong(
            song.copy(
                title = title.takeIf { it.isNotBlank() } ?: song.title,
                originalKey = key ?: parsed.key ?: song.originalKey,
                artist = parsed.artist ?: song.artist,
                tempo = parsed.tempo ?: song.tempo,
                content = raw,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSong(id: Long): SongEntity? = dao.getSong(id)

    suspend fun deleteSong(id: Long) = dao.deleteSongById(id)

    // -------------------------------------------------------------- first run

    /**
     * Seeds the three public-domain hymns and the default Sunday lineup the
     * first time the app runs. Guarded by a DataStore flag so that a user who
     * deliberately empties their library does not get it back.
     */
    suspend fun seedIfFirstRun() {
        val alreadySeeded = context.settingsStore.data
            .map { it[SEEDED] ?: false }
            .first()
        if (alreadySeeded) return

        val lineupId = if (dao.lineupCount() == 0) {
            dao.insertLineup(LineupEntity(name = SeedData.DEFAULT_LINEUP_NAME, isActive = false))
        } else {
            dao.getActiveLineup()?.id
                ?: dao.insertLineup(
                    LineupEntity(name = SeedData.DEFAULT_LINEUP_NAME, isActive = false)
                )
        }

        for (seed in SeedData.songs) {
            val existing = dao.findSongByTitle(seed.title)
            val songId = existing?.id ?: dao.insertSong(
                SongEntity(
                    title = seed.title,
                    artist = seed.artist,
                    originalKey = seed.key,
                    tempo = seed.tempo,
                    content = seed.content,
                    source = SongSource.SEED
                )
            )
            val slots = dao.getLineupItems(lineupId)
            if (slots.none { it.songId == songId }) {
                val index = dao.maxOrderIndex(lineupId) + 1
                dao.insertLineupItem(
                    LineupItemEntity(
                        lineupId = lineupId,
                        songId = songId,
                        orderIndex = index,
                        lineupKey = seed.lineupKey,
                        slotLabel = seed.slotLabel
                    )
                )
            }
        }

        dao.setActiveLineup(lineupId)
        context.settingsStore.edit { it[SEEDED] = true }
    }

    // --------------------------------------------------------------- settings

    fun observeSettings(): Flow<StageSettings> = context.settingsStore.data.map { prefs ->
        StageSettings(
            micGate = prefs[MIC_GATE] ?: 0.30f,
            confidence = prefs[CONFIDENCE] ?: 0.78f,
            fontScalePercent = prefs[FONT_SCALE] ?: 100,
            autoScrollEnabled = prefs[AUTO_SCROLL] ?: true
        )
    }

    suspend fun setMicGate(value: Float) {
        context.settingsStore.edit { it[MIC_GATE] = value.coerceIn(0f, 1f) }
    }

    suspend fun setConfidence(value: Float) {
        context.settingsStore.edit { it[CONFIDENCE] = value.coerceIn(0.5f, 0.99f) }
    }

    suspend fun setFontScale(percent: Int) {
        context.settingsStore.edit { it[FONT_SCALE] = percent.coerceIn(70, 220) }
    }

    suspend fun setAutoScroll(enabled: Boolean) {
        context.settingsStore.edit { it[AUTO_SCROLL] = enabled }
    }

    private companion object {
        val SEEDED = booleanPreferencesKey("seeded_v1")
        val MIC_GATE = floatPreferencesKey("mic_gate")
        val CONFIDENCE = floatPreferencesKey("confidence")
        val FONT_SCALE = intPreferencesKey("font_scale")
        val AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
    }
}
