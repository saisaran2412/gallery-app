package com.example.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import com.example.core.storage.AppStorageDirs
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

enum class FilterPreset(val label: String) {
    ORIGINAL("Original"),
    VIVID("Vivid"),
    WARM_FILM("Warm Film"),
    COOL_MIST("Cool Mist"),
    NOIR_BW("Noir B&W"),
    SEPIA("Vintage"),
    EMERALD("Emerald"),
    SUNSET("Sunset")
}

enum class CropAspect(val label: String, val ratio: Float?) {
    FREE("Original", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_FOUR("3:4", 3f / 4f),
    SIXTEEN_NINE("16:9", 16f / 9f)
}

data class TextOverlayItem(
    val id: String,
    val text: String,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.5f,
    val colorArgb: Long = 0xFFFFFFFFL,
    val scale: Float = 1f,
    val behind: Boolean = false   // "text depth": drawn behind the subject
)

data class StickerOverlayItem(
    val id: String,
    val emoji: String,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.35f,
    val scale: Float = 1f
)

data class EditParams(
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val cropAspect: CropAspect = CropAspect.FREE,
    val brightness: Float = 0f,      // -1f .. 1f
    val contrast: Float = 1f,        // 0.5f .. 1.8f
    val saturation: Float = 1f,      // 0f .. 2f
    val temperature: Float = 0f,     // -1f (cool) .. 1f (warm)
    val vignette: Float = 0f,        // 0f .. 1f
    val portraitBlur: Float = 0f,    // 0f .. 1f (offline radial depth blur)
    val depthBlur: Float = 0f,       // 0f .. 1f cinematic depth-of-field strength (MiDaS)
    val depthFocusX: Float = 0.5f,   // focus point, fraction of final image
    val depthFocusY: Float = 0.45f,
    val cutoutMask: Boolean = false, // offline subject spotlight cut-out
    val filterPreset: FilterPreset = FilterPreset.ORIGINAL,
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val stickerOverlays: List<StickerOverlayItem> = emptyList()
) {
    val isModified: Boolean
        get() = rotationDegrees != 0 ||
            flipHorizontal ||
            cropAspect != CropAspect.FREE ||
            brightness != 0f ||
            contrast != 1f ||
            saturation != 1f ||
            temperature != 0f ||
            vignette != 0f ||
            portraitBlur != 0f ||
            depthBlur != 0f ||
            cutoutMask ||
            filterPreset != FilterPreset.ORIGINAL ||
            textOverlays.isNotEmpty() ||
            stickerOverlays.isNotEmpty()

    fun toJson(): String {
        val root = JSONObject()
        root.put("rotationDegrees", rotationDegrees)
        root.put("flipHorizontal", flipHorizontal)
        root.put("cropAspect", cropAspect.name)
        root.put("brightness", brightness.toDouble())
        root.put("contrast", contrast.toDouble())
        root.put("saturation", saturation.toDouble())
        root.put("temperature", temperature.toDouble())
        root.put("vignette", vignette.toDouble())
        root.put("portraitBlur", portraitBlur.toDouble())
        root.put("depthBlur", depthBlur.toDouble())
        root.put("depthFocusX", depthFocusX.toDouble())
        root.put("depthFocusY", depthFocusY.toDouble())
        root.put("cutoutMask", cutoutMask)
        root.put("filterPreset", filterPreset.name)

        val texts = JSONArray()
        textOverlays.forEach { t ->
            texts.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("text", t.text)
                    put("x", t.xFraction.toDouble())
                    put("y", t.yFraction.toDouble())
                    put("color", t.colorArgb)
                    put("scale", t.scale.toDouble())
                    put("behind", t.behind)
                }
            )
        }
        root.put("textOverlays", texts)

