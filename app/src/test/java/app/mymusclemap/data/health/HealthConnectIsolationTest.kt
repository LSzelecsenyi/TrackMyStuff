package app.mymusclemap.data.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.appbackup.AppBackupFormat
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.AppBackupRepository
import app.mymusclemap.data.repository.BodyMeasurementRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.body.BodyMeasurementType
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.csv.WeightCsv
import app.mymusclemap.domain.health.HealthAvailability
import app.mymusclemap.domain.health.HealthGrants
import app.mymusclemap.domain.health.HealthMetricBucket
import app.mymusclemap.domain.health.HealthSource
import app.mymusclemap.domain.reports.ReportInputs
import app.mymusclemap.domain.reports.ReportKind
import app.mymusclemap.domain.reports.ReportLogic
import app.mymusclemap.domain.reports.ReportPeriod
import app.mymusclemap.domain.workout.BodyWeightSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class HealthConnectIsolationTest {
    private val today = LocalDate.of(2024, 9, 28)
    private val zone = ZoneId.of("Europe/Budapest")
    private lateinit var database: WeightDatabase
    private lateinit var weightRepository: WeightRepository
    private lateinit var sessions: WorkoutSessionRepository
    private lateinit var backup: AppBackupRepository
    private lateinit var clock: Clock

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)
        val dates = FixedDateProvider(today)
        weightRepository = WeightRepository(database.weightMeasurementDao(), clock)
        sessions = WorkoutSessionRepository(
            sessionDao = database.workoutSessionDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            weightRepository = weightRepository,
            clock = clock,
            dateProvider = dates
        )
        backup = AppBackupRepository(database, ThemePreferences(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun healthReadingsDoNotEnterWeightReportsWorkoutLookupOrBackup() = runTest {
        weightRepository.save(LocalDate.of(2024, 8, 15), 80.0)
        weightRepository.save(today, 81.5)
        val body = BodyMeasurementRepository(database.bodyMeasurementDao(), clock)
        body.save(BodyMeasurementType.WAIST.code, today, 91.25)
        val health = HealthRepository(
            source = object : HealthSource {
                override fun availability() = HealthAvailability.Available
                override suspend fun grantedPermissions() = HealthGrants(steps = true, restingHeartRate = true)
                override suspend fun readSteps(
                    startInclusive: LocalDateTime,
                    endExclusive: LocalDateTime
                ) = listOf(HealthMetricBucket(today, 424_242))
                override suspend fun readRestingHeartRate(
                    startInclusive: LocalDateTime,
                    endExclusive: LocalDateTime
                ) = listOf(HealthMetricBucket(today, 59))
            },
            dateProvider = FixedDateProvider(today)
        )
        health.refresh()
        assertEquals(424_242L, health.readings.value.steps.single().steps)

        val weights = weightRepository.observeAll().first()
        assertEquals(listOf(80.0, 81.5), weights.map { it.weightKg })
        val proposal = sessions.proposeBodyWeight(today)
        assertEquals(BodyWeightSource.MEASURED_SAME_DAY, proposal.source)
        assertEquals(81.5, proposal.kilograms!!, 0.0)

        val august = ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2024, 8, 1))
        val report = ReportLogic.summarize(
            period = august,
            inputs = ReportInputs(bodyWeights = weights),
            today = today,
            historyStart = LocalDate.of(2024, 8, 15)
        )
        val bodyWeight = report.bodyWeight
        assertEquals(80.0, bodyWeight?.firstKg ?: Double.NaN, 0.0)
        assertTrue(bodyWeight?.firstKg != 424_242.0)

        val csv = WeightCsv.export(weights)
        assertFalse(csv.contains("424242"))
        assertTrue(csv.startsWith(WeightCsv.HEADER))

        val json = backup.exportJson(AppBackupSource("app.mymusclemap", "test"))
        assertFalse(json.contains("424242"))
        assertFalse(json.contains("restingHeartRate"))
        assertFalse(json.contains("health_connect"))
        assertEquals(8, AppBackupFormat.SCHEMA_VERSION)
        assertTrue(AppBackupFormat.TABLE_NAMES.none { it.contains("health") || it.contains("step") })
        assertEquals(0, database.progressPhotoDao().observeAll().first().size)
        assertEquals(91.25, body.all().single().value, 0.0)
        health.refresh()
        assertEquals(listOf(80.0, 81.5), weightRepository.observeAll().first().map { it.weightKg })
        assertEquals(91.25, body.all().single().value, 0.0)
    }
}
