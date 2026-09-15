package hu.laca.weighttracker

import android.content.Context
import hu.laca.weighttracker.data.local.WeightDatabase
import hu.laca.weighttracker.data.preferences.ThemePreferences
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.domain.SystemDateProvider
import java.time.Clock

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val clock: Clock = Clock.systemDefaultZone()
    val dateProvider: DateProvider = SystemDateProvider(clock)
    private val database = WeightDatabase.create(appContext)
    val weightRepository = WeightRepository(
        dao = database.weightMeasurementDao(),
        clock = clock
    )
    val themePreferences = ThemePreferences(appContext)
    val viewModelFactory = WeightViewModelFactory(
        weightRepository = weightRepository,
        dateProvider = dateProvider,
        themePreferences = themePreferences
    )
}
