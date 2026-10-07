package app.mymusclemap.ui.achievements

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.AchievementCategory
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.AlmostThereEntry
import app.mymusclemap.domain.achievements.BadgeTier
import app.mymusclemap.domain.achievements.BadgeVisualState
import app.mymusclemap.domain.achievements.BadgeWallItem
import app.mymusclemap.domain.achievements.BadgeWallPresentation
import app.mymusclemap.domain.achievements.BadgeWallSection
import app.mymusclemap.domain.achievements.JourneyMilestone
import app.mymusclemap.domain.achievements.visualState
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import java.time.Instant
import java.time.ZoneId

internal const val ACHIEVEMENTS_SCREEN = "achievements-screen"
internal const val ACHIEVEMENTS_BACK = "achievements-back"
internal const val BADGE_SUMMARY = "badge-summary"
internal const val BADGE_ALMOST_THERE = "badge-almost-there"
internal const val BADGE_DETAIL = "badge-detail"

private val CardShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(50)
private val MinBadgeColumnWidth = 108.dp

internal fun badgeColumnCount(width: Dp): Int {
    return (width / MinBadgeColumnWidth).toInt().coerceIn(3, 6)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    presentation: BadgeWallPresentation,
    onBack: () -> Unit,
    onFilterSelected: (AchievementCategory?) -> Unit = {},
    selectedBadgeId: AchievementId? = null,
    onBadgeSelected: (AchievementId) -> Unit = {},
    onDismissBadge: () -> Unit = {}
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
            AchievementsBar(onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(bottom = AppDimens.scrollEndPadding)
            ) {
                FilterRow(presentation, onFilterSelected)
                Spacer(Modifier.height(AppDimens.itemGap))
                SummaryCard(presentation)
                if (presentation.almostThere.isNotEmpty()) {
                    Spacer(Modifier.height(AppDimens.sectionGap))
                    AlmostThereCard(presentation, onBadgeSelected)
                }
                presentation.sections.forEach { section ->
                    Spacer(Modifier.height(AppDimens.sectionGap))
                    BadgeSection(section, presentation, onBadgeSelected)
                }
            }
        }
    }
    val selected = selectedBadgeId?.let(presentation::item)
    if (selected != null) {
        BadgeDetailSheet(item = selected, onDismiss = onDismissBadge)
    }
}

