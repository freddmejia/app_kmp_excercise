package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.SessionDetail
import botix.dev.myweeklyroutinegymapp.domain.model.SessionTotals
import botix.dev.myweeklyroutinegymapp.domain.model.UntrainedMuscle
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ActivityFilter
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveActivitiesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogSetsResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclePickerUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDetailUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveUntrainedMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.RemoveMuscleFromSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartOrResumeSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartSessionResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WorkoutMessage {
    data object Logged : WorkoutMessage
    data object Removed : WorkoutMessage

    /** Effective sets below one; the muscle is effectively unchecked. */
    data object InvalidSets : WorkoutMessage

    data object ActivityNotFound : WorkoutMessage

    /** This form only fits STRENGTH activities; another type needs its own screen. */
    data object NotAStrengthActivity : WorkoutMessage
}

data class WorkoutLogUiState(
    val sessionId: Long? = null,
    /** Only STRENGTH activities: the other types do not use this form. */
    val strengthActivities: List<Activity> = emptyList(),
    val selectedActivityId: Long? = null,
    val sections: List<MuscleSection> = emptyList(),
    val detail: SessionDetail? = null,
    val untrainedThisWeek: List<UntrainedMuscle> = emptyList(),
    val isLoading: Boolean = true,
    val message: WorkoutMessage? = null,
) {
    val totals: SessionTotals get() = detail?.totals ?: SessionTotals(0, 0)

    /** Which muscles are currently checked — what the picker renders its ticks from. */
    val checkedMuscleIds: Set<Long>
        get() = detail?.entries?.map { it.muscleId }?.toSet().orEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLogViewModel(
    private val startOrResumeSession: StartOrResumeSessionUseCase,
    private val logMuscleSets: LogMuscleSetsUseCase,
    private val setMuscleSets: SetMuscleSetsUseCase,
    private val removeMuscle: RemoveMuscleFromSessionUseCase,
    private val observeSessionDetail: ObserveSessionDetailUseCase,
    private val observeMusclePicker: ObserveMusclePickerUseCase,
    private val observeUntrained: ObserveUntrainedMusclesUseCase,
    private val observeActivities: ObserveActivitiesUseCase,
) : ViewModel() {

    private val sessionId = MutableStateFlow<Long?>(null)
    private val selectedActivityId = MutableStateFlow<Long?>(null)
    private val message = MutableStateFlow<WorkoutMessage?>(null)

    val state: StateFlow<WorkoutLogUiState> = combine(
        sessionId,
        message,
        observeMusclePicker(),
        // No session open yet means no detail to observe — emit null rather than
        // querying for a row that cannot exist.
        sessionId.flatMapLatest { id -> if (id == null) flowOf(null) else observeSessionDetail(id) },
        observeUntrained(),
    ) { id, currentMessage, sections, detail, untrained ->
        WorkoutLogUiState(
            sessionId = id,
            sections = sections,
            detail = detail,
            untrainedThisWeek = untrained,
            isLoading = false,
            message = currentMessage,
        )
    }.combine(observeActivities(ActivityFilter.ACTIVE)) { partial, activities ->
        // combine() tops out at five flows, so the activity list folds in as a
        // second step rather than forcing the rest into a nested shape.
        partial.copy(
            strengthActivities = activities.filter { it.measurementType == MeasurementType.STRENGTH },
        )
    }.combine(selectedActivityId) { partial, activityId ->
        partial.copy(selectedActivityId = activityId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutLogUiState(),
    )

    fun consumeMessage() {
        message.value = null
    }

    /**
     * Opens today's session for an activity, or resumes it if the day was already
     * started. Rejects non-strength activities before any row is written.
     */
    fun openSession(activityId: Long, epochDay: Long? = null) {
        viewModelScope.launch {
            val result = if (epochDay == null) {
                startOrResumeSession(activityId)
            } else {
                startOrResumeSession(activityId, epochDay)
            }
            when (result) {
                is StartSessionResult.Started -> {
                    selectedActivityId.value = activityId
                    sessionId.value = result.sessionId
                }
                StartSessionResult.ActivityNotFound -> message.value = WorkoutMessage.ActivityNotFound
                StartSessionResult.NotAStrengthActivity ->
                    message.value = WorkoutMessage.NotAStrengthActivity
            }
        }
    }

    /** Checking a muscle, or adding to it if it is already checked. */
    fun addSets(muscleId: Long, effectiveSets: Long, warmupSets: Long = 0) {
        val id = sessionId.value ?: return
        viewModelScope.launch {
            message.value = logMuscleSets(id, muscleId, effectiveSets, warmupSets).toMessage()
        }
    }

    /** Correcting an entry to exact values rather than adding to it. */
    fun setSets(muscleId: Long, effectiveSets: Long, warmupSets: Long = 0) {
        val id = sessionId.value ?: return
        viewModelScope.launch {
            message.value = setMuscleSets(id, muscleId, effectiveSets, warmupSets).toMessage()
        }
    }

    /** Leaves the session, returning the screen to the activity picker. */
    fun closeSession() {
        sessionId.value = null
        selectedActivityId.value = null
    }

    fun uncheck(muscleId: Long) {
        val id = sessionId.value ?: return
        viewModelScope.launch {
            removeMuscle(id, muscleId)
            message.value = WorkoutMessage.Removed
        }
    }

    private fun LogSetsResult.toMessage(): WorkoutMessage = when (this) {
        LogSetsResult.Logged -> WorkoutMessage.Logged
        LogSetsResult.InvalidSets -> WorkoutMessage.InvalidSets
    }
}
