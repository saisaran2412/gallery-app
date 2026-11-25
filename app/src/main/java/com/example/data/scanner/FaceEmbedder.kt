package com.example.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.ai.edge.litert.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * MobileFaceNet (112x112 RGB, NCHW, 0..1 -> 128-d). The bundled model takes two images per
 * run (img1, img2 -> [2,128]), so faces are embedded in pairs.
 */
object FaceEmbedder {
    const val SIZE = 112
    const val DIM = 128
    private const val MODEL = "mobilefacenet.tflite"
    private const val PLANE = SIZE * SIZE

    private var interpreter: Interpreter? = null
    private var failed = false

    @Synchronized
    fun isAvailable(context: Context): Boolean = get(context) != null

    @Synchronized
    private fun get(context: Context): Interpreter? {
        if (interpreter != null) return interpreter
        if (failed) return null
        return runCatching {
            val bytes = context.assets.open(MODEL).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes); buf.rewind()
            Interpreter(buf, Interpreter.Options().apply { setNumThreads(2) })
        }.onFailure { failed = true }.getOrNull().also { interpreter = it }
    }

    /**
     * Aligns a face to 112x112 using the eyes (similarity transform to the standard template)
     * and returns CHW float pixels in 0..1.
     */
    fun alignToChw(src: Bitmap, lx: Float, ly: Float, rx: Float, ry: Float): FloatArray {
        // lx must be the smaller-x eye.
        val dx = rx - lx
        val dy = ry - ly
        val eyeDist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val scale = 35.2f / eyeDist
        val m = Matrix().apply {
            postTranslate(-(lx + rx) / 2f, -(ly + ry) / 2f)
            postRotate(-angle)
            postScale(scale, scale)
            postTranslate(55.9f, 51.6f)
        }
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        val px = IntArray(PLANE)
        out.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        out.recycle()
        val chw = FloatArray(3 * PLANE)
        for (i in 0 until PLANE) {
            val p = px[i]
            chw[i] = ((p shr 16) and 0xFF) / 255f
            chw[PLANE + i] = ((p shr 8) and 0xFF) / 255f
            chw[2 * PLANE + i] = (p and 0xFF) / 255f
        }
        return chw
    }

    /** Returns L2-normalised embeddings, same order as input; null entries if inference failed. */
    @Synchronized
    fun embed(context: Context, faces: List<FloatArray>): List<FloatArray?> {
        val it = get(context) ?: return faces.map { null }
        val result = ArrayList<FloatArray?>(faces.size)
        var i = 0
        while (i < faces.size) {
            val a = faces[i]
            val b = if (i + 1 < faces.size) faces[i + 1] else a
            val out = runCatching {
                val inA = toBuffer(a)
                val inB = toBuffer(b)
                val o = Array(2) { FloatArray(DIM) }
                it.runForMultipleInputsOutputs(arrayOf<Any>(inA, inB), mapOf<Int, Any>(0 to o))
                o
            }.getOrNull()
            result.add(out?.get(0)?.let(::l2))
            if (i + 1 < faces.size) result.add(out?.get(1)?.let(::l2))
            i += 2
        }
        return result
    }

    private fun toBuffer(chw: FloatArray): ByteBuffer {
        val b = ByteBuffer.allocateDirect(chw.size * 4).order(ByteOrder.nativeOrder())
        b.asFloatBuffer().put(chw)
        b.rewind()
        return b
    }

    fun l2(v: FloatArray): FloatArray {
        var n = 0f
        for (x in v) n += x * x
        n = sqrt(n).coerceAtLeast(1e-6f)
        return FloatArray(v.size) { v[it] / n }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        var d = 0f
        for (i in a.indices) d += a[i] * b[i]
        return d
    }

    fun encode(v: FloatArray): String = v.joinToString(",") { "%.5f".format(java.util.Locale.US, it) }

    fun decode(s: String): FloatArray? {
        if (s.isEmpty()) return null
        val parts = s.split(',')
        if (parts.size != DIM) return null
        val out = FloatArray(DIM)
        for (i in 0 until DIM) out[i] = parts[i].toFloatOrNull() ?: return null
        return out
    }
}
