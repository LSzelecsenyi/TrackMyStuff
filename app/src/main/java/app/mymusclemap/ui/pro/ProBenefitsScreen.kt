package app.mymusclemap.ui.pro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.mymusclemap.R
import app.mymusclemap.domain.billing.ProPlanPresentation
import app.mymusclemap.domain.billing.SubscriptionFacts
import app.mymusclemap.domain.billing.SubscriptionOffer
import app.mymusclemap.domain.entitlement.FeatureAccessPolicy
import app.mymusclemap.domain.entitlement.ProAccessLevel
import app.mymusclemap.domain.entitlement.ProAccessSource
import app.mymusclemap.domain.entitlement.ProBenefitsAccess
import app.mymusclemap.domain.entitlement.ProBenefitsStatus
import app.mymusclemap.domain.entitlement.ProComparisonRow
import app.mymusclemap.domain.entitlement.ProDiscoverySnapshot
import app.mymusclemap.domain.entitlement.WelcomeBackNotice
import app.mymusclemap.domain.entitlement.WelcomeBackSnapshot
import app.mymusclemap.domain.entitlement.WorkoutPlanAccess
import app.mymusclemap.domain.entitlement.formatTrialRemaining
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import java.time.Instant

internal const val PRO_BENEFITS_ROOT = "pro-benefits-root"
internal const val PRO_BENEFITS_BACK = "pro-benefits-back"
internal const val PRO_BENEFITS_INTRO = "pro-benefits-intro"
internal const val PRO_BENEFITS_STATUS = "pro-benefits-status"
internal const val PRO_BENEFITS_SOURCE = "pro-benefits-source"
internal const val PRO_BENEFITS_EXERCISES = "pro-benefits-exercises"
internal const val PRO_BENEFITS_TIMERS = "pro-benefits-timers"
internal const val PRO_BENEFITS_ACHIEVEMENTS = "pro-benefits-achievements"
internal const val PRO_BENEFITS_TRIAL = "pro-benefits-trial"
internal const val PRO_BENEFITS_OFFER = "pro-benefits-offer"
internal const val PRO_BENEFITS_WARNING = "pro-benefits-warning"
internal const val PRO_BENEFITS_SUBSCRIPTION = "pro-benefits-subscription"
internal const val PRO_BENEFITS_TABLE = "pro-benefits-table"
internal const val PRO_BENEFITS_SUBSCRIBE = "pro-benefits-subscribe"
internal const val PRO_BENEFITS_MANAGE = "pro-benefits-manage"
internal const val PRO_BENEFITS_PRIVACY = "pro-benefits-privacy"

/**
 * Free and Pro differences from the current feature policy.
 * This screen does not sell, renew, or change entitlement.
 */
@Composable
fun ProBenefitsScreen(
    status: ProBenefitsStatus,
    onBack: () -> Unit,
    discovery: ProDiscoverySnapshot = ProDiscoverySnapshot(),
    onActivateTrial: () -> Unit = {},
    welcomeBack: WelcomeBackSnapshot = WelcomeBackSnapshot(),
    onActivateWelcomeBack: () -> Unit = {},
    now: Instant = Instant.now(),
    access: ProBenefitsAccess = ProBenefitsAccess(
        level = if (status.proActive) ProAccessLevel.Pro else ProAccessLevel.Free
    ),
    plan: ProPlanPresentation = ProPlanPresentation.NotConfigured,
    subscription: SubscriptionFacts? = null,
    onSelectOffer: (String) -> Unit = {},
    onSubscribe: () -> Unit = {},
    onManageSubscription: (String) -> Unit = {},
    onOpenPrivacy: () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PRO_BENEFITS_ROOT),
        containerColor = StrictBrand.dark,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(PRO_BENEFITS_BACK)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = StrictBrand.lime
                    )
                }
                Text(
                    text = stringResource(R.string.pro_benefits_title),
                    style = AppTypeTokens.sectionTitle,
                    color = StrictBrand.lime,
                    modifier = Modifier.weight(1f)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(bottom = AppDimens.scrollEndPadding)
            ) {
                AccessIntroduction(access = access)
                DiscoverySection(
                    discovery = discovery,
                    onActivateTrial = onActivateTrial,
                    now = now
                )
                WelcomeBackSection(
                    welcomeBack = welcomeBack,
                    proActive = status.proActive,
                    onActivate = onActivateWelcomeBack
                )
                Spacer(Modifier.height(AppDimens.sectionGap))
                ComparisonTable()
                Spacer(Modifier.height(AppDimens.sectionGap))
                PurchaseSection(
                    plan = plan,
                    subscription = subscription,
                    onSelectOffer = onSelectOffer,
                    onSubscribe = onSubscribe,
                    onManageSubscription = onManageSubscription
                )
                Spacer(Modifier.height(AppDimens.sectionGap))
                Text(
                    text = stringResource(R.string.pro_plan_privacy_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StrictBrand.light
                )
                TextButton(
                    onClick = onOpenPrivacy,
                    modifier = Modifier.testTag(PRO_BENEFITS_PRIVACY)
                ) {
                    Text(stringResource(R.string.action_privacy_policy), color = StrictBrand.lime)
                }
            }
        }
    }
}

