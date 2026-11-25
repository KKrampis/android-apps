package io.github.kkrampis.clipharbor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VideoEntity::class,
        SavedEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        DownloadEntity::class,
        TranscriptEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    abstract fun downloads(): DownloadDao
    abstract fun transcripts(): TranscriptDao

    companion object {
        /** Version 2 adds transcripts. Existing playlists, saved videos and downloads are kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transcripts` (" +
                        "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                        "`language` TEXT NOT NULL, `text` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`progress` REAL NOT NULL, `error` TEXT, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`url`))",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "clipharbor.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
