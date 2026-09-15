package hu.laca.weighttracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hu.laca.weighttracker.R

@Composable
fun SettingsAction(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onOpenSettings, modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.action_open_settings)
        )
    }
}
