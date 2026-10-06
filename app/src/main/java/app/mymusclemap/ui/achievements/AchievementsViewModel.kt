package app.mymusclemap.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.AchievementRepository
import app.mymusclemap.domain.achievements.AchievementBoard
import app.mymusclemap.domain.achievements.AchievementBoardAssembler
import app.mymusclemap.domain.achievements.CelebrationAcknowledgement
import app.mymusclemap.domain.achievements.NextWorkoutMilestone
import app.mymusclemap.domain.achievements.PendingCelebration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AchievementsViewModel(
    private val repository: AchievementRepository
) : ViewModel() {
    val board: StateFlow<AchievementBoard> = repository.observeBoard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmptyAchievementBoard)

    val nextMilestone: StateFlow<NextWorkoutMilestone> = board
        .map { it.next }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), board.value.next)

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
