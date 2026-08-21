package botix.dev.myweeklyroutinegymapp.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.DeleteOutcome
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementTypeChange
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ActivityFilter
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ArchiveActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ChangeMeasurementTypeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.CreateActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.DeleteActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveActivitiesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.SaveActivityResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.UpdateActivityUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One-shot feedback for the catalogue screen.
 *
 * Carries the counts the domain returned rather than a formatted string, so the
 * wording — and its translation — stays in the UI layer.
 */
sealed interface ActivityMessage {
    data object NameBlank : ActivityMessage
    data object NameTaken : ActivityMessage
    data object Saved : ActivityMessage
    data object Deleted : ActivityMessage

    /** Offer archiving instead; deletion would break [sessionCount] sessions. */
    data class DeleteBlocked(val sessionCount: Long) : ActivityMessage

    /** The type is fixed now that sessions exist under it. */
    data class MeasurementTypeLocked(val sessionCount: Long) : ActivityMessage
}

data class ActivityListUiState(
    val activities: List<Activity> = emptyList(),
    val filter: ActivityFilter = ActivityFilter.ACTIVE,
    val isLoading: Boolean = true,
    val message: ActivityMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityListViewModel(
    private val observeActivities: ObserveActivitiesUseCase,
    private val createActivity: CreateActivityUseCase,
    private val updateActivity: UpdateActivityUseCase,
    private val archiveActivity: ArchiveActivityUseCase,
    private val deleteActivity: DeleteActivityUseCase,
    private val changeMeasurementType: ChangeMeasurementTypeUseCase,
) : ViewModel() {

    private val filter = MutableStateFlow(ActivityFilter.ACTIVE)
    private val message = MutableStateFlow<ActivityMessage?>(null)

    val state: StateFlow<ActivityListUiState> = combine(
        filter,
        message,
        // flatMapLatest, so switching filter cancels the previous query rather than
        // leaving two collectors racing to set the list.
        filter.flatMapLatest { observeActivities(it) },
    ) { currentFilter, currentMessage, activities ->
        ActivityListUiState(
            activities = activities,
            filter = currentFilter,
            isLoading = false,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        // Keeps the database query alive briefly across a config change, so a
        // rotation does not re-query and flash an empty list.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityListUiState(),
    )

    fun setFilter(value: ActivityFilter) {
        filter.value = value
    }

    /** Call after showing [ActivityListUiState.message] so it is not shown twice. */
    fun consumeMessage() {
        message.value = null
    }

    fun create(name: String, measurementType: MeasurementType, icon: String? = null, color: String? = null) {
        viewModelScope.launch {
            message.value = createActivity(name, measurementType, icon, color).toMessage()
        }
    }

    fun rename(id: Long, name: String, icon: String? = null, color: String? = null) {
        viewModelScope.launch {
            message.value = updateActivity(id, name, icon, color).toMessage()
        }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { archiveActivity(id, archived) }
    }

    /**
     * Attempts a hard delete. When history blocks it the screen is expected to offer
     * archiving — that is the documented alternative, not an error to swallow.
     */
    fun delete(id: Long) {
        viewModelScope.launch {
            message.value = when (val outcome = deleteActivity(id)) {
                is DeleteOutcome.Deleted -> ActivityMessage.Deleted
                is DeleteOutcome.BlockedByHistory -> ActivityMessage.DeleteBlocked(outcome.referenceCount)
            }
        }
    }

    fun changeType(id: Long, measurementType: MeasurementType) {
        viewModelScope.launch {
            message.value = when (val outcome = changeMeasurementType(id, measurementType)) {
                is MeasurementTypeChange.Changed -> ActivityMessage.Saved
                is MeasurementTypeChange.BlockedByHistory ->
                    ActivityMessage.MeasurementTypeLocked(outcome.sessionCount)
            }
        }
    }

    private fun SaveActivityResult.toMessage(): ActivityMessage = when (this) {
        is SaveActivityResult.Saved -> ActivityMessage.Saved
        SaveActivityResult.BlankName -> ActivityMessage.NameBlank
        SaveActivityResult.NameTaken -> ActivityMessage.NameTaken
    }
}
