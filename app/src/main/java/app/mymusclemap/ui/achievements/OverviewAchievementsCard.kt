package app.mymusclemap.ui.achievements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.OverviewAchievements
import app.mymusclemap.domain.achievements.OverviewProgress
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal const val OVERVIEW_ACHIEVEMENTS = "overview-achievements"
internal const val OVERVIEW_ACHIEVEMENTS_VIEW_ALL = "overview-achievements-view-all"
internal const val OVERVIEW_ACHIEVEMENTS_LATEST = "overview-achievements-latest"
internal const val OVERVIEW_ACHIEVEMENTS_UPCOMING = "overview-achievements-upcoming"
internal const val OVERVIEW_ACHIEVEMENTS_INVITATION = "overview-achievements-invitation"
internal const val OVERVIEW_ACHIEVEMENTS_PROGRESS = "overview-achievements-progress"
internal const val OVERVIEW_ACHIEVEMENTS_DIVIDER = "overview-achievements-divider"

private val LatestBadgeSize = 80.dp
private val UpcomingBadgeSize = 44.dp

@Composable
internal fun OverviewAchievementsCard(
    achievements: OverviewAchievements,
    today: LocalDate,
    onViewAll: () -> Unit,
    onOpenAchievement: (AchievementId) -> Unit,
    modifier: Modifier = Modifier
) {
    val upcoming = achievements.upcoming
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag(OVERVIEW_ACHIEVEMENTS),
        shape = AppShapeTokens.surface,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.achievements_title),
                    style = AppTypeTokens.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(role = Role.Button, onClick = onViewAll)
                        .testTag(OVERVIEW_ACHIEVEMENTS_VIEW_ALL),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.achievements_view_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = StrictBrand.actionContainer()
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = StrictBrand.actionContainer(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val latest = achievements.latest
            if (latest != null) {
                LatestUnlock(
                    id = latest.id,
                    unlockedAtEpochMillis = latest.unlockedAtEpochMillis,
                    today = today,
                    onClick = { onOpenAchievement(latest.id) }
                )
            } else {
                Text(
                    text = stringResource(R.string.achievements_earn_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .testTag(OVERVIEW_ACHIEVEMENTS_INVITATION)
                )
            }
            if (upcoming != null) {
                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .testTag(OVERVIEW_ACHIEVEMENTS_DIVIDER),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                UpcomingMilestone(
                    id = upcoming.id,
                    progress = upcoming.progress,
                    onClick = { onOpenAchievement(upcoming.id) }
                )
            }
        }
    }
}

@Composable
private fun LatestUnlock(
    id: AchievementId,
    unlockedAtEpochMillis: Long,
    today: LocalDate,
    onClick: () -> Unit
) {
    val earnedDate = Instant.ofEpochMilli(unlockedAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val earnedLabel = if (earnedDate == today) {
        stringResource(R.string.achievements_earned_today)
    } else {
        stringResource(R.string.achievements_unlocked_on, UiFormatters.compactDate(earnedDate))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(OVERVIEW_ACHIEVEMENTS_LATEST),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AchievementBadgeImage(
            id = id,
            unlocked = true,
            size = LatestBadgeSize,
            emphasis = BadgeArtworkEmphasis.Earned
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.achievements_latest_unlock).uppercase(AppLocale.UI),
                style = AppTypeTokens.columnHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = achievementTitle(id, revealed = true),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = achievementRequirement(id, revealed = true),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = StrictBrand.actionContainer(),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = earnedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = StrictBrand.actionContainer()
                )
            }
        }
    }
}

@Composable
private fun UpcomingMilestone(
    id: AchievementId,
    progress: OverviewProgress,
    onClick: () -> Unit
) {
    val detail = upcomingDetail(progress, id)
    val bar = progressBarFraction(progress)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(OVERVIEW_ACHIEVEMENTS_UPCOMING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AchievementBadgeImage(
            id = id,
            unlocked = false,
            size = UpcomingBadgeSize,
            emphasis = BadgeArtworkEmphasis.Next
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.achievements_next_milestone).uppercase(AppLocale.UI),
                style = AppTypeTokens.columnHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = achievementTitle(id, revealed = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (bar != null) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(OVERVIEW_ACHIEVEMENTS_PROGRESS)
                        .semantics { hideFromAccessibility() }
                ) {
                    LinearProgressIndicator(
                        progress = { bar.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = StrictBrand.result(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {}
                    )
                }
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
private fun upcomingDetail(progress: OverviewProgress, id: AchievementId): String {
    return when (progress) {
        is OverviewProgress.Count -> stringResource(
            R.string.achievements_progress_count,
            progress.current,
            progress.threshold
        )
        is OverviewProgress.Volume -> stringResource(
            R.string.badge_wall_volume_progress,
            volumeAmount(progress.currentKg),
            volumeAmount(progress.thresholdKg)
        )
        is OverviewProgress.TargetWeight -> stringResource(
            R.string.badge_wall_kg_to_target,
            UiFormatters.weightValue(progress.remainingKg)
        )
        OverviewProgress.Requirement -> achievementRequirement(id, revealed = false)
    }
}

private fun progressBarFraction(progress: OverviewProgress): Float? {
    return when (progress) {
        is OverviewProgress.Count ->
            if (progress.threshold > 0) progress.current.toFloat() / progress.threshold.toFloat() else null
        is OverviewProgress.Volume ->
            if (progress.thresholdKg > 0.0) (progress.currentKg / progress.thresholdKg).toFloat() else null
        is OverviewProgress.TargetWeight -> progress.fraction.toFloat()
        OverviewProgress.Requirement -> null
    }
}

private fun volumeAmount(value: Double): String {
    val whole = value.toLong()
    return if (value == whole.toDouble()) "%,d".format(AppLocale.UI, whole) else UiFormatters.weightValue(value)
}
