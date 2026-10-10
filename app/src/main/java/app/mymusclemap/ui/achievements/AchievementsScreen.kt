package app.mymusclemap.ui.achievements

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import app.mymusclemap.domain.achievements.AchievementAccess
import app.mymusclemap.domain.achievements.AchievementCategory
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.AlmostThereEntry
import app.mymusclemap.domain.achievements.BadgeFamily
import app.mymusclemap.domain.achievements.BadgeTier
import app.mymusclemap.domain.achievements.BadgeVisualState
import app.mymusclemap.domain.achievements.BadgeWallItem
import app.mymusclemap.domain.achievements.BadgeWallPresentation
import app.mymusclemap.domain.achievements.BadgeWallSection
import app.mymusclemap.domain.achievements.JourneyMilestone
import app.mymusclemap.domain.achievements.visualState
import app.mymusclemap.ui.components.SegmentedControl
import app.mymusclemap.domain.locale.AppLocale
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
private val MinBadgeColumnWidth = 108.dp
private const val LockedBadgeAlpha = 0.40f
private const val NextBadgeAlpha = 0.48f
private const val LockedBadgeSaturation = 0.50f

internal fun badgeColumnCount(width: Dp): Int {
    return (width / MinBadgeColumnWidth).toInt().coerceIn(3, 6)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    presentation: BadgeWallPresentation,
    onBack: () -> Unit,
    onFilterSelected: (AchievementAccess?) -> Unit = {},
    selectedBadgeId: AchievementId? = null,
    onBadgeSelected: (AchievementId) -> Unit = {},
    onDismissBadge: () -> Unit = {},
    onOpenPro: (() -> Unit)? = null
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
                if (presentation.catalog.any { it.visualState() == BadgeVisualState.REQUIREMENT_MET_PRO_LOCKED }) {
                    ProRequirementTeaser(onOpenPro)
                }
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
    onFilterSelected: (AchievementAccess?) -> Unit
) {
    val filters = presentation.filters
    val selectedIndex = presentation.selectedFilter?.let { filters.indexOf(it) + 1 }?.takeIf { it > 0 } ?: 0
    SegmentedControl(
        options = listOf(stringResource(R.string.badge_wall_filter_all)) +
            filters.map { stringResource(it.labelRes()) },
        selectedIndex = selectedIndex,
        onSelected = { index ->
            onFilterSelected(filters.getOrNull(index - 1))
        },
        compact = true,
        optionTestTags = listOf("badge-filter-ALL") + filters.map { "badge-filter-${it.name}" },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ProRequirementTeaser(onOpenPro: (() -> Unit)?) {
    val label = stringResource(R.string.badge_wall_unlock_with_pro)
    if (onOpenPro == null) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = StrictBrand.result(),
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("badge-pro-teaser")
        )
    } else {
        TextButton(
            onClick = onOpenPro,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("badge-pro-teaser")
        ) {
            Text(text = label, color = StrictBrand.result())
        }
    }
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
        BadgeArtwork(item, size = 44.dp, emphasis = BadgeArtworkEmphasis.Next)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = achievementTitle(item.id, revealed = item.unlocked),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.access != AchievementAccess.FREE) {
                    Spacer(Modifier.width(6.dp))
                    AccessMark(item)
                }
            }
            Text(
                text = achievementRequirement(item.id, revealed = item.unlocked),
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
    val volume = entry.volumeProgress
    if (volume != null) {
        return stringResource(
            R.string.badge_wall_volume_progress,
            volumeAmount(volume.currentKg),
            volumeAmount(volume.thresholdKg)
        )
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
        BadgeArtwork(
            item,
            imageSize,
            emphasis = if (item.unlocked) BadgeArtworkEmphasis.Earned else BadgeArtworkEmphasis.Locked
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = achievementTitle(item.id, revealed = item.unlocked),
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
        if (item.access != AchievementAccess.FREE) {
            Spacer(Modifier.height(4.dp))
            AccessMark(item)
        }
        val progressLabel = when {
            showProgress && item.countProgress != null -> stringResource(
                R.string.achievements_progress_count,
                item.countProgress.current,
                item.countProgress.threshold
            )
            showProgress && item.volumeProgress != null -> stringResource(
                R.string.badge_wall_volume_progress,
                volumeAmount(item.volumeProgress.currentKg),
                volumeAmount(item.volumeProgress.thresholdKg)
            )
            else -> null
        }
        if (progressLabel != null) {
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("achievement-progress-${item.id.name}")
            )
        }
    }
}

