package app.mymusclemap.data.repository

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.appbackup.AppBackupJson
import app.mymusclemap.data.appbackup.AppBackupParseResult
import app.mymusclemap.data.appbackup.AppBackupRestoreResult
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutTemplateEntity
import app.mymusclemap.data.local.WorkoutTemplateExerciseEntity
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDeleteResult
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.StarterCatalog
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.ui.exercises.CatalogMessage
import app.mymusclemap.ui.exercises.ExerciseEditorViewModel
import app.mymusclemap.ui.exercises.ExerciseListViewModel
import app.mymusclemap.data.appbackup.emptySnapshot
import app.mymusclemap.data.appbackup.emptyTables
import app.mymusclemap.data.local.ExerciseEntity
import app.mymusclemap.data.local.ExerciseMuscleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class SystemExerciseCatalogTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: WeightDatabase
    private lateinit var repository: ExerciseRepository
    private lateinit var reconciler: SystemCatalogReconciler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ExerciseRepository(
            dao = database.exerciseDao(),
            clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC),
            templateDao = database.workoutTemplateDao(),
            sessionDao = database.workoutSessionDao(),
            allowCustomCreate = { count -> count < 5 }
        )
        reconciler = SystemCatalogReconciler(database, repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun freshAccountReceivesTheCatalogOnceAndALaterEntryKeepsExistingIds() = runTest {
        reconciler.reconcile()
        val first = repository.all()
        assertEquals(StarterCatalog.keys, first.mapNotNull { it.catalogKey }.toSet())
        assertTrue(first.all { it.isVerifiedSystemExercise() && !it.custom })
        assertEquals(0, repository.activeCustomCount())
        val ids = first.associate { it.catalogKey to it.id }
        reconciler.reconcile()
        assertEquals(ids, repository.all().associate { it.catalogKey to it.id })
    }

    @Test
    fun anAddedCatalogEntryIsInsertedWithoutChangingExistingIds() = runTest {
        val withoutPlank = StarterCatalog.entries.filter { it.catalogKey != "PLANK" }
        SystemCatalogReconciler(database, repository, withoutPlank).reconcile()
        val ids = repository.all().associate { it.catalogKey to it.id }
        assertFalse(ids.containsKey("PLANK"))
        reconciler.reconcile()
        reconciler.reconcile()
        val after = repository.all()
        assertEquals(ids, after.filter { it.catalogKey in ids.keys }.associate { it.catalogKey to it.id })
        assertEquals(1, after.count { it.catalogKey == "PLANK" })
    }

    @Test
    fun recognizableLegacyStartersAreAdoptedOnAnAlreadyMigratedDatabase() = runTest {
        val ids = StarterCatalog.entries.map { entry ->
            val created = repository.save(entry.draft, asStarter = true) as ExerciseSaveResult.Created
            created.id
        }
        assertEquals(StarterCatalog.entries.size, ids.size)
        reconciler.reconcile()
        reconciler.reconcile()
        val after = repository.all()
        assertEquals(StarterCatalog.entries.size, after.size)
        assertEquals(ids, after.sortedBy { it.id }.map { it.id })
        assertEquals(StarterCatalog.keys, after.mapNotNull { it.catalogKey }.toSet())
        assertEquals(after.map { it.name }.toSet().size, after.size)
        val squat = after.single { it.catalogKey == "SQUAT" }
        assertEquals(ids.first(), squat.id)
        assertEquals("Squat", squat.name)
        assertTrue(repository.save(squat("Changed").copy(id = squat.id)) is ExerciseSaveResult.SystemProtected)
        assertEquals(ExerciseDeleteResult.SystemProtected, repository.deletePermanently(squat.id))
    }

    @Test
    fun legacyAndCustomRowsStayBesideTheOfficialExercise() = runTest {
        val legacy = repository.save(squat("Front Squat"), asStarter = true) as ExerciseSaveResult.Created
        val custom = repository.save(squat("Squat")) as ExerciseSaveResult.Created
        val templateId = database.workoutTemplateDao().insertTemplate(
            WorkoutTemplateEntity(
                name = "Legs",
                normalizedName = "legs",
                notes = null,
                archived = false,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        database.workoutTemplateDao().insertExercise(
            WorkoutTemplateExerciseEntity(
                templateId = templateId,
                exerciseId = legacy.id,
                position = 0,
                notes = null,
                createdAt = 1L
            )
        )
        reconciler.reconcile()
        val keptLegacy = repository.getById(legacy.id)!!
        val keptCustom = repository.getById(custom.id)!!
        assertNull(keptLegacy.catalogKey)
        assertEquals(false, keptLegacy.custom)
        assertEquals("Front Squat", keptLegacy.name)
        assertNull(keptCustom.catalogKey)
        assertEquals(true, keptCustom.custom)
        val official = repository.all().single { it.catalogKey == "SQUAT" }
        assertTrue(official.id != legacy.id && official.id != custom.id)
        val referenced = database.workoutTemplateDao().observeExercises().first().single()
        assertEquals(legacy.id, referenced.exerciseId)
    }

    @Test
    fun systemExercisesAreLockedAndCustomExercisesKeepTheirLimits() = runTest {
        reconciler.reconcile()
        val system = repository.all().single { it.catalogKey == "SQUAT" }
        val before = repository.getById(system.id)!!
        assertTrue(repository.save(squat("Renamed").copy(id = system.id)) is ExerciseSaveResult.SystemProtected)
        assertEquals(false, repository.archive(system.id))
        assertEquals(false, repository.restore(system.id))
        assertEquals(ExerciseDeleteResult.SystemProtected, repository.deletePermanently(system.id))
        assertEquals(before, repository.getById(system.id))
        val copied = repository.save(squat("Squat")) as ExerciseSaveResult.Created
        assertTrue(copied.id != system.id)
        assertEquals(before, repository.getById(system.id))
        assertEquals(ExerciseSaveResult.DuplicateName, repository.save(squat("Squat")))
        repeat(4) { index ->
            assertTrue(repository.save(squat("Custom $index")) is ExerciseSaveResult.Created)
        }
        assertEquals(ExerciseSaveResult.CreationLimited, repository.save(squat("Custom 4")))
        assertEquals(5, repository.activeCustomCount())
        val removable = repository.all().single { it.name == "Custom 0" }
        assertEquals(true, repository.archive(removable.id))
        assertEquals(ExerciseDeleteResult.Deleted, repository.deletePermanently(removable.id))

        val editor = ExerciseEditorViewModel(
            SavedStateHandle(mapOf(ExerciseEditorViewModel.EXERCISE_ID_KEY to system.id)),
            repository
        )
        val loaded = editor.uiState.first { it.systemProtected }
        assertFalse(loaded.canSave)
        editor.save()
        assertEquals(before, repository.getById(system.id))

        val list = ExerciseListViewModel(repository)
        list.uiState.first { !it.loading }
        list.archive(system.id)
        list.uiState.first { it.message == CatalogMessage.SystemProtected }
        assertEquals(before, repository.getById(system.id))
    }

    @Test
    fun restoreKeepsCustomRowsAndFillsMissingOfficialEntries() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = ThemePreferences(context)
        val custom = ExerciseEntity(
            id = 7L,
            name = "Squat",
            normalizedName = "squat",
            category = "STRENGTH",
            movementPattern = "SQUAT",
            measurementType = "REPETITIONS_AND_WEIGHT",
            resistanceBasis = "EXTERNAL",
            weightInterpretation = "TOTAL",
            notes = "mine",
            archived = false,
            createdAt = 1L,
            updatedAt = 1L,
            custom = true
        )
        val claimed = custom.copy(id = 8L, name = "Dip", normalizedName = "dip", notes = "hacked", catalogKey = "SQUAT")
        val snapshot = emptySnapshot().copy(
            tables = emptyTables().copy(
                exercises = listOf(custom, claimed),
                exerciseMuscles = listOf(
                    ExerciseMuscleEntity(7L, "QUADRICEPS", "PRIMARY"),
                    ExerciseMuscleEntity(8L, "TRICEPS", "PRIMARY")
                )
            )
        )
        val backup = AppBackupRepository(database, preferences)
        assertEquals(AppBackupRestoreResult.Success, backup.restoreJson(AppBackupJson.encode(snapshot)))
        val rows = repository.all()
        val restoredCustom = rows.single { it.id == 7L }
        val restoredClaim = rows.single { it.id == 8L }
        assertEquals("mine", restoredCustom.notes)
        assertNull(restoredCustom.catalogKey)
        assertEquals("hacked", restoredClaim.notes)
        assertNull(restoredClaim.catalogKey)
        assertEquals(1, rows.count { it.catalogKey == "SQUAT" })
        assertTrue(rows.single { it.catalogKey == "SQUAT" }.id != 7L)
        assertEquals(StarterCatalog.keys, rows.mapNotNull { it.catalogKey }.toSet())
        val exported = AppBackupJson.parse(
            AppBackupRepository(database, preferences).exportJson(AppBackupSource("app.mymusclemap", "test"))
        ) as AppBackupParseResult.Success
        assertEquals("SQUAT", exported.snapshot.tables.exercises.single { it.catalogKey == "SQUAT" }.catalogKey)
    }

    private fun squat(name: String): ExerciseDraft {
        return ExerciseDraft(
            name = name,
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.SQUAT,
            measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
            resistanceBasis = ResistanceBasis.EXTERNAL,
            weightInterpretation = WeightInterpretation.TOTAL,
            primaryMuscle = MuscleGroup.QUADRICEPS
        )
    }
}
