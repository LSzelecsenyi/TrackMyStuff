package app.mymusclemap.ui.progress

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.components.SegmentedControl
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import java.time.LocalDate
import kotlin.math.roundToInt

internal const val PROGRESS_PHOTO_VIEWER = "progress-photo-viewer"
internal const val PROGRESS_PHOTO_DELETE = "progress-photo-delete"
internal const val PROGRESS_PHOTO_COMPARE_SCREEN = "progress-photo-compare-screen"
internal const val PROGRESS_PHOTO_COMPARE_SLIDER = "progress-photo-compare-slider"
internal const val PROGRESS_PHOTO_COMPARE_SIDE_BY_SIDE = "progress-photo-compare-side-by-side"
internal const val PROGRESS_PHOTO_COMPARE_VIEWPORT = "progress-photo-compare-viewport"
internal const val PROGRESS_PHOTO_COMPARE_DIVIDER = "progress-photo-compare-divider"
internal const val PROGRESS_PHOTO_COMPARE_LEFT = "progress-photo-compare-left"
internal const val PROGRESS_PHOTO_COMPARE_RIGHT = "progress-photo-compare-right"

internal const val COMPARE_REVEAL_START = 0.5f

internal fun compareRevealFraction(raw: Float): Float {
    if (raw.isNaN()) return COMPARE_REVEAL_START
    return raw.coerceIn(0f, 1f)
}

internal enum class ProgressPhotoCompareMode {
    Slider,
    SideBySide
}

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
    var mode by remember { mutableStateOf(ProgressPhotoCompareMode.Slider) }
    var reveal by remember { mutableFloatStateOf(COMPARE_REVEAL_START) }
    val earlierDate = UiFormatters.compactDate(beforeDate)
    val laterDate = UiFormatters.compactDate(afterDate)
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(AppDimens.screenPadding)
                .testTag(PROGRESS_PHOTO_COMPARE_SCREEN)
        ) {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.progress_photos_compare_slider),
                    stringResource(R.string.progress_photos_compare_side_by_side)
                ),
                selectedIndex = if (mode == ProgressPhotoCompareMode.Slider) 0 else 1,
                onSelected = { index ->
                    mode = if (index == 0) {
                        ProgressPhotoCompareMode.Slider
                    } else {
                        ProgressPhotoCompareMode.SideBySide
                    }
                },
                optionTestTags = listOf(
                    PROGRESS_PHOTO_COMPARE_SLIDER,
                    PROGRESS_PHOTO_COMPARE_SIDE_BY_SIDE
                )
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            if (mode == ProgressPhotoCompareMode.Slider) {
                CompareSlider(
                    earlierDate = earlierDate,
                    laterDate = laterDate,
                    before = before,
                    beforeMissing = beforeMissing,
                    after = after,
                    afterMissing = afterMissing,
                    reveal = reveal,
                    onReveal = { reveal = compareRevealFraction(it) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Row(modifier = Modifier.weight(1f)) {
                    ComparePane(
                        date = beforeDate,
                        bitmap = before,
                        missing = beforeMissing,
                        description = stringResource(R.string.progress_photos_compare_earlier, earlierDate),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    )
                    ComparePane(
                        date = afterDate,
                        bitmap = after,
                        missing = afterMissing,
                        description = stringResource(R.string.progress_photos_compare_later, laterDate),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareSlider(
    earlierDate: String,
    laterDate: String,
    before: Bitmap?,
    beforeMissing: Boolean,
    after: Bitmap?,
    afterMissing: Boolean,
    reveal: Float,
    onReveal: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val positionLabel = stringResource(
        R.string.progress_photos_compare_position,
        earlierDate,
        laterDate
    )
    val earlierLabel = stringResource(R.string.progress_photos_compare_earlier, earlierDate)
    val laterLabel = stringResource(R.string.progress_photos_compare_later, laterDate)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = earlierDate,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "|",
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text(
                text = laterDate,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .weight(1f)
                .testTag(PROGRESS_PHOTO_COMPARE_VIEWPORT)
                .semantics {
                    contentDescription = positionLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(reveal, 0f..1f)
                    setProgress { value ->
                        onReveal(value)
                        true
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (size.width > 0) {
                            onReveal(down.position.x / size.width.toFloat())
                        }
                        horizontalDrag(down.id) { change ->
                            if (size.width > 0) {
                                onReveal(change.position.x / size.width.toFloat())
                            }
                        }
                    }
                }
        ) {
            CompareLayer(
                bitmap = after,
                missing = afterMissing,
                description = laterLabel,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(PROGRESS_PHOTO_COMPARE_RIGHT)
            )
            CompareLayer(
                bitmap = before,
                missing = beforeMissing,
                description = earlierLabel,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(PROGRESS_PHOTO_COMPARE_LEFT)
                    .clip(LeftReveal(reveal))
            )
            val handleWidth = 48.dp
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(handleWidth)
                    .offset {
                        val travel = constraints.maxWidth
                        val center = (travel * reveal).roundToInt()
                        IntOffset(center - handleWidth.roundToPx() / 2, 0)
                    }
                    .testTag(PROGRESS_PHOTO_COMPARE_DIVIDER),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onBackground)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun CompareLayer(
    bitmap: Bitmap?,
    missing: Boolean,
    description: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && !missing) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
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

private class LeftReveal(private val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Rectangle(
            Rect(
                left = 0f,
                top = 0f,
                right = size.width * compareRevealFraction(fraction),
                bottom = size.height
            )
        )
    }
}

@Composable
private fun ComparePane(
    date: LocalDate,
    bitmap: Bitmap?,
    missing: Boolean,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
    ) {
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
                    contentDescription = null,
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
