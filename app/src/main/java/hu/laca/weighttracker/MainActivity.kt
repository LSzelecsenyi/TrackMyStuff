package hu.laca.weighttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.laca.weighttracker.data.preferences.ThemePreference
import hu.laca.weighttracker.ui.navigation.WeightTrackerNavHost
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as WeightTrackerApplication).container
        setContent {
            val theme by container.themePreferences.theme.collectAsStateWithLifecycle(
                initialValue = ThemePreference.System
            )
            WeightTrackerTheme(themePreference = theme) {
                WeightTrackerNavHost(
                    factory = container.viewModelFactory,
                    dateProvider = container.dateProvider
                )
            }
        }
    }
}
