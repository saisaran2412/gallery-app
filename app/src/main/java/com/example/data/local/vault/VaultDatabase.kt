package com.example.data.local.vault

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.core.storage.AppStorageDirs
import com.example.data.local.GalleryTypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        VaultItemEntity::class,
        VaultAuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(GalleryTypeConverters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var nativeLoaded = false

        @Volatile
        private var INSTANCE: VaultDatabase? = null

        private fun ensureNativeLibraryLoaded() {
            if (!nativeLoaded) {
                synchronized(this) {
                    if (!nativeLoaded) {
                        System.loadLibrary("sqlcipher")
                        nativeLoaded = true
                    }
                }
            }
        }

        fun openWithPassphrase(context: Context, passphraseBytes: ByteArray): VaultDatabase {
            INSTANCE?.let { existing ->
                if (existing.isOpen) return existing
            }
            return synchronized(this) {
                val current = INSTANCE
                if (current != null && current.isOpen) {
                    current
                } else {
                    ensureNativeLibraryLoaded()
                    AppStorageDirs.ensureInitialized(context)
                    val dbFile = AppStorageDirs.vaultDatabaseFile(context)
                    val keyCopy = passphraseBytes.copyOf()
                    val factory = SupportOpenHelperFactory(keyCopy)
                    Room.databaseBuilder(
                        context.applicationContext,
                        VaultDatabase::class.java,
                        dbFile.absolutePath
                    )
                        .openHelperFactory(factory)
                        .fallbackToDestructiveMigration(dropAllTables = true)
                        .build()
                        .also { INSTANCE = it }
                }
            }
        }

        @VisibleForTesting
        fun openWithCustomFactory(
            context: Context,
            factory: SupportSQLiteOpenHelper.Factory? = null,
            inMemory: Boolean = true
        ): VaultDatabase {
            return synchronized(this) {
                INSTANCE?.takeIf { it.isOpen }?.close()
                val builder = if (inMemory) {
                    Room.inMemoryDatabaseBuilder(context.applicationContext, VaultDatabase::class.java)
                        .allowMainThreadQueries()
                } else {
                    Room.databaseBuilder(
                        context.applicationContext,
                        VaultDatabase::class.java,
                        AppStorageDirs.vaultDatabaseFile(context).absolutePath
                    ).allowMainThreadQueries()
                }
                if (factory != null) {
                    builder.openHelperFactory(factory)
                }
                builder.build().also { INSTANCE = it }
            }
        }

        fun lockAndClose() {
            synchronized(this) {
                runCatching { INSTANCE?.close() }
                INSTANCE = null
            }
        }
    }
}
