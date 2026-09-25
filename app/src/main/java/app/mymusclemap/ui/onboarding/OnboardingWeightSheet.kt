package app.mymusclemap.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.WeightParseError
import app.mymusclemap.domain.WeightParser
import app.mymusclemap.ui.components.weightErrorMessage
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingWeightSheet(
    weightInput: String,
    weightError: WeightParseError?,
    onWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onNotNow: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onNotNow,
        sheetState = sheetState,
        modifier = Modifier.testTag(ONBOARDING_WEIGHT_SHEET)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_weight_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_weight_body),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = weightInput,
                onValueChange = { onWeightChange(WeightParser.filterUserInput(it)) },
                label = { Text(stringResource(R.string.field_weight)) },
                suffix = { Text(stringResource(R.string.unit_kg)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = weightError != null,
                supportingText = {
                    Text(
                        text = weightError?.let { stringResource(weightErrorMessage(it)) }
                            ?: stringResource(R.string.weight_input_hint)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ONBOARDING_WEIGHT_INPUT)
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            Button(
                onClick = onSave,
                shape = AppShapeTokens.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(ONBOARDING_WEIGHT_SAVE)
            ) {
                Text(stringResource(R.string.onboarding_weight_save))
            }
            TextButton(
                onClick = onNotNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(ONBOARDING_WEIGHT_SKIP)
            ) {
                Text(stringResource(R.string.onboarding_weight_not_now))
            }
        }
    }
}
