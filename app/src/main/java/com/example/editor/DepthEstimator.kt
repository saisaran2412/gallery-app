package com.example.editor

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litert.Interpreter
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.segmentation.Segmentation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/** Relative depth, 0 = far .. 255 = near, at (w x h) with the source photo's aspect ratio. */
class DepthMap(val w: Int, val h: Int, val data: ByteArray, val mask: ByteArray? = null) {
    /** Person mask as a white ARGB bitmap whose alpha is the mask (for DST_IN compositing), or null. */
    fun maskBitmap(): Bitmap? {
        val m = mask ?: return null
        val px = IntArray(w * h) { i -> ((m[i].toInt() and 0xFF) shl 24) or 0x00FFFFFF }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun toBitmap(): Bitmap {
        val px = IntArray(w * h) { i ->
            val v = data[i].toInt() and 0xFF
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}

/**
 * Offline monocular depth (MiDaS v2 small, 256x256 NHWC 0..1 -> 256x256x1 inverse depth),
 * fused with ML Kit selfie segmentation so a person stays uniformly sharp with clean edges.
 */
object DepthEstimator {
    private const val MODEL = "midas.tflite"
    private const val IN = 256
    private const val OUT_MAX_SIDE = 512

    private var interpreter: Interpreter? = null
    private var failed = false

    private val segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
        )
    }

    @Synchronized
    fun isAvailable(context: Context): Boolean = get(context) != null

    @Synchronized
    private fun get(context: Context): Interpreter? {
        if (interpreter != null) return interpreter
        if (failed) return null
        return runCatching {
            // The model ships as midas_part0.bin, midas_part1.bin ... (each under 30 MB so AI Studio
            // accepts them); a single midas.tflite still works if present.
            val chunks = ArrayList<ByteArray>()
            var i = 0
            while (true) {
                val part = runCatching { context.assets.open("midas_part$i.bin").use { it.readBytes() } }.getOrNull() ?: break
                chunks.add(part); i++
            }
            if (chunks.isEmpty()) chunks.add(context.assets.open(MODEL).use { it.readBytes() })
            val buf = ByteBuffer.allocateDirect(chunks.sumOf { it.size }).order(ByteOrder.nativeOrder())
            chunks.forEach { buf.put(it) }
            buf.rewind()
            Interpreter(buf, Interpreter.Options().apply { setNumThreads(4) })
        }.onFailure { failed = true }.getOrNull().also { interpreter = it }
    }

    private const val CACHE_DIR = "depth_cache2"  // v2: edge-refined maps
    private const val CACHE_MAX_FILES = 80

    /** Disk-cached [estimate]: the same photo is never analysed twice. */
    fun estimateCached(context: Context, mediaId: String, source: Bitmap): DepthMap? {
        val dir = java.io.File(context.filesDir, CACHE_DIR).apply { mkdirs() }
        runCatching { java.io.File(context.filesDir, "depth_cache").takeIf { it.exists() }?.deleteRecursively() }  // old unrefined maps
        val f = java.io.File(dir, mediaId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".bin")
        val aspect = source.width.toFloat() / source.height
        runCatching {
            if (f.exists()) {
                java.io.DataInputStream(f.inputStream().buffered()).use { inp ->
                    val w = inp.readInt(); val h = inp.readInt()
                    if (w > 0 && h > 0 && kotlin.math.abs(w.toFloat() / h - aspect) / aspect < 0.03f) {
                        val data = ByteArray(w * h)
                        inp.readFully(data)
                        val mask = if (inp.read() == 1) ByteArray(w * h).also { inp.readFully(it) } else null
                        f.setLastModified(System.currentTimeMillis())
                        return DepthMap(w, h, data, mask)
                    }
                }
            }
        }
        val d = estimate(context, source) ?: return null
        runCatching {
            java.io.DataOutputStream(f.outputStream().buffered()).use { out ->
                out.writeInt(d.w); out.writeInt(d.h); out.write(d.data)
                if (d.mask != null) { out.writeByte(1); out.write(d.mask) } else out.writeByte(0)
            }
            val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            if (files.size > CACHE_MAX_FILES) files.take(files.size - CACHE_MAX_FILES).forEach { it.delete() }
        }
        return d
    }

    /** Blocking; call off the main thread. Returns null if the model is missing or fails. */
    fun estimate(context: Context, source: Bitmap): DepthMap? {
        val interp = get(context) ?: return null
        return runCatching {
            // 0. Start person segmentation now so it runs in parallel with MiDaS
            val sc0 = OUT_MAX_SIDE.toFloat() / max(source.width, source.height)
            val sw = (source.width * sc0).toInt().coerceAtLeast(16)
            val sh = (source.height * sc0).toInt().coerceAtLeast(16)
            val segBmp = Bitmap.createScaledBitmap(source, sw, sh, true)
            val segTask = runCatching { segmenter.process(InputImage.fromBitmap(segBmp, 0)) }.getOrNull()

            // 1. Run MiDaS
            val small = Bitmap.createScaledBitmap(source, IN, IN, true)
            val px = IntArray(IN * IN)
            small.getPixels(px, 0, IN, 0, 0, IN, IN)
            if (small !== source) small.recycle()
            val input = ByteBuffer.allocateDirect(IN * IN * 3 * 4).order(ByteOrder.nativeOrder())
            val fb = input.asFloatBuffer()
            for (p in px) {
                fb.put(((p shr 16) and 0xFF) / 255f)
                fb.put(((p shr 8) and 0xFF) / 255f)
                fb.put((p and 0xFF) / 255f)
            }
            input.rewind()
            val output = ByteBuffer.allocateDirect(IN * IN * 4).order(ByteOrder.nativeOrder())
            synchronized(this) { interp.run(input, output) }
            output.rewind()
            val raw = FloatArray(IN * IN)
            output.asFloatBuffer().get(raw)

            // 2. Robust normalise to 0..255 (near = 255)
            val sorted = raw.copyOf().also { it.sort() }
            val lo = sorted[(sorted.size * 0.02f).toInt()]
            val hi = sorted[(sorted.size * 0.98f).toInt().coerceAtMost(sorted.size - 1)]
            val range = max(hi - lo, 1e-6f)
            val gray = IntArray(IN * IN) { i ->
                val v = (((raw[i] - lo) / range).coerceIn(0f, 1f) * 255f).toInt()
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            val grayBmp = Bitmap.createBitmap(gray, IN, IN, Bitmap.Config.ARGB_8888)

            // 3. Upscale to source aspect (<=512)
            val scale = OUT_MAX_SIDE.toFloat() / max(source.width, source.height)
            val dw = (source.width * scale).toInt().coerceAtLeast(16)
            val dh = (source.height * scale).toInt().coerceAtLeast(16)
            val up = Bitmap.createScaledBitmap(grayBmp, dw, dh, true)
            grayBmp.recycle()
            val upPx = IntArray(dw * dh)
            up.getPixels(upPx, 0, dw, 0, 0, dw, dh)
            val depth = ByteArray(dw * dh) { (upPx[it] and 0xFF).toByte() }

            // 4. Fuse person mask (best-effort)
            var personMask: ByteArray? = null
            runCatching { segTask?.let { personMask = fusePerson(Tasks.await(it), depth, dw, dh) } }

            // 5. Edge-aware refinement: snap depth and the person mask to the photo's own edges
            if (segBmp.width == dw && segBmp.height == dh) {
                runCatching { refineWithGuide(depth, segBmp, dw, dh) }
                personMask?.let { pm ->
                    runCatching {
                        refineWithGuide(pm, segBmp, dw, dh)
                        for (i in pm.indices) {   // crisp edge: smoothstep(0.3, 0.7)
                            val t = (((pm[i].toInt() and 0xFF) / 255f - 0.3f) / 0.4f).coerceIn(0f, 1f)
                            pm[i] = ((t * t * (3f - 2f * t)) * 255f).toInt().toByte()
                        }
                    }
                }
            }
            if (segBmp !== source) segBmp.recycle()

            if (up !== grayBmp) up.recycle()
            DepthMap(dw, dh, depth, personMask)
        }.getOrNull()
    }

    /**
     * Guided filter (He et al.): filters [depth] using the photo's luminance as the guide, so depth
     * edges follow real image edges instead of the soft 256 px MiDaS / segmentation edges.
     */
    private fun refineWithGuide(depth: ByteArray, guideBmp: Bitmap, w: Int, h: Int) {
        val n = w * h
        val px = IntArray(n)
        guideBmp.getPixels(px, 0, w, 0, 0, w, h)
        val guide = FloatArray(n) { i ->
            val c = px[i]
            (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
        }
        val p = FloatArray(n) { (depth[it].toInt() and 0xFF) / 255f }
        val r = 6
        val eps = 0.0006f
        val meanI = boxFilter(guide, w, h, r)
        val meanP = boxFilter(p, w, h, r)
        val ii = FloatArray(n) { guide[it] * guide[it] }
        val ip = FloatArray(n) { guide[it] * p[it] }
        val corrI = boxFilter(ii, w, h, r)
        val corrIp = boxFilter(ip, w, h, r)
        val a = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val varI = corrI[i] - meanI[i] * meanI[i]
            val covIp = corrIp[i] - meanI[i] * meanP[i]
            a[i] = covIp / (varI + eps)
            b[i] = meanP[i] - a[i] * meanI[i]
        }
        val meanA = boxFilter(a, w, h, r)
        val meanB = boxFilter(b, w, h, r)
        for (i in 0 until n) {
            val q = meanA[i] * guide[i] + meanB[i]
            depth[i] = (q.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
    }

    /** Mean over a (2r+1) square window, borders use the available pixels only. O(w*h). */
    private fun boxFilter(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            var cnt = 0
            for (x in 0..minOf(r, w - 1)) { sum += src[row + x]; cnt++ }
            for (x in 0 until w) {
                tmp[row + x] = sum / cnt
                val add = x + r + 1
                val rem = x - r
                if (add < w) { sum += src[row + add]; cnt++ }
                if (rem >= 0) { sum -= src[row + rem]; cnt-- }
            }
        }
        for (x in 0 until w) {
            var sum = 0f
            var cnt = 0
            for (y in 0..minOf(r, h - 1)) { sum += tmp[y * w + x]; cnt++ }
            for (y in 0 until h) {
                out[y * w + x] = sum / cnt
                val add = y + r + 1
                val rem = y - r
                if (add < h) { sum += tmp[add * w + x]; cnt++ }
                if (rem >= 0) { sum -= tmp[rem * w + x]; cnt-- }
            }
        }
        return out
    }

    /** Flattens the person's depth to its median and returns the person mask (0..255), or null if no person. */
    private fun fusePerson(mask: com.google.mlkit.vision.segmentation.SegmentationMask, depth: ByteArray, w: Int, h: Int): ByteArray? {
        val mw = mask.width
        val mh = mask.height
        val buf = mask.buffer
        buf.rewind()
        val conf = FloatArray(mw * mh)
        buf.asFloatBuffer().get(conf)

        // Median depth inside the person
        val hist = IntArray(256)
        var count = 0
        for (y in 0 until h) {
            val my = (y * mh / h).coerceAtMost(mh - 1)
            for (x in 0 until w) {
                val mx = (x * mw / w).coerceAtMost(mw - 1)
                if (conf[my * mw + mx] > 0.65f) {
                    hist[depth[y * w + x].toInt() and 0xFF]++
                    count++
                }
            }
        }
        if (count < w * h * 0.01f) return null
        var acc = 0
        var median = 128
        for (i in 0..255) { acc += hist[i]; if (acc >= count / 2) { median = i; break } }

        val out = ByteArray(w * h)
        for (y in 0 until h) {
            val my = (y * mh / h).coerceAtMost(mh - 1)
            for (x in 0 until w) {
                val mx = (x * mw / w).coerceAtMost(mw - 1)
                val c = conf[my * mw + mx]
                out[y * w + x] = (((c - 0.2f) / 0.6f).coerceIn(0f, 1f) * 255f).toInt().toByte()
                if (c > 0.35f) {
                    val t = ((c - 0.35f) / 0.4f).coerceIn(0f, 1f)
                    val i = y * w + x
                    val d = depth[i].toInt() and 0xFF
                    depth[i] = (d + (median - d) * t).toInt().coerceIn(0, 255).toByte()
                }
            }
        }
        return out
    }
}