@Composable
private fun AccessIntroduction(access: ProBenefitsAccess) {
    val pro = access.level == ProAccessLevel.Pro
    Text(
        text = stringResource(
            if (pro) R.string.pro_benefits_yours_title else R.string.pro_benefits_intro_title
        ),
        style = MaterialTheme.typography.titleMedium,
        color = StrictBrand.lime,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PRO_BENEFITS_INTRO)
    )
    Text(
        text = stringResource(
            if (pro) R.string.pro_benefits_yours_body else R.string.pro_benefits_intro_body
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = StrictBrand.light,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.headerStackGap)
    )
    Text(
        text = stringResource(
            if (pro) R.string.pro_benefits_access_pro else R.string.pro_benefits_access_free
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = StrictBrand.light,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.headerStackGap)
            .testTag(PRO_BENEFITS_STATUS)
    )
    val source = sourceLabel(access.source)
    if (source != null) {
        Text(
            text = source,
            style = MaterialTheme.typography.bodyMedium,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
                .testTag(PRO_BENEFITS_SOURCE)
        )
    }
}

@Composable
private fun sourceLabel(source: ProAccessSource): String? {
    val res = when (source) {
        ProAccessSource.None -> null
        ProAccessSource.GooglePlaySubscription -> R.string.pro_benefits_source_play
        ProAccessSource.Trial -> R.string.pro_benefits_source_trial
        ProAccessSource.Founder -> R.string.pro_benefits_source_founder
        ProAccessSource.Promotional -> R.string.pro_benefits_source_promotional
        ProAccessSource.DebugOverride -> R.string.pro_benefits_source_debug
    }
    return res?.let { stringResource(it) }
}

@Composable
private fun DiscoverySection(
    discovery: ProDiscoverySnapshot,
    onActivateTrial: () -> Unit,
    now: Instant
) {
    val expiresAt = discovery.trialExpiresAt
    if (discovery.trialActive && expiresAt != null) {
        Text(
            text = stringResource(
                R.string.pro_discovery_trial_active,
                proDiscoveryDateTime(expiresAt)
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
                .testTag(PRO_BENEFITS_TRIAL)
        )
    }
    if (discovery.warningOnBenefits && expiresAt != null) {
        Text(
            text = stringResource(
                R.string.pro_discovery_warning_title,
                formatTrialRemaining(now, expiresAt)
            ),
            style = MaterialTheme.typography.titleMedium,
            color = StrictBrand.lime,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.sectionGap)
                .testTag(PRO_BENEFITS_WARNING)
        )
        Text(
            text = stringResource(R.string.pro_discovery_warning_message),
            style = MaterialTheme.typography.bodyLarge,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
        )
        Text(
            text = stringResource(
                R.string.pro_discovery_warning_when,
                proDiscoveryDateTime(expiresAt)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
        )
    }
    if (discovery.trialExpired) {
        Text(
            text = stringResource(R.string.pro_discovery_trial_expired),
            style = MaterialTheme.typography.bodyLarge,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
                .testTag(PRO_BENEFITS_TRIAL)
        )
    }
    if (discovery.offerAvailable) {
        Text(
            text = stringResource(R.string.pro_discovery_offer_title),
            style = MaterialTheme.typography.titleMedium,
            color = StrictBrand.lime,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.sectionGap)
                .testTag(PRO_BENEFITS_OFFER)
        )
        Text(
            text = stringResource(R.string.pro_discovery_offer_message),
            style = MaterialTheme.typography.bodyLarge,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
        )
        Text(
            text = stringResource(R.string.pro_discovery_offer_note),
            style = MaterialTheme.typography.bodyMedium,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        Button(
            onClick = onActivateTrial,
            shape = AppShapeTokens.button,
            colors = StrictBrand.actionButtonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PRO_DISCOVERY_ACTIVATE)
        ) {
            Text(stringResource(R.string.pro_discovery_activate))
        }
    }
}

