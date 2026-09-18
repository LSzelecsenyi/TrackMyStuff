package hu.laca.weighttracker.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.FakeWeightMeasurementDao
import hu.laca.weighttracker.MainDispatcherRule
import hu.laca.weighttracker.data.preferences.ThemePreferences
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.domain.FixedDateProvider
import hu.laca.weighttracker.domain.theme.HexColor
import hu.laca.weighttracker.domain.theme.PaletteSaveResult
import hu.laca.weighttracker.domain.theme.PaletteType
import hu.laca.weighttracker.domain.theme.SeedField
import hu.laca.weighttracker.domain.theme.ThemeMode
import hu.laca.weighttracker.domain.theme.ThemeSeeds
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dateProvider = FixedDateProvider(LocalDate.of(2026, 3, 11), LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-03-11T08:00:00Z"), ZoneOffset.UTC)
    private val viewModelStore = ViewModelStore()
    private lateinit var themePreferences: ThemePreferences
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        themePreferences = ThemePreferences(context)
        themePreferences.setThemeMode(ThemeMode.System)
        themePreferences.setPaletteType(PaletteType.Default)
        themePreferences.saveCustomPalette(
            ThemeSeeds.copyOfFactoryLight(),
            ThemeSeeds.copyOfFactoryDark()
        )
        themePreferences.setPaletteType(PaletteType.Default)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    repository = WeightRepository(FakeWeightMeasurementDao(), clock),
                    themePreferences = themePreferences,
                    dateProvider = dateProvider
                ) as T
            }
        }
        viewModel = ViewModelProvider(viewModelStore, factory)[SettingsViewModel::class.java]
        viewModel.uiState.first { it.appearance.paletteType == PaletteType.Default }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    private suspend fun selectCustomAndAwaitDraft() {
        viewModel.selectCustomPalette()
        themePreferences.appearance.first { it.paletteType == PaletteType.Custom }
        viewModel.uiState.first { it.appearance.paletteType == PaletteType.Custom && it.draft != null }
    }

    @Test
    fun unsavedHexUpdatesDraftPreviewButNotPersistedTheme() = runTest {
        selectCustomAndAwaitDraft()
        viewModel.onDraftFieldChange(SeedField.Primary, "#8A2BE2")
        val state = viewModel.uiState.value
        assertEquals("#8A2BE2", state.draft!!.lightPrimary)
        assertEquals("#8A2BE2", HexColor.format(state.draft.lightPreview.primary))
        val persisted = themePreferences.appearance.first { it.paletteType == PaletteType.Custom }
        assertEquals(PaletteType.Custom, persisted.paletteType)
        assertEquals("#2457C5", persisted.customLight.canonical().primary)
        assertEquals("#2457C5", ThemeSeeds.DefaultLight.canonical().primary)
    }

    @Test
    fun cancelWithoutSaveDoesNotPersistDraftColors() = runTest {
        selectCustomAndAwaitDraft()
        viewModel.onDraftFieldChange(SeedField.Primary, "#8A2BE2")
        viewModel.cancelDraft()
        assertEquals("#2457C5", viewModel.uiState.value.draft!!.lightPrimary)
        val persisted = themePreferences.appearance.first { it.paletteType == PaletteType.Custom }
        assertEquals("#2457C5", persisted.customLight.canonical().primary)
    }

    @Test
    fun validSavePersistsOnceAndReloadsSavedValue() = runTest {
        selectCustomAndAwaitDraft()
        viewModel.onDraftFieldChange(SeedField.Primary, "#8A2BE2")
        viewModel.saveDraft()
        viewModel.saveDraft()
        val persisted = themePreferences.appearance.first {
            it.customLight.canonical().primary == "#8A2BE2"
        }
        assertEquals(PaletteType.Custom, persisted.paletteType)
        assertEquals("#8A2BE2", persisted.customLight.canonical().primary)
        assertEquals("#8A2BE2", viewModel.uiState.value.draft!!.lightPrimary)
        assertNull(viewModel.uiState.value.saveError)
    }

    @Test
    fun invalidHexKeepsSaveErrorAndDoesNotPersist() = runTest {
        selectCustomAndAwaitDraft()
        viewModel.onDraftFieldChange(SeedField.Primary, "#ZZZZZZ")
        viewModel.saveDraft()
        assertEquals(PaletteSaveResult.InvalidHex, viewModel.uiState.value.saveError)
        val persisted = themePreferences.appearance.first { it.paletteType == PaletteType.Custom }
        assertEquals("#2457C5", persisted.customLight.canonical().primary)
    }

    @Test
    fun lightAndDarkDraftsSurviveEditorSwitch() = runTest {
        selectCustomAndAwaitDraft()
        viewModel.onDraftFieldChange(SeedField.Primary, "#8A2BE2")
        viewModel.setEditingDark(true)
        assertTrue(viewModel.uiState.value.draft!!.editingDark)
        viewModel.onDraftFieldChange(SeedField.Primary, "#BA81EE")
        viewModel.setEditingDark(false)
        val draft = viewModel.uiState.value.draft!!
        assertFalse(draft.editingDark)
        assertEquals("#8A2BE2", draft.lightPrimary)
        assertEquals("#BA81EE", draft.darkPrimary)
    }
}
