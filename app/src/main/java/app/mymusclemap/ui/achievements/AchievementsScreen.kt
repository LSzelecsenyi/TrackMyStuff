package app.mymusclemap.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementCategory
import app.mymusclemap.domain.achievements.BadgeWallItem
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import java.time.Instant
import java.time.ZoneId

internal const val ACHIEVEMENTS_SCREEN = "achievements-screen"
internal const val ACHIEVEMENTS_BACK = "achievements-back"

@Composable
fun AchievementsScreen(
    items: List<BadgeWallItem>,
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ACHIEVEMENTS_SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(ACHIEVEMENTS_BACK)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
                Text(
                    text = stringResource(R.string.achievements_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(bottom = 24.dp)
            ) {
                AchievementCategory.entries.forEach { category ->
                    CategoryBlock(
                        category = category,
                        items = items.filter { it.category == category }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBlock(
    category: AchievementCategory,
    items: List<BadgeWallItem>
) {
    Text(
        text = stringResource(category.labelRes()),
        style = AppTypeTokens.columnHeader,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = AppDimens.sectionGap, bottom = AppDimens.itemGap)
            .testTag("achievements-category-${category.name}")
            .semantics { heading() }
    )
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.achievements_category_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("achievements-empty-${category.name}")
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.itemGap)) {
            items.forEach { item ->
                BadgeRow(item)
            }
        }
    }
}

@Composable
private fun BadgeRow(item: BadgeWallItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("achievement-${item.id.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(BadgeArtworkResolver.drawableFor(item.badgeKey)),
            contentDescription = stringResource(R.string.achievements_badge_content_description),
            tint = if (item.unlocked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier
                .size(48.dp)
                .alpha(if (item.unlocked) 1f else 0.55f)
                .testTag(
                    if (item.unlocked) "achievement-badge-unlocked-${item.id.name}"
                    else "achievement-badge-locked-${item.id.name}"
                )
        )
        Column(modifier = Modifier.padding(start = AppDimens.itemGap)) {
            Text(
                text = achievementTitle(item),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = achievementRequirement(item),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val detail = achievementDetail(item)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("achievement-detail-${item.id.name}")
                )
            }
        }
    }
}

@Composable
private fun achievementTitle(item: BadgeWallItem): String {
    val progress = item.workoutProgress
    return if (progress != null) {
        stringResource(R.string.achievements_workouts_name, progress.threshold)
    } else {
        stringResource(R.string.achievement_on_target_name)
    }
}

@Composable
private fun achievementRequirement(item: BadgeWallItem): String {
    val progress = item.workoutProgress
    return if (progress != null) {
        stringResource(R.string.achievements_workouts_requirement, progress.threshold)
    } else {
        stringResource(R.string.achievement_on_target_requirement)
    }
}

@Composable
private fun achievementDetail(item: BadgeWallItem): String? {
    if (item.unlocked) {
        val unlockedAt = item.unlockedAt ?: return null
        val date = Instant.ofEpochMilli(unlockedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        return stringResource(R.string.achievements_unlocked_on, UiFormatters.compactDate(date))
    }
    val progress = item.workoutProgress ?: return null
    return stringResource(
        R.string.achievements_progress_count,
        progress.current,
        progress.threshold
    )
}

private fun AchievementCategory.labelRes(): Int {
    return when (this) {
        AchievementCategory.CONSISTENCY -> R.string.achievements_category_consistency
        AchievementCategory.PERFORMANCE -> R.string.achievements_category_performance
        AchievementCategory.JOURNEY -> R.string.achievements_category_journey
        AchievementCategory.GOALS -> R.string.achievements_category_goals
    }
}
