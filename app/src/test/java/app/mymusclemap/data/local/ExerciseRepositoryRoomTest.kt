package app.mymusclemap.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDeleteResult
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ExerciseRepositoryRoomTest {
    private lateinit var database: WeightDatabase
    private lateinit var dao: ExerciseDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.exerciseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveCreatesExerciseAndMusclesInOneTransaction() = runTest {
        val repository = repository(clock(1_000L))
        val result = repository.save(pullUpDraft())
        val created = result as ExerciseSaveResult.Created
        val stored = repository.getById(created.id)!!
        assertEquals("Húzódzkodás", stored.name)
        assertEquals("húzódzkodás", stored.normalizedName)
        assertEquals(MuscleGroup.LATS, stored.primaryMuscle)
        assertEquals(listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS), stored.secondaryMuscles)
        assertEquals(ResistanceBasis.BODYWEIGHT, stored.resistanceBasis)
        assertEquals(WeightInterpretation.NOT_APPLICABLE, stored.weightInterpretation)
        assertEquals(1, dao.observeActive().first().size)
        assertEquals(3, dao.getMuscles(created.id).size)
    }

    @Test
    fun duplicateNameIsRejectedCaseInsensitively() = runTest {
        val repository = repository(clock(1_000L))
        repository.save(pullUpDraft())
        val duplicate = repository.save(pullUpDraft(name = "  húzódzkodás "))
        assertEquals(ExerciseSaveResult.DuplicateName, duplicate)
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun archivedExercisesStillBlockDuplicateNames() = runTest {
        val repository = repository(clock(1_000L))
        val created = repository.save(pullUpDraft()) as ExerciseSaveResult.Created
        assertTrue(repository.archive(created.id))
        val duplicate = repository.save(pullUpDraft(name = "HÚZÓDZKODÁS"))
        assertEquals(ExerciseSaveResult.DuplicateName, duplicate)
    }

    @Test
    fun editPreservesStableIdAndCreatedTimestamp() = runTest {
        val createdAt = 1_000L
        val repository = repository(clock(createdAt))
        val created = repository.save(pullUpDraft()) as ExerciseSaveResult.Created
        val original = repository.getById(created.id)!!
        val later = repository(clock(5_000L))
        val updated = later.save(
            pullUpDraft(
                id = created.id,
                name = "Húzódzkodás",
                notes = "Szoros fogás"
            )
        )
        val stored = later.getById(created.id)!!
        assertTrue(updated is ExerciseSaveResult.Updated)
        assertEquals(original.id, stored.id)
        assertEquals(createdAt, stored.createdAt)
        assertEquals(5_000L, stored.updatedAt)
        assertEquals("Szoros fogás", stored.notes)
        assertNotEquals(stored.createdAt, stored.updatedAt)
    }

    @Test
    fun archiveHidesFromActiveQueryAndRestoreReturnsIt() = runTest {
        val repository = repository(clock(1_000L))
        val created = repository.save(pullUpDraft()) as ExerciseSaveResult.Created
        assertEquals(1, dao.observeActive().first().size)
        assertTrue(repository.archive(created.id))
        assertTrue(dao.observeActive().first().isEmpty())
        assertEquals(1, dao.observeArchived().first().size)
        assertEquals(created.id, dao.observeArchived().first().single().id)
        assertTrue(repository.restore(created.id))
        assertEquals(created.id, dao.observeActive().first().single().id)
        assertTrue(dao.observeArchived().first().isEmpty())
    }

    @Test
    fun permanentDeleteRemovesUnusedExercise() = runTest {
        val repository = repository(clock(1_000L))
        val created = repository.save(pullUpDraft()) as ExerciseSaveResult.Created
        assertTrue(repository.canDeletePermanently(created.id))
        assertEquals(ExerciseDeleteResult.Deleted, repository.deletePermanently(created.id))
        assertTrue(repository.observeAll().first().isEmpty())
        assertTrue(dao.getMuscles(created.id).isEmpty())
        assertFalse(repository.canDeletePermanently(created.id))
    }

    @Test
    fun uniqueNormalizedNameIsEnforcedByRoom() = runTest {
        dao.insert(entity(normalizedName = "húzódzkodás"))
        var threw = false
        try {
            dao.insert(entity(name = "HÚZÓDZKODÁS", normalizedName = "húzódzkodás"))
        } catch (error: Exception) {
            threw = error.message.orEmpty().contains("UNIQUE", ignoreCase = true) ||
                error.cause?.message.orEmpty().contains("UNIQUE", ignoreCase = true)
        }
        assertTrue(threw)
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun malformedPersistedEnumCodesUseSafeFallbacks() = runTest {
        val id = dao.insert(
            entity(
                category = "LEGACY_POWER",
                movementPattern = "UNKNOWN_PATTERN",
                measurementType = "SETS_ONLY",
                resistanceBasis = "BAND",
                weightInterpretation = "BODY"
            )
        )
        dao.insertMuscles(
            listOf(
                ExerciseMuscleEntity(id, "NOT_A_MUSCLE", "PRIMARY"),
                ExerciseMuscleEntity(id, "BICEPS", "SECONDARY")
            )
        )
        val mapped = dao.getById(id)!!.toModel(dao.getMuscles(id))
        assertEquals(ExerciseCategory.STRENGTH, mapped.category)
        assertEquals(MovementPattern.OTHER, mapped.movementPattern)
        assertEquals(MeasurementType.COMPLETION_ONLY, mapped.measurementType)
        assertEquals(ResistanceBasis.NONE, mapped.resistanceBasis)
        assertEquals(WeightInterpretation.NOT_APPLICABLE, mapped.weightInterpretation)
        assertEquals(MuscleGroup.FULL_BODY, mapped.primaryMuscle)
        assertEquals(listOf(MuscleGroup.BICEPS), mapped.secondaryMuscles)
    }

    @Test
    fun neckMusclePersistsAsRoomStringWithoutSchemaChange() = runTest {
        val repository = repository(clock(1_000L))
        val created = repository.save(
            pullUpDraft(name = "Nyakhajlítás").copy(
                primaryMuscle = MuscleGroup.NECK,
                secondaryMuscles = listOf(MuscleGroup.UPPER_BACK)
            )
        ) as ExerciseSaveResult.Created
        val storedMuscles = dao.getMuscles(created.id)
        assertEquals(setOf("NECK", "UPPER_BACK"), storedMuscles.map { it.muscleGroup }.toSet())
        assertEquals("PRIMARY", storedMuscles.single { it.muscleGroup == "NECK" }.role)
        assertEquals("SECONDARY", storedMuscles.single { it.muscleGroup == "UPPER_BACK" }.role)
        val mapped = repository.getById(created.id)!!
        assertEquals(MuscleGroup.NECK, mapped.primaryMuscle)
        assertEquals(listOf(MuscleGroup.UPPER_BACK), mapped.secondaryMuscles)
        assertEquals(7, database.openHelper.readableDatabase.version)
    }

    @Test
    fun oneBodyweightCatalogRecordIsEnoughForLaterPerSetLoad() = runTest {
        val repository = repository(clock(1_000L))
        repository.save(pullUpDraft())
        val catalog = repository.observeAll().first()
        assertEquals(1, catalog.size)
        assertEquals(ResistanceBasis.BODYWEIGHT, catalog.single().resistanceBasis)
        assertEquals(MeasurementType.REPETITIONS, catalog.single().measurementType)
        assertFalse(
            catalog.any { it.name.contains("súly", ignoreCase = true) }
        )
    }

    private fun repository(clock: Clock): ExerciseRepository {
        return ExerciseRepository(dao, clock)
    }

    private fun clock(epochMilli: Long): Clock {
        return Clock.fixed(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC)
    }

    private fun pullUpDraft(
        id: Long? = null,
        name: String = "Húzódzkodás",
        notes: String = ""
    ): ExerciseDraft {
        return ExerciseDraft(
            id = id,
            name = name,
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.VERTICAL_PULL,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = MuscleGroup.LATS,
            secondaryMuscles = listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.LATS),
            notes = notes
        )
    }

    private fun entity(
        name: String = "Húzódzkodás",
        normalizedName: String = "húzódzkodás",
        category: String = "STRENGTH",
        movementPattern: String = "VERTICAL_PULL",
        measurementType: String = "REPETITIONS",
        resistanceBasis: String = "BODYWEIGHT",
        weightInterpretation: String = "NOT_APPLICABLE"
    ): ExerciseEntity {
        return ExerciseEntity(
            id = 0,
            name = name,
            normalizedName = normalizedName,
            category = category,
            movementPattern = movementPattern,
            measurementType = measurementType,
            resistanceBasis = resistanceBasis,
            weightInterpretation = weightInterpretation,
            notes = null,
            archived = false,
            createdAt = 1L,
            updatedAt = 1L
        )
    }
}
