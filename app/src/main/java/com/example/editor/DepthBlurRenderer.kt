package com.example.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Cinematic depth-of-field. Builds 3 progressively blurred copies (with highlight bloom for
 * bokeh), then composites them over the sharp image using per-pixel masks derived from the depth
 * map: pixels at the focus depth stay sharp, the further from focus the stronger the blur.
 */
object DepthBlurRenderer {

    private const val MASK_MAX_SIDE = 640

    /**
     * @param image  mutable ARGB bitmap (already colour-graded), modified in place
     * @param depth  grayscale depth bitmap (near = bright) with the same orientation/crop as image
     */
    fun apply(image: Bitmap, depth: Bitmap, focusX: Float, focusY: Float, strength: Float, cacheKey: Int = 0) {
        val s = strength.coerceIn(0f, 1f)
        if (s < 0.02f) return
        val w = image.width
        val h = image.height
        val maxDim = max(w, h)

        // --- coc masks at reduced resolution -------------------------------------------------
        val ms = MASK_MAX_SIDE.toFloat() / max(depth.width, depth.height)
        val mw = if (ms < 1f) (depth.width * ms).roundToInt().coerceAtLeast(8) else depth.width
        val mh = if (ms < 1f) (depth.height * ms).roundToInt().coerceAtLeast(8) else depth.height
        val dScaled = if (mw != depth.width) Bitmap.createScaledBitmap(depth, mw, mh, true) else depth
        val dpx = IntArray(mw * mh)
        dScaled.getPixels(dpx, 0, mw, 0, 0, mw, mh)
        if (dScaled !== depth) dScaled.recycle()

        // Focus depth = average in a small window around the tap
        val fx = (focusX.coerceIn(0f, 1f) * (mw - 1)).roundToInt()
        val fy = (focusY.coerceIn(0f, 1f) * (mh - 1)).roundToInt()
        val rad = max(2, (max(mw, mh) * 0.02f).roundToInt())
        var sum = 0L
        var cnt = 0
        for (y in (fy - rad).coerceAtLeast(0)..(fy + rad).coerceAtMost(mh - 1)) {
            for (x in (fx - rad).coerceAtLeast(0)..(fx + rad).coerceAtMost(mw - 1)) {
                sum += dpx[y * mw + x] and 0xFF
                cnt++
            }
        }
        val focusDepth = sum.toFloat() / cnt.coerceAtLeast(1)

        val deadZone = 0.06f
        val spread = 0.42f
        val m1 = ByteArray(mw * mh)
        val m2 = ByteArray(mw * mh)
        val m3 = ByteArray(mw * mh)
        var maxT = 0f
        for (i in dpx.indices) {
            val x = abs((dpx[i] and 0xFF) - focusDepth) / 255f
            val c = ((x - deadZone) / spread).coerceIn(0f, 1f)
            val t = c * 3f * s
            if (t > maxT) maxT = t
            m1[i] = (t.coerceIn(0f, 1f) * 255f).toInt().toByte()
            m2[i] = ((t - 1f).coerceIn(0f, 1f) * 255f).toInt().toByte()
            m3[i] = ((t - 2f).coerceIn(0f, 1f) * 255f).toInt().toByte()
        }
        if (maxT <= 0.01f) return
        val levelsNeeded = if (maxT > 2f) 3 else if (maxT > 1f) 2 else 1

        // --- blurred levels --------------------------------------------------------------------
        val factors = intArrayOf(2, 4, 8)
        val sigmas = floatArrayOf(0.004f, 0.010f, 0.022f)
        val levels = Array(levelsNeeded) { k -> levelFor(k, image, cacheKey, maxDim, factors, sigmas) }

        // --- composite ---------------------------------------------------------------------------
        val tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val tmpCanvas = Canvas(tmp)
        val out = Canvas(image)
        val dest = Rect(0, 0, w, h)
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
        val dstIn = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        val masks = arrayOf(m1, m2, m3)
        for (k in 0 until levelsNeeded) {
            tmp.eraseColor(0)
            tmpCanvas.drawBitmap(levels[k], null, dest, smooth)
            val mb = Bitmap.createBitmap(mw, mh, Bitmap.Config.ALPHA_8)
            mb.copyPixelsFromBuffer(ByteBuffer.wrap(masks[k]))
            tmpCanvas.drawBitmap(mb, null, dest, dstIn)
            mb.recycle()
            out.drawBitmap(tmp, 0f, 0f, null)
        }
        tmp.recycle()
    }

