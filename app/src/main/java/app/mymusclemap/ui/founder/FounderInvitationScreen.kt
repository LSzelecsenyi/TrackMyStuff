package app.mymusclemap.ui.founder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.ui.theme.AppDimens

/**
 * Onboarding step. Decline is one-shot. Join stays available after a cancelled or failed sign-in.
 */
@Composable
fun FounderInvitationScreen(
    rules: FounderProgramRules,
    onJoin: () -> Unit,
    onNotNow: () -> Unit,
    joining: Boolean = false,
    joinNotice: FounderJoinNotice = FounderJoinNotice.None
) {
    var declined by remember { mutableStateOf(false) }
    BackHandler(enabled = !declined && !joining) {
        declined = true
        onNotNow()
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(FOUNDER_INVITATION),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
        ) {
            FounderWelcomeContent(
                rules = rules,
                onEnroll = {
                    if (declined || joining) return@FounderWelcomeContent
                    onJoin()
                },
                onDecline = {
                    if (declined || joining) return@FounderWelcomeContent
                    declined = true
                    onNotNow()
                },
                actionsEnabled = !declined && !joining,
                joining = joining,
                joinNotice = joinNotice
            )
        }
    }
}
