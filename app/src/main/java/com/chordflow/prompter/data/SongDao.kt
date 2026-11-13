package com.chordflow.prompter.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Declared as an abstract class rather than an interface so that the
 * `@Transaction` helpers below can have real bodies - Room generates its
 * implementation as a subclass and inherits them directly.
 */
@Dao
abstract class SongDao {

    // ------------------------------------------------------------------ songs

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    abstract fun observeSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    abstract suspend fun getSong(id: Long): SongEntity?

    @Query("SELECT COUNT(*) FROM songs")
    abstract suspend fun songCount(): Int

    @Query("SELECT * FROM songs WHERE title = :title LIMIT 1")
    abstract suspend fun findSongByTitle(title: String): SongEntity?

    @Insert
    abstract suspend fun insertSong(song: SongEntity): Long

    @Update
    abstract suspend fun updateSong(song: SongEntity)

    @Delete
    abstract suspend fun deleteSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :id")
    abstract suspend fun deleteSongById(id: Long)

    // ---------------------------------------------------------------- lineups

    @Query("SELECT * FROM lineups ORDER BY isActive DESC, updatedAt DESC")
    abstract fun observeLineups(): Flow<List<LineupEntity>>

    /**
     * Returns a list rather than a nullable single row: at most one lineup is
     * ever active, and a list keeps the Room return type unambiguous.
     */
    @Query("SELECT * FROM lineups WHERE isActive = 1 LIMIT 1")
    abstract fun observeActiveLineups(): Flow<List<LineupEntity>>

    @Query("SELECT * FROM lineups WHERE isActive = 1 LIMIT 1")
    abstract suspend fun getActiveLineup(): LineupEntity?

    @Query("SELECT * FROM lineups WHERE id = :id")
    abstract suspend fun getLineup(id: Long): LineupEntity?

    @Query("SELECT COUNT(*) FROM lineups")
    abstract suspend fun lineupCount(): Int

    @Insert
    abstract suspend fun insertLineup(lineup: LineupEntity): Long

    @Update
    abstract suspend fun updateLineup(lineup: LineupEntity)

    @Query("DELETE FROM lineups WHERE id = :id")
    abstract suspend fun deleteLineupById(id: Long)

    @Query("UPDATE lineups SET isActive = 0")
    abstract suspend fun clearActiveLineups()

    @Query("UPDATE lineups SET isActive = 1, updatedAt = :now WHERE id = :lineupId")
    abstract suspend fun markLineupActive(lineupId: Long, now: Long)

    @Transaction
    open suspend fun setActiveLineup(lineupId: Long) {
        clearActiveLineups()
        markLineupActive(lineupId, System.currentTimeMillis())
    }

    // ----------------------------------------------------------- lineup items

    @Query("SELECT * FROM lineup_items WHERE lineupId = :lineupId ORDER BY orderIndex ASC")
    abstract fun observeLineupItems(lineupId: Long): Flow<List<LineupItemEntity>>

    /** Every slot in every lineup, so the UI can combine three flows instead
     *  of switching flows whenever the active lineup changes. */
    @Query("SELECT * FROM lineup_items ORDER BY lineupId ASC, orderIndex ASC")
    abstract fun observeAllLineupItems(): Flow<List<LineupItemEntity>>

    @Query("SELECT * FROM lineup_items WHERE lineupId = :lineupId ORDER BY orderIndex ASC")
    abstract suspend fun getLineupItems(lineupId: Long): List<LineupItemEntity>

    @Query("SELECT IFNULL(MAX(orderIndex), -1) FROM lineup_items WHERE lineupId = :lineupId")
    abstract suspend fun maxOrderIndex(lineupId: Long): Int

    @Insert
    abstract suspend fun insertLineupItem(item: LineupItemEntity): Long

    @Update
    abstract suspend fun updateLineupItem(item: LineupItemEntity)

    @Update
    abstract suspend fun updateLineupItems(items: List<LineupItemEntity>)

    @Query("DELETE FROM lineup_items WHERE id = :id")
    abstract suspend fun deleteLineupItemById(id: Long)

    @Transaction
    open suspend fun appendSongToLineup(lineupId: Long, songId: Long, lineupKey: String?): Long {
        val next = maxOrderIndex(lineupId) + 1
        return insertLineupItem(
            LineupItemEntity(
                lineupId = lineupId,
                songId = songId,
                orderIndex = next,
                lineupKey = lineupKey
            )
        )
    }

    /** Move a slot up or down, used by the reorder arrows on the home screen. */
    @Transaction
    open suspend fun moveLineupItem(lineupId: Long, fromIndex: Int, toIndex: Int) {
        val items = getLineupItems(lineupId)
        if (fromIndex !in items.indices || toIndex !in items.indices) return
        val reordered = items.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        updateLineupItems(reordered.mapIndexed { index, item -> item.copy(orderIndex = index) })
    }

    /** Close gaps in orderIndex after a removal. */
    @Transaction
    open suspend fun compactOrder(lineupId: Long) {
        val items = getLineupItems(lineupId)
        updateLineupItems(items.mapIndexed { index, item -> item.copy(orderIndex = index) })
    }
}
