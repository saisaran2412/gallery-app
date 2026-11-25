package com.example.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import java.security.MessageDigest

data class DuplicateGroup(
    val id: String,
    val title: String,
    val isExact: Boolean,
    val items: List<MediaItemEntity>
)

object MediaHashingEngine {

    fun computeSha256(context: Context, uri: Uri): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16 * 1024)
            context.contentResolver.openInputStream(uri)?.use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            } ?: return ""
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    /**
     * Computes a 64-bit difference hash (dHash) on a 9x8 grayscale downsample
     * for offline visually similar photo detection.
     */
    fun computeDHash(context: Context, uri: Uri): String {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ""

            var sample = 1
            while (bounds.outWidth / sample > 128 && bounds.outHeight / sample > 128) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return ""

            val tiny = Bitmap.createScaledBitmap(decoded, 9, 8, true)
            if (tiny !== decoded) decoded.recycle()

            var hash = 0L
            var bit = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val left = luma(tiny.getPixel(x, y))
                    val right = luma(tiny.getPixel(x + 1, y))
                    if (left > right) {
                        hash = hash or (1L shl bit)
                    }
                    bit++
                }
            }
            tiny.recycle()
            "%016x".format(hash)
        }.getOrDefault("")
    }

    private fun luma(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 30 + g * 59 + b * 11) / 100
    }

    fun hammingDistance(hexA: String, hexB: String): Int {
        if (hexA.length != 16 || hexB.length != 16) return 64
        val a = hexA.toULongOrNull(16) ?: return 64
        val b = hexB.toULongOrNull(16) ?: return 64
        return (a xor b).countOneBits()
    }

    fun findDuplicateGroups(items: List<MediaItemEntity>): List<DuplicateGroup> {
        val result = mutableListOf<DuplicateGroup>()
        val exactIds = mutableSetOf<String>()

        // 1. Exact SHA-256 duplicates (photos & videos)
        items.filter { it.sha256Hash.isNotEmpty() }
            .groupBy { it.sha256Hash }
            .filterValues { it.size > 1 }
            .forEach { (hash, group) ->
                exactIds.addAll(group.map { it.id })
                result.add(
                    DuplicateGroup(
                        id = "exact_$hash",
                        title = "Exact Match (${group.size} files)",
                        isExact = true,
                        items = group.sortedByDescending { it.sizeBytes }
                    )
                )
            }

        // 2. Perceptual dHash similar photos (Hamming distance <= 7)
        val remainingPhotos = items.filter {
            it.mediaType == MediaType.PHOTO && it.dHash.isNotEmpty() && it.id !in exactIds
        }
        val visited = mutableSetOf<String>()
        for (i in remainingPhotos.indices) {
            val anchor = remainingPhotos[i]
            if (anchor.id in visited) continue
            val cluster = mutableListOf(anchor)
            for (j in (i + 1) until remainingPhotos.size) {
                val candidate = remainingPhotos[j]
                if (candidate.id in visited) continue
                if (hammingDistance(anchor.dHash, candidate.dHash) <= 7) {
                    cluster.add(candidate)
                }
            }
            if (cluster.size > 1) {
                cluster.forEach { visited.add(it.id) }
                result.add(
                    DuplicateGroup(
                        id = "sim_${anchor.id}",
                        title = "Visually Similar (${cluster.size} photos)",
                        isExact = false,
                        items = cluster.sortedByDescending { it.width * it.height }
                    )
                )
            }
        }

        return result
    }
}
