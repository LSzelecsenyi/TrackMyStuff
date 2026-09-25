package app.mymusclemap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.ui.navigation.WeightTrackerNavHost
import app.mymusclemap.ui.theme.WeightTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as WeightTrackerApplication).container
        setContent {
            val appearance by container.themePreferences.appearance.collectAsStateWithLifecycle(
                initialValue = AppearanceSettings.Default
            )
            WeightTrackerTheme(appearance = appearance) {
                WeightTrackerNavHost(
                    factory = container.viewModelFactory,
                    dateProvider = container.dateProvider
                )
            }
        }
    }
}
