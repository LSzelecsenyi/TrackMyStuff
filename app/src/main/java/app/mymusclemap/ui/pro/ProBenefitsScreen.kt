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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FeatureAccessPolicy
import app.mymusclemap.domain.entitlement.ProBenefitsStatus
import app.mymusclemap.domain.entitlement.ProDiscoverySnapshot
import app.mymusclemap.domain.entitlement.WorkoutPlanAccess
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
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

/**
 * Free and Pro differences from the current feature policy.
 * This screen does not sell, renew, or change entitlement.
 */
@Composable
fun ProBenefitsScreen(
    status: ProBenefitsStatus,
    onBack: () -> Unit,
    discovery: ProDiscoverySnapshot = ProDiscoverySnapshot(),
    onActivateTrial: () -> Unit = {}
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
                DiscoverySection(discovery = discovery, onActivateTrial = onActivateTrial)
                Spacer(Modifier.height(AppDimens.sectionGap))
                Benefit(
                    title = stringResource(R.string.pro_benefits_statistics_title),
                    free = stringResource(
                        R.string.pro_benefits_statistics_free,
                        StatisticsRange.DAYS_30
                    ),
                    pro = stringResource(R.string.pro_benefits_statistics_pro)
                )
                Benefit(
                    title = stringResource(R.string.pro_benefits_reports_title),
                    free = stringResource(R.string.pro_benefits_reports_free),
                    pro = stringResource(R.string.pro_benefits_reports_pro)
                )
                Benefit(
                    title = stringResource(R.string.pro_benefits_exercises_title),
                    free = stringResource(
                        R.string.pro_benefits_exercises_free,
                        FeatureAccessPolicy.FREE_CUSTOM_EXERCISE_LIMIT
                    ),
                    pro = stringResource(R.string.pro_benefits_exercises_pro),
                    tag = PRO_BENEFITS_EXERCISES
                )
                Benefit(
                    title = stringResource(R.string.pro_benefits_plans_title),
                    free = stringResource(
                        R.string.pro_benefits_plans_free,
                        WorkoutPlanAccess.FREE_PLAN_LIMIT
                    ),
                    pro = stringResource(R.string.pro_benefits_plans_pro)
                )
                Benefit(
                    title = stringResource(R.string.pro_benefits_measurements_title),
                    free = stringResource(R.string.pro_benefits_measurements_free),
                    pro = stringResource(R.string.pro_benefits_measurements_pro)
                )
                Benefit(
                    title = stringResource(R.string.pro_benefits_photos_title),
                    free = stringResource(R.string.pro_benefits_photos_free),
                    pro = stringResource(R.string.pro_benefits_photos_pro)
                )
                Benefit(
                    title = stringResource(R.string.pro_benefits_achievements_title),
                    free = stringResource(R.string.pro_benefits_achievements_free),
                    pro = stringResource(R.string.pro_benefits_achievements_pro),
                    tag = PRO_BENEFITS_ACHIEVEMENTS
                )
                Benefit(
                    title = stringResource(R.string.pro_benefits_scheduling_title),
                    free = stringResource(R.string.pro_benefits_scheduling_free),
                    pro = stringResource(R.string.pro_benefits_scheduling_pro)
                )
                Benefit(
                    title = stringResource(R.string.pro_benefits_import_title),
                    free = stringResource(R.string.pro_benefits_import_free),
                    pro = stringResource(R.string.pro_benefits_import_pro)
                )
            }
        }
    }
}

@Composable
private fun DiscoverySection(
    discovery: ProDiscoverySnapshot,
    onActivateTrial: () -> Unit
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
    if (discovery.warningOnBenefits) {
        Text(
            text = stringResource(R.string.pro_discovery_warning_title),
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
        if (expiresAt != null) {
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
    Text(
        text = stringResource(R.string.pro_discovery_subscriptions_unavailable),
        style = MaterialTheme.typography.bodyMedium,
        color = StrictBrand.light,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppDimens.sectionGap)
            .testTag(PRO_BENEFITS_SUBSCRIPTION)
    )
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

@Composable
private fun Benefit(
    title: String,
    free: String,
    pro: String,
    tag: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = AppDimens.sectionGap)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = StrictBrand.lime
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        Text(
            text = stringResource(R.string.pro_benefits_free),
            style = AppTypeTokens.sectionKicker,
            color = StrictBrand.lime
        )
        Text(
            text = free,
            style = MaterialTheme.typography.bodyLarge,
            color = StrictBrand.light,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        Text(
            text = stringResource(R.string.pro_benefits_pro),
            style = AppTypeTokens.sectionKicker,
            color = StrictBrand.lime
        )
        Text(
            text = pro,
            style = MaterialTheme.typography.bodyLarge,
            color = StrictBrand.light
        )
    }
}
