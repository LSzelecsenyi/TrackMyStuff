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
 * Post-onboarding handoff. The welcome copy and enrollment button are [FounderWelcomeContent].
 * Choosing either action is one-shot for this composition so a second tap cannot enroll twice.
 */
@Composable
fun FounderInvitationScreen(
    rules: FounderProgramRules,
    onJoin: () -> Unit,
    onNotNow: () -> Unit
) {
    var handled by remember { mutableStateOf(false) }
    BackHandler(enabled = !handled) {
        handled = true
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
                    if (handled) return@FounderWelcomeContent
                    handled = true
                    onJoin()
                },
                onDecline = {
                    if (handled) return@FounderWelcomeContent
                    handled = true
                    onNotNow()
                },
                actionsEnabled = !handled
            )
        }
    }
}