@Composable
private fun WelcomeBackSection(
    welcomeBack: WelcomeBackSnapshot,
    proActive: Boolean,
    onActivate: () -> Unit
) {
    val expiresAt = welcomeBack.trialExpiresAt
    if (welcomeBack.trialActive && expiresAt != null) {
        Text(
            text = stringResource(
                R.string.welcome_back_active,
                proDiscoveryDateTime(expiresAt)
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
                .testTag(WELCOME_BACK_STATUS)
        )
    }
    if (welcomeBack.trialExpired && !proActive) {
        Text(
            text = stringResource(R.string.welcome_back_expired),
            style = MaterialTheme.typography.bodyLarge,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
                .testTag(WELCOME_BACK_STATUS)
        )
    }
    if (welcomeBack.notice != WelcomeBackNotice.None) {
        Text(
            text = stringResource(R.string.welcome_back_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
                .testTag(WELCOME_BACK_NOTICE)
        )
    }
    if (welcomeBack.offerAvailable && !proActive) {
        Text(
            text = stringResource(R.string.welcome_back_offer_title),
            style = MaterialTheme.typography.titleMedium,
            color = StrictBrand.lime,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.sectionGap)
                .testTag(WELCOME_BACK_BENEFITS)
        )
        Text(
            text = stringResource(R.string.welcome_back_offer_message),
            style = MaterialTheme.typography.bodyLarge,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
        )
        Text(
            text = stringResource(R.string.welcome_back_offer_note),
            style = MaterialTheme.typography.bodyMedium,
            color = StrictBrand.light,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        Button(
            onClick = onActivate,
            shape = AppShapeTokens.button,
            colors = StrictBrand.actionButtonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WELCOME_BACK_ACTIVATE)
        ) {
            Text(stringResource(R.string.welcome_back_activate))
        }
    }
}

internal const val WELCOME_BACK_STATUS = "welcome-back-status"
internal const val WELCOME_BACK_NOTICE = "welcome-back-notice"
internal const val WELCOME_BACK_BENEFITS = "welcome-back-benefits"

@Composable
private fun ComparisonTable() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PRO_BENEFITS_TABLE)
    ) {
        ComparisonRow(
            feature = stringResource(R.string.pro_benefits_column_feature),
            free = stringResource(R.string.pro_benefits_free),
            pro = stringResource(R.string.pro_benefits_pro),
            header = true
        )
        ProComparisonRow.order.forEach { row ->
            HorizontalDivider(color = StrictBrand.light.copy(alpha = 0.16f))
            val cells = comparisonCells(row)
            ComparisonRow(
                feature = cells.feature,
                free = cells.free,
                pro = cells.pro,
                tag = cells.tag
            )
        }
    }
}

private data class ComparisonCells(
    val feature: String,
    val free: String,
    val pro: String,
    val tag: String?
)

@Composable
private fun comparisonCells(row: ProComparisonRow): ComparisonCells {
    return when (row) {
        ProComparisonRow.WorkoutPlans -> ComparisonCells(
            feature = stringResource(R.string.pro_benefits_plans_title),
            free = stringResource(R.string.pro_benefits_cell_count, WorkoutPlanAccess.FREE_PLAN_LIMIT),
            pro = stringResource(R.string.pro_benefits_cell_unlimited),
            tag = null
        )
        ProComparisonRow.CustomExercises -> ComparisonCells(
            feature = stringResource(R.string.pro_benefits_exercises_title),
            free = stringResource(R.string.pro_benefits_cell_count, FeatureAccessPolicy.FREE_CUSTOM_EXERCISE_LIMIT),
            pro = stringResource(R.string.pro_benefits_cell_unlimited),
            tag = PRO_BENEFITS_EXERCISES
        )
        ProComparisonRow.TimedSetsAndTimers -> ComparisonCells(
            feature = stringResource(R.string.pro_benefits_timers),
            free = stringResource(R.string.pro_benefits_cell_unavailable),
            pro = stringResource(R.string.pro_benefits_cell_included),
            tag = PRO_BENEFITS_TIMERS
        )
        ProComparisonRow.Statistics -> ComparisonCells(
            feature = stringResource(R.string.pro_benefits_statistics_title),
            free = stringResource(R.string.pro_benefits_cell_days, StatisticsRange.DAYS_30),
            pro = stringResource(R.string.pro_benefits_cell_all_time),
            tag = null
        )
        ProComparisonRow.Reports -> ComparisonCells(
            feature = stringResource(R.string.pro_benefits_reports_title),
            free = stringResource(R.string.pro_benefits_cell_reports_free),
            pro = stringResource(R.string.pro_benefits_cell_reports_pro),
            tag = null
        )
        ProComparisonRow.WorkoutScheduling -> ComparisonCells(
            feature = stringResource(R.string.pro_benefits_scheduling_title),
            free = stringResource(R.string.pro_benefits_cell_scheduling_free),
            pro = stringResource(R.string.pro_benefits_cell_scheduling_pro),
            tag = null
        )
        ProComparisonRow.Achievements -> ComparisonCells(
            feature = stringResource(R.string.pro_benefits_achievements_title),
            free = stringResource(R.string.pro_benefits_cell_achievements_free),
            pro = stringResource(R.string.pro_benefits_cell_achievements_pro),
            tag = PRO_BENEFITS_ACHIEVEMENTS
        )
        ProComparisonRow.BodyMeasurements -> ComparisonCells(
            feature = stringResource(R.string.pro_benefits_measurements_title),
            free = stringResource(R.string.pro_benefits_cell_measurements_free),
            pro = stringResource(R.string.pro_benefits_cell_measurements_pro),
            tag = null
        )
        ProComparisonRow.ProgressPhotos -> ComparisonCells(
            feature = stringResource(R.string.pro_benefits_photos_title),
            free = stringResource(R.string.pro_benefits_cell_photos_free),
            pro = stringResource(R.string.pro_benefits_cell_photos_pro),
            tag = null
        )
        ProComparisonRow.ExternalWorkouts -> ComparisonCells(
            feature = stringResource(R.string.pro_benefits_import_title),
            free = stringResource(R.string.pro_benefits_cell_import_free),
            pro = stringResource(R.string.pro_benefits_cell_import_pro),
            tag = null
        )
    }
}

