package app.mymusclemap.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.body.BodyMeasurement
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.model.ChartRange
import app.mymusclemap.domain.model.ChartValueDomain
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.ui.components.SectionHeader
import app.mymusclemap.ui.components.SegmentedControl
import app.mymusclemap.ui.components.SeriesChart
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.components.UserMessage
import app.mymusclemap.ui.components.UserMessageEffect
import app.mymusclemap.ui.pro.ProInfoSheet
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import java.time.LocalDate

internal const val BODY_HISTORY_TAG = "body-measurement-history-"
internal const val BODY_ADD_TAG = "body-measurement-add"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMeasurementDetailScreen(
    detail: BodyMeasurementDetail,
    today: LocalDate,
    editor: BodyEditorUiState?,
    lockedFeature: AppFeature?,
    userMessage: UserMessage?,
    onBack: () -> Unit,
    onRangeSelected: (ChartRange) -> Unit,
    onAdd: () -> Unit,
    onOpenHistory: (BodyMeasurement) -> Unit,
    onBodyDateChange: (LocalDate) -> Unit,
    onBodyValueChange: (String) -> Unit,
    onSaveBody: () -> Unit,
    onDismissBodyEditor: () -> Unit,
    onBodyDeleteRequest: () -> Unit,
    onBodyDeleteDismiss: () -> Unit,
    onBodyDeleteConfirm: () -> Unit,
    onDismissLocked: () -> Unit,
    onMessageConsumed: () -> Unit,
    onOpened: () -> Unit = {},
    onClosed: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val title = detail.type?.let { stringResource(it.labelRes()) } ?: detail.typeCode
    UserMessageEffect(userMessage, snackbarHostState, onMessageConsumed)
    DisposableEffect(Unit) {
        onOpened()
        onDispose { onClosed() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(top = 8.dp, bottom = AppDimens.scrollEndPadding)
        ) {
            val latest = detail.window.latest
            if (latest != null) {
                Text(
                    text = formatBodyValue(latest.value, detail.type?.unit),
                    style = AppTypeTokens.statValue,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = UiFormatters.compactDate(latest.date),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            detail.window.change?.let { change ->
                Text(
                    text = stringResource(
                        R.string.body_measurement_change,
                        formatSignedBodyValue(change, detail.type?.unit)
                    ),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(AppDimens.sectionGap))
            val ranges = ChartRange.entries
            SegmentedControl(
                options = ranges.map { stringResource(it.labelRes()) },
                selectedIndex = ranges.indexOf(detail.range).coerceAtLeast(0),
                onSelected = { onRangeSelected(ranges[it]) }
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            if (detail.showChart) {
                SeriesChart(
                    points = detail.window.points.map { SeriesPoint(it.date, it.value) },
                    contentDescription = stringResource(
                        R.string.body_measurement_chart_description,
                        title,
                        detail.window.points.size
                    ),
                    modifier = Modifier.testTag(BODY_CHART_TAG),
                    chartHeight = 160.dp,
                    valueDomain = ChartValueDomain.Padded,
                    subdued = true
                )
            } else if (detail.window.points.isEmpty()) {
                Text(
                    text = stringResource(R.string.body_measurement_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (detail.historyNewestFirst.isNotEmpty()) {
                Spacer(Modifier.height(AppDimens.sectionGap))
                SectionHeader(title = stringResource(R.string.body_measurement_history))
                detail.historyNewestFirst.forEach { measurement ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AppDimens.minTouch)
                            .clickable(enabled = detail.type != null) {
                                onOpenHistory(measurement)
                            }
                            .testTag(BODY_HISTORY_TAG + measurement.id)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.body_measurement_history_row,
                                UiFormatters.compactDate(measurement.date),
                                formatBodyValue(measurement.value, detail.type?.unit)
                            ),
                            style = AppTypeTokens.statSecondary,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            if (detail.type != null) {
                Spacer(Modifier.height(AppDimens.sectionGap))
                Button(
                    onClick = onAdd,
                    enabled = detail.canAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BODY_ADD_TAG)
                ) {
                    Text(stringResource(R.string.body_measurement_add))
                }
            }
        }
    }
    editor?.let { current ->
        BodyMeasurementEditorSheet(
            state = current,
            today = today,
            onDateChange = onBodyDateChange,
            onValueChange = onBodyValueChange,
            onSave = onSaveBody,
            onDismiss = onDismissBodyEditor,
            onDeleteRequest = onBodyDeleteRequest,
            onDeleteDismiss = onBodyDeleteDismiss,
            onDeleteConfirm = onBodyDeleteConfirm
        )
    }
    lockedFeature?.let { feature ->
        ProInfoSheet(feature = feature, onDismiss = onDismissLocked)
    }
}
