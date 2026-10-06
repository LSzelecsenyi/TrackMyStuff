package app.mymusclemap.ui.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.health.HealthSettingsAction
import app.mymusclemap.domain.health.HealthSettingsState
import app.mymusclemap.domain.health.HealthSettingsStatus
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.components.CompactEditorSection
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val SETTINGS_HEALTH = "settings-health"
internal const val SETTINGS_HEALTH_ACTION = "settings-health-action"
internal const val SETTINGS_HEALTH_DETAILS = "settings-health-details"

@Composable
fun HealthConnectSettingsSection(
    state: HealthSettingsState,
    onAction: () -> Unit,
    onOpenDetails: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    CompactEditorSection(
        title = stringResource(R.string.health_connect_title).uppercase(AppLocale.UI),
        modifier = modifier.testTag(SETTINGS_HEALTH)
    ) {
        if (state.ready) {
            Text(
                text = statusText(state),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (state.status == HealthSettingsStatus.Connected) {
                Text(
                    text = connectedDetail(state),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.ready && state.action != HealthSettingsAction.None) {
            Spacer(Modifier.height(AppDimens.itemGap))
            HealthActionRow(
                icon = Icons.Outlined.MonitorHeart,
                title = actionTitle(state),
                onClick = onAction
            )
            if (state.status == HealthSettingsStatus.Connected) {
                HealthActionRow(
                    icon = Icons.Outlined.BarChart,
                    title = stringResource(R.string.health_connect_view_data),
                    onClick = onOpenDetails,
                    tag = SETTINGS_HEALTH_DETAILS
                )
            }
        }
    }
}

@Composable
private fun statusText(state: HealthSettingsState): String {
    return stringResource(
        when (state.status) {
            HealthSettingsStatus.Unavailable -> R.string.health_connect_status_unavailable
            HealthSettingsStatus.UpdateRequired -> R.string.health_connect_status_update
            HealthSettingsStatus.NotConnected -> R.string.health_connect_status_not_connected
            HealthSettingsStatus.Connected -> R.string.health_connect_status_connected
        }
    )
}

@Composable
private fun connectedDetail(state: HealthSettingsState): String {
    val extra = state.exerciseGranted || state.hrvGranted || state.sleepGranted
    if (!extra) {
        return when {
            state.stepsGranted && state.restingHeartRateGranted ->
                stringResource(R.string.health_connect_both)
            state.stepsGranted -> stringResource(R.string.health_connect_partial_steps)
            state.restingHeartRateGranted -> stringResource(R.string.health_connect_partial_heart)
            else -> stringResource(R.string.health_connect_status_not_connected)
        }
    }
    val names = buildList {
        if (state.stepsGranted) add(stringResource(R.string.health_connect_steps))
        if (state.exerciseGranted) add(stringResource(R.string.health_connect_settings_workouts))
        if (state.restingHeartRateGranted) add(stringResource(R.string.health_connect_settings_heart))
        if (state.hrvGranted) add(stringResource(R.string.health_connect_hrv))
        if (state.sleepGranted) add(stringResource(R.string.health_connect_sleep))
    }
    return names.joinToString(", ")
}

@Composable
private fun actionTitle(state: HealthSettingsState): String {
    return stringResource(
        when (state.action) {
            HealthSettingsAction.InstallOrUpdate -> R.string.health_connect_action_install
            HealthSettingsAction.RequestPermissions -> R.string.health_connect_action_connect
            HealthSettingsAction.ManageAccess -> R.string.health_connect_action_manage
            HealthSettingsAction.None -> R.string.health_connect_action_connect
        }
    )
}

@Composable
private fun HealthActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tag: String = SETTINGS_HEALTH_ACTION
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AppDimens.itemGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