@Composable
private fun ComparisonRow(
    feature: String,
    free: String,
    pro: String,
    header: Boolean = false,
    tag: String? = null
) {
    val fontScale = LocalDensity.current.fontScale
    val narrow = LocalConfiguration.current.screenWidthDp <= 340
    val stacked = !header && (fontScale >= 1.5f || (narrow && fontScale >= 1.3f))
    val featureStyle = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    val valueStyle = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    if (stacked) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (tag == null) Modifier else Modifier.testTag(tag))
                .padding(vertical = AppDimens.headerStackGap)
        ) {
            Text(
                text = feature,
                style = featureStyle,
                color = StrictBrand.light,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.headerStackGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = free,
                    style = valueStyle,
                    color = StrictBrand.light,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = pro,
                    style = valueStyle,
                    color = StrictBrand.lime,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (tag == null) Modifier else Modifier.testTag(tag))
                .padding(vertical = AppDimens.headerStackGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = feature,
                style = featureStyle,
                color = StrictBrand.light,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(2.1f)
            )
            Text(
                text = free,
                style = valueStyle,
                color = StrictBrand.light,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.9f)
            )
            Text(
                text = pro,
                style = valueStyle,
                color = StrictBrand.lime,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.95f)
            )
        }
    }
}

@Composable
private fun PurchaseSection(
    plan: ProPlanPresentation,
    subscription: SubscriptionFacts?,
    onSelectOffer: (String) -> Unit,
    onSubscribe: () -> Unit,
    onManageSubscription: (String) -> Unit
) {
    val facts = subscription ?: subscriptionFactsFor(plan)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (plan is ProPlanPresentation.Founder) {
            val text = if (plan.lifetime || plan.expiresAt == null) {
                stringResource(R.string.pro_plan_founder_open)
            } else {
                stringResource(R.string.pro_plan_founder, proDiscoveryDateTime(plan.expiresAt))
            }
            StatusCopy(text)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.sectionGap)
                .testTag(PRO_BENEFITS_SUBSCRIPTION)
        ) {
            Text(
                text = stringResource(R.string.pro_benefits_subscription),
                style = MaterialTheme.typography.titleMedium,
                color = StrictBrand.lime
            )
            when (facts) {
                SubscriptionFacts.Loading -> {
                    CircularProgressIndicator(
                        color = StrictBrand.lime,
                        modifier = Modifier.padding(top = AppDimens.headerStackGap)
                    )
                    StatusCopy(stringResource(R.string.pro_plan_loading))
                }
                SubscriptionFacts.Unavailable -> StatusCopy(stringResource(R.string.pro_plan_unavailable))
                SubscriptionFacts.NotConfigured -> StatusCopy(stringResource(R.string.pro_plan_not_configured))
                SubscriptionFacts.None -> {
                    if (plan is ProPlanPresentation.Paid && plan.testOnly) {
                        StatusCopy(stringResource(R.string.pro_plan_test_banner))
                    } else {
                        StatusCopy(stringResource(R.string.pro_benefits_subscription_none))
                    }
                }
                is SubscriptionFacts.Active -> {
                    val whenText = proDiscoveryDateTime(facts.expiresAt)
                    StatusCopy(
                        if (facts.autoRenewing) {
                            stringResource(R.string.pro_plan_paid_renews, whenText)
                        } else {
                            stringResource(R.string.pro_plan_paid_ends, whenText)
                        }
                    )
                    Button(
                        onClick = { onManageSubscription(facts.productId) },
                        shape = AppShapeTokens.button,
                        colors = StrictBrand.actionButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppDimens.headerStackGap)
                            .testTag(PRO_BENEFITS_MANAGE)
                    ) {
                        Text(stringResource(R.string.pro_plan_manage))
                    }
                }
            }
            if (plan is ProPlanPresentation.Offers) {
                OfferList(
                    plan = plan,
                    onSelectOffer = onSelectOffer,
                    onSubscribe = onSubscribe
                )
            }
        }
    }
}

