package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MediaItemEntity
import com.example.data.repository.GalleryRepository
import com.example.editor.CropAspect
import com.example.editor.EditParams
import com.example.editor.FilterPreset
import com.example.editor.PhotoEditorEngine
import com.example.editor.StickerOverlayItem
import com.example.editor.TextOverlayItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class EditorUiState(
    val item: MediaItemEntity? = null,
    val params: EditParams = EditParams(),
    val previewBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val isDepthComputing: Boolean = false,
    val message: String? = null
)

data class CompressionUiState(
    val item: MediaItemEntity? = null,
    val quality: Int = 80,
    val scaleFraction: Float = 0.75f,
    val originalBytes: Long = 0L,
    val estimatedBytes: Long = 0L,
    val previewBitmap: Bitmap? = null,
    val isComputing: Boolean = true,
    val isExporting: Boolean = false,
    val message: String? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository.getInstance(application)

    private var sourcePreviewBitmap: Bitmap? = null
    private var renderJob: Job? = null
    private var depthMap: com.example.editor.DepthMap? = null
    private var depthJob: Job? = null
    private var depthFailed = false

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private val _compressionState = MutableStateFlow(CompressionUiState())
    val compressionState: StateFlow<CompressionUiState> = _compressionState.asStateFlow()

    fun loadForEditor(mediaId: String) {
        viewModelScope.launch {
            _editorState.value = EditorUiState(isLoading = true)
            val item = repository.getMediaById(mediaId)
            if (item == null) {
                _editorState.value = EditorUiState(isLoading = false, message = "Photo not found")
                return@launch
            }
            val loadedBitmap = withContext(Dispatchers.IO) {
                PhotoEditorEngine.decodeSampledBitmap(getApplication(), Uri.parse(item.uriString), 1080)
            }
            sourcePreviewBitmap = loadedBitmap
            depthMap = null
            depthFailed = false
            depthJob?.cancel()
            val initialParams = EditParams.fromJson(item.editParamsJson)
            _editorState.value = EditorUiState(
                item = item,
                params = initialParams,
                isLoading = false
            )
            ensureDepth(initialParams)
            rerenderPreview()
        }
    }

    /** Computes the depth map once per photo, the first time depth blur is used. */
    private fun ensureDepth(params: EditParams) {
        if ((params.depthBlur <= 0.02f && params.textOverlays.none { it.behind }) || depthMap != null || depthFailed || depthJob?.isActive == true) return
        val src = sourcePreviewBitmap ?: return
        depthJob = viewModelScope.launch(Dispatchers.Default) {
            _editorState.value = _editorState.value.copy(isDepthComputing = true)
            val d = com.example.editor.DepthEstimator.estimateCached(getApplication(), _editorState.value.item?.id ?: "tmp", src)
            depthMap = d
            depthFailed = d == null
            _editorState.value = _editorState.value.copy(
                isDepthComputing = false,
                message = if (d == null) "Depth model missing: add assets/midas.tflite" else _editorState.value.message
            )
            if (d != null) rerenderPreview()
        }
    }

    fun updateParams(transform: (EditParams) -> EditParams) {
        val next = transform(_editorState.value.params)
        _editorState.value = _editorState.value.copy(params = next)
        ensureDepth(next)
        rerenderPreview()
    }

    fun rotate90() = updateParams { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    fun toggleFlip() = updateParams { it.copy(flipHorizontal = !it.flipHorizontal) }
    fun setCropAspect(aspect: CropAspect) = updateParams { it.copy(cropAspect = aspect) }
    fun setFilterPreset(preset: FilterPreset) = updateParams { it.copy(filterPreset = preset) }
    fun resetAll() = updateParams { EditParams() }

    fun addTextOverlay(text: String, colorArgb: Long = 0xFFFFFFFFL, behind: Boolean = false) {
        if (text.isBlank()) return
        updateParams {
            it.copy(
                textOverlays = it.textOverlays + TextOverlayItem(
                    id = UUID.randomUUID().toString(),
                    text = text.trim(),
                    xFraction = 0.5f,
                    yFraction = 0.78f - (it.textOverlays.size * 0.08f).coerceAtMost(0.4f),
                    colorArgb = colorArgb,
                    scale = if (behind) 2.4f else 1f,
                    behind = behind
                )
            )
        }
    }

    /** Text depth: flips a text between in front of and behind the subject. */
    fun toggleTextBehind(id: String) = updateParams { p ->
        p.copy(textOverlays = p.textOverlays.map { if (it.id == id) it.copy(behind = !it.behind) else it })
    }

    fun setTextScale(id: String, scale: Float) = updateParams { p ->
        p.copy(textOverlays = p.textOverlays.map { if (it.id == id) it.copy(scale = scale.coerceIn(0.4f, 6f)) else it })
    }

    fun moveText(id: String, x: Float, y: Float) = updateParams { p ->
        p.copy(textOverlays = p.textOverlays.map { if (it.id == id) it.copy(xFraction = x.coerceIn(0f, 1f), yFraction = y.coerceIn(0f, 1f)) else it })
    }

    fun addSticker(emoji: String) {
        updateParams {
            it.copy(
                stickerOverlays = it.stickerOverlays + StickerOverlayItem(
                    id = UUID.randomUUID().toString(),
                    emoji = emoji,
                    xFraction = 0.5f + ((it.stickerOverlays.size % 3) - 1) * 0.14f,
                    yFraction = 0.28f
                )
            )
        }
    }

    fun clearOverlays() {
        updateParams { it.copy(textOverlays = emptyList(), stickerOverlays = emptyList()) }
    }

    private fun rerenderPreview() {
        val src = sourcePreviewBitmap ?: return
        val params = _editorState.value.params
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val rendered = PhotoEditorEngine.renderEditedBitmap(src, params, depthMap)
            _editorState.value = _editorState.value.copy(previewBitmap = rendered)
        }
    }

    fun saveNonDestructive(onSaved: () -> Unit = {}) {
        val item = _editorState.value.item ?: return
        val params = _editorState.value.params
        viewModelScope.launch {
            repository.saveNonDestructiveEdit(item.id, params)
            _editorState.value = _editorState.value.copy(message = "Saved non-destructive edits")
            onSaved()
        }
    }

    fun exportEditedCopy() {
        val item = _editorState.value.item ?: return
        val params = _editorState.value.params
        viewModelScope.launch {
            _editorState.value = _editorState.value.copy(isExporting = true)
            repository.saveNonDestructiveEdit(item.id, params)
            val uri = repository.exportEditedCopy(item, params, depthMap)
            _editorState.value = _editorState.value.copy(
                isExporting = false,
                message = if (uri != null) "Exported copy to Pictures/FolioAlbum/Edited" else "Export failed"
            )
        }
    }

    // Compression flow
    private var compressionSource: Bitmap? = null

    fun loadForCompression(mediaId: String) {
        viewModelScope.launch {
            _compressionState.value = CompressionUiState(isComputing = true)
            val item = repository.getMediaById(mediaId) ?: return@launch
            val src = withContext(Dispatchers.IO) {
                PhotoEditorEngine.decodeSampledBitmap(getApplication(), Uri.parse(item.uriString), 1440)
            }
            compressionSource = src
            _compressionState.value = CompressionUiState(
                item = item,
                originalBytes = item.sizeBytes,
                isComputing = false
            )
            recomputeCompressionPreview()
        }
    }

    fun setCompressionQuality(quality: Int) {
        _compressionState.value = _compressionState.value.copy(quality = quality.coerceIn(10, 100))
        recomputeCompressionPreview()
    }

    fun setCompressionScale(scale: Float) {
        _compressionState.value = _compressionState.value.copy(scaleFraction = scale.coerceIn(0.25f, 1f))
        recomputeCompressionPreview()
    }

    private fun recomputeCompressionPreview() {
        val src = compressionSource ?: return
        val state = _compressionState.value
        viewModelScope.launch(Dispatchers.Default) {
            val (preview, estBytes) = PhotoEditorEngine.saveCompressedPreviewEstimate(
                src, state.scaleFraction, state.quality
            )
            _compressionState.value = _compressionState.value.copy(
                previewBitmap = preview,
                estimatedBytes = estBytes
            )
        }
    }

    fun exportCompressedCopy() {
        val state = _compressionState.value
        val item = state.item ?: return
        viewModelScope.launch {
            _compressionState.value = state.copy(isExporting = true)
            val uri = repository.exportCompressedCopy(item, state.scaleFraction, state.quality)
            _compressionState.value = _compressionState.value.copy(
                isExporting = false,
                message = if (uri != null) {
                    "Saved compressed copy to Pictures/FolioAlbum/Compressed (original preserved)"
                } else {
                    "Compression export failed"
                }
            )
        }
    }

    override fun onCleared() {
        com.example.editor.DepthBlurRenderer.clearCache()
        super.onCleared()
    }

    fun clearMessage() {
        _editorState.value = _editorState.value.copy(message = null)
        _compressionState.value = _compressionState.value.copy(message = null)
    }
}
