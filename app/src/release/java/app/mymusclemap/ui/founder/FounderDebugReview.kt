package app.mymusclemap.ui.founder

import androidx.compose.runtime.Composable
import app.mymusclemap.domain.entitlement.FounderProgramStatus

@Suppress("UNUSED_PARAMETER")
@Composable
fun FounderDebugReview(
    status: FounderProgramStatus,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) = Unit
