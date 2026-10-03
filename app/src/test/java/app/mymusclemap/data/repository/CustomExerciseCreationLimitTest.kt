package app.mymusclemap.data.repository

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.CustomExerciseAccess
import app.mymusclemap.domain.entitlement.EffectiveEntitlement
import app.mymusclemap.domain.entitlement.EntitlementTier
import app.mymusclemap.domain.entitlement.FeatureAccessPolicy
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
import app.mymusclemap.domain.exercise.ExerciseDeleteResult
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.ui.exercises.ExerciseEditorViewModel
import app.mymusclemap.ui.exercises.ExerciseListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class CustomExerciseCreationLimitTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: WeightDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun freeCreationFollowsTheActiveCustomCountAndKeepsExistingExercisesUsable() = runTest {
        val repository = repository(free = true)
        repository.save(draft("Squat"), asStarter = true)
        assertEquals(0, repository.activeCustomCount())
        assertTrue(repository.save(draft("Custom 1")) is ExerciseSaveResult.Created)
        assertTrue(repository.save(draft("Custom 2")) is ExerciseSaveResult.Created)
        assertTrue(repository.save(draft("Custom 3")) is ExerciseSaveResult.Created)
        assertTrue(repository.save(draft("Custom 4")) is ExerciseSaveResult.Created)
        val fifth = repository.save(draft("Custom 5")) as ExerciseSaveResult.Created
        assertEquals(ExerciseSaveResult.CreationLimited, repository.save(draft("Custom 6")))
        assertEquals(5, repository.activeCustomCount())

        val edited = repository.getById(fifth.id)!!
        val update = repository.save(
            draft("Custom 5 renamed").copy(
                id = edited.id,
                notes = "still mine",
                primaryMuscle = edited.primaryMuscle
            )
        )
        assertTrue(update is ExerciseSaveResult.Updated)
        val stored = repository.getById(fifth.id)!!
        assertEquals("Custom 5 renamed", stored.name)
        assertTrue(stored.custom)
        assertFalse(stored.archived)
        assertTrue(repository.observeActive().first().any { it.id == fifth.id })

        assertTrue(repository.archive(fifth.id))
        assertEquals(4, repository.activeCustomCount())
        assertTrue(repository.save(draft("Custom 6")) is ExerciseSaveResult.Created)
        val removable = repository.observeActive().first().first { it.name == "Custom 1" }
        assertEquals(ExerciseDeleteResult.Deleted, repository.deletePermanently(removable.id))
    }

    @Test
    fun retainedCustomExercisesAboveFiveStayEditableWhileCreationStaysBlocked() = runTest {
        var free = false
        val repository = repository { free }
        val ids = (1..8).map { index ->
            (repository.save(draft("Kept $index")) as ExerciseSaveResult.Created).id
        }
        free = true
        assertEquals(ExerciseSaveResult.CreationLimited, repository.save(draft("Kept 9")))
        val updated = repository.save(draft("Kept 8 edited").copy(id = ids.last()))
        assertTrue(updated is ExerciseSaveResult.Updated)
        assertEquals(8, repository.observeActive().first().size)
        assertTrue(repository.archive(ids.first()))
        assertEquals(ExerciseDeleteResult.Deleted, repository.deletePermanently(ids[1]))
    }

    @Test
    fun proCreationIgnoresTheFreeThreshold() = runTest {
        val repository = repository(free = false)
        repeat(6) { index ->
            assertTrue(repository.save(draft("Pro $index")) is ExerciseSaveResult.Created)
        }
        assertEquals(6, repository.activeCustomCount())
    }

    @Test
    fun editorSaveIsBlockedWhenTheRepositoryGateRefusesAnotherCustomExercise() = runTest {
        val repository = repository(free = true)
        repeat(5) { index -> repository.save(draft("Existing $index")) }
        val editor = ExerciseEditorViewModel(SavedStateHandle(), repository)
        editor.onNameChange("One more")
        editor.onPrimaryMuscleChange(MuscleGroup.BICEPS)
        editor.save()
        val locked = editor.uiState.first { it.lockedFeature == AppFeature.UnlimitedCustomExercises }
        assertFalse(locked.finished)
        assertEquals(5, repository.activeCustomCount())
    }

    @Test
    fun listCreateUsesTheSamePolicyAndDoesNotOpenWhenFreeIsAtTheLimit() = runTest {
        val repository = repository(free = true)
        repeat(5) { index -> repository.save(draft("Listed $index")) }
        val viewModel = ExerciseListViewModel(
            repository,
            SelectiveFeatureEntitlements(emptySet())
        )
        viewModel.uiState.first { !it.loading }
        var opened = false
        viewModel.requestCreate { opened = true }
        val locked = viewModel.uiState.first { it.lockedFeature == AppFeature.UnlimitedCustomExercises }
        assertFalse(opened)
        assertEquals(AppFeature.UnlimitedCustomExercises, locked.lockedFeature)
        assertFalse(CustomExerciseAccess.canCreateAnother(5, SelectiveFeatureEntitlements(emptySet())))
        assertTrue(
            CustomExerciseAccess.canCreateAnother(
                5,
                SelectiveFeatureEntitlements(setOf(AppFeature.UnlimitedCustomExercises))
            )
        )
    }

    private fun repository(free: Boolean): ExerciseRepository = repository { free }

    private fun repository(free: () -> Boolean): ExerciseRepository {
        return ExerciseRepository(
            dao = database.exerciseDao(),
            clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC),
            allowCustomCreate = { count ->
                FeatureAccessPolicy(
                    EffectiveEntitlement(
                        tier = if (free()) EntitlementTier.Free else EntitlementTier.Pro,
                        subscriptionValid = !free(),
                        founderLifetime = false,
                        temporaryTesterPro = false
                    )
                ).customExercises(count).canCreate
            }
        )
    }

    private fun draft(name: String): ExerciseDraft {
        return ExerciseDraft(
            name = name,
            primaryMuscle = MuscleGroup.BICEPS
        )
    }
}
