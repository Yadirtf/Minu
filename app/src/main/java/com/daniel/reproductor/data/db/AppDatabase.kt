package com.daniel.reproductor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Song::class, Playlist::class, PlaylistSong::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "reproductor.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: cada playlist recibe un uid para sincronizarla con la nube. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Playlist ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE Playlist SET uid = lower(hex(randomblob(16))) WHERE uid = ''")
            }
        }
    }
}
