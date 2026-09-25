package hu.laca.weighttracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.data.preferences.ThemePreferences
import hu.laca.weighttracker.data.repository.AppBackupRepository
import hu.laca.weighttracker.data.appbackup.AppBackupError
import hu.laca.weighttracker.data.appbackup.AppBackupRestoreResult
import hu.laca.weighttracker.data.appbackup.AppBackupSource
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.domain.csv.WeightCsv
import hu.laca.weighttracker.domain.theme.AppearanceSettings
import hu.laca.weighttracker.domain.theme.PaletteDraft
import hu.laca.weighttracker.domain.theme.PaletteDraftLogic
import hu.laca.weighttracker.domain.theme.PaletteSession
import hu.laca.weighttracker.domain.theme.PaletteSessionLogic
import hu.laca.weighttracker.domain.theme.PaletteSaveResult
import hu.laca.weighttracker.domain.theme.PaletteType
import hu.laca.weighttracker.domain.theme.SeedField
import hu.laca.weighttracker.domain.theme.ThemeMode
import hu.laca.weighttracker.ui.components.UserMessage
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
    val showRestoreExplanation: Boolean = false,
    val restoreErrors: List<AppBackupError> = emptyList(),
    val userMessage: UserMessage? = null
) {
    val customEditorVisible: Boolean get() = draft != null
}

private data class SettingsChrome(
    val appearance: AppearanceSettings,
    val draft: PaletteDraft?,
    val saveError: PaletteSaveResult?
)

private data class BackupChrome(
    val showRestoreExplanation: Boolean,
    val restoreErrors: List<AppBackupError>
)

class SettingsViewModel(
    private val repository: WeightRepository,
    private val themePreferences: ThemePreferences,
    private val dateProvider: DateProvider,
    private val appBackupRepository: AppBackupRepository
) : ViewModel() {
    private val appearance = MutableStateFlow(AppearanceSettings.Default)
    private val draft = MutableStateFlow<PaletteDraft?>(null)
    private val saveError = MutableStateFlow<PaletteSaveResult?>(null)
    private val showImportExplanation = MutableStateFlow(false)
    private val importErrors = MutableStateFlow<List<WeightCsv.RowError>>(emptyList())
    private val showRestoreExplanation = MutableStateFlow(false)
    private val restoreErrors = MutableStateFlow<List<AppBackupError>>(emptyList())
    private val userMessage = MutableStateFlow<UserMessage?>(null)

    private val chrome = combine(appearance, draft, saveError) { current, draftState, error ->
        SettingsChrome(current, draftState, error)
    }

    private val backupChrome = combine(showRestoreExplanation, restoreErrors) { explanation, errors ->
        BackupChrome(explanation, errors)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        chrome,
        showImportExplanation,
        importErrors,
        userMessage,
        backupChrome
    ) { chromeState, explanation, errors, message, backup ->
        SettingsUiState(
            appearance = chromeState.appearance,
            draft = chromeState.draft,
            saveError = chromeState.saveError,
            showImportExplanation = explanation,
            importErrors = errors,
            showRestoreExplanation = backup.showRestoreExplanation,
            restoreErrors = backup.restoreErrors,
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

    suspend fun buildAppBackupJson(source: AppBackupSource): String {
        return appBackupRepository.exportJson(source)
    }

    fun onAppBackupExportFinished(success: Boolean) {
        userMessage.value = if (success) {
            UserMessage.AppBackupExportSucceeded
        } else {
            UserMessage.AppBackupExportFailed
        }
    }

    fun onRestoreClicked() {
        showRestoreExplanation.value = true
    }

    fun dismissRestoreExplanation() {
        showRestoreExplanation.value = false
    }

    fun confirmRestoreExplanation() {
        showRestoreExplanation.value = false
    }

    fun restoreAppBackup(content: String) {
        restoreParsed { appBackupRepository.restoreJson(content) }
    }

    fun restoreAppBackup(bytes: ByteArray) {
        restoreParsed { appBackupRepository.restoreBytes(bytes) }
    }

    private fun restoreParsed(restore: suspend () -> AppBackupRestoreResult) {
        viewModelScope.launch {
            when (val result = restore()) {
                AppBackupRestoreResult.Success -> {
                    restoreErrors.value = emptyList()
                    userMessage.value = UserMessage.AppBackupRestoreSucceeded
                }
                is AppBackupRestoreResult.Invalid -> {
                    restoreErrors.value = result.errors
                }
            }
        }
    }

    fun onAppBackupReadFailed() {
        userMessage.value = UserMessage.AppBackupReadFailed
    }

    fun dismissRestoreErrors() {
        restoreErrors.value = emptyList()
    }
}
