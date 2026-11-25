package com.example.core.storage

import android.content.Context
import java.io.File
import kotlin.concurrent.thread

/**
 * Automatic housekeeping for regenerable data only (never vault, database, backups or user files).
 * Runs on a background thread at most once every 12 hours.
 */
object CacheCleaner {
    private const val PREFS = "folio_cache_clean"
    private const val KEY_LAST = "last_run"
    private const val INTERVAL_MS = 12L * 60 * 60 * 1000
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private const val THUMB_CAP = 200L * 1024 * 1024
    private const val DEPTH_CAP = 60L * 1024 * 1024

    fun runIfDue(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST, 0L) < INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST, now).apply()
        thread(name = "folio-cache-clean", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            runCatching { clean(app) }
        }
    }

    /** Frees everything regenerable right now (for a manual "Clear cache" button). Returns bytes freed. */
    fun clearAll(context: Context): Long {
        val app = context.applicationContext
        var freed = 0L
        listOf(
            File(app.cacheDir, "temp"),
            File(app.cacheDir, "thumbnails"),
            File(app.filesDir, "depth_cache2")
        ).forEach { d -> d.listFiles()?.forEach { freed += deleteCounting(it) } }
        return freed
    }

    fun sizeBytes(context: Context): Long =
        listOf(
            File(context.cacheDir, "temp"),
            File(context.cacheDir, "thumbnails"),
            File(context.filesDir, "depth_cache2")
        ).sumOf { dirSize(it) }

    private fun clean(context: Context) {
        val now = System.currentTimeMillis()
        // Scratch files older than a day
        deleteOlderThan(File(context.cacheDir, "temp"), now - DAY_MS)
        // Half-finished AirShare downloads older than 2 days
        File(context.filesDir, "airshare").listFiles()?.forEach {
            if (it.isFile && (it.name.endsWith(".part") || it.name.endsWith(".tmp")) &&
                it.lastModified() < now - 2 * DAY_MS
            ) it.delete()
        }
        // Depth maps: unused for 30 days, then cap by size (they are recomputed on demand)
        val depth = File(context.filesDir, "depth_cache2")
        deleteOlderThan(depth, now - 30 * DAY_MS)
        trimToSize(depth, DEPTH_CAP)
        // Thumbnail cache: cap by size, oldest first
        trimToSize(File(context.cacheDir, "thumbnails"), THUMB_CAP)
    }

    private fun deleteOlderThan(dir: File, cutoff: Long) {
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) deleteCounting(it) }
    }

    private fun trimToSize(dir: File, cap: Long) {
        val files = dir.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= cap) break
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    private fun deleteCounting(f: File): Long {
        val size = if (f.isDirectory) dirSize(f) else f.length()
        if (f.isDirectory) f.deleteRecursively() else f.delete()
        return size
    }

    private fun dirSize(d: File): Long =
        if (!d.exists()) 0L else d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
