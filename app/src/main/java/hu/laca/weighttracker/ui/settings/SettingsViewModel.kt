package hu.laca.weighttracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.data.preferences.ThemePreference
import hu.laca.weighttracker.data.preferences.ThemePreferences
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.domain.csv.WeightCsv
import hu.laca.weighttracker.ui.components.UserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: ThemePreference = ThemePreference.System,
    val showImportExplanation: Boolean = false,
    val importErrors: List<WeightCsv.RowError> = emptyList(),
    val userMessage: UserMessage? = null
)

class SettingsViewModel(
    private val repository: WeightRepository,
    private val themePreferences: ThemePreferences,
    private val dateProvider: DateProvider
) : ViewModel() {
    private val showImportExplanation = MutableStateFlow(false)
    private val importErrors = MutableStateFlow<List<WeightCsv.RowError>>(emptyList())
    private val userMessage = MutableStateFlow<UserMessage?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        themePreferences.theme,
        showImportExplanation,
        importErrors,
        userMessage
    ) { theme, explanation, errors, message ->
        SettingsUiState(
            theme = theme,
            showImportExplanation = explanation,
            importErrors = errors,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState()
    )

    fun setTheme(preference: ThemePreference) {
        viewModelScope.launch {
            themePreferences.setTheme(preference)
        }
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
