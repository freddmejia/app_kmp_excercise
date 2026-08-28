package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.CreateMuscleGroupUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.CreateMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMuscleGroupsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.RenameMuscleUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SaveMuscleResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MuscleEditorUiState(
    val name: String = "",
    /** The group a new leaf is going into, so the form can say where it will land. */
    val groupName: String = "",
    val isGroup: Boolean = false,
    val isLoading: Boolean = true,
    /** Blank is the only way to be refused here; there is no uniqueness rule. */
    val isNameBlank: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !isLoading
}

/**
 * The one form behind all four ways this catalogue is edited: a new group, a group
 * renamed, a new leaf inside a group, a leaf renamed.
 *
 * They are one screen because they are one field. Four screens — or four dialogs,
 * which is what this replaces — would be four copies of the same text box, differing
 * only in which use case the button reaches.
 *
 * ### Groups and leaves
 * A session always points at a *leaf*, never at a group, so the two are not
 * interchangeable: [isGroup] decides which half of the catalogue is being written, and
 * [groupId] is where a new leaf lands. A leaf cannot be moved between groups from
 * here — that is a different operation, and the entries pointing at it would not
 * follow.
 */
class MuscleEditorViewModel(
    private val groupId: Long,
    private val muscleId: Long,
    private val isGroup: Boolean,
    private val observeGroups: ObserveMuscleGroupsUseCase,
    private val observeMuscles: ObserveMusclesUseCase,
    private val createGroup: CreateMuscleGroupUseCase,
    private val createMuscle: CreateMuscleUseCase,
    private val rename: RenameMuscleUseCase,
) : ViewModel() {

    /** Fired once per save, and never remembered. */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val _state = MutableStateFlow(MuscleEditorUiState(isGroup = isGroup))
    val state: StateFlow<MuscleEditorUiState> = _state.asStateFlow()

    /** Whether this screen is editing something that already exists. */
    private val editing: Boolean get() = if (isGroup) groupId != NEW else muscleId != NEW

    fun onOpened() {
        _state.update { it.copy(isLoading = true, isNameBlank = false) }
        viewModelScope.launch {
            // Archived rows included: a row that can be shown has to be editable, and
            // renaming an archived muscle is how it gets tidied up before it comes
            // back.
            val groups = observeGroups(includeArchived = true).first()
            val name = when {
                !editing -> ""
                isGroup -> groups.firstOrNull { it.id == groupId }?.name.orEmpty()
                else -> observeMuscles(includeArchived = true).first()
                    .firstOrNull { it.id == muscleId }?.name.orEmpty()
            }
            _state.update {
                it.copy(
                    name = name,
                    groupName = groups.firstOrNull { group -> group.id == groupId }?.name.orEmpty(),
                    isGroup = isGroup,
                    isLoading = false,
                )
            }
        }
    }

    fun setName(text: String) = _state.update { it.copy(name = text, isNameBlank = false) }

    fun save() {
        viewModelScope.launch {
            val name = _state.value.name
            val result = when {
                isGroup && !editing -> createGroup(name)
                isGroup -> rename.group(groupId, name)
                !editing -> createMuscle(groupId, name)
                else -> rename(muscleId, name)
            }
            when (result) {
                is SaveMuscleResult.Saved -> _saved.tryEmit(Unit)
                SaveMuscleResult.BlankName -> _state.update { it.copy(isNameBlank = true) }
            }
        }
    }

    companion object {
        /** The id a route carries for something that does not exist yet. */
        const val NEW: Long = 0L
    }
}
