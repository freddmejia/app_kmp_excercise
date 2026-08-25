package botix.dev.myweeklyroutinegymapp.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityVolume
import botix.dev.myweeklyroutinegymapp.domain.model.CategorySelection
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.DeleteOutcome
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementTypeChange
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ActivityFilter
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ArchiveActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.AssignActivityCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ChangeMeasurementTypeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.CreateActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.DeleteActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveActivitiesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveCategoriesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.SaveActivityResult
import botix.dev.myweeklyroutinegymapp.presentation.common.WEEK
import botix.dev.myweeklyroutinegymapp.presentation.common.weekOf
import botix.dev.myweeklyroutinegymapp.presentation.common.weekStartOf
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.UpdateActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityMuscleLinksUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityVolumeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDaysUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclePickerUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetActivityMusclesUseCase
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
    /** Active categories, for the filter row, the create form and the row badges. */
    val categories: List<ActivityCategory> = emptyList(),
    val categorySelection: CategorySelection = CategorySelection.All,
    /**
     * The muscle catalogue, for the selection dialogs. Kept on the list state rather
     * than fetched when a dialog opens: the creation form needs it before any
     * activity exists, so there is no row to hang a lazy subscription off.
     */
    val muscleSections: List<MuscleSection> = emptyList(),
    /** Linked muscle ids per activity; absent means none, which reads as "no opinion". */
    val musclesByActivity: Map<Long, Set<Long>> = emptyMap(),
    val selectedDay: Long = 0,
    val today: Long = 0,
    /** Days of the visible week that have a session, for the strip's markers. */
    val daysWithSessions: Set<Long> = emptyList<Long>().toSet(),
    /** Per-activity totals for [selectedDay] alone; absent means not trained that day. */
    val dayVolume: Map<Long, ActivityVolume> = emptyMap(),
    val isLoading: Boolean = true,
    val message: ActivityMessage? = null,
) {
    val visibleDays: List<Long> get() = weekOf(selectedDay)

    val isToday: Boolean get() = selectedDay == today

    /**
     * The selected day splits the list rather than truncating it.
     *
     * It follows the *day*, not the week the day sits in. A strip you can tap seven
     * cells of, that answers all seven identically, is not a filter — it was showing
     * "Trained 24–30 Aug" whichever cell you pressed, so the only way to ask "what did
     * I do on Wednesday" was to read the log screen instead.
     *
     * This screen is also where activities are created, archived and deleted, so
     * nothing may become unreachable because of the day on screen — an activity made
     * seconds ago has trained nothing and must still be editable. Showing both halves
     * keeps the day meaningful without the catalogue losing rows.
     */
    val trainedOnDay: List<Activity> get() = activities.filter { it.id in dayVolume }

    val untrainedOnDay: List<Activity> get() = activities.filterNot { it.id in dayVolume }
    /** Name lookup for the row badge; a category deleted mid-scroll simply drops out. */
    val categoryNamesById: Map<Long, String> get() = categories.associate { it.id to it.name }
}



/**
 * The day-scoped reads, kept together so they can never describe different days.
 *
 * [markers] is the exception and is deliberately week-wide: it paints the dots on the
 * strip, which have to say which *other* days hold something — a marker set narrowed
 * to the selected day would only ever mark the cell you are already on.
 */
