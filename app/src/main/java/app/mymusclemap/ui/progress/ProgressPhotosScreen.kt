package app.mymusclemap.ui.progress

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.pro.ProBadge
import app.mymusclemap.ui.pro.ProInfoSheet
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val PROGRESS_PHOTOS_ROW = "progress-photos-row"
internal const val PROGRESS_PHOTOS_OVERVIEW_THUMB = "progress-photos-overview-thumb-"
internal const val PROGRESS_PHOTO_ADD = "progress-photo-add"
internal const val PROGRESS_PHOTO_CELL = "progress-photo-cell-"
internal const val PROGRESS_PHOTO_COMPARE = "progress-photo-compare"
internal const val PROGRESS_PHOTO_COMPARE_SHOW = "progress-photo-compare-show"

@Composable
internal fun ProgressPhotosOverviewCard(
    count: Int,
    latestDate: java.time.LocalDate?,
    showProBadge: Boolean,
    thumbnails: List<Bitmap>,
    latestMissing: Boolean,
    onOpen: () -> Unit
) {
    val openLabel = stringResource(R.string.progress_photos_open)
    val proState = stringResource(R.string.pro_badge)
    val preview = thumbnails.take(OVERVIEW_PREVIEW_COUNT)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PROGRESS_PHOTOS_ROW)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen),
        shape = AppShapeTokens.surface,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.itemGap)
                .semantics {
                    if (showProBadge) stateDescription = proState
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.progress_photos_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (showProBadge) {
                    Spacer(Modifier.width(8.dp))
                    ProBadge()
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (count == 0) {
                Text(
                    text = stringResource(R.string.progress_photos_overview_support),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = stringResource(R.string.progress_photos_empty),
                    style = AppTypeTokens.sectionSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                if (preview.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        preview.forEachIndexed { index, thumbnail ->
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(AppShapeTokens.compact)
                                    .testTag(PROGRESS_PHOTOS_OVERVIEW_THUMB + index)
                            ) {
                                Image(
                                    bitmap = thumbnail.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
                val summary = when {
                    latestMissing -> stringResource(R.string.progress_photos_missing)
                    latestDate != null -> {
                        pluralStringResource(R.plurals.progress_photos_count, count, count) +
                            " · " + UiFormatters.compactDate(latestDate)
                    }
                    else -> pluralStringResource(R.plurals.progress_photos_count, count, count)
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (preview.isEmpty()) 4.dp else 8.dp)
                )
            }
        }
    }
}

private const val OVERVIEW_PREVIEW_COUNT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressPhotosScreen(
    state: ProgressPhotosUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onConsumeLaunchPicker: () -> Unit,
    onPicked: (Uri?) -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onBeginCompare: () -> Unit,
    onToggleCompare: (Long) -> Unit,
    onCancelCompare: () -> Unit,
    onShowCompare: (Long, Long) -> Unit,
    onDismissLocked: () -> Unit,
    decode: suspend (String, Int) -> Bitmap? = { _, _ -> null }
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onPicked(uri)
    }
    LaunchedEffect(state.launchPicker) {
        if (state.launchPicker) {
            onConsumeLaunchPicker()
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.progress_photos_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = AppDimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    if (state.photos.isEmpty()) {
                        Text(
                            text = stringResource(R.string.progress_photos_empty),
                            style = AppTypeTokens.sectionTitle,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.progress_photos_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (state.importing) {
                        Text(
                            text = stringResource(R.string.progress_photos_adding),
                            style = AppTypeTokens.statCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.message == ProgressPhotoMessage.Unreadable) {
                        Text(
                            text = stringResource(R.string.progress_photos_unreadable),
                            style = AppTypeTokens.statCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(AppDimens.itemGap))
                    Button(
                        onClick = onAdd,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(PROGRESS_PHOTO_ADD)
                    ) {
                        Text(stringResource(R.string.progress_photos_add))
                    }
                    if (state.photos.size >= 2) {
                        Spacer(Modifier.height(8.dp))
                        if (state.selectingCompare) {
                            Text(
                                text = stringResource(R.string.progress_photos_compare_hint),
                                style = AppTypeTokens.statCaption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                TextButton(onClick = onCancelCompare) {
                                    Text(stringResource(R.string.progress_photos_compare_cancel))
                                }
                                if (state.selectedIds.size == 2) {
                                    TextButton(
                                        onClick = {
                                            onShowCompare(state.selectedIds[0], state.selectedIds[1])
                                        },
                                        modifier = Modifier.testTag(PROGRESS_PHOTO_COMPARE_SHOW)
                                    ) {
                                        Text(stringResource(R.string.progress_photos_compare_action))
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = onBeginCompare,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(PROGRESS_PHOTO_COMPARE)
                            ) {
                                Text(stringResource(R.string.progress_photos_compare))
                            }
                        }
                    }
                }
            }
            items(state.photos, key = { it.id }) { photo ->
                ProgressPhotoCell(
                    photo = photo,
                    selected = photo.id in state.selectedIds,
                    decode = decode,
                    onClick = {
                        if (state.selectingCompare) onToggleCompare(photo.id) else onOpenPhoto(photo.id)
                    }
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(AppDimens.scrollEndPadding))
            }
        }
    }
    state.lockedFeature?.let { feature ->
        ProInfoSheet(feature = feature, onDismiss = onDismissLocked)
    }
}

@Composable
private fun ProgressPhotoCell(
    photo: ProgressPhotoListItem,
    selected: Boolean,
    decode: suspend (String, Int) -> Bitmap?,
    onClick: () -> Unit
) {
    val date = UiFormatters.compactDate(photo.date)
    var bitmap by remember(photo.fileName, photo.missing) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.fileName, photo.missing) {
        bitmap = if (photo.missing) null else decode(photo.fileName, 360)
    }
    Surface(
        modifier = Modifier
            .testTag(PROGRESS_PHOTO_CELL + photo.id)
            .clickable(
                onClickLabel = stringResource(R.string.progress_photos_thumbnail, date),
                onClick = onClick
            ),
        shape = AppShapeTokens.compact,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                val image = bitmap
                if (image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = stringResource(R.string.progress_photos_thumbnail, date),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = stringResource(
                            if (photo.missing) R.string.progress_photos_missing else R.string.progress_photos_empty
                        ),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            Text(
                text = date,
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                maxLines = 1
            )
        }
    }
}