private enum class BadgeArtworkEmphasis {
    Earned,
    Next,
    Locked
}

@Composable
private fun BadgeArtwork(item: BadgeWallItem, size: Dp, emphasis: BadgeArtworkEmphasis) {
    val earned = emphasis == BadgeArtworkEmphasis.Earned
    val secretLocked = item.id.secret && !earned
    val modifier = Modifier
        .size(size)
        .alpha(
            if (secretLocked) {
                1f
            } else {
                when (emphasis) {
                    BadgeArtworkEmphasis.Earned -> 1f
                    BadgeArtworkEmphasis.Next -> NextBadgeAlpha
                    BadgeArtworkEmphasis.Locked -> LockedBadgeAlpha
                }
            }
        )
        .testTag(
            if (earned) "achievement-badge-unlocked-${item.id.name}"
            else "achievement-badge-locked-${item.id.name}"
        )
    val painter = painterResource(BadgeArtworkResolver.drawableForWall(item.id, earned))
    val lockedFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(LockedBadgeSaturation) })
    }
    if (secretLocked || BadgeArtworkResolver.isProductionArtwork(item.badgeKey)) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = if (!secretLocked && emphasis == BadgeArtworkEmphasis.Locked) lockedFilter else null,
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
            BadgeArtwork(
                item,
                size = 140.dp,
                emphasis = if (item.unlocked) BadgeArtworkEmphasis.Earned else BadgeArtworkEmphasis.Locked
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = achievementTitle(item.id, revealed = item.unlocked),
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
            if (item.access != AchievementAccess.FREE) {
                Spacer(Modifier.height(8.dp))
                AccessMark(item)
            }
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
                text = achievementRequirement(item.id, revealed = item.unlocked),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("badge-detail-requirement")
            )
            Spacer(Modifier.height(8.dp))
            val state = item.visualState()
            Text(
                text = stringResource(detailStateRes(state)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("badge-detail-state")
            )
            if (state == BadgeVisualState.REQUIREMENT_MET_PRO_LOCKED) {
                Text(
                    text = stringResource(R.string.badge_wall_unlock_with_pro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("badge-detail-pro-lock")
                )
            }
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
            } else if (state == BadgeVisualState.LOCKED_PROGRESS || state == BadgeVisualState.REQUIREMENT_MET_PRO_LOCKED) {
                ProgressDetail(item)
            }
        }
    }
}

@Composable
internal fun achievementTitle(id: AchievementId, revealed: Boolean = !id.secret): String {
    if (id.secret && !revealed) {
        return stringResource(R.string.achievement_secret_name)
    }
    val workouts = id.workoutThreshold
    val weeks = id.streakWeeks
    return when {
        id == AchievementId.SILENT_NIGHT -> stringResource(R.string.achievement_silent_night_name)
        id == AchievementId.TRICK_OR_LIFT -> stringResource(R.string.achievement_trick_or_lift_name)
        id == AchievementId.NEW_YEAR_SAME_ME -> stringResource(R.string.achievement_new_year_name)
        id == AchievementId.LEAP_DAY_LIFTER -> stringResource(R.string.achievement_leap_day_name)
        id == AchievementId.FRIDAY_THE_STRONGTEENTH -> stringResource(R.string.achievement_friday_thirteenth_name)
        id == AchievementId.ONE_MORE_THING -> stringResource(R.string.achievement_one_more_thing_name)
        id == AchievementId.TRIPLE_CROWN -> stringResource(R.string.achievement_triple_crown_name)
        id == AchievementId.IRON_DISCIPLINE -> stringResource(R.string.achievement_iron_discipline_name)
        id == AchievementId.FOUNDER -> stringResource(R.string.achievement_founder_name)
        id == AchievementId.EARLY_ADOPTER -> stringResource(R.string.achievement_early_adopter_name)
        id == AchievementId.DEVELOPER -> stringResource(R.string.achievement_developer_name)
        id == AchievementId.VOLUME_MASTER -> stringResource(R.string.achievement_volume_master_name)
        id.badgeFamily == BadgeFamily.PR_HUNTER -> stringResource(R.string.achievement_pr_hunter_name)
        id.badgeFamily == BadgeFamily.EXERCISE_MASTERY -> stringResource(R.string.achievement_exercise_mastery_name)
        workouts != null -> stringResource(R.string.achievements_workouts_name, workouts)
        weeks != null -> stringResource(R.string.achievement_streak_name, weeks)
        id.journeyMilestone != null -> stringResource(journeyTitleRes(id.journeyMilestone))
        id.category == AchievementCategory.PERFORMANCE -> stringResource(performanceTitleRes(id))
        else -> stringResource(R.string.achievement_on_target_name)
    }
}

