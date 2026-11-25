package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.storage.AppStorageDirs

class GalleryTypeConverters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType =
        MediaType.entries.find { it.name == value } ?: MediaType.PHOTO
}

@Database(
    entities = [
        MediaItemEntity::class,
        VirtualAlbumEntity::class,
        AlbumMediaCrossRef::class,
        TagEntity::class,
        MediaTagCrossRef::class,
        PersonGroupEntity::class,
        MediaFaceEntity::class,
        BirthdayEntity::class,
        OcrEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(GalleryTypeConverters::class)
abstract class AlbumDatabase : RoomDatabase() {
    abstract fun galleryDao(): GalleryDao

    companion object {
        @Volatile
        private var INSTANCE: AlbumDatabase? = null

        fun getInstance(context: Context): AlbumDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        /** v1 -> v2: birthdays and OCR text tables. Existing albums, tags and face groups are kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `birthdays` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `day` INTEGER NOT NULL, `month` INTEGER NOT NULL, `year` INTEGER, " +
                        "`face_group_id` INTEGER, `enabled` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_ocr` (`media_id` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`scanned_at` INTEGER NOT NULL, PRIMARY KEY(`media_id`))"
                )
            }
        }

        private fun buildDatabase(context: Context): AlbumDatabase {
            AppStorageDirs.ensureInitialized(context)
            val dbFile = AppStorageDirs.databaseFile(context)
            return Room.databaseBuilder(
                context,
                AlbumDatabase::class.java,
                dbFile.absolutePath
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
