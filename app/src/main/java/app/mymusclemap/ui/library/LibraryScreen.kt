package app.mymusclemap.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand

internal const val LIBRARY_ROOT = "library-root"
internal const val LIBRARY_TAB_PLANS = "library-tab-plans"
internal const val LIBRARY_TAB_EXERCISES = "library-tab-exercises"

enum class LibraryTab {
    Plans,
    Exercises
}

@Composable
fun LibraryScreen(
    plans: @Composable () -> Unit,
    exercises: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var tabName by rememberSaveable { mutableStateOf(LibraryTab.Plans.name) }
    val tab = runCatching { LibraryTab.valueOf(tabName) }.getOrDefault(LibraryTab.Plans)
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(LIBRARY_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            LibraryTabs(
                selected = tab,
                onSelected = { tabName = it.name }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (tab == LibraryTab.Plans) plans() else exercises()
            }
        }
    }
}

@Composable
private fun LibraryTabs(
    selected: LibraryTab,
    onSelected: (LibraryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.screenPadding)
    ) {
        LibraryTabItem(
            label = stringResource(R.string.templates_title),
            selected = selected == LibraryTab.Plans,
            testTag = LIBRARY_TAB_PLANS,
            onClick = { onSelected(LibraryTab.Plans) },
            modifier = Modifier.weight(1f)
        )
        LibraryTabItem(
            label = stringResource(R.string.exercises_title),
            selected = selected == LibraryTab.Exercises,
            testTag = LIBRARY_TAB_EXERCISES,
            onClick = { onSelected(LibraryTab.Exercises) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LibraryTabItem(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (selected) {
        StrictBrand.actionContainer()
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.Tab
            }
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = AppTypeTokens.sectionTitle,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) StrictBrand.actionContainer() else MaterialTheme.colorScheme.background)
        )
    }
}