        val stickers = JSONArray()
        stickerOverlays.forEach { s ->
            stickers.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("emoji", s.emoji)
                    put("x", s.xFraction.toDouble())
                    put("y", s.yFraction.toDouble())
                    put("scale", s.scale.toDouble())
                }
            )
        }
        root.put("stickerOverlays", stickers)
        return root.toString()
    }

    companion object {
        fun fromJson(json: String): EditParams {
            if (json.isBlank()) return EditParams()
            return runCatching {
                val obj = JSONObject(json)
                val textsArr = obj.optJSONArray("textOverlays") ?: JSONArray()
                val texts = buildList {
                    for (i in 0 until textsArr.length()) {
                        val item = textsArr.getJSONObject(i)
                        add(
                            TextOverlayItem(
                                id = item.optString("id"),
                                text = item.optString("text"),
                                xFraction = item.optDouble("x", 0.5).toFloat(),
                                yFraction = item.optDouble("y", 0.5).toFloat(),
                                colorArgb = item.optLong("color", 0xFFFFFFFFL),
                                scale = item.optDouble("scale", 1.0).toFloat(),
                                behind = item.optBoolean("behind", false)
                            )
                        )
                    }
                }
                val stickersArr = obj.optJSONArray("stickerOverlays") ?: JSONArray()
                val stickers = buildList {
                    for (i in 0 until stickersArr.length()) {
                        val item = stickersArr.getJSONObject(i)
                        add(
                            StickerOverlayItem(
                                id = item.optString("id"),
                                emoji = item.optString("emoji"),
                                xFraction = item.optDouble("x", 0.5).toFloat(),
                                yFraction = item.optDouble("y", 0.35).toFloat(),
                                scale = item.optDouble("scale", 1.0).toFloat()
                            )
                        )
                    }
                }
                EditParams(
                    rotationDegrees = obj.optInt("rotationDegrees", 0),
                    flipHorizontal = obj.optBoolean("flipHorizontal", false),
                    cropAspect = CropAspect.entries.find { it.name == obj.optString("cropAspect") }
                        ?: CropAspect.FREE,
                    brightness = obj.optDouble("brightness", 0.0).toFloat(),
                    contrast = obj.optDouble("contrast", 1.0).toFloat(),
                    saturation = obj.optDouble("saturation", 1.0).toFloat(),
                    temperature = obj.optDouble("temperature", 0.0).toFloat(),
                    vignette = obj.optDouble("vignette", 0.0).toFloat(),
                    portraitBlur = obj.optDouble("portraitBlur", 0.0).toFloat(),
                    depthBlur = obj.optDouble("depthBlur", 0.0).toFloat(),
                    depthFocusX = obj.optDouble("depthFocusX", 0.5).toFloat(),
                    depthFocusY = obj.optDouble("depthFocusY", 0.45).toFloat(),
                    cutoutMask = obj.optBoolean("cutoutMask", false),
                    filterPreset = FilterPreset.entries.find { it.name == obj.optString("filterPreset") }
                        ?: FilterPreset.ORIGINAL,
                    textOverlays = texts,
                    stickerOverlays = stickers
                )
            }.getOrDefault(EditParams())
        }
    }
}

object PhotoEditorEngine {

    fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var inSampleSize = 1
        while (rawW / inSampleSize > maxDimension * 2 || rawH / inSampleSize > maxDimension * 2) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val maxSide = maxOf(decoded.width, decoded.height)
        if (maxSide <= maxDimension) return decoded
        val scale = maxDimension.toFloat() / maxSide.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt().coerceAtLeast(1),
            (decoded.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
        if (scaled != decoded) decoded.recycle()
        return scaled
    }

    fun renderEditedBitmap(source: Bitmap, params: EditParams, depth: DepthMap? = null): Bitmap {
        // 1. Rotate & Flip
        val matrix = Matrix().apply {
            if (params.rotationDegrees != 0) postRotate(params.rotationDegrees.toFloat())
            if (params.flipHorizontal) postScale(-1f, 1f)
        }
        val transformed = if (!matrix.isIdentity) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        // 2. Center Crop to aspect ratio if selected
        val cropped = applyAspectCrop(transformed, params.cropAspect)
        if (cropped !== transformed && transformed !== source) transformed.recycle()

        // 3. Apply ColorMatrix (adjustments + filter preset)
        val out = createBitmap(cropped.width, cropped.height)
        val canvas = Canvas(out)
        val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(buildColorMatrix(params))
        }
        canvas.drawBitmap(cropped, 0f, 0f, colorPaint)
        if (cropped !== source) cropped.recycle()

