package app.mymusclemap.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.csv.WeightCsv
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.theme.PaletteDraft
import app.mymusclemap.domain.theme.PaletteDraftLogic
import app.mymusclemap.domain.theme.PaletteSession
import app.mymusclemap.domain.theme.PaletteSessionLogic
import app.mymusclemap.domain.theme.PaletteSaveResult
import app.mymusclemap.domain.theme.PaletteType
import app.mymusclemap.domain.theme.SeedField
import app.mymusclemap.domain.theme.ThemeMode
import app.mymusclemap.ui.components.UserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val appearance: AppearanceSettings = AppearanceSettings.Default,
    val draft: PaletteDraft? = null,
    val saveError: PaletteSaveResult? = null,
    val showImportExplanation: Boolean = false,
    val importErrors: List<WeightCsv.RowError> = emptyList(),
    val userMessage: UserMessage? = null
) {
    val customEditorVisible: Boolean get() = draft != null
}

private data class SettingsChrome(
    val appearance: AppearanceSettings,
    val draft: PaletteDraft?,
    val saveError: PaletteSaveResult?
)

class SettingsViewModel(
    private val repository: WeightRepository,
    private val themePreferences: ThemePreferences,
    private val dateProvider: DateProvider
) : ViewModel() {
    private val appearance = MutableStateFlow(AppearanceSettings.Default)
    private val draft = MutableStateFlow<PaletteDraft?>(null)
    private val saveError = MutableStateFlow<PaletteSaveResult?>(null)
    private val showImportExplanation = MutableStateFlow(false)
    private val importErrors = MutableStateFlow<List<WeightCsv.RowError>>(emptyList())
    private val userMessage = MutableStateFlow<UserMessage?>(null)

    private val chrome = combine(appearance, draft, saveError) { current, draftState, error ->
        SettingsChrome(current, draftState, error)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        chrome,
        showImportExplanation,
        importErrors,
        userMessage
    ) { chromeState, explanation, errors, message ->
        SettingsUiState(
            appearance = chromeState.appearance,
            draft = chromeState.draft,
            saveError = chromeState.saveError,
            showImportExplanation = explanation,
            importErrors = errors,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState()
    )

    init {
        viewModelScope.launch {
            themePreferences.appearance.collect { settings ->
                appearance.value = settings
                if (settings.paletteType == PaletteType.Default) {
                    draft.value = null
                } else if (draft.value == null) {
                    draft.value = PaletteDraftLogic.fromSeeds(
                        settings.customLight,
                        settings.customDark
                    )
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun selectDefaultPalette() {
        val next = PaletteSessionLogic.selectDefault(PaletteSession(appearance.value, draft.value))
        appearance.value = next.appearance
        draft.value = next.draft
        saveError.value = null
        viewModelScope.launch {
            themePreferences.setPaletteType(PaletteType.Default)
        }
    }

    fun selectCustomPalette() {
        val next = PaletteSessionLogic.selectCustom(PaletteSession(appearance.value, draft.value))
        appearance.value = next.appearance
        draft.value = next.draft
        saveError.value = null
        viewModelScope.launch {
            themePreferences.setPaletteType(PaletteType.Custom)
        }
    }

    fun setEditingDark(editingDark: Boolean) {
        draft.value = draft.value?.copy(editingDark = editingDark)
    }

    fun onDraftFieldChange(field: SeedField, raw: String) {
        val current = draft.value ?: return
        draft.value = PaletteDraftLogic.updateField(current, field, raw)
        saveError.value = null
    }

    fun onDraftColorPicked(field: SeedField, rgb: Int) {
        val current = draft.value ?: return
        draft.value = PaletteDraftLogic.applyParsedColor(current, field, rgb)
        saveError.value = null
    }

    fun generateDarkFromLight() {
        val current = draft.value ?: return
        draft.value = PaletteDraftLogic.generateDark(current)
        saveError.value = null
    }

    fun cancelDraft() {
        val next = PaletteSessionLogic.cancelDraft(PaletteSession(appearance.value, draft.value))
        draft.value = next.draft
        saveError.value = null
    }

    fun saveDraft() {
        val current = draft.value ?: return
        when (val result = PaletteDraftLogic.validateForSave(current)) {
            is PaletteSaveResult.Success -> {
                val next = PaletteSessionLogic.saveCustom(
                    PaletteSession(appearance.value, current),
                    result.light,
                    result.dark
                )
                appearance.value = next.appearance
                draft.value = next.draft
                saveError.value = null
                viewModelScope.launch {
                    themePreferences.saveCustomPalette(result.light, result.dark)
                    userMessage.value = UserMessage.PaletteSaved
                }
            }
            PaletteSaveResult.InvalidHex,
            is PaletteSaveResult.Contrast -> {
                saveError.value = result
            }
        }
    }

    fun resetCustomDraft() {
        val next = PaletteSessionLogic.resetCustomDraft(PaletteSession(appearance.value, draft.value))
        draft.value = next.draft
        saveError.value = null
    }

    suspend fun buildExportCsv(): String {
        return WeightCsv.export(repository.observeAll().first())
    }

    fun onExportFinished(success: Boolean) {
        userMessage.value = if (success) UserMessage.ExportSucceeded else UserMessage.ExportFailed
    }

    fun onImportClicked() {
        showImportExplanation.value = true
    }

    fun dismissImportExplanation() {
        showImportExplanation.value = false
    }

    fun confirmImportExplanation() {
        showImportExplanation.value = false
    }

    fun importCsv(content: String) {
        viewModelScope.launch {
            when (val parsed = WeightCsv.parse(content, dateProvider.today())) {
                is WeightCsv.ParseResult.Failure -> {
                    importErrors.value = parsed.errors
                }
                is WeightCsv.ParseResult.Success -> {
                    val summary = repository.importRows(parsed.rows)
                    userMessage.value = UserMessage.ImportSucceeded(
                        created = summary.createdCount,
                        updated = summary.updatedCount
                    )
                }
            }
        }
    }

    fun onImportReadFailed() {
        userMessage.value = UserMessage.ImportReadFailed
    }

    fun dismissImportErrors() {
        importErrors.value = emptyList()
    }

    fun consumeMessage() {
        userMessage.value = null
    }
}
