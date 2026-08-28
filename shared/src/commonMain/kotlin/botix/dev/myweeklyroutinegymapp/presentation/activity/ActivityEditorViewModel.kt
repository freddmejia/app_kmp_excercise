package botix.dev.myweeklyroutinegymapp.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementTypeChange
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ChangeMeasurementTypeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.CreateActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveCategoriesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.SaveActivityResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.UpdateActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityMuscleLinksUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclePickerUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetActivityMusclesUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why an activity was not saved. Reported on the screen that caused it. */
sealed interface ActivityEditorError {
    data object NameBlank : ActivityEditorError

    /** Another activity already holds this name. */
    data object NameTaken : ActivityEditorError

    /**
     * The measurement type is fixed now that sessions exist under it.
     *
     * Nothing else was saved either: the type is attempted first, so a refusal leaves
     * the activity exactly as it was rather than half-updated.
     */
    data class TypeLocked(val sessionCount: Long) : ActivityEditorError
}

data class ActivityEditorUiState(
    val name: String = "",
    val icon: String = "",
    val type: MeasurementType = MeasurementType.STRENGTH,
    val categoryId: Long? = null,
    val muscleIds: Set<Long> = emptySet(),
    /** The catalogue this form picks from, live: a category added elsewhere shows up. */
    val categories: List<ActivityCategory> = emptyList(),
    val muscleSections: List<MuscleSection> = emptyList(),
    val isLoading: Boolean = true,
    val error: ActivityEditorError? = null,
) {
    /** Inert until there is a name; a *wrong* name is refused and explained. */
    val canSave: Boolean get() = name.isNotBlank() && !isLoading

    /**
     * Muscles are offered for STRENGTH only.
     *
     * The link exists to scope the strength log picker, and no other type opens it —
     * an always-visible control that silently does nothing is worse than an absent one.
     */
    val showsMuscles: Boolean get() = type == MeasurementType.STRENGTH
}

/**
 * Creating an activity, or editing one.
 *
 * [activityId] is [NEW] for one that does not exist yet. It replaces three dialogs:
 * the creation form, the category picker and the muscle checklist, which between them
 * meant that the *same* four fields were edited in three places, in two of which you
 * could not see the others.
 */
class ActivityEditorViewModel(
    private val activityId: Long,
    private val observeActivity: ObserveActivityUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeMusclePicker: ObserveMusclePickerUseCase,
    private val observeLinks: ObserveActivityMuscleLinksUseCase,
    private val createActivity: CreateActivityUseCase,
    private val updateActivity: UpdateActivityUseCase,
    private val changeType: ChangeMeasurementTypeUseCase,
    private val setMuscles: SetActivityMusclesUseCase,
) : ViewModel() {

    /** Fired once per save, and never remembered. */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    /** The form as typed. Everything else on the state is read from the database. */
    private val form = MutableStateFlow(ActivityEditorUiState())

    /** What the type was when the form opened, so a save knows whether to touch it. */
    private var loadedType: MeasurementType? = null

    val state: StateFlow<ActivityEditorUiState> = combine(
        form,
        observeCategories(),
        observeMusclePicker(),
    ) { current, categories, sections ->
        current.copy(categories = categories, muscleSections = sections)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityEditorUiState(),
    )

    /** Called every time the screen appears, so a second visit starts from what is stored. */
    fun onOpened() {
        form.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val existing = if (activityId == NEW) null else observeActivity(activityId).first()
            val links = if (activityId == NEW) {
                emptySet()
            } else {
                observeLinks().first()[activityId].orEmpty()
            }
            loadedType = existing?.measurementType
            form.update {
                it.copy(
                    name = existing?.name.orEmpty(),
                    icon = existing?.icon.orEmpty(),
                    type = existing?.measurementType ?: MeasurementType.STRENGTH,
                    categoryId = existing?.categoryId,
                    muscleIds = links,
                    isLoading = false,
                )
            }
        }
    }

    fun setName(text: String) = form.update { it.copy(name = text, error = null) }

    fun setIcon(text: String) = form.update { it.copy(icon = text, error = null) }

    /**
     * Switching type keeps whatever muscles were ticked rather than clearing them, so
     * flipping back and forth by accident does not silently undo the selection. What
     * is *saved* still follows the type — see [save].
     */
    fun setType(value: MeasurementType) = form.update { it.copy(type = value, error = null) }

    fun setCategory(id: Long?) = form.update { it.copy(categoryId = id) }

    fun toggleMuscle(id: Long) = form.update {
        it.copy(muscleIds = if (id in it.muscleIds) it.muscleIds - id else it.muscleIds + id)
    }

    fun toggleGroup(ids: Set<Long>, selectAll: Boolean) = form.update {
        it.copy(muscleIds = if (selectAll) it.muscleIds + ids else it.muscleIds - ids)
    }

    fun save() {
        viewModelScope.launch {
            val current = form.value
            val icon = current.icon.trim().takeIf { it.isNotBlank() }
            // Dropped for non-strength types, so a selection made and then switched
            // away from cannot be saved invisibly.
            val muscles = if (current.showsMuscles) current.muscleIds else emptySet()

            if (activityId == NEW) {
                when (val result = createActivity(
                    name = current.name,
                    measurementType = current.type,
                    icon = icon,
                    categoryId = current.categoryId,
                    muscleIds = muscles,
                )) {
                    is SaveActivityResult.Saved -> _saved.tryEmit(Unit)
                    SaveActivityResult.BlankName -> fail(ActivityEditorError.NameBlank)
                    SaveActivityResult.NameTaken -> fail(ActivityEditorError.NameTaken)
                }
                return@launch
            }

            // The type first, and nothing else if it is refused. Saving the rest and
            // then reporting the refusal would leave the user unable to tell what had
            // gone through — a rename that took effect and a type change that did not
            // look the same from the outside.
            if (loadedType != null && current.type != loadedType) {
                val change = changeType(activityId, current.type)
                if (change is MeasurementTypeChange.BlockedByHistory) {
                    fail(ActivityEditorError.TypeLocked(change.sessionCount))
                    return@launch
                }
            }

            when (val result = updateActivity(
                id = activityId,
                name = current.name,
                icon = icon,
                categoryId = current.categoryId,
            )) {
                is SaveActivityResult.Saved -> {
                    // Written second, and separately: the links are an optional
                    // default for the log picker, not part of the activity row.
                    setMuscles(activityId, muscles)
                    _saved.tryEmit(Unit)
                }

                SaveActivityResult.BlankName -> fail(ActivityEditorError.NameBlank)
                SaveActivityResult.NameTaken -> fail(ActivityEditorError.NameTaken)
            }
        }
    }

    private fun fail(error: ActivityEditorError) = form.update { it.copy(error = error) }

    companion object {
        /** The id a route carries when it is creating rather than editing. */
        const val NEW: Long = 0L
    }
}