@Composable
private fun AchievementsBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag(ACHIEVEMENTS_BACK)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back)
            )
        }
        Text(
            text = stringResource(R.string.achievements_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
private fun FilterRow(
    presentation: BadgeWallPresentation,
    onFilterSelected: (AchievementCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            label = stringResource(R.string.badge_wall_filter_all),
            selected = presentation.selectedFilter == null,
            tag = "badge-filter-ALL",
            onClick = { onFilterSelected(null) }
        )
        presentation.filters.forEach { category ->
            FilterChip(
                label = stringResource(category.labelRes()),
                selected = presentation.selectedFilter == category,
                tag = "badge-filter-${category.name}",
                onClick = { onFilterSelected(category) }
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val background = if (selected) StrictBrand.actionContainer() else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) StrictBrand.onAction() else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = content,
        modifier = Modifier
            .clip(ChipShape)
            .background(background)
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(tag)
    )
}

@Composable
private fun SummaryCard(presentation: BadgeWallPresentation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .testTag(BADGE_SUMMARY)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = StrictBrand.result(),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(
                        R.string.badge_wall_section_progress,
                        presentation.earnedCount,
                        presentation.totalCount
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("badge-summary-count")
                )
                Text(
                    text = stringResource(R.string.badge_wall_badges_earned),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ThinProgress(presentation.overallFraction)
    }
}

@Composable
private fun AlmostThereCard(
    presentation: BadgeWallPresentation,
    onBadgeSelected: (AchievementId) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(BADGE_ALMOST_THERE)
    ) {
        Text(
            text = stringResource(R.string.badge_wall_almost_there),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .semantics { heading() }
        )
        presentation.almostThere.forEachIndexed { index, entry ->
            val item = presentation.item(entry.achievementId) ?: return@forEachIndexed
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            AlmostThereRow(item, entry, onBadgeSelected)
        }
    }
}

@Composable
private fun AlmostThereRow(
    item: BadgeWallItem,
    entry: AlmostThereEntry,
    onBadgeSelected: (AchievementId) -> Unit
) {
    val progressText = almostThereProgress(entry)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onBadgeSelected(item.id) }, role = Role.Button)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("badge-almost-${item.id.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgeArtwork(item, size = 44.dp, earned = false)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = achievementTitle(item.id),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = achievementRequirement(item.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            ThinProgress(entry.fraction.toFloat())
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = progressText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun almostThereProgress(entry: AlmostThereEntry): String {
    val count = entry.countProgress
    if (count != null) {
        return stringResource(R.string.achievements_progress_count, count.current, count.threshold)
    }
    val remaining = entry.remainingKg
    if (remaining != null) {
        return stringResource(R.string.badge_wall_kg_to_target, UiFormatters.weightValue(remaining))
    }
    return ""
}

@Composable
private fun BadgeSection(
    section: BadgeWallSection,
    presentation: BadgeWallPresentation,
    onBadgeSelected: (AchievementId) -> Unit
) {
    val earned = section.items.count { it.unlocked }
    Column(modifier = Modifier.fillMaxWidth().testTag("badge-section-${section.category.name}")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(StrictBrand.result())
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(section.category.labelRes()),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            Text(
                text = stringResource(R.string.badge_wall_section_progress, earned, section.items.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        Spacer(Modifier.height(8.dp))
        BadgeGrid(section.items, presentation, onBadgeSelected)
    }
}

@Composable
private fun BadgeGrid(
    items: List<BadgeWallItem>,
    presentation: BadgeWallPresentation,
    onBadgeSelected: (AchievementId) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = badgeColumnCount(maxWidth)
        val imageSize = (maxWidth / columns * 0.62f).coerceIn(56.dp, 88.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.chunked(columns).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { item ->
                        BadgeCell(
                            item = item,
                            imageSize = imageSize,
                            showProgress = presentation.almostThere.any { it.achievementId == item.id },
                            onClick = { onBadgeSelected(item.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeCell(
    item: BadgeWallItem,
    imageSize: Dp,
    showProgress: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateLabel = stringResource(
        if (item.unlocked) R.string.badge_wall_earned else R.string.badge_wall_locked
    )
    Column(
        modifier = modifier
            .clickable(onClick = onClick, role = Role.Button)
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .testTag("achievement-${item.id.name}")
            .semantics {
                role = Role.Button
                stateDescription = stateLabel
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BadgeArtwork(item, imageSize, earned = item.unlocked)
        Spacer(Modifier.height(6.dp))
        Text(
            text = achievementTitle(item.id),
            style = MaterialTheme.typography.labelMedium,
            color = if (item.unlocked) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (showProgress && item.countProgress != null) {
            Text(
                text = stringResource(
                    R.string.achievements_progress_count,
                    item.countProgress.current,
                    item.countProgress.threshold
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("achievement-progress-${item.id.name}")
            )
        }
    }
}

@Composable
private fun BadgeArtwork(item: BadgeWallItem, size: Dp, earned: Boolean) {
    val modifier = Modifier
        .size(size)
        .alpha(if (earned) 1f else 0.48f)
        .testTag(
            if (earned) "achievement-badge-unlocked-${item.id.name}"
            else "achievement-badge-locked-${item.id.name}"
        )
    val painter = painterResource(BadgeArtworkResolver.drawableFor(item.badgeKey))
    if (BadgeArtworkResolver.isProductionArtwork(item.badgeKey)) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )
    }
}

@Composable
private fun ThinProgress(fraction: Float) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
        color = StrictBrand.result(),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        drawStopIndicator = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BadgeDetailSheet(item: BadgeWallItem, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .testTag(BADGE_DETAIL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BadgeArtwork(item, size = 140.dp, earned = item.unlocked)
            Spacer(Modifier.height(16.dp))
            Text(
                text = achievementTitle(item.id),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("badge-detail-title")
            )
            Text(
                text = stringResource(item.category.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            item.badgeTier?.let { tier ->
                Text(
                    text = stringResource(tier.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = StrictBrand.result(),
                    modifier = Modifier.testTag("badge-detail-tier")
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = achievementRequirement(item.id),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("badge-detail-requirement")
            )
            Spacer(Modifier.height(8.dp))
            val state = item.visualState()
            Text(
                text = stringResource(
                    if (item.unlocked) R.string.badge_wall_earned else R.string.badge_wall_locked
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("badge-detail-state")
            )
            if (item.unlocked) {
                item.unlockedAt?.let { unlockedAt ->
                    val date = Instant.ofEpochMilli(unlockedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    Text(
                        text = stringResource(
                            R.string.achievements_unlocked_on,
                            UiFormatters.compactDate(date)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("badge-detail-earned-date")
                    )
                }
            } else if (state == BadgeVisualState.LOCKED_PROGRESS && item.countProgress != null) {
                Spacer(Modifier.height(12.dp))
                val progress = item.countProgress
                ThinProgress(progress.current.toFloat() / progress.threshold.toFloat())
                Text(
                    text = stringResource(
                        R.string.achievements_progress_count,
                        progress.current,
                        progress.threshold
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .testTag("badge-detail-progress")
                )
            }
        }
    }
}

@Composable
internal fun achievementTitle(id: AchievementId): String {
    val workouts = id.workoutThreshold
    val weeks = id.streakWeeks
    return when {
        workouts != null -> stringResource(R.string.achievements_workouts_name, workouts)
        weeks != null -> stringResource(R.string.achievement_streak_name, weeks)
        id.journeyMilestone != null -> stringResource(journeyTitleRes(id.journeyMilestone))
        else -> stringResource(R.string.achievement_on_target_name)
    }
}

@Composable
internal fun achievementRequirement(id: AchievementId): String {
    val workouts = id.workoutThreshold
    val weeks = id.streakWeeks
    return when {
        workouts != null -> stringResource(R.string.achievements_workouts_requirement, workouts)
        weeks != null -> stringResource(R.string.achievement_streak_requirement, weeks)
        id.journeyMilestone != null -> stringResource(journeyRequirementRes(id.journeyMilestone))
        else -> stringResource(R.string.achievement_on_target_requirement)
    }
}

private fun journeyTitleRes(milestone: JourneyMilestone): Int {
    return when (milestone) {
        JourneyMilestone.FIRST_WORKOUT -> R.string.achievement_first_step_name
        JourneyMilestone.FIRST_PLAN -> R.string.achievement_planner_name
        JourneyMilestone.FIRST_MONTHLY_REPORT -> R.string.achievement_monthly_review_name
    }
}

private fun journeyRequirementRes(milestone: JourneyMilestone): Int {
    return when (milestone) {
        JourneyMilestone.FIRST_WORKOUT -> R.string.achievement_first_step_requirement
        JourneyMilestone.FIRST_PLAN -> R.string.achievement_planner_requirement
        JourneyMilestone.FIRST_MONTHLY_REPORT -> R.string.achievement_monthly_review_requirement
    }
}

private fun AchievementCategory.labelRes(): Int {
    return when (this) {
        AchievementCategory.CONSISTENCY -> R.string.achievements_category_consistency
        AchievementCategory.PERFORMANCE -> R.string.achievements_category_performance
        AchievementCategory.JOURNEY -> R.string.achievements_category_journey
        AchievementCategory.GOALS -> R.string.achievements_category_goals
    }
}

private fun BadgeTier.labelRes(): Int {
    return when (this) {
        BadgeTier.BRONZE -> R.string.badge_wall_tier_bronze
        BadgeTier.SILVER -> R.string.badge_wall_tier_silver
        BadgeTier.GOLD -> R.string.badge_wall_tier_gold
    }
}
