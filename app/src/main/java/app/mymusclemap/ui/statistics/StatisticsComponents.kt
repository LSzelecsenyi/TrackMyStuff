package app.mymusclemap.ui.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.statistics.ExerciseBest
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.domain.workout.QuantityParser
import app.mymusclemap.ui.components.SegmentedControl
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val STATISTICS_ROOT = "statistics-root"
internal const val STATISTICS_EMPTY = "statistics-empty"
internal const val STATISTICS_RANGE = "statistics-range"
internal const val STATISTICS_ACTIVITY = "statistics-activity"
internal const val STATISTICS_ADHERENCE = "statistics-adherence"
internal const val STATISTICS_VOLUME = "statistics-volume"
internal const val STATISTICS_MUSCLES = "statistics-muscles"
internal const val STATISTICS_REST = "statistics-rest"
internal const val STATISTICS_EXERCISES = "statistics-exercises"
internal const val STATISTICS_SEE_MUSCLES = "statistics-see-muscles"
internal const val STATISTICS_SEE_REST = "statistics-see-rest"
internal const val STATISTICS_SEE_EXERCISES = "statistics-see-exercises"

internal val statisticsRangeTags = listOf(
    "statistics-range-30d",
    "statistics-range-3m",
    "statistics-range-6m",
    "statistics-range-1y",
    "statistics-range-all"
)

@Composable
internal fun StatisticsHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.screenPadding)
            .defaultMinSize(minHeight = AppDimens.minTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(AppDimens.minTouch)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back)
            )
        }
        Text(
            text = title,
            style = AppTypeTokens.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun StatisticsRangeSelector(
    selected: StatisticsRange,
    onSelected: (StatisticsRange) -> Unit
) {
    val options = StatisticsRange.entries.map { stringResource(it.labelRes()) }
    SegmentedControl(
        options = options,
        selectedIndex = StatisticsRange.entries.indexOf(selected),
        onSelected = { index -> onSelected(StatisticsRange.entries[index]) },
        compact = true,
        optionTestTags = statisticsRangeTags,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(STATISTICS_RANGE)
    )
}

@Composable
internal fun StatisticsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapeTokens.surface,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.heroPadding),
            content = content
        )
    }
}

@Composable
internal fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = AppTypeTokens.statValue,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = AppDimens.itemGap)
        )
    }
}

@Composable
internal fun SeeMoreRow(
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(testTag)
    ) {
        Text(label.uppercase(AppLocale.UI))
    }
}

@Composable
internal fun DestinationRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun bestLabel(best: ExerciseBest): String {
    return when (best) {
        is ExerciseBest.Reps -> stringResource(R.string.statistics_best_reps, best.reps)
        is ExerciseBest.WeightedSet -> if (best.perSide) {
            stringResource(
                R.string.statistics_best_weight_per_side,
                UiFormatters.weightValue(best.recordedKg),
                best.reps,
                UiFormatters.weightKg(best.effectiveKg)
            )
        } else {
            stringResource(
                R.string.statistics_best_weight,
                UiFormatters.weightKg(best.effectiveKg),
                best.reps
            )
        }
        is ExerciseBest.Duration -> ElapsedTime.formatMillis(best.seconds * 1000L)
        is ExerciseBest.Distance -> distanceLabel(best)
        is ExerciseBest.Completions -> pluralStringResource(
            R.plurals.statistics_completions,
            best.count,
            best.count
        )
    }
}

@Composable
private fun distanceLabel(best: ExerciseBest.Distance): String {
    val distance = if (best.meters >= 1000.0) {
        stringResource(
            R.string.set_copy_distance_km,
            QuantityParser.formatDisplay(QuantityParser.fromMeters(best.meters, DistanceUnit.KILOMETERS))
        )
    } else {
        stringResource(
            R.string.set_copy_distance_m,
            QuantityParser.formatDisplay(best.meters)
        )
    }
    val duration = best.durationSeconds?.let { ElapsedTime.formatMillis(it * 1000L) }
    return if (duration == null) {
        distance
    } else {
        stringResource(R.string.statistics_best_distance_duration, distance, duration)
    }
}

internal fun StatisticsRange.labelRes(): Int {
    return when (this) {
        StatisticsRange.Days30 -> R.string.statistics_range_30d
        StatisticsRange.Months3 -> R.string.statistics_range_3m
        StatisticsRange.Months6 -> R.string.statistics_range_6m
        StatisticsRange.Year1 -> R.string.statistics_range_1y
        StatisticsRange.All -> R.string.statistics_range_all
    }
}