private fun subscriptionFactsFor(plan: ProPlanPresentation): SubscriptionFacts {
    return when (plan) {
        ProPlanPresentation.Loading -> SubscriptionFacts.Loading
        ProPlanPresentation.Unavailable -> SubscriptionFacts.Unavailable
        ProPlanPresentation.NotConfigured -> SubscriptionFacts.NotConfigured
        is ProPlanPresentation.Paid -> {
            val expiresAt = plan.expiresAt
            if (plan.testOnly || expiresAt == null) {
                SubscriptionFacts.None
            } else {
                SubscriptionFacts.Active(
                    productId = plan.productId,
                    expiresAt = expiresAt,
                    autoRenewing = plan.renews
                )
            }
        }
        is ProPlanPresentation.Founder,
        is ProPlanPresentation.Offers -> SubscriptionFacts.None
    }
}

@Composable
private fun OfferList(
    plan: ProPlanPresentation.Offers,
    onSelectOffer: (String) -> Unit,
    onSubscribe: () -> Unit
) {
    if (plan.testOnly) {
        StatusCopy(stringResource(R.string.pro_plan_test_banner))
    }
    if (plan.expiredPreview) {
        StatusCopy(stringResource(R.string.pro_plan_expired_preview))
    }
    val trialEnds = plan.trialExpiresAt
    if (trialEnds != null) {
        StatusCopy(stringResource(R.string.pro_plan_trial_note, proDiscoveryDateTime(trialEnds)))
    }
    if (plan.pending) {
        StatusCopy(stringResource(R.string.pro_plan_pending))
    }
    if (plan.canceled) {
        StatusCopy(stringResource(R.string.pro_plan_canceled))
    }
    if (plan.verificationFailed) {
        StatusCopy(stringResource(R.string.pro_plan_verify_failed))
    }
    if (plan.ownershipConflict) {
        StatusCopy(stringResource(R.string.pro_plan_ownership))
    }
    plan.offers.forEach { offer ->
        OfferRow(
            offer = offer,
            selected = offer.offerToken == plan.selectedOfferToken,
            onSelect = { onSelectOffer(offer.offerToken) }
        )
    }
    if (plan.offers.isNotEmpty()) {
        Button(
            onClick = onSubscribe,
            shape = AppShapeTokens.button,
            colors = StrictBrand.actionButtonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.headerStackGap)
                .testTag(PRO_BENEFITS_SUBSCRIBE)
        ) {
            Text(stringResource(R.string.pro_plan_subscribe))
        }
    }
}

@Composable
private fun OfferRow(
    offer: SubscriptionOffer,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.headerStackGap)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = StrictBrand.lime,
                unselectedColor = StrictBrand.light
            )
        )
        Column(modifier = Modifier.padding(start = AppDimens.headerStackGap)) {
            Text(
                text = offer.formattedPrice,
                style = MaterialTheme.typography.titleMedium,
                color = StrictBrand.lime
            )
            Text(
                text = billingPeriodLabel(offer.billingPeriod),
                style = MaterialTheme.typography.bodyMedium,
                color = StrictBrand.light
            )
        }
    }
}

@Composable
private fun StatusCopy(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = StrictBrand.light,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.headerStackGap)
    )
}

@Composable
private fun billingPeriodLabel(period: String): String {
    val res = when (period) {
        "P1W" -> R.string.billing_period_week
        "P1M" -> R.string.billing_period_month
        "P3M" -> R.string.billing_period_quarter
        "P6M" -> R.string.billing_period_half_year
        "P1Y" -> R.string.billing_period_year
        else -> null
    }
    return if (res == null) period else stringResource(res)
}