private data class DayState(
    val day: Long,
    val volume: Map<Long, ActivityVolume>,
    val markers: Set<Long>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityListViewModel(
    private val observeActivities: ObserveActivitiesUseCase,
    private val createActivity: CreateActivityUseCase,
    private val updateActivity: UpdateActivityUseCase,
    private val archiveActivity: ArchiveActivityUseCase,
    private val deleteActivity: DeleteActivityUseCase,
    private val changeMeasurementType: ChangeMeasurementTypeUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
    private val assignCategory: AssignActivityCategoryUseCase,
    private val observeMusclePicker: ObserveMusclePickerUseCase,
    private val observeMuscleLinks: ObserveActivityMuscleLinksUseCase,
    private val setActivityMuscles: SetActivityMusclesUseCase,
    private val observeActivityVolume: ObserveActivityVolumeUseCase,
    private val observeSessionDays: ObserveSessionDaysUseCase,
    clock: Clock,
) : ViewModel() {

    /**
     * Resolved once, not per emission: a value that changed mid-session would move
     * the strip under the user's finger.
     */
    private val today = clock.today()
    private val selectedDay = MutableStateFlow(today)

    /** The day's reads, resolved together so they can never describe different days. */
    private val dayState = selectedDay.flatMapLatest { day ->
        val start = weekStartOf(day)
        combine(
            // One day, both ends — this is what makes the strip a filter. The
            // markers below stay week-wide; they are the strip's own dots.
            observeActivityVolume(day, day),
            observeSessionDays(start, start + WEEK - 1),
        ) { volume, markers ->
            DayState(day, volume.associateBy { it.activityId }, markers)
        }
    }

    private val filter = MutableStateFlow(ActivityFilter.ACTIVE)
    private val categorySelection = MutableStateFlow<CategorySelection>(CategorySelection.All)
    private val message = MutableStateFlow<ActivityMessage?>(null)

    /**
     * flatMapLatest over *both* filters, so changing either cancels the previous
     * query rather than leaving two collectors racing to set the list.
     */
    private val activities = combine(filter, categorySelection, ::Pair)
        .flatMapLatest { (currentFilter, currentCategory) ->
            observeActivities(currentFilter, currentCategory)
        }

    val state: StateFlow<ActivityListUiState> = combine(
        filter,
        categorySelection,
        message,
        activities,
        // The category list is its own observable source: creating one on the
        // category screen makes it appear in this filter row without a refresh.
        observeCategories(),
    ) { currentFilter, currentCategory, currentMessage, currentActivities, categories ->
        ActivityListUiState(
            activities = currentActivities,
            filter = currentFilter,
            categories = categories,
            categorySelection = currentCategory,
            isLoading = false,
            message = currentMessage,
        )
    }.combine(observeMusclePicker()) { partial, sections ->
        // combine() tops out at five flows, so the muscle data folds in as two
        // further steps rather than forcing the rest into a nested shape.
        partial.copy(muscleSections = sections)
    }.combine(observeMuscleLinks()) { partial, links ->
        partial.copy(musclesByActivity = links)
    }.combine(dayState) { partial, day ->
        // The split happens in the state's own getters rather than here: the activity
        // list and the day's volume come from different tables, and absence from the
        // volume map *is* "not trained", so it is a set membership test on read.
        partial.copy(
            selectedDay = day.day,
            today = today,
            daysWithSessions = day.markers,
            dayVolume = day.volume,
        )
    }.stateIn(
        scope = viewModelScope,
        // Keeps the database query alive briefly across a config change, so a
        // rotation does not re-query and flash an empty list.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityListUiState(selectedDay = today, today = today),
    )

    /** An empty set is meaningful: it clears the link and restores "no opinion". */
    fun setMuscles(activityId: Long, muscleIds: Set<Long>) {
        viewModelScope.launch { setActivityMuscles(activityId, muscleIds) }
    }

    fun setFilter(value: ActivityFilter) {
        filter.value = value
    }

    fun setCategorySelection(value: CategorySelection) {
        categorySelection.value = value
    }

    /**
     * Selects a day. Within the visible week this does not move the strip — the week
     * is derived from the selection, so a day in the same week yields the same week.
     */
    fun selectDay(day: Long) {
        selectedDay.value = day
    }

    /** The week arrows: seven days over, keeping the weekday column. */
    fun stepWeek(forward: Boolean) = selectDay(selectedDay.value + if (forward) WEEK else -WEEK)

    fun goToToday() = selectDay(today)

    /** Call after showing [ActivityListUiState.message] so it is not shown twice. */
    fun consumeMessage() {
        message.value = null
    }

    fun create(
        name: String,
        measurementType: MeasurementType,
        icon: String? = null,
        color: String? = null,
        categoryId: Long? = null,
        muscleIds: Set<Long> = emptySet(),
    ) {
        viewModelScope.launch {
            message.value =
                createActivity(name, measurementType, icon, color, categoryId, muscleIds).toMessage()
        }
    }

    fun rename(
        id: Long,
        name: String,
        icon: String? = null,
        color: String? = null,
        categoryId: Long? = null,
    ) {
        viewModelScope.launch {
            message.value = updateActivity(id, name, icon, color, categoryId).toMessage()
        }
    }

    /**
     * Moves an activity between categories from the list row; null uncategorises it.
     *
     * Distinct from [rename] because the row has no name field in hand — routing this
     * through the rename use case would make a re-validation of the name a condition
     * of changing its label.
     */
    fun setCategory(id: Long, categoryId: Long?) {
        viewModelScope.launch { assignCategory(id, categoryId) }
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
