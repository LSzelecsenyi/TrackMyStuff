package app.mymusclemap.ui.progress

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.ProgressPhotoRepository
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.progress.ProgressPhotoAccess
import app.mymusclemap.domain.progress.ProgressPhotoImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.LocalDate

data class ProgressPhotoListItem(
    val id: Long,
    val date: LocalDate,
    val fileName: String,
    val missing: Boolean
)

enum class ProgressPhotoMessage {
    Unreadable
}

data class ProgressPhotosUiState(
    val photos: List<ProgressPhotoListItem> = emptyList(),
    val canAdd: Boolean = false,
    val showProBadge: Boolean = false,
    val launchPicker: Boolean = false,
    val importing: Boolean = false,
    val message: ProgressPhotoMessage? = null,
    val lockedFeature: AppFeature? = null,
    val selectingCompare: Boolean = false,
    val selectedIds: List<Long> = emptyList(),
    val latestThumbnail: Bitmap? = null,
    val recentThumbnails: List<Bitmap> = emptyList(),
    val loaded: Boolean = false
) {
    val latest: ProgressPhotoListItem? get() = photos.firstOrNull()
}

class ProgressPhotosViewModel(
    private val repository: ProgressPhotoRepository,
    private val entitlements: FeatureEntitlements = OpenFeatureEntitlements
) : ViewModel() {
    private val photos = MutableStateFlow<List<ProgressPhotoListItem>>(emptyList())
    private val recentThumbnails = MutableStateFlow<List<Bitmap>>(emptyList())
    private val launchPicker = MutableStateFlow(false)
    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<ProgressPhotoMessage?>(null)
    private val lockedFeature = MutableStateFlow<AppFeature?>(null)
    private val selectingCompare = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<List<Long>>(emptyList())
    private val loaded = MutableStateFlow(false)

    val uiState: StateFlow<ProgressPhotosUiState> = combine(
        combine(photos, recentThumbnails, launchPicker, importing, loaded) { items, thumbnails, picker, busy, ready ->
            PhotoListInputs(items, thumbnails, picker, busy, ready)
        },
        combine(message, lockedFeature, selectingCompare, selectedIds) { note, locked, selecting, selected ->
            PhotoChromeInputs(note, locked, selecting, selected)
        }
    ) { list, chrome ->
        val canAdd = ProgressPhotoAccess.canAdd(entitlements::hasAccess)
        ProgressPhotosUiState(
            photos = list.items,
            canAdd = canAdd,
            showProBadge = !canAdd,
            launchPicker = list.picker,
            importing = list.busy,
            message = chrome.note,
            lockedFeature = chrome.locked,
            selectingCompare = chrome.selecting,
            selectedIds = chrome.selected,
            latestThumbnail = list.thumbnails.firstOrNull(),
            recentThumbnails = list.thumbnails,
            loaded = list.ready
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ProgressPhotosUiState(
            canAdd = ProgressPhotoAccess.canAdd(entitlements::hasAccess),
            showProBadge = !ProgressPhotoAccess.canAdd(entitlements::hasAccess)
        )
    )

    init {
        viewModelScope.launch {
            repository.sweepOrphans()
            repository.observeAll().collect { stored ->
                val items = stored.map { photo ->
                    ProgressPhotoListItem(
                        id = photo.id,
                        date = photo.date,
                        fileName = photo.fileName,
                        missing = !repository.isAvailable(photo.fileName)
                    )
                }
                photos.value = items
                loaded.value = true
                selectedIds.value = selectedIds.value.filter { id -> items.any { it.id == id } }
                recentThumbnails.value = withContext(Dispatchers.IO) {
                    items.filter { !it.missing }
                        .take(OVERVIEW_THUMBNAILS)
                        .mapNotNull { repository.decode(it.fileName, THUMBNAIL_EDGE) }
                }
            }
        }
    }

    fun requestAdd() {
        message.value = null
        if (!ProgressPhotoAccess.canAdd(entitlements::hasAccess)) {
            lockedFeature.value = AppFeature.ProgressPhotos
            return
        }
        launchPicker.value = true
    }

    fun consumeLaunchPicker() {
        launchPicker.value = false
    }

    fun onPickerCancelled() = Unit

    fun import(source: InputStream) {
        if (!ProgressPhotoAccess.canAdd(entitlements::hasAccess)) {
            source.close()
            lockedFeature.value = AppFeature.ProgressPhotos
            return
        }
        viewModelScope.launch {
            importing.value = true
            try {
                source.use { stream ->
                    when (repository.import(stream)) {
                        is ProgressPhotoImportResult.Saved -> Unit
                        ProgressPhotoImportResult.Unreadable,
                        ProgressPhotoImportResult.FutureDate -> message.value = ProgressPhotoMessage.Unreadable
                    }
                }
            } finally {
                importing.value = false
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun dismissLockedFeature() {
        lockedFeature.value = null
    }

    fun consumeMessage() {
        message.value = null
    }

    fun beginCompare() {
        if (photos.value.size < 2) return
        selectingCompare.value = true
        selectedIds.value = emptyList()
    }

    fun toggleCompareSelection(id: Long) {
        if (!selectingCompare.value) return
        val current = selectedIds.value
        selectedIds.value = when {
            id in current -> current.filterNot { it == id }
            current.size >= 2 -> current
            else -> current + id
        }
    }

    fun cancelCompare() {
        selectingCompare.value = false
        selectedIds.value = emptyList()
    }

    fun decode(fileName: String, edge: Int): Bitmap? = repository.decode(fileName, edge)

    fun photo(id: Long): ProgressPhotoListItem? = photos.value.find { it.id == id }

    private data class PhotoListInputs(
        val items: List<ProgressPhotoListItem>,
        val thumbnails: List<Bitmap>,
        val picker: Boolean,
        val busy: Boolean,
        val ready: Boolean
    )

    private data class PhotoChromeInputs(
        val note: ProgressPhotoMessage?,
        val locked: AppFeature?,
        val selecting: Boolean,
        val selected: List<Long>
    )

    private companion object {
        const val THUMBNAIL_EDGE = 240
        const val OVERVIEW_THUMBNAILS = 3
    }
}
