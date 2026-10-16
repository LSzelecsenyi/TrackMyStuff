package app.mymusclemap.ui.muscleinsights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
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
internal const val MUSCLE_INSIGHTS_INFO_COMPARISON = "muscle-insights-info-comparison"
internal const val MUSCLE_INSIGHTS_INFO_SUMMARY_STIMULUS = "muscle-insights-info-summary-stimulus"
internal const val MUSCLE_INSIGHTS_INFO_SUMMARY_LOAD = "muscle-insights-info-summary-load"
internal const val MUSCLE_INSIGHTS_SUMMARY_CARD = "muscle-insights-summary-card"
internal const val MUSCLE_INSIGHTS_COMPARISON_CARD = "muscle-insights-comparison-card"
internal const val MUSCLE_INSIGHTS_RECOVERY_CARD = "muscle-insights-recovery-card"
internal const val MUSCLE_INSIGHTS_PERIOD_LABEL = "muscle-insights-period-label"
internal const val MUSCLE_INSIGHTS_PARTIAL = "muscle-insights-partial"
internal const val MUSCLE_INSIGHTS_MEANING = "muscle-insights-meaning"
internal const val MUSCLE_INSIGHTS_CALC_TOGGLE = "muscle-insights-calc-toggle"
internal const val MUSCLE_INSIGHTS_CALC_DETAILS = "muscle-insights-calc-details"
internal const val MUSCLE_INSIGHTS_CHANGE = "muscle-insights-change"

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
                    ContributionSection(state.period, state.insight, onExplain)
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
    InsightSection(
        title = stringResource(R.string.muscle_insights_summary),
        cardTag = MUSCLE_INSIGHTS_SUMMARY_CARD
    ) {
        ExplainedRow(
            label = stringResource(R.string.muscle_insights_last_stimulus),
            value = stimulusText(insight.lastStimulus),
            onExplain = { onExplain(MuscleInsightTopic.LAST_STIMULUS) },
            infoTag = MUSCLE_INSIGHTS_INFO_SUMMARY_STIMULUS
        )
        ExplainedRow(
            label = stringResource(R.string.muscle_insights_recent_load),
            value = comparisonText(insight.comparison),
            onExplain = { onExplain(MuscleInsightTopic.RECENT_LOAD) },
            infoTag = MUSCLE_INSIGHTS_INFO_SUMMARY_LOAD
        )
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
    InsightSection(
        title = stringResource(R.string.muscle_insights_comparison),
        onExplain = { onExplain(MuscleInsightTopic.RECENT_LOAD) },
        infoTag = MUSCLE_INSIGHTS_INFO_COMPARISON,
        caption = stringResource(R.string.muscle_insights_comparison_window),
        cardTag = MUSCLE_INSIGHTS_COMPARISON_CARD
    ) {
        StatRow(
            label = stringResource(R.string.muscle_insights_sets_7d),
            value = completedSetsLabel(insight.recentPrimarySets)
        )
        StatRow(
            label = stringResource(R.string.muscle_insights_baseline),
            value = insight.baselineWeeklySets?.let { weeklySetsLabel(it) }
                ?: stringResource(R.string.muscle_insights_insufficient)
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        ComparisonResult(insight)
        PartialHistoryNote(insight, tagged = true)
    }
}

@Composable
private fun RecoverySection(insight: MuscleInsight, onExplain: (MuscleInsightTopic) -> Unit) {
    InsightSection(
        title = stringResource(R.string.muscle_insights_recovery),
        onExplain = { onExplain(MuscleInsightTopic.SPACING) },
        infoTag = MUSCLE_INSIGHTS_EXPLAIN,
        cardTag = MUSCLE_INSIGHTS_RECOVERY_CARD
    ) {
        StatRow(
            label = stringResource(R.string.muscle_insights_last_stimulus),
            value = stimulusText(insight.lastStimulus)
        )
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
            text = stringResource(R.string.muscle_insights_recovery_body),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContributionSection(
    period: MuscleInsightsPeriod,
    insight: MuscleInsight,
    onExplain: (MuscleInsightTopic) -> Unit
) {
    InsightSection(
        title = stringResource(R.string.muscle_insights_contribution_title),
        onExplain = { onExplain(MuscleInsightTopic.CONTRIBUTION) },
        caption = stringResource(R.string.muscle_insights_selected_period, stringResource(period.rangeNameRes())),
        captionTag = MUSCLE_INSIGHTS_PERIOD_LABEL
    ) {
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
private fun InsightSection(
    title: String,
    onExplain: (() -> Unit)? = null,
    infoTag: String? = null,
    caption: String? = null,
    captionTag: String? = null,
    cardTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(title = title, onExplain = onExplain, infoTag = infoTag)
        if (caption != null) {
            Text(
                text = caption,
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (captionTag == null) Modifier.fillMaxWidth() else Modifier
                    .fillMaxWidth()
                    .testTag(captionTag)
            )
        }
        Spacer(Modifier.height(AppDimens.itemGap))
        Box(modifier = if (cardTag == null) Modifier else Modifier.testTag(cardTag)) {
            StatisticsCard(content = content)
        }
        Spacer(Modifier.height(AppDimens.sectionGap))
    }
}

@Composable
private fun ComparisonResult(insight: MuscleInsight) {
    val change = loadChangeLabel(
        insight.recentPrimarySets,
        insight.baselineWeeklySets,
        insight.comparison
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = comparisonText(insight.comparison),
            style = AppTypeTokens.statValue,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (change != null) {
            Text(
                text = change,
                style = AppTypeTokens.statValue,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = AppDimens.itemGap)
                    .testTag(MUSCLE_INSIGHTS_CHANGE)
            )
        }
    }
}

@Composable
private fun ExplainedRow(
    label: String,
    value: String,
    onExplain: () -> Unit,
    infoTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            StatRow(label = label, value = value)
        }
        IconButton(
            onClick = onExplain,
            modifier = Modifier.testTag(infoTag)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = stringResource(R.string.muscle_insights_calc_title)
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    onExplain: (() -> Unit)? = null,
    infoTag: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(AppLocale.UI),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (onExplain != null) {
            IconButton(
                onClick = onExplain,
                modifier = if (infoTag == null) {
                    Modifier.defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
                } else {
                    Modifier
                        .defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
                        .testTag(infoTag)
                }
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
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.92f
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.screenPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                when (topic) {
                    MuscleInsightTopic.RECENT_LOAD -> LoadComparisonExplanation(insight)
                    MuscleInsightTopic.SPACING -> SpacingExplanation()
                    else -> {
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
                }
            }
            Spacer(Modifier.height(AppDimens.itemGap))
            Button(
                onClick = onDismiss,
                shape = AppShapeTokens.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .padding(bottom = AppDimens.itemGap)
            ) {
                Text(stringResource(R.string.muscle_insights_close))
            }
        }
    }
}

@Composable
private fun LoadComparisonExplanation(insight: MuscleInsight) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val calculationState = stringResource(
        if (expanded) {
            R.string.muscle_insights_calc_expanded
        } else {
            R.string.muscle_insights_calc_collapsed
        }
    )
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
    if (insight.coverage == BaselineCoverage.PARTIAL) {
        Spacer(Modifier.height(AppDimens.itemGap))
        Text(
            text = stringResource(R.string.muscle_insights_partial_context),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(AppDimens.sectionGap))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable { expanded = !expanded }
            .semantics {
                role = Role.Button
                stateDescription = calculationState
            }
            .testTag(MUSCLE_INSIGHTS_CALC_TOGGLE),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.muscle_insights_explain_how),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (expanded) {
        Spacer(Modifier.height(AppDimens.headerStackGap))
        Text(
            text = stringResource(R.string.muscle_insights_explain_load),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(MUSCLE_INSIGHTS_CALC_DETAILS)
        )
    }
}

@Composable
private fun SpacingExplanation() {
    Text(
        text = stringResource(R.string.muscle_insights_spacing),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(AppDimens.itemGap))
    Text(
        text = stringResource(R.string.muscle_insights_spacing_question),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(AppDimens.itemGap))
    Text(
        text = stringResource(R.string.muscle_insights_explain_spacing),
        style = AppTypeTokens.statSecondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
