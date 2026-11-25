package com.example.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.local.AlbumDatabase
import com.example.data.local.OcrEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * On-device text recognition (Latin script) so photos can be found by the words in them.
 * Single shared scan: progress and control live here so Settings can drive it without a ViewModel.
 */
object OcrScanner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()
    private val _hold = MutableStateFlow(HoldReason.NONE)
    val hold: StateFlow<HoldReason> = _hold.asStateFlow()
    @Volatile private var control: ScanControl? = null

    val isRunning: Boolean get() = _progress.value != null

    fun pause() { control?.pause() }
    fun resume() { control?.resume() }
    fun stop() { control?.stop() }
    val paused: StateFlow<Boolean>? get() = control?.isPaused

    fun start(context: Context) {
        if (isRunning) return
        val app = context.applicationContext
        val ctl = ScanControl(PowerGate(app))
        control = ctl
        _progress.value = 0 to 1
        val holdJob = scope.launch { ctl.hold.collect { _hold.value = it } }
        scope.launch {
            try {
                val dao = AlbumDatabase.getInstance(app).galleryDao()
                dao.deleteOrphanOcr()
                val done = dao.getOcrScannedIds().toHashSet()
                val todo = dao.getActivePhotosSnapshot().filter { it.id !in done }
                if (todo.isEmpty()) return@launch
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val batch = java.util.Collections.synchronizedList(ArrayList<OcrEntity>())
                try {
                    runParallel(todo, ctl, workers = 1, onProgress = { c, t -> _progress.value = c to t.coerceAtLeast(1) }) { item ->
                        val text = recognize(app, recognizer, Uri.parse(item.uriString))
                        // empty text is stored too, so the photo is not rescanned next time
                        batch.add(OcrEntity(item.id, text))
                        if (batch.size >= 25) flush(dao, batch)
                    }
                } finally {
                    flush(dao, batch)
                    recognizer.close()
                }
            } catch (_: Throwable) {
            } finally {
                holdJob.cancel()
                _hold.value = HoldReason.NONE
                _progress.value = null
                control = null
            }
        }
    }

    private suspend fun flush(dao: com.example.data.local.GalleryDao, batch: MutableList<OcrEntity>) {
        val rows = synchronized(batch) { val c = ArrayList(batch); batch.clear(); c }
        if (rows.isNotEmpty()) dao.upsertOcr(rows)
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        scope.launch { AlbumDatabase.getInstance(app).galleryDao().deleteAllOcr() }
    }

    private suspend fun recognize(
        context: Context,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        uri: Uri
    ): String = withContext(Dispatchers.IO) {
        val bmp = decode(context, uri) ?: return@withContext ""
        try {
            suspendCancellableCoroutine<String> { cont ->
                recognizer.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it.text.replace('\n', ' ').take(4000)) }
                    .addOnFailureListener { if (cont.isActive) cont.resume("") }
            }
        } finally {
            bmp.recycle()
        }
    }

    /** Decode around 1600 px on the long side: enough for text, light on memory. */
    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (longSide / (sample * 2) >= 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}
