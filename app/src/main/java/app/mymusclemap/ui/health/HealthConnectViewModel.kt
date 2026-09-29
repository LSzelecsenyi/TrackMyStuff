package app.mymusclemap.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.health.HealthRepository
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.health.HealthCardState
import app.mymusclemap.domain.health.HealthDetailPresentation
import app.mymusclemap.domain.health.HealthDetailState
import app.mymusclemap.domain.health.HealthPresentation
import app.mymusclemap.domain.health.HealthSettingsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HealthConnectViewModel(
    private val repository: HealthRepository,
    dateProvider: DateProvider
) : ViewModel() {
    val card: StateFlow<HealthCardState> = combine(
        repository.access,
        repository.readings,
        dateProvider.observeToday()
    ) { access, readings, today ->
        HealthPresentation.card(access, readings, today)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HealthCardState.Checking)

    val detail: StateFlow<HealthDetailState> = combine(
        repository.access,
        repository.readings,
        dateProvider.observeToday()
    ) { access, readings, today ->
        HealthDetailPresentation.detail(access, readings, today)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HealthDetailState.Checking)

    val settings: StateFlow<HealthSettingsState> = repository.access
        .map { HealthPresentation.settings(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            HealthPresentation.settings(repository.access.value)
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refresh()
        }
    }
}
