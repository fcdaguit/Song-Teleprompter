package com.chordflow.prompter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SongEntity::class, LineupEntity::class, LineupItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ChordFlowDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        private const val NAME = "chordflow.db"

        @Volatile
        private var instance: ChordFlowDatabase? = null

        fun get(context: Context): ChordFlowDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChordFlowDatabase::class.java,
                    NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
