package app.mymusclemap.domain.health

import java.time.LocalDate

enum class HealthQuietStatus {
    Unavailable,
    UpdateRequired,
    NotConnected
}

data class StepSlot(
    val date: LocalDate,
    val steps: Long?
)

sealed interface HealthCardState {
    data object Checking : HealthCardState

    data class Quiet(val status: HealthQuietStatus) : HealthCardState

    data class Readings(
        val todaySteps: Long?,
        val todayHeartRate: Long?,
        val stepsGranted: Boolean,
        val heartRateGranted: Boolean,
        val recentSteps: List<StepSlot>,
        val readFailed: Boolean,
        val stepsFailed: Boolean = false,
        val heartFailed: Boolean = false
    ) : HealthCardState
}

enum class HealthSettingsStatus {
    Unavailable,
    UpdateRequired,
    NotConnected,
    Connected
}

enum class HealthSettingsAction {
    None,
    InstallOrUpdate,
    RequestPermissions,
    ManageAccess
}

data class HealthSettingsState(
    val ready: Boolean,
    val status: HealthSettingsStatus,
    val stepsGranted: Boolean,
    val restingHeartRateGranted: Boolean,
    val action: HealthSettingsAction,
    val exerciseGranted: Boolean = false,
    val hrvGranted: Boolean = false,
    val sleepGranted: Boolean = false
) {
    companion object {
        val NotConnected = HealthSettingsState(
            ready = true,
            status = HealthSettingsStatus.NotConnected,
            stepsGranted = false,
            restingHeartRateGranted = false,
            action = HealthSettingsAction.RequestPermissions
        )
    }
}

object HealthPresentation {
    const val RECENT_STEP_DAYS = 7

    fun card(access: HealthAccess, readings: HealthReadings, today: LocalDate): HealthCardState {
        if (!access.checked) return HealthCardState.Checking
        return when (access.availability) {
            HealthAvailability.Unavailable -> HealthCardState.Quiet(HealthQuietStatus.Unavailable)
            HealthAvailability.ProviderUpdateRequired ->
                HealthCardState.Quiet(HealthQuietStatus.UpdateRequired)
            HealthAvailability.Available -> {
                if (!access.anyGranted()) {
                    HealthCardState.Quiet(HealthQuietStatus.NotConnected)
                } else {
                    HealthCardState.Readings(
                        todaySteps = readingForToday(access.stepsGranted, readings.steps, today) { it.date to it.steps },
                        todayHeartRate = readingForToday(
                            access.restingHeartRateGranted,
                            readings.restingHeartRate,
                            today
                        ) { it.date to it.beatsPerMinute },
                        stepsGranted = access.stepsGranted,
                        heartRateGranted = access.restingHeartRateGranted,
                        recentSteps = if (access.stepsGranted) recentSteps(readings.steps, today) else emptyList(),
                        readFailed = readings.readFailed,
                        stepsFailed = readings.failed.contains(HealthMetric.STEPS),
                        heartFailed = readings.failed.contains(HealthMetric.RESTING_HEART_RATE)
                    )
                }
            }
        }
    }

    fun settings(access: HealthAccess): HealthSettingsState {
        if (!access.checked) {
            return HealthSettingsState(
                ready = false,
                status = HealthSettingsStatus.Unavailable,
                stepsGranted = false,
                restingHeartRateGranted = false,
                action = HealthSettingsAction.None
            )
        }
        return when (access.availability) {
            HealthAvailability.Unavailable -> HealthSettingsState(
                ready = true,
                status = HealthSettingsStatus.Unavailable,
                stepsGranted = false,
                restingHeartRateGranted = false,
                action = HealthSettingsAction.None
            )
            HealthAvailability.ProviderUpdateRequired -> HealthSettingsState(
                ready = true,
                status = HealthSettingsStatus.UpdateRequired,
                stepsGranted = false,
                restingHeartRateGranted = false,
                action = HealthSettingsAction.InstallOrUpdate
            )
            HealthAvailability.Available -> {
                val connected = access.anyGranted()
                HealthSettingsState(
                    ready = true,
                    status = if (connected) {
                        HealthSettingsStatus.Connected
                    } else {
                        HealthSettingsStatus.NotConnected
                    },
                    stepsGranted = access.stepsGranted,
                    restingHeartRateGranted = access.restingHeartRateGranted,
                    action = if (connected) {
                        HealthSettingsAction.ManageAccess
                    } else {
                        HealthSettingsAction.RequestPermissions
                    },
                    exerciseGranted = access.exerciseGranted,
                    hrvGranted = access.hrvGranted,
                    sleepGranted = access.sleepGranted
                )
            }
        }
    }

    fun recentSteps(steps: List<DailyStepTotal>, today: LocalDate): List<StepSlot> {
        return (RECENT_STEP_DAYS - 1 downTo 0).map { ago ->
            val date = today.minusDays(ago.toLong())
            StepSlot(date, steps.firstOrNull { it.date == date }?.steps)
        }
    }

    private fun <T> readingForToday(
        granted: Boolean,
        rows: List<T>,
        today: LocalDate,
        dateAndValue: (T) -> Pair<LocalDate, Long>
    ): Long? {
        if (!granted) return null
        return rows.firstOrNull { dateAndValue(it).first == today }?.let { dateAndValue(it).second }
    }
}
