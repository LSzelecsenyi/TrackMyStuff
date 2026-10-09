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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.mymusclemap.R
import app.mymusclemap.domain.billing.ProPlanPresentation
import app.mymusclemap.domain.billing.SubscriptionOffer
import app.mymusclemap.domain.entitlement.FeatureAccessPolicy
import app.mymusclemap.domain.entitlement.ProBenefitsStatus
import app.mymusclemap.domain.entitlement.ProDiscoverySnapshot
import app.mymusclemap.domain.entitlement.WorkoutPlanAccess
import app.mymusclemap.domain.entitlement.formatTrialRemaining
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import java.time.Instant
import java.time.ZoneId

internal const val PRO_BENEFITS_ROOT = "pro-benefits-root"
internal const val PRO_BENEFITS_BACK = "pro-benefits-back"
internal const val PRO_BENEFITS_STATUS = "pro-benefits-status"
internal const val PRO_BENEFITS_EXERCISES = "pro-benefits-exercises"
internal const val PRO_BENEFITS_ACHIEVEMENTS = "pro-benefits-achievements"
internal const val PRO_BENEFITS_TRIAL = "pro-benefits-trial"
internal const val PRO_BENEFITS_OFFER = "pro-benefits-offer"
internal const val PRO_BENEFITS_WARNING = "pro-benefits-warning"
internal const val PRO_BENEFITS_SUBSCRIPTION = "pro-benefits-subscription"
internal const val PRO_BENEFITS_TABLE = "pro-benefits-table"
internal const val PRO_BENEFITS_SUBSCRIBE = "pro-benefits-subscribe"
internal const val PRO_BENEFITS_MANAGE = "pro-benefits-manage"

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
    now: Instant = Instant.now(),
    plan: ProPlanPresentation = ProPlanPresentation.NotConfigured,
    onSelectOffer: (String) -> Unit = {},
    onSubscribe: () -> Unit = {},
    onManageSubscription: (String) -> Unit = {}
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
                Text(
                    text = accessLine(status),
                    style = MaterialTheme.typography.bodyLarge,
                    color = StrictBrand.light,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PRO_BENEFITS_STATUS)
                )
                DiscoverySection(
                    discovery = discovery,
                    onActivateTrial = onActivateTrial,
                    now = now
                )
                Spacer(Modifier.height(AppDimens.sectionGap))
                ComparisonTable()
                Spacer(Modifier.height(AppDimens.sectionGap))
                PurchaseSection(
                    plan = plan,
                    onSelectOffer = onSelectOffer,
                    onSubscribe = onSubscribe,
                    onManageSubscription = onManageSubscription
                )
            }
        }
    }
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
private fun ComparisonTable() {
    val rows = listOf(
        Triple(
            stringResource(R.string.pro_benefits_statistics_title),
            stringResource(R.string.pro_benefits_cell_days, StatisticsRange.DAYS_30),
            stringResource(R.string.pro_benefits_cell_all_time)
        ),
        Triple(
            stringResource(R.string.pro_benefits_reports_title),
            stringResource(R.string.pro_benefits_cell_reports_free),
            stringResource(R.string.pro_benefits_cell_reports_pro)
        ),
        Triple(
            stringResource(R.string.pro_benefits_exercises_title),
            stringResource(R.string.pro_benefits_cell_count, FeatureAccessPolicy.FREE_CUSTOM_EXERCISE_LIMIT),
            stringResource(R.string.pro_benefits_cell_unlimited)
        ),
        Triple(
            stringResource(R.string.pro_benefits_plans_title),
            stringResource(R.string.pro_benefits_cell_count, WorkoutPlanAccess.FREE_PLAN_LIMIT),
            stringResource(R.string.pro_benefits_cell_unlimited)
        ),
        Triple(
            stringResource(R.string.pro_benefits_measurements_title),
            stringResource(R.string.pro_benefits_cell_measurements_free),
            stringResource(R.string.pro_benefits_cell_measurements_pro)
        ),
        Triple(
            stringResource(R.string.pro_benefits_photos_title),
            stringResource(R.string.pro_benefits_cell_photos_free),
            stringResource(R.string.pro_benefits_cell_photos_pro)
        ),
        Triple(
            stringResource(R.string.pro_benefits_achievements_title),
            stringResource(R.string.pro_benefits_cell_achievements_free),
            stringResource(R.string.pro_benefits_cell_achievements_pro)
        ),
        Triple(
            stringResource(R.string.pro_benefits_scheduling_title),
            stringResource(R.string.pro_benefits_cell_scheduling_free),
            stringResource(R.string.pro_benefits_cell_scheduling_pro)
        ),
        Triple(
            stringResource(R.string.pro_benefits_import_title),
            stringResource(R.string.pro_benefits_cell_import_free),
            stringResource(R.string.pro_benefits_cell_import_pro)
        )
    )
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
        rows.forEachIndexed { index, row ->
            HorizontalDivider(color = StrictBrand.light.copy(alpha = 0.16f))
            ComparisonRow(
                feature = row.first,
                free = row.second,
                pro = row.third,
                tag = when (index) {
                    2 -> PRO_BENEFITS_EXERCISES
                    6 -> PRO_BENEFITS_ACHIEVEMENTS
                    else -> null
                }
            )
        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .padding(vertical = AppDimens.headerStackGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = feature,
            style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            color = StrictBrand.light,
            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1.4f)
        )
        Text(
            text = free,
            style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            color = StrictBrand.light,
            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = pro,
            style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            color = StrictBrand.lime,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PurchaseSection(
    plan: ProPlanPresentation,
    onSelectOffer: (String) -> Unit,
    onSubscribe: () -> Unit,
    onManageSubscription: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PRO_BENEFITS_SUBSCRIPTION)
    ) {
        when (plan) {
            ProPlanPresentation.Loading -> {
                CircularProgressIndicator(color = StrictBrand.lime)
                Text(
                    text = stringResource(R.string.pro_plan_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StrictBrand.light,
                    modifier = Modifier.padding(top = AppDimens.headerStackGap)
                )
            }
            ProPlanPresentation.Unavailable -> StatusCopy(stringResource(R.string.pro_plan_unavailable))
            ProPlanPresentation.NotConfigured -> StatusCopy(stringResource(R.string.pro_plan_not_configured))
            is ProPlanPresentation.Founder -> {
                val text = if (plan.lifetime || plan.expiresAt == null) {
                    stringResource(R.string.pro_plan_founder_open)
                } else {
                    stringResource(R.string.pro_plan_founder, proDiscoveryDateTime(plan.expiresAt))
                }
                StatusCopy(text)
            }
            is ProPlanPresentation.Paid -> {
                if (plan.testOnly) {
                    StatusCopy(stringResource(R.string.pro_plan_test_banner))
                }
                val whenText = plan.expiresAt?.let { proDiscoveryDateTime(it) }.orEmpty()
                StatusCopy(
                    if (plan.renews) {
                        stringResource(R.string.pro_plan_paid_renews, whenText)
                    } else {
                        stringResource(R.string.pro_plan_paid_ends, whenText)
                    }
                )
                Button(
                    onClick = { onManageSubscription(plan.productId) },
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
            is ProPlanPresentation.Offers -> {
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

@Composable
private fun accessLine(status: ProBenefitsStatus): String {
    if (!status.proActive) {
        return stringResource(R.string.pro_benefits_lead)
    }
    val expiresAt = status.expiresAt ?: return stringResource(R.string.pro_benefits_active)
    val date = UiFormatters.longDate(expiresAt.atZone(ZoneId.systemDefault()).toLocalDate())
    return stringResource(R.string.pro_benefits_active_until, date)
}
