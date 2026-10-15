package app.mymusclemap.ui.muscleinsights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.locale.LocalizedLabelOrder
import app.mymusclemap.domain.model.ChartValueDomain
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.domain.muscleinsights.BaselineCoverage
import app.mymusclemap.domain.muscleinsights.MuscleInsight
import app.mymusclemap.domain.muscleinsights.MuscleInsightTopic
import app.mymusclemap.domain.muscleinsights.MuscleInsightsPeriod
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.ui.components.CompactEditorSection
import app.mymusclemap.ui.components.SegmentedControl
import app.mymusclemap.ui.components.SeriesChart
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.statistics.StatRow
import app.mymusclemap.ui.statistics.StatisticsCard
import app.mymusclemap.ui.statistics.StatisticsHeader
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val MUSCLE_INSIGHTS_ROOT = "muscle-insights"
internal const val MUSCLE_INSIGHTS_DEMO = "muscle-insights-demo"
internal const val MUSCLE_INSIGHTS_UPGRADE = "muscle-insights-upgrade"
internal const val MUSCLE_INSIGHTS_MUSCLE = "muscle-insights-muscle"
internal const val MUSCLE_INSIGHTS_EXPLAIN = "muscle-insights-explain"
internal const val MUSCLE_INSIGHTS_PARTIAL = "muscle-insights-partial"
internal const val MUSCLE_INSIGHTS_MEANING = "muscle-insights-meaning"

@Composable
fun MuscleInsightsScreen(
    state: MuscleInsightsUiState,
    onBack: () -> Unit,
    onMuscleSelected: (MuscleGroup) -> Unit,
    onPeriodSelected: (MuscleInsightsPeriod) -> Unit,
    onExplain: (MuscleInsightTopic) -> Unit,
    onDismissExplanation: () -> Unit,
    onUpgrade: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MUSCLE_INSIGHTS_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            StatisticsHeader(
                title = stringResource(R.string.muscle_insights_title),
                onBack = onBack
            )
            if (!state.loading) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppDimens.screenPadding)
                        .padding(top = AppDimens.headerStackGap, bottom = AppDimens.scrollEndPadding)
                ) {
                    if (state.demo) {
                        Text(
                            text = stringResource(R.string.muscle_insights_demo),
                            style = AppTypeTokens.sectionKicker,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag(MUSCLE_INSIGHTS_DEMO)
                        )
                        Spacer(Modifier.height(AppDimens.headerStackGap))
                        Text(
                            text = stringResource(R.string.muscle_insights_demo_body),
                            style = AppTypeTokens.statCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(AppDimens.itemGap))
                        Button(
                            onClick = onUpgrade,
                            shape = AppShapeTokens.button,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AppDimens.minTouch)
                                .testTag(MUSCLE_INSIGHTS_UPGRADE)
                        ) {
                            Text(stringResource(R.string.muscle_insights_upgrade))
                        }
                        Spacer(Modifier.height(AppDimens.sectionGap))
                    }
                    MuscleSelector(selected = state.muscle, onSelected = onMuscleSelected)
                    Spacer(Modifier.height(AppDimens.itemGap))
                    PeriodSelector(selected = state.period, onSelected = onPeriodSelected)
                    Spacer(Modifier.height(AppDimens.sectionGap))
                    SummarySection(state.insight, onExplain)
                    ChartSection(state.insight, onExplain)
                    ComparisonSection(state.insight, onExplain)
                    RecoverySection(state.insight, onExplain)
                    ContributionSection(state.insight, onExplain)
                }
            }
        }
    }
    state.explanation?.let { topic ->
        MuscleInsightExplanationSheet(
            topic = topic,
            insight = state.insight,
            onDismiss = onDismissExplanation
        )
    }
}