    // Blurred levels depend only on the colour-graded image, not on strength or focus point,
    // so dragging the slider / tapping a new focus reuses them.
    private var cacheKey = Int.MIN_VALUE
    private var cacheW = 0
    private var cacheH = 0
    private val cache = arrayOfNulls<Bitmap>(3)

    @Synchronized
    fun clearCache() {
        for (i in cache.indices) cache[i] = null  // not recycled: a concurrent render may still be drawing it
        cacheKey = Int.MIN_VALUE
    }

    @Synchronized
    private fun levelFor(k: Int, image: Bitmap, key: Int, maxDim: Int, factors: IntArray, sigmas: FloatArray): Bitmap {
        if (key != cacheKey || image.width != cacheW || image.height != cacheH) {
            clearCache()
            cacheKey = key; cacheW = image.width; cacheH = image.height
        }
        cache[k]?.let { if (!it.isRecycled) return it }
        val f = factors[k]
        val small = downscale(image, f)
        val sw = small.width
        val sh = small.height
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        bloomHighlights(px)
        val r = (maxDim * sigmas[k] / f / 0.8f).roundToInt().coerceIn(1, 12)
        boxBlur(px, sw, sh, r)
        boxBlur(px, sw, sh, r)
        small.setPixels(px, 0, sw, 0, 0, sw, sh)
        cache[k] = small
        return small
    }

    /** Halve repeatedly (avoids aliasing) until reaching 1/factor of the source. */
    private fun downscale(src: Bitmap, factor: Int): Bitmap {
        var cur = src
        var f = 1
        while (f < factor) {
            val next = Bitmap.createScaledBitmap(cur, (cur.width / 2).coerceAtLeast(2), (cur.height / 2).coerceAtLeast(2), true)
            if (cur !== src) cur.recycle()
            cur = next
            f *= 2
        }
        return if (cur === src) src.copy(Bitmap.Config.ARGB_8888, true) else cur.copy(Bitmap.Config.ARGB_8888, true).also { cur.recycle() }
    }

    /** Brightens specular highlights so blurred lights glow like bokeh balls. */
    private fun bloomHighlights(px: IntArray) {
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = (r * 30 + g * 59 + b * 11) / 100
            if (luma > 215) {
                val gain = 1f + 0.9f * ((luma - 215) / 40f).coerceIn(0f, 1f)
                px[i] = (0xFF shl 24) or
                    ((r * gain).toInt().coerceAtMost(255) shl 16) or
                    ((g * gain).toInt().coerceAtMost(255) shl 8) or
                    (b * gain).toInt().coerceAtMost(255)
            }
        }
    }

    /** Separable box blur on packed ARGB (alpha forced opaque), edge-clamped. */
    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        val win = 2 * r + 1
        // horizontal
        for (y in 0 until h) {
            val row = y * w
            var sr = 0; var sg = 0; var sb = 0
            for (k in -r..r) {
                val p = px[row + k.coerceIn(0, w - 1)]
                sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = (0xFF shl 24) or ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
                val add = px[row + (x + r + 1).coerceAtMost(w - 1)]
                val sub = px[row + (x - r).coerceAtLeast(0)]
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
        // vertical
        for (x in 0 until w) {
            var sr = 0; var sg = 0; var sb = 0
            for (k in -r..r) {
                val p = tmp[k.coerceIn(0, h - 1) * w + x]
                sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            }
            for (y in 0 until h) {
                px[y * w + x] = (0xFF shl 24) or ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
                val add = tmp[(y + r + 1).coerceAtMost(h - 1) * w + x]
                val sub = tmp[(y - r).coerceAtLeast(0) * w + x]
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}
