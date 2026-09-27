package app.mymusclemap.ui.progress

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import java.time.LocalDate

internal const val PROGRESS_PHOTO_VIEWER = "progress-photo-viewer"
internal const val PROGRESS_PHOTO_DELETE = "progress-photo-delete"
internal const val PROGRESS_PHOTO_COMPARE_SCREEN = "progress-photo-compare-screen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressPhotoViewerScreen(
    date: LocalDate,
    bitmap: Bitmap?,
    missing: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiFormatters.compactDate(date)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag(PROGRESS_PHOTO_DELETE)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .testTag(PROGRESS_PHOTO_VIEWER),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null && !missing) {
                ZoomablePhoto(bitmap)
            } else {
                Text(
                    text = stringResource(R.string.progress_photos_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.action_delete)) },
            text = {
                Text(
                    stringResource(
                        R.string.progress_photos_delete_message,
                        UiFormatters.longDate(date)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ZoomablePhoto(bitmap: Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.progress_photos_title),
        modifier = Modifier
            .fillMaxSize()
            .padding(AppDimens.screenPadding)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 3f)
                    scale = next
                    offset = if (next == 1f) Offset.Zero else offset + pan
                }
            },
        contentScale = ContentScale.Fit
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressPhotoCompareScreen(
    beforeDate: LocalDate,
    before: Bitmap?,
    beforeMissing: Boolean,
    afterDate: LocalDate,
    after: Bitmap?,
    afterMissing: Boolean,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.progress_photos_compare_title)) },
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
        Row(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(AppDimens.screenPadding)
                .testTag(PROGRESS_PHOTO_COMPARE_SCREEN)
        ) {
            ComparePane(
                date = beforeDate,
                bitmap = before,
                missing = beforeMissing,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            )
            ComparePane(
                date = afterDate,
                bitmap = after,
                missing = afterMissing,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun ComparePane(
    date: LocalDate,
    bitmap: Bitmap?,
    missing: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = UiFormatters.compactDate(date),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null && !missing) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(
                        R.string.progress_photos_thumbnail,
                        UiFormatters.compactDate(date)
                    ),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = stringResource(R.string.progress_photos_missing),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
