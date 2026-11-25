package com.example.core.storage

import android.content.Context
import java.io.File

/**
 * Manages the app-private storage layout and user-visible MediaStore relative paths
 * strictly offline without touching external folders directly.
 */
object AppStorageDirs {

    const val EXPORT_EDITED_REL_PATH = "Pictures/FolioAlbum/Edited"
    const val EXPORT_COMPRESSED_REL_PATH = "Pictures/FolioAlbum/Compressed"
    const val EXPORT_SHARED_REL_PATH = "Pictures/FolioAlbum/SharedExports"

    fun ensureInitialized(context: Context) {
        databaseDir(context)
        privateVaultDir(context)
        editsDir(context)
        importsDir(context)
        aiModelsDir(context)
        backupsDir(context)
        thumbnailsCacheDir(context)
        previewsCacheDir(context)
        tempCacheDir(context)
    }

    fun databaseDir(context: Context): File =
        File(context.filesDir, "database").apply { mkdirs() }

    fun databaseFile(context: Context): File =
        File(databaseDir(context), "album_database")

    fun privateVaultDir(context: Context): File =
        File(context.filesDir, "private_vault").apply { mkdirs() }

    fun vaultDatabaseFile(context: Context): File =
        File(privateVaultDir(context), "vault_encrypted.db")

    fun editsDir(context: Context): File =
        File(context.filesDir, "edits").apply { mkdirs() }

    fun importsDir(context: Context): File =
        File(context.filesDir, "imports").apply { mkdirs() }

    fun airShareDir(context: Context): File =
        File(context.filesDir, "airshare").apply { mkdirs() }

    fun aiModelsDir(context: Context): File =
        File(context.filesDir, "ai_models").apply { mkdirs() }

    fun backupsDir(context: Context): File =
        File(context.filesDir, "backups").apply { mkdirs() }

    fun thumbnailsCacheDir(context: Context): File =
        File(context.cacheDir, "thumbnails").apply { mkdirs() }

    fun previewsCacheDir(context: Context): File =
        File(context.cacheDir, "previews").apply { mkdirs() }

    fun tempCacheDir(context: Context): File =
        File(context.cacheDir, "temp").apply { mkdirs() }

    fun clearTempPreviews(context: Context) {
        tempCacheDir(context).listFiles()?.forEach { it.deleteRecursively() }
    }
}
