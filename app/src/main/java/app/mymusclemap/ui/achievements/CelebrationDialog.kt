package app.mymusclemap.ui.achievements

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.achievements.WeightMilestone
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.StrictBrand

internal const val CELEBRATION_DIALOG = "celebration-dialog"
internal const val CELEBRATION_CONFIRM = "celebration-confirm"

internal fun PendingCelebration.artworkAchievementId(): AchievementId? {
    return when (this) {
        is PendingCelebration.WorkoutCountUnlocked -> achievementId
        is PendingCelebration.WeeklyStreakUnlocked -> achievementId
        is PendingCelebration.JourneyUnlocked -> achievementId
        is PendingCelebration.PerformanceUnlocked -> achievementId
        is PendingCelebration.ProUnlocked -> achievementId
        is PendingCelebration.SecretUnlocked -> achievementId
        is PendingCelebration.TargetWeightMilestone ->
            if (includesLifetimeUnlock) AchievementId.TARGET_WEIGHT_REACHED else null
        is PendingCelebration.HistoryRecognized,
        is PendingCelebration.WeeklyGoalCompleted -> null
    }
}

@Composable
fun CelebrationDialog(
    celebrations: List<PendingCelebration>,
    onDismiss: () -> Unit
) {
    if (celebrations.isEmpty()) return
    var delivered by remember { mutableStateOf(false) }
    val acknowledge = {
        if (!delivered) {
            delivered = true
            onDismiss()
        }
    }
    val hasArtwork = celebrations.any { it.artworkAchievementId() != null }
    Dialog(
        onDismissRequest = acknowledge,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .testTag(CELEBRATION_DIALOG),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (hasArtwork) {
                            stringResource(R.string.celebration_unlocked_heading)
                        } else if (celebrations.size == 1) {
                            celebrationTitle(celebrations.first())
                        } else {
                            stringResource(R.string.achievements_title)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (hasArtwork) StrictBrand.result() else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    celebrations.forEachIndexed { index, celebration ->
                        Spacer(Modifier.height(if (index == 0) 20.dp else 16.dp))
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(16.dp))
                        }
                        CelebrationBlock(
                            celebration = celebration,
                            showName = artworkId(celebration) != null || celebrations.size > 1
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = acknowledge,
                    shape = AppShapeTokens.button,
                    colors = StrictBrand.actionButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CELEBRATION_CONFIRM)
                ) {
                    Text(stringResource(R.string.celebration_awesome))
                }
            }
        }
    }
}

private fun artworkId(celebration: PendingCelebration): AchievementId? = celebration.artworkAchievementId()

@Composable
internal fun CelebrationBlock(
    celebration: PendingCelebration,
    showName: Boolean = true
) {
    val artworkId = celebration.artworkAchievementId()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (artworkId != null) {
            CelebrationArtwork(artworkId)
            Spacer(Modifier.height(16.dp))
        }
        if (showName) {
            Text(
                text = celebrationName(celebration),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = celebrationDetail(celebration),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun CelebrationArtwork(id: AchievementId, badgeSize: androidx.compose.ui.unit.Dp = 120.dp) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val fitted = minOf(badgeSize, maxWidth * 0.46f).coerceIn(96.dp, 140.dp)
        Image(
            painter = painterResource(BadgeArtworkResolver.drawableFor(id.name)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(fitted)
                .testTag("celebration-badge-${id.name}")
        )
    }
}

@Composable
internal fun celebrationName(celebration: PendingCelebration): String {
    val artworkId = celebration.artworkAchievementId()
    return if (artworkId != null && celebration !is PendingCelebration.TargetWeightMilestone) {
        achievementTitle(artworkId, revealed = true)
    } else {
        celebrationTitle(celebration)
    }
}

@Composable
internal fun celebrationDetail(celebration: PendingCelebration): String {
    val artworkId = celebration.artworkAchievementId()
    return if (artworkId != null && celebration !is PendingCelebration.TargetWeightMilestone) {
        achievementRequirement(artworkId, revealed = true)
    } else {
        celebrationBody(celebration)
    }
}

@Composable
fun celebrationTitle(celebration: PendingCelebration): String {
    return when (celebration) {
        is PendingCelebration.HistoryRecognized -> stringResource(R.string.celebration_history_title)
        is PendingCelebration.WeeklyGoalCompleted -> stringResource(R.string.celebration_weekly_title)
        is PendingCelebration.WorkoutCountUnlocked -> stringResource(R.string.celebration_workout_title)
        is PendingCelebration.WeeklyStreakUnlocked ->
            stringResource(R.string.achievement_streak_name, celebration.weeks)
        is PendingCelebration.JourneyUnlocked -> achievementTitle(celebration.achievementId)
        is PendingCelebration.PerformanceUnlocked -> achievementTitle(celebration.achievementId)
        is PendingCelebration.ProUnlocked -> achievementTitle(celebration.achievementId)
        is PendingCelebration.SecretUnlocked -> achievementTitle(celebration.achievementId, revealed = true)
        is PendingCelebration.TargetWeightMilestone -> when {
            celebration.includesLifetimeUnlock -> stringResource(R.string.achievement_on_target_name)
            celebration.milestone == WeightMilestone.HALFWAY ->
                stringResource(R.string.celebration_weight_halfway_title)
            celebration.milestone == WeightMilestone.REMAINING_5 ->
                stringResource(R.string.celebration_weight_5_title)
            celebration.milestone == WeightMilestone.REMAINING_2 ->
                stringResource(R.string.celebration_weight_2_title)
            celebration.milestone == WeightMilestone.REMAINING_1 ->
                stringResource(R.string.celebration_weight_1_title)
            else -> stringResource(R.string.celebration_weight_reached_title)
        }
    }
}

@Composable
fun celebrationBody(celebration: PendingCelebration): String {
    return when (celebration) {
        is PendingCelebration.HistoryRecognized -> pluralStringResource(
            R.plurals.celebration_history_body,
            celebration.badgeCount,
            celebration.badgeCount
        )
        is PendingCelebration.WeeklyGoalCompleted -> stringResource(
            R.string.celebration_weekly_body,
            celebration.completed
        )
        is PendingCelebration.WorkoutCountUnlocked -> stringResource(
            R.string.celebration_workout_body,
            celebration.threshold
        )
        is PendingCelebration.WeeklyStreakUnlocked -> stringResource(
            R.string.celebration_streak_body,
            celebration.weeks
        )
        is PendingCelebration.JourneyUnlocked -> achievementRequirement(celebration.achievementId)
        is PendingCelebration.PerformanceUnlocked -> achievementRequirement(celebration.achievementId)
        is PendingCelebration.ProUnlocked -> achievementRequirement(celebration.achievementId)
        is PendingCelebration.SecretUnlocked -> achievementRequirement(celebration.achievementId, revealed = true)
        is PendingCelebration.TargetWeightMilestone -> when (celebration.milestone) {
            WeightMilestone.HALFWAY ->
                stringResource(R.string.celebration_weight_halfway_body)
            WeightMilestone.REMAINING_5 ->
                stringResource(R.string.celebration_weight_5_body)
            WeightMilestone.REMAINING_2 ->
                stringResource(R.string.celebration_weight_2_body)
            WeightMilestone.REMAINING_1 ->
                stringResource(R.string.celebration_weight_1_body)
            WeightMilestone.REACHED ->
                stringResource(R.string.celebration_weight_reached_body)
        }
    }
}