@Composable
private fun MuscleSelector(selected: MuscleGroup, onSelected: (MuscleGroup) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    val muscles = LocalizedLabelOrder.sorted(
        MuscleHeatmapAssembler.anatomicalGroups,
        label = { resources.getString(it.labelRes()) },
        key = { it.name }
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { open = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MUSCLE_INSIGHTS_MUSCLE)
        ) {
            Text(
                text = stringResource(selected.labelRes()),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            muscles.forEach { muscle ->
                DropdownMenuItem(
                    text = { Text(stringResource(muscle.labelRes())) },
                    onClick = {
                        open = false
                        onSelected(muscle)
                    }
                )
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selected: MuscleInsightsPeriod,
    onSelected: (MuscleInsightsPeriod) -> Unit
) {
    val options = MuscleInsightsPeriod.entries
    SegmentedControl(
        options = options.map { stringResource(it.labelRes()) },
        selectedIndex = options.indexOf(selected),
        onSelected = { index -> onSelected(options[index]) },
        compact = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SummarySection(insight: MuscleInsight, onExplain: (MuscleInsightTopic) -> Unit) {
    CompactEditorSection(title = stringResource(R.string.muscle_insights_summary).uppercase(AppLocale.UI)) {
        StatisticsCard {
            ExplainedRow(
                label = stringResource(R.string.muscle_insights_last_stimulus),
                value = stimulusText(insight.lastStimulus),
                onExplain = { onExplain(MuscleInsightTopic.LAST_STIMULUS) }
            )
            ExplainedRow(
                label = stringResource(R.string.muscle_insights_recent_load),
                value = comparisonText(insight.comparison),
                onExplain = { onExplain(MuscleInsightTopic.RECENT_LOAD) }
            )
            PartialHistoryNote(insight)
        }
    }
}

@Composable
private fun ChartSection(insight: MuscleInsight, onExplain: (MuscleInsightTopic) -> Unit) {
    SectionTitle(
        title = stringResource(R.string.muscle_insights_chart_title),
        onExplain = { onExplain(MuscleInsightTopic.CHART) }
    )
    Spacer(Modifier.height(AppDimens.itemGap))
    if (!insight.hasPeriodSets) {
        Text(
            text = stringResource(R.string.muscle_insights_chart_empty),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else if (insight.weeklySets.size < 2) {
        Text(
            text = pluralStringResource(
                R.plurals.statistics_muscle_sets,
                insight.periodPrimarySets,
                insight.periodPrimarySets
            ),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onBackground
        )
    } else {
        SeriesChart(
            points = insight.weeklySets.map { SeriesPoint(it.weekStart, it.primarySets.toDouble()) },
            contentDescription = stringResource(
                R.string.muscle_insights_chart_description,
                insight.weeklySets.size
            ),
            subdued = true,
            chartHeight = 180.dp,
            valueDomain = ChartValueDomain.NonNegative,
            snapXAxisToPoints = true,
            yAxisLabel = { value -> value.toInt().toString() }
        )
    }
    Spacer(Modifier.height(AppDimens.sectionGap))
}

@Composable
private fun ComparisonSection(insight: MuscleInsight, onExplain: (MuscleInsightTopic) -> Unit) {
    CompactEditorSection(title = stringResource(R.string.muscle_insights_comparison).uppercase(AppLocale.UI)) {
        StatisticsCard {
            StatRow(
                label = stringResource(R.string.muscle_insights_sets_7d),
                value = insight.recentPrimarySets.toString()
            )
            StatRow(
                label = stringResource(R.string.muscle_insights_baseline),
                value = insight.baselineWeeklySets?.let { formatWeeklySets(it) }
                    ?: stringResource(R.string.muscle_insights_insufficient)
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Text(
                text = comparisonText(insight.comparison),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onBackground
            )
            PartialHistoryNote(insight, tagged = true)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.muscle_insights_counted),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = { onExplain(MuscleInsightTopic.RECENT_LOAD) }) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = stringResource(R.string.muscle_insights_calc_title)
                )
            }
        }
    }
}

@Composable
private fun RecoverySection(insight: MuscleInsight, onExplain: (MuscleInsightTopic) -> Unit) {
    CompactEditorSection(title = stringResource(R.string.muscle_insights_recovery).uppercase(AppLocale.UI)) {
        StatisticsCard {
            StatRow(
                label = stringResource(R.string.muscle_insights_last_stimulus),
                value = stimulusText(insight.lastStimulus)
            )
            StatRow(
                label = stringResource(R.string.muscle_insights_recent_load),
                value = comparisonText(insight.comparison)
            )
            PartialHistoryNote(insight)
            val spacing = insight.spacing
            if (spacing == null) {
                Text(
                    text = stringResource(R.string.muscle_insights_spacing_unavailable),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                StatRow(
                    label = stringResource(R.string.muscle_insights_spacing),
                    value = stringResource(R.string.statistics_rest_days_value, spacing.averageDays)
                )
            }
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Text(
                text = stringResource(R.string.muscle_insights_not_measured),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.muscle_insights_recovery_body),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = { onExplain(MuscleInsightTopic.SPACING) },
                modifier = Modifier.testTag(MUSCLE_INSIGHTS_EXPLAIN)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = stringResource(R.string.muscle_insights_calc_title)
                )
            }
        }
    }
}

@Composable
private fun ContributionSection(insight: MuscleInsight, onExplain: (MuscleInsightTopic) -> Unit) {
    SectionTitle(
        title = stringResource(R.string.muscle_insights_contribution_title),
        onExplain = { onExplain(MuscleInsightTopic.CONTRIBUTION) }
    )
    Spacer(Modifier.height(AppDimens.itemGap))
    StatisticsCard {
        StatRow(
            label = stringResource(R.string.muscle_insights_primary_sets),
            value = insight.periodPrimarySets.toString()
        )
        StatRow(
            label = stringResource(R.string.muscle_insights_secondary_sets),
            value = insight.periodSecondarySets.toString()
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        Text(
            text = stringResource(R.string.muscle_insights_secondary_limit),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExplainedRow(label: String, value: String, onExplain: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            StatRow(label = label, value = value)
        }
        IconButton(onClick = onExplain) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = stringResource(R.string.muscle_insights_calc_title)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, onExplain: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(AppLocale.UI),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onExplain) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = stringResource(R.string.muscle_insights_calc_title)
            )
        }
    }
}

@Composable
private fun PartialHistoryNote(insight: MuscleInsight, tagged: Boolean = false) {
    if (insight.coverage != BaselineCoverage.PARTIAL) return
    Text(
        text = stringResource(R.string.muscle_insights_partial_history),
        style = AppTypeTokens.statCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (tagged) Modifier.testTag(MUSCLE_INSIGHTS_PARTIAL) else Modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleInsightExplanationSheet(
    topic: MuscleInsightTopic,
    insight: MuscleInsight,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(bottom = 28.dp)
        ) {
            if (topic == MuscleInsightTopic.RECENT_LOAD) {
                LoadComparisonExplanation(insight)
            } else {
                Text(
                    text = stringResource(R.string.muscle_insights_calc_title),
                    style = AppTypeTokens.sectionKicker,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AppDimens.headerStackGap))
                Text(
                    text = stringResource(topic.titleRes()),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(AppDimens.itemGap))
                Text(
                    text = stringResource(topic.bodyRes()),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(AppDimens.sectionGap))
            Button(
                onClick = onDismiss,
                shape = AppShapeTokens.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
            ) {
                Text(stringResource(R.string.muscle_insights_close))
            }
        }
    }
}

@Composable
private fun LoadComparisonExplanation(insight: MuscleInsight) {
    Text(
        text = stringResource(R.string.muscle_insights_explain_what),
        style = AppTypeTokens.sectionKicker,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(MUSCLE_INSIGHTS_MEANING)
    )
    Spacer(Modifier.height(AppDimens.headerStackGap))
    Text(
        text = stringResource(R.string.muscle_insights_load_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(AppDimens.itemGap))
    Text(
        text = stringResource(R.string.muscle_insights_load_question),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(AppDimens.itemGap))
    Text(
        text = stringResource(R.string.muscle_insights_load_meaning),
        style = AppTypeTokens.statSecondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(AppDimens.sectionGap))
    Text(
        text = stringResource(R.string.muscle_insights_explain_how),
        style = AppTypeTokens.sectionKicker,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(AppDimens.headerStackGap))
    Text(
        text = stringResource(R.string.muscle_insights_explain_load),
        style = AppTypeTokens.statSecondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val historyDays = insight.partialHistoryDays
    if (insight.coverage == BaselineCoverage.PARTIAL && historyDays != null) {
        Spacer(Modifier.height(AppDimens.itemGap))
        Text(
            text = stringResource(R.string.muscle_insights_partial_history_days, historyDays),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun MuscleInsightsBars(
    bars: List<app.mymusclemap.domain.muscleinsights.MuscleSetBar>,
    modifier: Modifier = Modifier
) {
    val max = bars.maxOfOrNull { it.primarySets }?.coerceAtLeast(1) ?: 1
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppDimens.itemGap)) {
        bars.forEach { bar ->
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(bar.muscle.labelRes()),
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = bar.primarySets.toString(),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, AppShapeTokens.compact)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bar.primarySets.toFloat() / max)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, AppShapeTokens.compact)
                    )
                }
            }
        }
    }
}
