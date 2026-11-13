package com.chordflow.prompter.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a song came from. Stored as plain text so the schema never needs a migration. */
object SongSource {
    const val SEED = "seed"
    const val LOCAL = "local"
    const val IMPORT = "import"
    const val DOWNLOAD = "download"
}

/**
 * A song as the user keeps it: the raw ChordPro / chord-over-lyric source plus
 * the metadata needed to transpose it. Nothing here is pre-stripped, so the
 * original is never lost and the parser can re-derive lyrics on demand.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val artist: String? = null,
    @ColumnInfo(name = "originalKey") val originalKey: String? = null,
    val tempo: String? = null,
    val content: String,
    val source: String = SongSource.LOCAL,
    val updatedAt: Long = System.currentTimeMillis()
)

/** A named service lineup, e.g. "Sunday Morning Worship". */
@Entity(tableName = "lineups")
data class LineupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val isActive: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * One slot in a lineup. This is the cross-reference between a lineup and a
 * song, and it carries the two things that are per-service rather than
 * per-song: the running order and the key the team is playing it in today.
 */
@Entity(
    tableName = "lineup_items",
    foreignKeys = [
        ForeignKey(
            entity = LineupEntity::class,
            parentColumns = ["id"],
            childColumns = ["lineupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lineupId"), Index("songId")]
)
data class LineupItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val lineupId: Long,
    val songId: Long,
    val orderIndex: Int,
    /** Key chosen for this service. Null means "use the song's own key". */
    val lineupKey: String? = null,
    /** Extra manual offset applied on top of the key choice, -11..+11. */
    val transposeSemitones: Int = 0,
    /** Optional service slot name, e.g. "Call to Worship", "Praise 1". */
    val slotLabel: String? = null
)
