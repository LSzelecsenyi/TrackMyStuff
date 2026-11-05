package app.mymusclemap.domain.units

import app.mymusclemap.data.appbackup.AppBackupFormat
import app.mymusclemap.data.preferences.UnitSystemBackup
import app.mymusclemap.domain.MeasurementValidator
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.ProAchievementEvaluator
import app.mymusclemap.domain.achievements.TargetWeightDirection
import app.mymusclemap.domain.achievements.TargetWeightGoalFacts
import app.mymusclemap.domain.achievements.TargetWeightProgressEvaluator
import app.mymusclemap.domain.body.BodyMeasurementType
import app.mymusclemap.domain.body.BodyMeasurementValidator
import app.mymusclemap.domain.health.HealthGrants
import app.mymusclemap.domain.workout.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class UnitSystemTest {
    @Test
    fun convertsKilogramsAndPoundsWithTheSpecifiedFactor() {
        val pounds = UnitConversions.kgToLb(1.0)
        assertEquals(0, pounds.compareTo(BigDecimal("2.2046226218487757")))
        val kilograms = UnitConversions.lbToKg(BigDecimal("2.2046226218487757"))
        assertEquals(1.0, kilograms.toDouble(), 0.000000001)
    }

    @Test
    fun convertsLengthAndDistanceWithTheSpecifiedFactors() {
        assertEquals(0, UnitConversions.inToCm(BigDecimal.ONE).compareTo(BigDecimal("2.54")))
        assertEquals(1.0, UnitConversions.cmToIn(2.54).toDouble(), 0.000000001)
        assertEquals(0, UnitConversions.ftToM(BigDecimal.ONE).compareTo(BigDecimal("0.3048")))
        assertEquals(1.0, UnitConversions.mToFt(0.3048).toDouble(), 0.000000001)
        assertEquals(0, UnitConversions.miToM(BigDecimal.ONE).compareTo(BigDecimal("1609.344")))
        assertEquals(1.0, UnitConversions.mToMi(1609.344).toDouble(), 0.000000001)
    }

    @Test
    fun displayRoundingDoesNotReplaceAnUntouchedCanonicalValue() {
        val stored = 80.0
        val shown = MeasurementInput.bodyWeightInput(stored, UnitSystem.IMPERIAL)
        assertEquals(
            stored,
            MeasurementInput.resolveBodyWeightKg(shown, UnitSystem.IMPERIAL, stored)
        )
        val metric = MeasurementInput.bodyWeightInput(stored, UnitSystem.METRIC)
        assertEquals(stored, MeasurementInput.resolveBodyWeightKg(metric, UnitSystem.METRIC, stored))
        repeat(4) {
            val again = MeasurementInput.reformatBodyWeight(shown, stored, UnitSystem.IMPERIAL, UnitSystem.METRIC)
            val back = MeasurementInput.reformatBodyWeight(again, stored, UnitSystem.METRIC, UnitSystem.IMPERIAL)
            assertEquals(shown, back)
            assertEquals(stored, MeasurementInput.resolveBodyWeightKg(back, UnitSystem.IMPERIAL, stored))
        }
    }

    @Test
    fun anEditedImperialWeightConvertsOnce() {
        val kilograms = MeasurementInput.resolveBodyWeightKg("180.0", UnitSystem.IMPERIAL, 80.0)
        val expected = UnitConversions.lbToKg(BigDecimal("180.0")).toDouble()
        assertEquals(expected, kilograms!!, 0.000000001)
        assertTrue(kilograms != 180.0)
    }

    @Test
    fun viewingAConvertedWorkoutWeightDoesNotChangeTheCanonicalText() {
        val canonical = "80"
        val shown = WorkoutWeightInput.displayOf(canonical, UnitSystem.IMPERIAL)
        assertEquals(canonical, WorkoutWeightInput.commit(canonical, shown, UnitSystem.IMPERIAL))
        assertEquals(canonical, WorkoutWeightInput.displayOf(canonical, UnitSystem.METRIC))
        val edited = WorkoutWeightInput.commit(canonical, "200", UnitSystem.IMPERIAL)
        assertTrue(edited != "200")
        assertTrue(edited != canonical)
    }

    @Test
    fun distanceToggleWithoutAnEditDoesNotWrite() {
        val meters = 1609.344
        val shown = WorkoutDistanceInput.displayOf(meters, DistanceDisplay.MILES)
        assertNull(WorkoutDistanceInput.commitMeters(meters, shown, DistanceDisplay.MILES))
        val edited = WorkoutDistanceInput.commitMeters(meters, "2", DistanceDisplay.MILES)
        assertEquals("3218.688", edited)
    }

    @Test
    fun bodyLengthKeepsTheStoredCentimetersWhenTheDisplayIsUnchanged() {
        val today = LocalDate.of(2026, 10, 31)
        val stored = 80.0
        val shown = MeasurementInput.lengthInput(stored, UnitSystem.IMPERIAL)
        val result = BodyMeasurementValidator.validate(
            BodyMeasurementType.WAIST,
            today,
            shown,
            today,
            UnitSystem.IMPERIAL,
            stored
        )
        assertTrue(result is app.mymusclemap.domain.body.BodyMeasurementValidationResult.Valid)
        assertEquals(stored, (result as app.mymusclemap.domain.body.BodyMeasurementValidationResult.Valid).value, 0.0)
    }

    @Test
    fun volumeMasterAndGoalMathStayInKilograms() {
        assertEquals(100_000.0, ProAchievementEvaluator.VOLUME_MASTER_KG, 0.0)
        assertEquals(100_000.0, AchievementId.VOLUME_MASTER.volumeThresholdKg)
        val goal = TargetWeightGoalFacts(
            id = 1L,
            targetKg = 75.0,
            baselineKg = 80.0,
            direction = TargetWeightDirection.LOSS,
            active = true
        )
        val progress = TargetWeightProgressEvaluator.progress(goal, 78.0)!!
        assertEquals(2.0, progress.progressedKg, 0.0)
        val shown = MeasurementText.weightLabel(progress.remainingTowardTargetKg, UnitSystem.IMPERIAL)
        assertTrue(shown.endsWith("lb"))
        assertEquals(3.0, progress.remainingTowardTargetKg, 0.0)
    }

    @Test
    fun suggestionUsesOnlyUsLiberiaAndMyanmarAndDoesNotImplyASavedPreference() {
        assertEquals(UnitSystem.IMPERIAL, UnitSystemSuggestion.fromRegion("US"))
        assertEquals(UnitSystem.IMPERIAL, UnitSystemSuggestion.fromRegion("lr"))
        assertEquals(UnitSystem.IMPERIAL, UnitSystemSuggestion.fromRegion("MM"))
        assertEquals(UnitSystem.METRIC, UnitSystemSuggestion.fromRegion("GB"))
        assertEquals(UnitSystem.METRIC, UnitSystemSuggestion.fromRegion("HU"))
        assertNull(UnitSystem.fromStored(null))
        assertEquals(UnitSystem.METRIC, UnitSystemBackup.decode(emptyMap()))
    }

    @Test
    fun legacyBackupWithoutAUnitKeyIsMetricAndTheSchemaStaysCompatible() {
        assertEquals(UnitSystem.METRIC, UnitSystemBackup.decode(emptyMap()))
        assertEquals(UnitSystem.IMPERIAL, UnitSystemBackup.decode(mapOf("unit_system" to "IMPERIAL")))
        assertEquals(UnitSystem.METRIC, UnitSystemBackup.decode(mapOf("unit_system" to "stones")))
        assertEquals(16, AppBackupFormat.ARCHIVE_WORK_SCHEMA_VERSION)
    }

    @Test
    fun healthConnectGrantsDoNotIncludeMassOrLength() {
        val grants = HealthGrants(
            steps = true,
            restingHeartRate = true,
            exercise = true,
            hrv = true,
            sleep = true
        )
        assertTrue(grants.any())
        val fields = HealthGrants::class.java.declaredFields.map { it.name }
        assertTrue(fields.none { it.contains("weight", ignoreCase = true) || it.contains("height", ignoreCase = true) })
    }

    @Test
    fun incrementsFollowTheSelectedSystem() {
        assertEquals(BigDecimal("2.5"), WeightIncrements.defaultStep(UnitSystem.METRIC))
        assertEquals(BigDecimal("5"), WeightIncrements.defaultStep(UnitSystem.IMPERIAL))
    }

    @Test
    fun metricBodyWeightValidationIsUnchanged() {
        val today = LocalDate.of(2026, 10, 31)
        val result = MeasurementValidator.validate(today, "80.0", today)
        assertTrue(result is app.mymusclemap.domain.MeasurementValidationResult.Valid)
        assertEquals(80.0, (result as app.mymusclemap.domain.MeasurementValidationResult.Valid).weightKg, 0.0)
    }

    @Test
    fun distanceUnitStoredOnDraftsRemainsMetersOrKilometers() {
        assertEquals(2, DistanceUnit.entries.size)
        assertEquals(DistanceUnit.METERS, DistanceUnit.valueOf("METERS"))
        assertEquals(DistanceUnit.KILOMETERS, DistanceUnit.valueOf("KILOMETERS"))
    }
}
