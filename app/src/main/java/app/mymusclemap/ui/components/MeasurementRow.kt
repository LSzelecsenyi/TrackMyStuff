package app.mymusclemap.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.model.MeasurementListItem

@Composable
fun MeasurementRow(
    item: MeasurementListItem,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = UiFormatters.longDate(item.measurement.date),
                style = MaterialTheme.typography.titleMedium
            )
            val difference = item.differenceFromPreviousKg
            Text(
                text = if (difference == null) {
                    stringResource(R.string.no_previous_measurement)
                } else {
                    stringResource(
                        R.string.change_from_previous_value,
                        UiFormatters.signedWeightKg(difference)
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = UiFormatters.weightKg(item.measurement.weightKg),
            style = MaterialTheme.typography.titleMedium
        )
        if (showActions) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.action_edit)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_delete)
                )
            }
        }
    }
}
