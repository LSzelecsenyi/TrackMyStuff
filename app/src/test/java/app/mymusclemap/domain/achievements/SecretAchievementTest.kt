package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SecretAchievementTest {
    @Test
    fun calendarAwardsUseTheStoredStartDate() {
        val christmas = workout(date = LocalDate.of(2026, 12, 25), client = "christmas")
        val newYear = workout(date = LocalDate.of(2026, 1, 1), client = "new-year")
        val leap = workout(date = LocalDate.of(2024, 2, 29), client = "leap")
        val friday = workout(date = LocalDate.of(2026, 2, 13), client = "friday")
        val awards = SecretAchievementEvaluator.qualifications(listOf(christmas, newYear, leap, friday))
        assertEquals("christmas", client(awards, AchievementId.SILENT_NIGHT))
        assertEquals("new-year", client(awards, AchievementId.NEW_YEAR_SAME_ME))
        assertEquals("leap", client(awards, AchievementId.LEAP_DAY_LIFTER))
        assertEquals("friday", client(awards, AchievementId.FRIDAY_THE_STRONGTEENTH))
        assertEquals(christmas.session.finishedAt, unlocked(awards, AchievementId.SILENT_NIGHT))
    }

    @Test
    fun halloweenIsOctober31InAnyYearAndIgnoresTheUtcInstant() {
        val dayBefore = workout(date = LocalDate.of(2026, 10, 30), client = "before")
        val halloween = workout(date = LocalDate.of(2026, 10, 31), client = "halloween", startedAt = 500L)
        val dayAfter = workout(date = LocalDate.of(2026, 11, 1), client = "after")
        val earlierYear = workout(date = LocalDate.of(2024, 10, 31), client = "earlier", startedAt = 100L)
        val laterYear = workout(date = LocalDate.of(2028, 10, 31), client = "later", startedAt = 900L)
        val awards = SecretAchievementEvaluator.qualifications(
            listOf(dayBefore, halloween, dayAfter, laterYear, earlierYear)
        )
        assertEquals("earlier", client(awards, AchievementId.TRICK_OR_LIFT))
        assertEquals(earlierYear.session.finishedAt, unlocked(awards, AchievementId.TRICK_OR_LIFT))
        val storedDate = workout(
            date = LocalDate.of(2027, 10, 31),
            client = "stored-halloween",
            startedAt = Instant.parse("2027-10-30T23:30:00Z").toEpochMilli()
        )
        assertEquals(
            "stored-halloween",
            client(SecretAchievementEvaluator.qualifications(listOf(storedDate)), AchievementId.TRICK_OR_LIFT)
        )
        val instantOnly = workout(
            date = LocalDate.of(2027, 10, 30),
            client = "instant-only",
            startedAt = Instant.parse("2027-10-31T00:30:00Z").toEpochMilli()
        )
        assertTrue(
            SecretAchievementEvaluator.qualifications(listOf(instantOnly))
                .none { it.achievementId == AchievementId.TRICK_OR_LIFT }
        )
    }

    @Test
    fun christmasCoversDecember24Through26WithoutChangingAnExistingEarn() {
        val tooEarly = workout(date = LocalDate.of(2025, 12, 23), client = "early")
        val eve = workout(date = LocalDate.of(2025, 12, 24), client = "eve", startedAt = 200L)
        val day = workout(date = LocalDate.of(2026, 12, 25), client = "day", startedAt = 300L)
        val boxing = workout(date = LocalDate.of(2024, 12, 26), client = "boxing", startedAt = 100L)
        val tooLate = workout(date = LocalDate.of(2026, 12, 27), client = "late")
        val awards = SecretAchievementEvaluator.qualifications(listOf(tooEarly, eve, day, boxing, tooLate))
        assertEquals("boxing", client(awards, AchievementId.SILENT_NIGHT))
        assertEquals(boxing.session.finishedAt, unlocked(awards, AchievementId.SILENT_NIGHT))
        val again = SecretAchievementEvaluator.qualifications(listOf(tooEarly, eve, day, boxing, tooLate))
        assertEquals(awards, again)

        val historical = AchievementReconciler.plan(
            request(
                initialized = true,
                trigger = "unrelated",
                qualifications = listOf(
                    SecretQualification(AchievementId.SILENT_NIGHT, boxing.session.finishedAt!!, "boxing"),
                    SecretQualification(AchievementId.TRICK_OR_LIFT, 400L, "halloween")
                )
            ),
            unlocks = emptyList(),
            events = emptyList()
        ).insertUnlocks
        assertEquals(1_000L, historical.single { it.achievementId == AchievementId.SILENT_NIGHT }.celebratedAt)
        assertEquals(boxing.session.finishedAt, historical.single { it.achievementId == AchievementId.SILENT_NIGHT }.unlockedAt)
        assertEquals(1_000L, historical.single { it.achievementId == AchievementId.TRICK_OR_LIFT }.celebratedAt)

        val owned = StoredUnlock(AchievementId.SILENT_NIGHT, celebratedAt = 80L)
        val kept = AchievementReconciler.plan(
            request(
                initialized = true,
                trigger = "day",
                qualifications = listOf(SecretQualification(AchievementId.SILENT_NIGHT, 9_999L, "day"))
            ),
            unlocks = listOf(owned),
            events = emptyList()
        )
        assertTrue(kept.insertUnlocks.none { it.achievementId == AchievementId.SILENT_NIGHT })
        assertFalse(AchievementId.SILENT_NIGHT in kept.revoke)
        val deletedHistory = AchievementReconciler.plan(
            request(initialized = true, trigger = null, qualifications = emptyList()),
            unlocks = listOf(
                owned,
                StoredUnlock(AchievementId.TRICK_OR_LIFT, celebratedAt = 80L)
            ),
            events = emptyList()
        )
        assertTrue(deletedHistory.insertUnlocks.isEmpty())
        assertFalse(AchievementId.SILENT_NIGHT in deletedHistory.revoke)
        assertFalse(AchievementId.TRICK_OR_LIFT in deletedHistory.revoke)
    }

    @Test
    fun nearbyDatesAndTheCurrentZoneDoNotQualify() {
        val newYearsEve = workout(date = LocalDate.of(2025, 12, 31), client = "eve-year")
        val januarySecond = workout(date = LocalDate.of(2026, 1, 2), client = "second")
        val dayBeforeLeap = workout(date = LocalDate.of(2024, 2, 28), client = "before-leap")
        val dayAfterLeap = workout(date = LocalDate.of(2024, 3, 1), client = "after-leap")
        val tuesdayThe13th = workout(date = LocalDate.of(2026, 1, 13), client = "tuesday")
        val mondayThe13th = workout(date = LocalDate.of(2026, 4, 13), client = "monday")
        val anotherFriday = workout(date = LocalDate.of(2026, 3, 13), client = "also-friday")
        val awards = SecretAchievementEvaluator.qualifications(
            listOf(
                newYearsEve,
                januarySecond,
                dayBeforeLeap,
                dayAfterLeap,
                tuesdayThe13th,
                mondayThe13th,
                anotherFriday
            )
        )
        assertTrue(awards.none { it.achievementId == AchievementId.SILENT_NIGHT })
        assertTrue(awards.none { it.achievementId == AchievementId.NEW_YEAR_SAME_ME })
        assertTrue(awards.none { it.achievementId == AchievementId.LEAP_DAY_LIFTER })
        assertEquals("also-friday", client(awards, AchievementId.FRIDAY_THE_STRONGTEENTH))
        val zoneShifted = workout(
            date = LocalDate.of(2026, 12, 25),
            client = "stored-date",
            startedAt = Instant.parse("2026-12-24T23:30:00Z").toEpochMilli()
        )
        assertEquals(
            "stored-date",
            client(SecretAchievementEvaluator.qualifications(listOf(zoneShifted)), AchievementId.SILENT_NIGHT)
        )
        val wrongStoredDate = workout(
            date = LocalDate.of(2026, 12, 23),
            client = "wrong-date",
            startedAt = Instant.parse("2026-12-25T00:30:00Z").toEpochMilli()
        )
        assertTrue(
            SecretAchievementEvaluator.qualifications(listOf(wrongStoredDate))
                .none { it.achievementId == AchievementId.SILENT_NIGHT }
        )
    }

    @Test
    fun emptyAbandonedAndSkippedWorkoutsDoNotQualify() {
        val skipped = workout(date = LocalDate.of(2026, 12, 25), client = "skipped", performed = false)
        val abandoned = workout(
            date = LocalDate.of(2026, 1, 1),
            client = "abandoned",
            status = SessionStatus.ABANDONED
        )
        val active = workout(
            date = LocalDate.of(2024, 2, 29),
            client = "active",
            status = SessionStatus.IN_PROGRESS
        )
        val halloweenSkipped = workout(
            date = LocalDate.of(2026, 10, 31),
            client = "halloween-skipped",
            performed = false
        )
        val halloweenAbandoned = workout(
            date = LocalDate.of(2026, 10, 31),
            client = "halloween-abandoned",
            status = SessionStatus.ABANDONED
        )
        val halloweenActive = workout(
            date = LocalDate.of(2026, 10, 31),
            client = "halloween-active",
            status = SessionStatus.IN_PROGRESS
        )
        assertTrue(
            SecretAchievementEvaluator.qualifications(
                listOf(skipped, abandoned, active, halloweenSkipped, halloweenAbandoned, halloweenActive)
            ).isEmpty()
        )
    }

    @Test
    fun oneMoreThingRequiresALaterExtraSetOnAPrescribedExercise() {
        val prescribedDone = listOf(set(id = 1, completedAt = 100), set(id = 2, completedAt = 200))
        val earlyExtra = planned(
            prescribed = prescribedDone,
            extras = listOf(set(id = 3, completedAt = 150, added = true))
        )
        val lateExtra = planned(
            prescribed = prescribedDone,
            extras = listOf(set(id = 3, completedAt = 250, added = true))
        )
        val skipped = planned(
            prescribed = listOf(
                set(id = 1, completedAt = 100),
                set(id = 2, completedAt = null, status = SessionSetStatus.SKIPPED)
            ),
            extras = listOf(set(id = 3, completedAt = 300, added = true))
        )
        val unplanned = planned(
            templateId = null,
            prescribed = prescribedDone,
            extras = listOf(set(id = 3, completedAt = 250, added = true))
        )
        val missingTime = planned(
            prescribed = listOf(set(id = 1, completedAt = null)),
            extras = listOf(set(id = 2, completedAt = 250, added = true))
        )
        val newExerciseOnly = planned(
            prescribed = prescribedDone,
            extras = listOf(set(id = 3, completedAt = 250, added = true)),
            extraOnNewExercise = true
        )
        assertFalse(SecretAchievementEvaluator.oneMoreThing(earlyExtra))
        assertTrue(SecretAchievementEvaluator.oneMoreThing(lateExtra))
        assertFalse(SecretAchievementEvaluator.oneMoreThing(skipped))
        assertFalse(SecretAchievementEvaluator.oneMoreThing(unplanned))
        assertFalse(SecretAchievementEvaluator.oneMoreThing(missingTime))
        assertFalse(SecretAchievementEvaluator.oneMoreThing(newExerciseOnly))
        assertEquals(
            "late",
            client(
                SecretAchievementEvaluator.qualifications(listOf(lateExtra.copy(session = lateExtra.session.copy(clientWorkoutId = "late")))),
                AchievementId.ONE_MORE_THING
            )
        )
    }

    @Test
    fun importedHistoryWithoutExtraSetEvidenceDoesNotQualifyOneMoreThing() {
        val imported = planned(
            prescribed = listOf(set(id = 1, completedAt = 100), set(id = 2, completedAt = 100)),
            fingerprint = "csv-1"
        )
        assertFalse(SecretAchievementEvaluator.oneMoreThing(imported))
        assertTrue(
            SecretAchievementEvaluator.qualifications(
                listOf(imported.copy(session = imported.session.copy(workoutDate = LocalDate.of(2026, 12, 25))))
            ).any { it.achievementId == AchievementId.SILENT_NIGHT }
        )
    }

    @Test
    fun tripleCrownUsesExistingRecordEventsAndIgnoresTheFirstPerformance() {
        val baseline = weighted(sessionId = 1, finishedAt = 100, weight = 60.0, reps = 5, client = "base")
        assertTrue(
            SecretAchievementEvaluator.qualifications(listOf(baseline))
                .none { it.achievementId == AchievementId.TRIPLE_CROWN }
        )
        val crown = weighted(sessionId = 2, finishedAt = 200, weight = 70.0, reps = 8, client = "crown")
        val awards = SecretAchievementEvaluator.qualifications(listOf(baseline, crown))
        assertEquals("crown", client(awards, AchievementId.TRIPLE_CROWN))
        assertEquals(200L, unlocked(awards, AchievementId.TRIPLE_CROWN))
        val weightOnly = weighted(sessionId = 3, finishedAt = 300, weight = 80.0, reps = 5, client = "weight")
        assertEquals(
            "crown",
            client(
                SecretAchievementEvaluator.qualifications(listOf(baseline, weightOnly, crown)),
                AchievementId.TRIPLE_CROWN
            )
        )
    }

    @Test
    fun reconciliationCelebratesOnlyTheTriggeringWorkoutAndKeepsTheUnlock() {
        val qualification = SecretQualification(AchievementId.SILENT_NIGHT, 80L, "christmas")
        val startup = AchievementReconciler.plan(
            request(initialized = false, trigger = "christmas", qualifications = listOf(qualification)),
            unlocks = emptyList(),
            events = emptyList()
        )
        val silent = startup.insertUnlocks.single { it.achievementId == AchievementId.SILENT_NIGHT }
        assertEquals(80L, silent.unlockedAt)
        assertEquals(1_000L, silent.celebratedAt)

        val live = AchievementReconciler.plan(
            request(initialized = true, trigger = "christmas", qualifications = listOf(qualification)),
            unlocks = emptyList(),
            events = emptyList()
        ).insertUnlocks.single()
        assertNull(live.celebratedAt)
        assertEquals("christmas", live.triggerClientWorkoutId)

        val historical = AchievementReconciler.plan(
            request(initialized = true, trigger = "other", qualifications = listOf(qualification)),
            unlocks = emptyList(),
            events = emptyList()
        ).insertUnlocks.single()
        assertEquals(1_000L, historical.celebratedAt)

        val stored = StoredUnlock(AchievementId.SILENT_NIGHT, celebratedAt = 1_000L)
        val again = AchievementReconciler.plan(
            request(initialized = true, trigger = null, qualifications = emptyList()),
            unlocks = listOf(stored),
            events = emptyList()
        )
        assertTrue(again.insertUnlocks.isEmpty())
        assertFalse(AchievementId.SILENT_NIGHT in again.revoke)
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 0,
            unlocks = listOf(
                UnlockSnapshot(AchievementId.SILENT_NIGHT, unlockedAt = 80L, celebratedAt = 1_000L, triggerClientWorkoutId = null)
            ),
            events = emptyList()
        )
        assertTrue(board.items.single { it.id == AchievementId.SILENT_NIGHT }.unlocked)
        assertTrue(board.pending.none { it is PendingCelebration.SecretUnlocked })
        val pending = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 0,
            unlocks = listOf(
                UnlockSnapshot(AchievementId.TRIPLE_CROWN, unlockedAt = 80L, celebratedAt = null, triggerClientWorkoutId = "crown")
            ),
            events = emptyList()
        )
        assertTrue(pending.pending.filterIsInstance<PendingCelebration.SecretUnlocked>().single().achievementId == AchievementId.TRIPLE_CROWN)
        assertTrue(BadgeWallPresenter.almostThere(pending).none { it.achievementId.secret })
    }

    private fun client(awards: List<SecretQualification>, id: AchievementId): String? {
        return awards.single { it.achievementId == id }.clientWorkoutId
    }

    private fun unlocked(awards: List<SecretQualification>, id: AchievementId): Long {
        return awards.single { it.achievementId == id }.unlockedAt
    }

    private fun request(
        initialized: Boolean,
        trigger: String?,
        qualifications: List<SecretQualification>
    ): ReconcileRequest {
        return ReconcileRequest(
            initialized = initialized,
            completedWorkoutCount = 0,
            achievedWeeks = emptyList(),
            nowMillis = 1_000L,
            triggerClientWorkoutId = trigger,
            secretQualifications = qualifications
        )
    }

    private fun workout(
        date: LocalDate,
        client: String,
        status: SessionStatus = SessionStatus.COMPLETED,
        performed: Boolean = true,
        startedAt: Long = 10L
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = session(
                id = client.hashCode().toLong(),
                client = client,
                date = date,
                status = status,
                templateId = 1L,
                startedAt = startedAt,
                finishedAt = if (status == SessionStatus.COMPLETED) startedAt + 50 else null
            ),
            exercises = listOf(
                item(
                    sets = listOf(
                        set(
                            id = 1,
                            completedAt = if (performed && status == SessionStatus.COMPLETED) startedAt + 20 else null,
                            status = if (performed && status == SessionStatus.COMPLETED) {
                                SessionSetStatus.COMPLETED
                            } else {
                                SessionSetStatus.SKIPPED
                            }
                        )
                    )
                )
            )
        )
    }

    private fun planned(
        prescribed: List<SessionSet>,
        extras: List<SessionSet> = emptyList(),
        templateId: Long? = 4L,
        extraOnNewExercise: Boolean = false,
        fingerprint: String? = null
    ): WorkoutSessionAggregate {
        val exercises = if (extraOnNewExercise) {
            listOf(item(exerciseId = 1, sets = prescribed), item(exerciseId = 2, sets = extras))
        } else {
            listOf(item(exerciseId = 1, sets = prescribed + extras))
        }
        return WorkoutSessionAggregate(
            session = session(
                id = 9,
                client = "planned",
                date = LocalDate.of(2026, 6, 2),
                status = SessionStatus.COMPLETED,
                templateId = templateId,
                startedAt = 10L,
                finishedAt = 400L,
                fingerprint = fingerprint
            ),
            exercises = exercises
        )
    }

    private fun weighted(
        sessionId: Long,
        finishedAt: Long,
        weight: Double,
        reps: Int,
        client: String
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = session(
                id = sessionId,
                client = client,
                date = LocalDate.of(2026, 6, 2),
                status = SessionStatus.COMPLETED,
                templateId = null,
                startedAt = finishedAt - 10,
                finishedAt = finishedAt
            ),
            exercises = listOf(
                item(
                    sets = listOf(
                        set(
                            id = sessionId,
                            reps = reps,
                            weight = weight,
                            completedAt = finishedAt
                        )
                    )
                )
            )
        )
    }

    private fun session(
        id: Long,
        client: String,
        date: LocalDate,
        status: SessionStatus,
        templateId: Long?,
        startedAt: Long,
        finishedAt: Long?,
        fingerprint: String? = null
    ): WorkoutSession {
        return WorkoutSession(
            id = id,
            templateId = templateId,
            templateName = "Plan",
            status = status,
            workoutDate = date,
            startedAt = startedAt,
            finishedAt = finishedAt,
            abandonedAt = if (status == SessionStatus.ABANDONED) finishedAt else null,
            notes = null,
            bodyWeightKg = null,
            bodyWeightSource = BodyWeightSource.UNKNOWN,
            bodyWeightSourceDate = null,
            createdAt = startedAt,
            updatedAt = finishedAt ?: startedAt,
            importFingerprint = fingerprint,
            clientWorkoutId = client
        )
    }

    private fun item(exerciseId: Long = 1, sets: List<SessionSet>): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = exerciseId,
                sessionId = 0,
                exerciseId = exerciseId,
                position = 0,
                name = "Lift",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.OTHER,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = WeightInterpretation.TOTAL,
                primaryMuscle = MuscleGroup.FULL_BODY,
                secondaryMuscles = emptyList(),
                notes = null
            ),
            sets = sets
        )
    }

    private fun set(
        id: Long,
        completedAt: Long?,
        added: Boolean = false,
        status: SessionSetStatus = SessionSetStatus.COMPLETED,
        reps: Int = 5,
        weight: Double = 40.0
    ): SessionSet {
        return SessionSet(
            id = id,
            sessionExerciseId = 1,
            position = id.toInt(),
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
            plannedWeightKg = weight,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = if (status == SessionSetStatus.COMPLETED) reps else null,
            actualLoadKind = if (status == SessionSetStatus.COMPLETED) PlannedLoadKind.EXTERNAL_WEIGHT else null,
            actualWeightKg = if (status == SessionSetStatus.COMPLETED) weight else null,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = status,
            completedAt = completedAt,
            addedDuringWorkout = added
        )
    }
}
