package com.daniel.reproductor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Song::class, Playlist::class, PlaylistSong::class, PlayHistory::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "reproductor.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2: cada playlist recibe un uid para sincronizarla con la nube. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Playlist ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE Playlist SET uid = lower(hex(randomblob(16))) WHERE uid = ''")
            }
        }

        /** v3: historial de escucha para las recomendaciones. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `PlayHistory` (`videoId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`uploader` TEXT NOT NULL, `durationSec` INTEGER NOT NULL, `thumbnailUrl` TEXT NOT NULL, " +
                        "`playCount` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`videoId`))"
                )
            }
        }
    }
}
