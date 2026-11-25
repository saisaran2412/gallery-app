package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.storage.MediaFilter
import com.example.core.storage.PreferencesManager
import com.example.core.storage.SortOrder
import com.example.core.storage.UserSettings
import com.example.core.util.Formatters
import com.example.data.local.AlbumSummary
import com.example.data.local.DeviceFolderSummary
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.data.local.PersonGroupEntity
import com.example.data.local.TagEntity
import com.example.data.repository.GalleryRepository
import com.example.data.scanner.DuplicateGroup
import com.example.data.scanner.ExifMetadata
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

data class TimelineSection(
    val title: String,
    val items: List<MediaItemEntity>
)

data class GalleryUiState(
    val settings: UserSettings = UserSettings(),
    val timelineSections: List<TimelineSection> = emptyList(),
    val filteredMedia: List<MediaItemEntity> = emptyList(),
    val allActiveMedia: List<MediaItemEntity> = emptyList(),
    val favoriteMedia: List<MediaItemEntity> = emptyList(),
    val trashedMedia: List<MediaItemEntity> = emptyList(),
    val virtualAlbums: List<AlbumSummary> = emptyList(),
    val deviceFolders: List<DeviceFolderSummary> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val mediaTagMap: Map<String, List<TagEntity>> = emptyMap(),
    val personGroups: List<PersonGroupEntity> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isSyncing: Boolean = false,
    val statusMessage: String? = null
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

data class SearchState(
    val query: String = "",
    val filter: MediaFilter = MediaFilter.ALL,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val favoritesOnly: Boolean = false,
    val selectedTagId: Long? = null,
    val selectedFolderId: String? = null
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository.getInstance(application)
    private val prefs = PreferencesManager(application)

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSyncing = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _duplicateScanProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val duplicateScanProgress: StateFlow<Pair<Int, Int>?> = _duplicateScanProgress.asStateFlow()

    private val _faceScanProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val faceScanProgress: StateFlow<Pair<Int, Int>?> = _faceScanProgress.asStateFlow()

    private val _loadedExif = MutableStateFlow<Map<String, ExifMetadata>>(emptyMap())
    val loadedExif: StateFlow<Map<String, ExifMetadata>> = _loadedExif.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.settingsFlow.collect { repository.stripLocationOnShare = !it.showLocationMetadata }
        }
    }

    val uiState: StateFlow<GalleryUiState> = combine(
        prefs.settingsFlow,
        repository.activeMedia,
        repository.favoriteMedia,
        repository.trashedMedia,
        repository.virtualAlbums,
        repository.albumCrossRefs,
        repository.allTags,
        repository.mediaTagRefs,
        repository.personGroups,
        _selectedIds,
        _isSyncing,
        _statusMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val settings = values[0] as UserSettings
        @Suppress("UNCHECKED_CAST")
        val active = values[1] as List<MediaItemEntity>
        @Suppress("UNCHECKED_CAST")
        val favorites = values[2] as List<MediaItemEntity>
        @Suppress("UNCHECKED_CAST")
        val trashed = values[3] as List<MediaItemEntity>
        @Suppress("UNCHECKED_CAST")
        val albums = values[4] as List<com.example.data.local.VirtualAlbumEntity>
        @Suppress("UNCHECKED_CAST")
        val albumRefs = values[5] as List<com.example.data.local.AlbumMediaCrossRef>
        @Suppress("UNCHECKED_CAST")
        val tags = values[6] as List<TagEntity>
        @Suppress("UNCHECKED_CAST")
        val tagRefs = values[7] as List<com.example.data.local.MediaTagCrossRef>
        @Suppress("UNCHECKED_CAST")
        val people = values[8] as List<PersonGroupEntity>
        @Suppress("UNCHECKED_CAST")
        val selected = values[9] as Set<String>
        val syncing = values[10] as Boolean
        val status = values[11] as String?

        val filtered = filterAndSort(active, settings.mediaFilter, settings.sortOrder)
        val sections = if (settings.sortOrder == SortOrder.NEWEST || settings.sortOrder == SortOrder.OLDEST) {
            val headerResolver = Formatters.SectionHeaderResolver()
            filtered.groupBy { headerResolver.resolve(it.dateTakenMillis) }
                .map { (title, items) -> TimelineSection(title, items) }
        } else {
            if (filtered.isEmpty()) emptyList() else listOf(TimelineSection(settings.sortOrder.label, filtered))
        }

        val activeById = active.associateBy { it.id }
        val refsByAlbum = albumRefs.groupBy { it.albumId }
        val albumSummaries = albums.map { album ->
            val albumItems = refsByAlbum[album.id].orEmpty().mapNotNull { activeById[it.mediaId] }
            AlbumSummary(
                id = album.id,
                name = album.name,
                parentAlbumId = album.parentAlbumId,
                coverUri = album.coverUri.ifEmpty { albumItems.firstOrNull()?.uriString.orEmpty() },
                itemCount = albumItems.size
            )
        }

        val folderSummaries = active.groupBy { it.bucketId.ifEmpty { it.bucketName } }
            .map { (bucketKey, list) ->
                val newest = list.maxByOrNull { it.dateTakenMillis }
                DeviceFolderSummary(
                    bucketId = bucketKey,
                    bucketName = list.firstOrNull()?.bucketName ?: "Folder",
                    coverUri = newest?.uriString.orEmpty(),
                    itemCount = list.size,
                    latestMillis = newest?.dateTakenMillis ?: 0L
                )
            }
            .sortedByDescending { it.latestMillis }

        val tagsById = tags.associateBy { it.id }
        val mediaTagMap = tagRefs.groupBy { it.mediaId }
            .mapValues { (_, list) -> list.mapNotNull { tagsById[it.tagId] } }

        GalleryUiState(
            settings = settings,
            timelineSections = sections,
            filteredMedia = filtered,
            allActiveMedia = active,
            favoriteMedia = favorites,
            trashedMedia = trashed,
            virtualAlbums = albumSummaries,
            deviceFolders = folderSummaries,
            allTags = tags,
            mediaTagMap = mediaTagMap,
            personGroups = people,
            selectedIds = selected,
            isSyncing = syncing,
            statusMessage = status
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GalleryUiState()
    )

    private val ocrDao = com.example.data.local.AlbumDatabase.getInstance(application).galleryDao()

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val ocrMatches: kotlinx.coroutines.flow.Flow<Set<String>> = _searchState
        .map { it.query.trim() }
        .distinctUntilChanged()
        .debounce(250)
        .mapLatest { q ->
            if (q.length < 2) emptySet() else runCatching { ocrDao.searchOcrIds(q).toSet() }.getOrDefault(emptySet())
        }

    val searchResults: StateFlow<List<MediaItemEntity>> = combine(
        uiState,
        _searchState,
        ocrMatches
    ) { state, search, ocrIds ->
        val query = search.query.trim().lowercase()
        val base = state.allActiveMedia.filter { item ->
            val matchesFilter = when (search.filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> item.mediaType == MediaType.PHOTO
                MediaFilter.VIDEOS -> item.mediaType == MediaType.VIDEO
            }
            val matchesFav = !search.favoritesOnly || item.isFavorite
            val matchesFolder = search.selectedFolderId == null ||
                item.bucketId == search.selectedFolderId ||
                item.bucketName == search.selectedFolderId
            val itemTags = state.mediaTagMap[item.id].orEmpty()
            val matchesTag = search.selectedTagId == null || itemTags.any { it.id == search.selectedTagId }
            val matchesQuery = query.isEmpty() ||
                item.displayName.lowercase().contains(query) ||
                item.bucketName.lowercase().contains(query) ||
                item.note.lowercase().contains(query) ||
                item.mimeType.lowercase().contains(query) ||
                itemTags.any { it.name.lowercase().contains(query) } ||
                item.id in ocrIds

            matchesFilter && matchesFav && matchesFolder && matchesTag && matchesQuery
        }
        filterAndSort(base, search.filter, search.sortOrder)
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private fun filterAndSort(
        items: List<MediaItemEntity>,
        filter: MediaFilter,
        sortOrder: SortOrder
    ): List<MediaItemEntity> {
        val typed = when (filter) {
            MediaFilter.ALL -> items
            MediaFilter.PHOTOS -> items.filter { it.mediaType == MediaType.PHOTO }
            MediaFilter.VIDEOS -> items.filter { it.mediaType == MediaType.VIDEO }
        }
        return when (sortOrder) {
            SortOrder.NEWEST -> typed.sortedByDescending { it.dateTakenMillis }
            SortOrder.OLDEST -> typed.sortedBy { it.dateTakenMillis }
            SortOrder.NAME_ASC -> typed.sortedBy { it.displayName.lowercase() }
            SortOrder.SIZE_DESC -> typed.sortedByDescending { it.sizeBytes }
            SortOrder.DURATION_DESC -> typed.sortedByDescending { it.durationMillis }
        }
    }

    @Volatile
    private var lastSyncTimeMs: Long = 0L

    fun syncMediaStore(force: Boolean = true) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && lastSyncTimeMs > 0L && (now - lastSyncTimeMs) < 12_000L) return
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            runCatching {
                val autoClean = prefs.settingsFlow.first().trashAutoCleanDays
                repository.syncMediaStore(autoClean)
                lastSyncTimeMs = android.os.SystemClock.elapsedRealtime()
            }
            _isSyncing.value = false
        }
    }

    fun importPickedMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = repository.importFromPickedUris(uris)
            _statusMessage.value = "Imported $count item(s) offline"
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showStatus(msg: String) {
        _statusMessage.value = msg
    }

    // Selection
    fun toggleSelection(mediaId: String) {
        val current = _selectedIds.value.toMutableSet()
        if (!current.add(mediaId)) current.remove(mediaId)
        _selectedIds.value = current
    }

    fun startSelection(mediaId: String) {
        _selectedIds.value = _selectedIds.value + mediaId
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun selectAllVisible(items: List<MediaItemEntity>) {
        _selectedIds.value = items.map { it.id }.toSet()
    }

    fun getSelectedItems(): List<MediaItemEntity> {
        val ids = _selectedIds.value
        return uiState.value.allActiveMedia.filter { it.id in ids }
    }

    // Media Actions
    fun toggleFavoriteForSelected() {
        val selected = getSelectedItems()
        if (selected.isEmpty()) return
        val targetFav = selected.any { !it.isFavorite }
        viewModelScope.launch {
            repository.setFavorites(selected.map { it.id }, targetFav)
            clearSelection()
            _statusMessage.value = if (targetFav) "Added to Favorites" else "Removed from Favorites"
        }
    }

    fun toggleFavoriteSingle(item: MediaItemEntity) {
        viewModelScope.launch {
            repository.setFavorites(listOf(item.id), !item.isFavorite)
        }
    }

    fun moveSelectedToTrash() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveToTrash(ids)
            clearSelection()
            _statusMessage.value = "Moved ${ids.size} item(s) to Recycle Bin"
        }
    }

    fun moveSingleToTrash(mediaId: String) {
        viewModelScope.launch {
            repository.moveToTrash(listOf(mediaId))
            _statusMessage.value = "Moved to Recycle Bin"
        }
    }

    fun moveIdsToTrash(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveToTrash(ids)
            _statusMessage.value = "Moved ${ids.size} duplicate(s) to Recycle Bin"
        }
    }

    fun restoreFromTrash(ids: List<String>) {
        viewModelScope.launch {
            repository.restoreFromTrash(ids)
            _statusMessage.value = "Restored ${ids.size} item(s)"
        }
    }

    fun permanentlyDelete(items: List<MediaItemEntity>) {
        viewModelScope.launch {
            repository.permanentlyDeleteFromApp(items)
            _statusMessage.value = "Removed ${items.size} item(s) permanently"
        }
    }

    fun updateNote(mediaId: String, note: String) {
        viewModelScope.launch {
            repository.updateNote(mediaId, note)
            _statusMessage.value = "Note saved"
        }
    }

    fun updatePlaybackPosition(mediaId: String, positionMs: Long) {
        viewModelScope.launch {
            repository.updatePlaybackPosition(mediaId, positionMs)
        }
    }

    fun loadExifIfNeeded(item: MediaItemEntity) {
        if (item.isVideo || _loadedExif.value.containsKey(item.id)) return
        viewModelScope.launch {
            val meta = repository.loadAndCacheExif(item)
            _loadedExif.value = _loadedExif.value + (item.id to meta)
        }
    }

    // Virtual Albums & Subfolders
    fun createAlbum(name: String, parentAlbumId: Long? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.createVirtualAlbum(name, parentAlbumId)
            val selected = getSelectedItems()
            if (selected.isNotEmpty()) {
                repository.addMediaToAlbum(newId, selected)
                clearSelection()
            }
            _statusMessage.value = "Created album \"$name\""
        }
    }

    fun renameAlbum(albumId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameVirtualAlbum(albumId, newName)
            _statusMessage.value = "Renamed album"
        }
    }

    fun setAlbumCover(albumId: Long, coverUri: String) {
        viewModelScope.launch {
            repository.updateAlbumCover(albumId, coverUri)
            _statusMessage.value = "Album cover updated"
        }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch {
            repository.deleteVirtualAlbum(albumId)
            _statusMessage.value = "Virtual album deleted (originals untouched)"
        }
    }

    fun addItemsToAlbum(albumId: Long, items: List<MediaItemEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.addMediaToAlbum(albumId, items)
            clearSelection()
            _statusMessage.value = "Added ${items.size} item(s) to album"
        }
    }

    fun removeItemsFromAlbum(albumId: Long, mediaIds: List<String>) {
        viewModelScope.launch {
            repository.removeMediaFromAlbum(albumId, mediaIds)
            _statusMessage.value = "Removed from album"
        }
    }

    fun observeVirtualAlbumMedia(albumId: Long) = repository.observeVirtualAlbumMedia(albumId)
    fun observeFolderMedia(bucketId: String) = repository.observeFolderMedia(bucketId)
    fun observePersonMedia(personId: Long) = repository.observeMediaByPerson(personId)

    // Tags
    fun addTagToMedia(mediaId: String, tagName: String) {
        viewModelScope.launch {
            repository.addTagToMedia(mediaId, tagName)
        }
    }

    fun removeTagFromMedia(mediaId: String, tagId: Long) {
        viewModelScope.launch {
            repository.removeTagFromMedia(mediaId, tagId)
        }
    }

    // Search
    fun updateSearchQuery(q: String) {
        _searchState.value = _searchState.value.copy(query = q)
    }

    fun updateSearchFilter(filter: MediaFilter) {
        _searchState.value = _searchState.value.copy(filter = filter)
    }

    fun updateSearchSort(sortOrder: SortOrder) {
        _searchState.value = _searchState.value.copy(sortOrder = sortOrder)
    }

    fun toggleSearchFavoritesOnly() {
        _searchState.value = _searchState.value.copy(favoritesOnly = !_searchState.value.favoritesOnly)
    }

    fun setSearchTag(tagId: Long?) {
        _searchState.value = _searchState.value.copy(selectedTagId = tagId)
    }

    fun setSearchFolder(folderId: String?) {
        _searchState.value = _searchState.value.copy(selectedFolderId = folderId)
    }

    // Scan controls (pause / resume / stop + battery-smart holding)
    private val powerGate = com.example.data.scanner.PowerGate(application)
    private var dupControl: com.example.data.scanner.ScanControl? = null
    private var faceControl: com.example.data.scanner.ScanControl? = null

    private val _dupHold = MutableStateFlow(com.example.data.scanner.HoldReason.NONE)
    val duplicateHold: StateFlow<com.example.data.scanner.HoldReason> = _dupHold.asStateFlow()
    private val _faceHold = MutableStateFlow(com.example.data.scanner.HoldReason.NONE)
    val faceHold: StateFlow<com.example.data.scanner.HoldReason> = _faceHold.asStateFlow()

    // Duplicate Finder
    fun scanDuplicates() {
        if (_duplicateScanProgress.value != null) return
        val control = com.example.data.scanner.ScanControl(powerGate)
        dupControl = control
        _duplicateScanProgress.value = 0 to 1
        val holdJob = viewModelScope.launch { control.hold.collect { _dupHold.value = it } }
        viewModelScope.launch {
            try {
                val groups = repository.scanHashesAndFindDuplicates(control) { current, total ->
                    _duplicateScanProgress.value = current to total.coerceAtLeast(1)
                }
                _duplicateGroups.value = groups
                if (control.isStopped) _statusMessage.value = "Scan stopped - showing results so far"
            } finally {
                holdJob.cancel()
                _dupHold.value = com.example.data.scanner.HoldReason.NONE
                _duplicateScanProgress.value = null
                dupControl = null
            }
        }
    }

    fun pauseDuplicateScan() { dupControl?.pause() }
    fun resumeDuplicateScan() { dupControl?.resume() }
    fun stopDuplicateScan() { dupControl?.stop() }

    // Offline People / Face Grouping
    fun scanFaces() {
        if (_faceScanProgress.value != null) return
        if (!com.example.data.scanner.FaceEmbedder.isAvailable(getApplication())) {
            _statusMessage.value = "Face model missing: add assets/mobilefacenet.tflite"
            return
        }
        val control = com.example.data.scanner.ScanControl(powerGate)
        faceControl = control
        _faceScanProgress.value = 0 to 1
        val holdJob = viewModelScope.launch { control.hold.collect { _faceHold.value = it } }
        viewModelScope.launch {
            try {
                val found = repository.scanOfflineFaces(control) { current, total ->
                    _faceScanProgress.value = current to total.coerceAtLeast(1)
                }
                _statusMessage.value = when {
                    control.isStopped -> "Face scan stopped ($found face(s) so far)"
                    found > 0 -> "Detected $found face(s) offline"
                    else -> "Face scan complete"
                }
            } finally {
                holdJob.cancel()
                _faceHold.value = com.example.data.scanner.HoldReason.NONE
                _faceScanProgress.value = null
                faceControl = null
            }
        }
    }

    fun pauseFaceScan() { faceControl?.pause() }
    fun resumeFaceScan() { faceControl?.resume() }
    fun stopFaceScan() { faceControl?.stop() }

    fun renamePerson(personId: Long, label: String) {
        viewModelScope.launch {
            repository.renamePersonGroup(personId, label)
        }
    }

    fun deleteAllFaceData() {
        viewModelScope.launch {
            repository.deleteAllFaceData()
            _statusMessage.value = "All local face data erased"
        }
    }

    // Backup & Restore
    fun createLocalBackup() {
        viewModelScope.launch {
            val file = repository.createLocalMetadataBackup()
            _statusMessage.value = "Saved offline backup: ${file.name}"
        }
    }

    fun restoreLatestBackup() {
        viewModelScope.launch {
            val file = repository.getLatestBackupFile()
            if (file == null) {
                _statusMessage.value = "No local backup found"
                return@launch
            }
            val ok = repository.restoreFromBackupFile(file)
            _statusMessage.value = if (ok) "Restored albums, tags & notes" else "Backup restore failed"
        }
    }

    // Preferences / Settings
    fun markIntroSeen() = viewModelScope.launch { prefs.markIntroSeen() }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setShareAnimStyle(style: com.example.core.storage.ShareAnimStyle) = viewModelScope.launch { prefs.setShareAnimStyle(style) }
    fun setReducedMotion(enabled: Boolean) = viewModelScope.launch { prefs.setReducedMotion(enabled) }
    fun setGridColumns(cols: Int) = viewModelScope.launch { prefs.setGridColumns(cols) }
    fun setShowLocationMetadata(show: Boolean) = viewModelScope.launch { prefs.setShowLocationMetadata(show) }
    fun setTrashAutoCleanDays(days: Int) = viewModelScope.launch { prefs.setTrashAutoCleanDays(days) }
    fun setSortOrder(order: SortOrder) = viewModelScope.launch { prefs.setSortOrder(order) }
    fun setMediaFilter(filter: MediaFilter) = viewModelScope.launch { prefs.setMediaFilter(filter) }
    fun setOfflineFaceGroupingEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setOfflineFaceGroupingEnabled(enabled) }
    fun setImportedAiModelName(name: String) = viewModelScope.launch { prefs.setImportedAiModelName(name) }

    fun mediaForDayKey(items: List<MediaItemEntity>, dayKey: Int): List<MediaItemEntity> {
        val cal = java.util.Calendar.getInstance()
        return items.filter { item ->
            cal.timeInMillis = item.dateTakenMillis
            val itemKey = cal.get(java.util.Calendar.YEAR) * 10_000 +
                (cal.get(java.util.Calendar.MONTH) + 1) * 100 +
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            itemKey == dayKey
        }
    }

    fun buildShareIntent(items: List<MediaItemEntity>) = repository.buildShareIntent(items)
    suspend fun readHasSeenIntro(): Boolean = prefs.settingsFlow.first().hasSeenIntro

    fun buildTargetedMultiShareIntent(items: List<MediaItemEntity>, packageName: String, activityClassName: String? = null) =
        repository.buildTargetedMultiShareIntent(items, packageName, activityClassName)
    fun buildTargetedShareIntent(item: MediaItemEntity, packageName: String, activityClassName: String? = null) =
        repository.buildTargetedShareIntent(item, packageName, activityClassName)
    suspend fun buildLiteTargetedShareIntent(item: MediaItemEntity, packageName: String? = null, activityClassName: String? = null) =
        repository.buildLiteTargetedShareIntent(item, packageName, activityClassName)
    fun copyMediaToClipboard(item: MediaItemEntity): Boolean = repository.copyMediaToClipboard(item)
    fun buildExternalPlayerIntent(item: MediaItemEntity) = repository.buildExternalPlayerIntent(item)
    fun createMediaStoreDeleteIntent(items: List<MediaItemEntity>) = repository.createMediaStoreDeleteRequestIntent(items)
}
