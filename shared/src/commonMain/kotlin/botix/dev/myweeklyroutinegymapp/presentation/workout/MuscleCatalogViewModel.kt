package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.model.DeleteOutcome
import botix.dev.myweeklyroutinegymapp.domain.model.Muscle
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleGroup
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ArchiveMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.CreateMuscleGroupUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.CreateMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.DeleteMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMuscleGroupsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SaveMuscleResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MuscleCatalogMessage {
    data object Saved : MuscleCatalogMessage
    data object BlankName : MuscleCatalogMessage
    data object Deleted : MuscleCatalogMessage

    /** Archive instead — [referenceCount] sessions would lose their volume. */
    data class DeleteBlocked(val referenceCount: Long) : MuscleCatalogMessage
}

data class MuscleCatalogUiState(
    val groups: List<MuscleGroup> = emptyList(),
    val musclesByGroup: Map<Long, List<Muscle>> = emptyMap(),
    val isLoading: Boolean = true,
    val message: MuscleCatalogMessage? = null,
) {
    /** True on a fresh install: nothing can be logged until a group has a leaf. */
    val isEmpty: Boolean get() = groups.isEmpty()
}

/**
 * Managing the muscle hierarchy.
 *
 * Separate from [WorkoutLogViewModel] because the two have different lifetimes: the
 * log screen holds an open session, this one is a catalogue editor that should not
 * keep a session alive behind it.
 */
class MuscleCatalogViewModel(
    private val observeGroups: ObserveMuscleGroupsUseCase,
    private val observeMuscles: ObserveMusclesUseCase,
    private val createGroup: CreateMuscleGroupUseCase,
    private val createMuscle: CreateMuscleUseCase,
    private val archiveMuscle: ArchiveMuscleUseCase,
    private val deleteMuscle: DeleteMuscleUseCase,
) : ViewModel() {

    private val message = MutableStateFlow<MuscleCatalogMessage?>(null)

    val state: StateFlow<MuscleCatalogUiState> = combine(
        observeGroups(),
        observeMuscles(),
        message,
    ) { groups, muscles, currentMessage ->
        MuscleCatalogUiState(
            groups = groups,
            musclesByGroup = muscles.groupBy { it.groupId },
            isLoading = false,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MuscleCatalogUiState(),
    )

    fun consumeMessage() {
        message.value = null
    }

    fun addGroup(name: String) {
        viewModelScope.launch { message.value = createGroup(name).toMessage() }
    }

    fun addMuscle(groupId: Long, name: String) {
        viewModelScope.launch { message.value = createMuscle(groupId, name).toMessage() }
    }

    fun setMuscleArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { archiveMuscle(id, archived) }
    }

    fun removeMuscle(id: Long) {
        viewModelScope.launch { message.value = deleteMuscle(id).toMessage() }
    }

    fun removeGroup(id: Long) {
        viewModelScope.launch { message.value = deleteMuscle.group(id).toMessage() }
    }

    private fun SaveMuscleResult.toMessage(): MuscleCatalogMessage = when (this) {
        is SaveMuscleResult.Saved -> MuscleCatalogMessage.Saved
        SaveMuscleResult.BlankName -> MuscleCatalogMessage.BlankName
    }

    private fun DeleteOutcome.toMessage(): MuscleCatalogMessage = when (this) {
        DeleteOutcome.Deleted -> MuscleCatalogMessage.Deleted
        is DeleteOutcome.BlockedByHistory -> MuscleCatalogMessage.DeleteBlocked(referenceCount)
    }
}