@Composable
internal fun achievementRequirement(id: AchievementId, revealed: Boolean = !id.secret): String {
    if (id.secret && !revealed) {
        return stringResource(R.string.achievement_secret_requirement)
    }
    val workouts = id.workoutThreshold
    val weeks = id.streakWeeks
    return when {
        id == AchievementId.SILENT_NIGHT -> stringResource(R.string.achievement_silent_night_requirement)
        id == AchievementId.TRICK_OR_LIFT -> stringResource(R.string.achievement_trick_or_lift_requirement)
        id == AchievementId.NEW_YEAR_SAME_ME -> stringResource(R.string.achievement_new_year_requirement)
        id == AchievementId.LEAP_DAY_LIFTER -> stringResource(R.string.achievement_leap_day_requirement)
        id == AchievementId.FRIDAY_THE_STRONGTEENTH ->
            stringResource(R.string.achievement_friday_thirteenth_requirement)
        id == AchievementId.ONE_MORE_THING -> stringResource(R.string.achievement_one_more_thing_requirement)
        id == AchievementId.TRIPLE_CROWN -> stringResource(R.string.achievement_triple_crown_requirement)
        id == AchievementId.IRON_DISCIPLINE -> stringResource(R.string.achievement_iron_discipline_requirement)
        id == AchievementId.FOUNDER -> stringResource(R.string.achievement_founder_requirement)
        id == AchievementId.EARLY_ADOPTER -> stringResource(R.string.achievement_early_adopter_requirement)
        id == AchievementId.DEVELOPER -> stringResource(R.string.achievement_developer_requirement)
        id == AchievementId.VOLUME_MASTER -> stringResource(R.string.achievement_volume_master_requirement)
        id.prHunterTarget != null -> stringResource(R.string.achievement_pr_hunter_requirement, id.prHunterTarget)
        id.masterySetTarget != null -> stringResource(
            R.string.achievement_exercise_mastery_requirement,
            id.masterySetTarget
        )
        workouts != null -> stringResource(R.string.achievements_workouts_requirement, workouts)
        weeks != null -> stringResource(R.string.achievement_streak_requirement, weeks)
        id.journeyMilestone != null -> stringResource(journeyRequirementRes(id.journeyMilestone))
        id.category == AchievementCategory.PERFORMANCE -> stringResource(performanceRequirementRes(id))
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

private fun performanceTitleRes(id: AchievementId): Int {
    return when (id) {
        AchievementId.FIRST_PR -> R.string.achievement_first_pr_name
        AchievementId.WEIGHT_PR -> R.string.achievement_weight_pr_name
        AchievementId.REP_RECORD -> R.string.achievement_rep_record_name
        AchievementId.VOLUME_RECORD -> R.string.achievement_volume_record_name
        else -> R.string.achievement_first_pr_name
    }
}

private fun performanceRequirementRes(id: AchievementId): Int {
    return when (id) {
        AchievementId.FIRST_PR -> R.string.achievement_first_pr_requirement
        AchievementId.WEIGHT_PR -> R.string.achievement_weight_pr_requirement
        AchievementId.REP_RECORD -> R.string.achievement_rep_record_requirement
        AchievementId.VOLUME_RECORD -> R.string.achievement_volume_record_requirement
        else -> R.string.achievement_first_pr_requirement
    }
}

private fun journeyRequirementRes(milestone: JourneyMilestone): Int {
    return when (milestone) {
        JourneyMilestone.FIRST_WORKOUT -> R.string.achievement_first_step_requirement
        JourneyMilestone.FIRST_PLAN -> R.string.achievement_planner_requirement
        JourneyMilestone.FIRST_MONTHLY_REPORT -> R.string.achievement_monthly_review_requirement
    }
}

private fun AchievementAccess.labelRes(): Int {
    return when (this) {
        AchievementAccess.FREE -> R.string.badge_wall_filter_free
        AchievementAccess.PRO -> R.string.badge_wall_filter_pro
        AchievementAccess.SPECIAL -> R.string.badge_wall_filter_special
    }
}

private fun detailStateRes(state: BadgeVisualState): Int {
    return when (state) {
        BadgeVisualState.EARNED -> R.string.badge_wall_earned
        BadgeVisualState.REQUIREMENT_MET_PRO_LOCKED -> R.string.badge_wall_requirement_complete
        BadgeVisualState.LOCKED_PROGRESS,
        BadgeVisualState.LOCKED -> R.string.badge_wall_locked
    }
}

@Composable
private fun AccessMark(item: BadgeWallItem) {
    val label = when (item.access) {
        AchievementAccess.FREE -> return
        AchievementAccess.PRO -> R.string.badge_wall_pro
        AchievementAccess.SPECIAL -> R.string.badge_wall_special
    }
    val tag = when (item.access) {
        AchievementAccess.PRO -> "badge-pro-${item.id.name}"
        AchievementAccess.SPECIAL -> "badge-special-${item.id.name}"
        AchievementAccess.FREE -> return
    }
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        color = StrictBrand.onAction(),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(StrictBrand.actionContainer())
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .testTag(tag)
    )
}

@Composable
private fun ProgressDetail(item: BadgeWallItem) {
    val count = item.countProgress
    val volume = item.volumeProgress
    if (count == null && volume == null) return
    Spacer(Modifier.height(12.dp))
    val fraction = when {
        count != null && count.threshold > 0 -> count.current.toFloat() / count.threshold.toFloat()
        volume != null && volume.thresholdKg > 0.0 ->
            (volume.currentKg / volume.thresholdKg).toFloat()
        else -> 0f
    }
    ThinProgress(fraction)
    val label = when {
        count != null -> stringResource(
            R.string.achievements_progress_count,
            count.current,
            count.threshold
        )
        volume != null -> stringResource(
            R.string.badge_wall_volume_progress,
            volumeAmount(volume.currentKg),
            volumeAmount(volume.thresholdKg)
        )
        else -> return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 6.dp)
            .testTag("badge-detail-progress")
    )
}

private fun volumeAmount(value: Double): String {
    val whole = value.toLong()
    return if (value == whole.toDouble()) "%,d".format(AppLocale.UI, whole) else UiFormatters.weightValue(value)
}

private fun AchievementCategory.labelRes(): Int {
    return when (this) {
        AchievementCategory.CONSISTENCY -> R.string.achievements_category_consistency
        AchievementCategory.PERFORMANCE -> R.string.achievements_category_performance
        AchievementCategory.JOURNEY -> R.string.achievements_category_journey
        AchievementCategory.GOALS -> R.string.achievements_category_goals
        AchievementCategory.HIDDEN_GEMS -> R.string.achievements_category_hidden_gems
        AchievementCategory.SPECIAL -> R.string.achievements_category_special
    }
}

private fun BadgeTier.labelRes(): Int {
    return when (this) {
        BadgeTier.BRONZE -> R.string.badge_wall_tier_bronze
        BadgeTier.SILVER -> R.string.badge_wall_tier_silver
        BadgeTier.GOLD -> R.string.badge_wall_tier_gold
    }
}
