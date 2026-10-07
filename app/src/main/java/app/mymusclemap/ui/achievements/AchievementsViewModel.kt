package app.mymusclemap.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.AchievementRepository
import app.mymusclemap.domain.achievements.AchievementBoard
import app.mymusclemap.domain.achievements.AchievementBoardAssembler
import app.mymusclemap.domain.achievements.AchievementCategory
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.BadgeWallPresentation
import app.mymusclemap.domain.achievements.BadgeWallPresenter
import app.mymusclemap.domain.achievements.CelebrationAcknowledgement
import app.mymusclemap.domain.achievements.NextWorkoutMilestone
import app.mymusclemap.domain.achievements.PendingCelebration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AchievementsViewModel(
    private val repository: AchievementRepository
) : ViewModel() {
    val board: StateFlow<AchievementBoard> = repository.observeBoard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmptyAchievementBoard)

    private val selectedFilter = MutableStateFlow<AchievementCategory?>(null)
    private val openedBadge = MutableStateFlow<AchievementId?>(null)

    val presentation: StateFlow<BadgeWallPresentation> = combine(board, selectedFilter) { current, filter ->
        BadgeWallPresenter.present(current, filter)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BadgeWallPresenter.present(EmptyAchievementBoard)
    )

    val openedBadgeId: StateFlow<AchievementId?> = openedBadge

    val nextMilestone: StateFlow<NextWorkoutMilestone> = board
        .map { it.next }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), board.value.next)

    fun selectFilter(category: AchievementCategory?) {
        selectedFilter.value = category
    }

    fun openBadge(id: AchievementId) {
        openedBadge.value = id
    }

    fun closeBadge() {
        openedBadge.value = null
    }

    fun acknowledge(acknowledgements: List<CelebrationAcknowledgement>) {
        if (acknowledgements.isEmpty()) return
        viewModelScope.launch {
            repository.acknowledge(acknowledgements)
        }
    }

    fun acknowledgeCelebrations(celebrations: List<PendingCelebration>) {
        acknowledge(celebrations.map { it.acknowledgement })
    }
}

private val EmptyAchievementBoard = AchievementBoardAssembler.assemble(
    completedWorkoutCount = 0,
    unlocks = emptyList(),
    events = emptyList()
)