        // 3b. Depth map (and person mask) go through the same rotate/flip/crop; used for depth blur and text depth
        val wantsBehind = depth != null && params.textOverlays.any { it.behind }
        var depthC: Bitmap? = null
        var maskC: Bitmap? = null
        if (depth != null && (params.depthBlur > 0.02f || wantsBehind)) {
            fun xform(b: Bitmap): Bitmap {
                val t = if (!matrix.isIdentity) Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true) else b
                val c = applyAspectCrop(t, params.cropAspect)
                if (t !== b && t !== c) t.recycle()
                return c
            }
            val depthBase = depth.toBitmap()
            depthC = xform(depthBase)
            if (depthBase !== depthC) depthBase.recycle()
            if (wantsBehind) {
                maskC = depth.maskBitmap()?.let { m -> xform(m).also { if (m !== it) m.recycle() } }
            }
            if (params.depthBlur > 0.02f) {
                runCatching {
                    DepthBlurRenderer.apply(
                        out, depthC, params.depthFocusX, params.depthFocusY, params.depthBlur,
                        cacheKey = java.util.Objects.hash(
                            System.identityHashCode(source), params.rotationDegrees, params.flipHorizontal,
                            params.cropAspect, params.brightness, params.contrast, params.saturation,
                            params.temperature, params.filterPreset
                        )
                    )
                }
            }
        }

        // 4. Optional offline Portrait Depth Blur or Subject Cut-out Spotlight
        if (params.portraitBlur > 0.05f) {
            applyRadialDepthBlur(canvas, out, params.portraitBlur)
        }
        if (params.cutoutMask) {
            applySubjectCutoutSpotlight(canvas, out)
        }

        // 5. Vignette
        if (params.vignette > 0.02f) {
            val cx = out.width / 2f
            val cy = out.height / 2f
            val radius = maxOf(out.width, out.height) * 0.72f
            val alpha = (params.vignette.coerceIn(0f, 1f) * 190).roundToInt()
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(0x00000000, 0x00000000, (alpha shl 24)),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), vignettePaint)
        }

        // 6. Stickers & Text Overlays
        val baseTextSize = min(out.width, out.height) * 0.065f
        params.stickerOverlays.forEach { sticker ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = baseTextSize * 1.45f * sticker.scale
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                sticker.emoji,
                out.width * sticker.xFraction,
                out.height * sticker.yFraction,
                p
            )
        }

        fun drawOverlayText(overlay: TextOverlayItem) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = baseTextSize * overlay.scale
                color = overlay.colorArgb.toInt()
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                setShadowLayer(8f, 0f, 3f, 0x99000000.toInt())
            }
            canvas.drawText(
                overlay.text,
                out.width * overlay.xFraction,
                out.height * overlay.yFraction,
                p
            )
        }

        val behindTexts = params.textOverlays.filter { it.behind }
        val frontTexts = params.textOverlays.filter { !it.behind }
        val dMap = depthC
        if (behindTexts.isNotEmpty() && dMap != null) {
            // Text depth: snapshot the picture, draw the text, then paint the subject (cut out by mask) on top.
            val subjectMask = buildSubjectMask(maskC, dMap, params, out.width, out.height)
            val snapshot = out.copy(Bitmap.Config.ARGB_8888, false)
            behindTexts.forEach { drawOverlayText(it) }
            if (subjectMask != null && snapshot != null) {
                val layer = createBitmap(out.width, out.height)
                val lc = Canvas(layer)
                lc.drawBitmap(snapshot, 0f, 0f, null)
                val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                }
                lc.drawBitmap(subjectMask, 0f, 0f, maskPaint)
                canvas.drawBitmap(layer, 0f, 0f, null)
                layer.recycle()
            }
            snapshot?.recycle()
            subjectMask?.recycle()
        } else {
            behindTexts.forEach { drawOverlayText(it) }
        }
        frontTexts.forEach { drawOverlayText(it) }

        depthC?.let { if (!it.isRecycled) it.recycle() }
        maskC?.let { if (!it.isRecycled) it.recycle() }

        return out
    }

    /**
     * Subject mask (white, alpha = how much is subject) at the final image size. Uses the refined person
     * mask when there is one; otherwise everything about as near as the focus point counts as subject.
     */
    private fun buildSubjectMask(maskC: Bitmap?, depthC: Bitmap, params: EditParams, outW: Int, outH: Int): Bitmap? {
        return runCatching {
            if (maskC != null) {
                return@runCatching Bitmap.createScaledBitmap(maskC, outW, outH, true)
            }
            val w = depthC.width
            val h = depthC.height
            val px = IntArray(w * h)
            depthC.getPixels(px, 0, w, 0, 0, w, h)
            val fx = (params.depthFocusX * (w - 1)).roundToInt().coerceIn(0, w - 1)
            val fy = (params.depthFocusY * (h - 1)).roundToInt().coerceIn(0, h - 1)
            var sum = 0f
            var cnt = 0
            for (dy in -3..3) for (dx in -3..3) {
                val x = (fx + dx).coerceIn(0, w - 1)
                val y = (fy + dy).coerceIn(0, h - 1)
                sum += (px[y * w + x] and 0xFF) / 255f
                cnt++
            }
            val d0 = sum / cnt
            val lo = d0 - 0.16f
            val hi = d0 - 0.07f
            for (i in px.indices) {
                val d = (px[i] and 0xFF) / 255f
                val t = ((d - lo) / (hi - lo)).coerceIn(0f, 1f)
                px[i] = (((t * t * (3f - 2f * t)) * 255f).toInt() shl 24) or 0x00FFFFFF
            }
            val small = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            val big = Bitmap.createScaledBitmap(small, outW, outH, true)
            if (big !== small) small.recycle()
            big
        }.getOrNull()
    }

    private fun applyAspectCrop(bitmap: Bitmap, aspect: CropAspect): Bitmap {
        val targetRatio = aspect.ratio ?: return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val currentRatio = w.toFloat() / h.toFloat()
        val (cropW, cropH) = if (currentRatio > targetRatio) {
            (h * targetRatio).roundToInt().coerceAtLeast(1) to h
        } else {
            w to (w / targetRatio).roundToInt().coerceAtLeast(1)
        }
        val startX = ((w - cropW) / 2).coerceAtLeast(0)
        val startY = ((h - cropH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(bitmap, startX, startY, cropW.coerceAtMost(w), cropH.coerceAtMost(h))
    }

    private fun buildColorMatrix(params: EditParams): ColorMatrix {
        val cm = ColorMatrix()

        // Preset base
        when (params.filterPreset) {
            FilterPreset.ORIGINAL -> Unit
            FilterPreset.VIVID -> {
                cm.setSaturation(1.35f)
            }
            FilterPreset.WARM_FILM -> {
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.10f, 0f, 0f, 0f, 10f,
                            0f, 1.02f, 0f, 0f, 4f,
                            0f, 0f, 0.88f, 0f, -6f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            FilterPreset.COOL_MIST -> {
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            0.90f, 0f, 0f, 0f, -4f,
                            0f, 1.02f, 0f, 0f, 4f,
                            0f, 0f, 1.14f, 0f, 12f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            FilterPreset.NOIR_BW -> {
                cm.setSaturation(0f)
                val contrastCm = ColorMatrix(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, -20f,
                        0f, 1.2f, 0f, 0f, -20f,
                        0f, 0f, 1.2f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(contrastCm)
            }
            FilterPreset.SEPIA -> {
                cm.setSaturation(0f)
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1f, 0f, 0f, 0f, 32f,
                            0f, 0.92f, 0f, 0f, 18f,
                            0f, 0f, 0.72f, 0f, -8f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            FilterPreset.EMERALD -> {
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            0.92f, 0f, 0f, 0f, 0f,
                            0f, 1.12f, 0f, 0f, 8f,
                            0f, 0f, 0.96f, 0f, 4f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            FilterPreset.SUNSET -> {
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.18f, 0f, 0f, 0f, 14f,
                            0f, 0.96f, 0f, 0f, 2f,
                            0f, 0f, 0.85f, 0f, -10f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
        }

        // User Saturation
        if (params.saturation != 1f) {
            val satCm = ColorMatrix().apply { setSaturation(params.saturation) }
            cm.postConcat(satCm)
        }

        // Contrast & Brightness
        val c = params.contrast
        val b = params.brightness * 120f
        val translate = (-0.5f * c + 0.5f) * 255f + b
        val bcCm = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(bcCm)

        // Temperature (warm/cool)
        if (params.temperature != 0f) {
            val t = params.temperature * 34f
            val tempCm = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, t,
                    0f, 1f, 0f, 0f, t * 0.2f,
                    0f, 0f, 1f, 0f, -t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(tempCm)
        }

        return cm
    }

    /**
     * Offline portrait depth-blur effect: creates a softly blurred background layer
     * and preserves a crisp central subject ellipse using radial masking.
     */
    private fun applyRadialDepthBlur(canvas: Canvas, sharpBitmap: Bitmap, strength: Float) {
        val factor = (0.14f - (strength * 0.08f)).coerceIn(0.05f, 0.18f)
        val tinyW = (sharpBitmap.width * factor).roundToInt().coerceAtLeast(8)
        val tinyH = (sharpBitmap.height * factor).roundToInt().coerceAtLeast(8)
        val tiny = Bitmap.createScaledBitmap(sharpBitmap, tinyW, tinyH, true)
        val blurred = Bitmap.createScaledBitmap(tiny, sharpBitmap.width, sharpBitmap.height, true)
        tiny.recycle()

        val layer = createBitmap(sharpBitmap.width, sharpBitmap.height)
        val layerCanvas = Canvas(layer)
        layerCanvas.drawBitmap(blurred, 0f, 0f, null)
        blurred.recycle()

        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            shader = RadialGradient(
                sharpBitmap.width * 0.5f,
                sharpBitmap.height * 0.46f,
                min(sharpBitmap.width, sharpBitmap.height) * 0.48f,
                intArrayOf(0xFF000000.toInt(), 0xAA000000.toInt(), 0x00000000),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        layerCanvas.drawRect(0f, 0f, layer.width.toFloat(), layer.height.toFloat(), clearPaint)
        canvas.drawBitmap(layer, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (strength.coerceIn(0f, 1f) * 235).roundToInt()
        })
        layer.recycle()
    }

    private fun applySubjectCutoutSpotlight(canvas: Canvas, bitmap: Bitmap) {
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                bitmap.width * 0.5f,
                bitmap.height * 0.45f,
                min(bitmap.width, bitmap.height) * 0.46f,
                intArrayOf(0x00000000, 0x22000000, 0xEE0B1320.toInt()),
                floatArrayOf(0f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), maskPaint)
    }

    fun exportBitmapToMediaStore(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        relativePath: String,
        quality: Int = 92
    ): Uri? {
        val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val resolver = context.contentResolver

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                relativePath.removePrefix("Pictures/")
            ).apply { mkdirs() }
            val outFile = File(dir, cleanName)
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), fos)
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, outFile.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
    }

    fun saveCompressedPreviewEstimate(
        bitmap: Bitmap,
        scaleFraction: Float,
        quality: Int
    ): Pair<Bitmap, Long> {
        val targetW = (bitmap.width * scaleFraction).roundToInt().coerceAtLeast(16)
        val targetH = (bitmap.height * scaleFraction).roundToInt().coerceAtLeast(16)
        val scaled = if (scaleFraction < 0.99f) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), baos)
        val bytes = baos.toByteArray()
        val decodedPreview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: scaled
        if (scaled !== bitmap && scaled !== decodedPreview) scaled.recycle()
        return decodedPreview to bytes.size.toLong()
    }

    fun saveWorkingCopy(context: Context, bitmap: Bitmap, mediaId: String): File {
        val dir = AppStorageDirs.editsDir(context)
        val outFile = File(dir, "edit_${mediaId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.jpg")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
        }
        return outFile
    }
}
