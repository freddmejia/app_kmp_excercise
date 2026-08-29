package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ActivityFilter
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveActivitiesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.DeleteSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayLogUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDaysUseCase
import botix.dev.myweeklyroutinegymapp.presentation.common.WEEK
import botix.dev.myweeklyroutinegymapp.presentation.common.weekOf
import botix.dev.myweeklyroutinegymapp.presentation.common.weekStartOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What this screen has to announce.
 *
 * One member, because this screen only reads and deletes now. Everything that can be
 * said about *writing* a log belongs to the screen that writes it, and is
 * [WorkoutEntryMessage].
 */
sealed interface WorkoutMessage {
    data object Deleted : WorkoutMessage
}

data class WorkoutLogUiState(
    /** Only STRENGTH activities: they are the ones that open the muscle picker. */
    val strengthActivities: List<Activity> = emptyList(),
    /** DISTANCE_TIME activities, which log a distance and a time in one form. */
    val distanceActivities: List<Activity> = emptyList(),
    /** The day being logged or browsed, as days since the Unix epoch. */
    val selectedDay: Long = 0,
    /** Marked distinctly in the strip, and the day the screen opens on. */
    val today: Long = 0,
    /** Days in [visibleDays] that already have a session, for the strip's markers. */
    val daysWithSessions: Set<Long> = emptySet(),
    /** What is logged on [selectedDay]. A day can hold several sessions. */
    val dayLog: List<SessionSummary> = emptyList(),
    val isLoading: Boolean = true,
    val message: WorkoutMessage? = null,
) {
    /**
     * The calendar week [selectedDay] falls in, Monday to Sunday.
     *
     * Derived rather than stored, so the strip and the selection can never disagree.
     * Because it is the *week* that is derived, tapping another day in the same week
     * leaves the strip exactly where it is — only the arrows and the date picker move
     * it, which is what makes the row stable enough to aim at.
     */
    val visibleDays: List<Long> get() = weekOf(selectedDay)

    val isToday: Boolean get() = selectedDay == today
}

/** The day-scoped reads, kept together so they can never describe different days. */
private data class DayState(
    val day: Long,
    val markers: Set<Long>,
    val log: List<SessionSummary>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLogViewModel(
    private val observeActivities: ObserveActivitiesUseCase,
    private val observeSessionDays: ObserveSessionDaysUseCase,
    private val observeDayLog: ObserveDayLogUseCase,
    private val deleteSession: DeleteSessionUseCase,
    clock: Clock,
) : ViewModel() {

    private val message = MutableStateFlow<WorkoutMessage?>(null)

    /**
     * Resolved once, when the screen is created, rather than read on every emission:
     * a value that changed mid-session would move the strip under the user's finger.
     */
    private val today = clock.today()
    private val selectedDay = MutableStateFlow(today)

    /**
     * Everything that depends on the chosen day, resolved together.
     *
     * One `flatMapLatest` rather than three: the strip markers and the day's log are
     * always read for the same day, and folding them in separately would let a stale
     * emission from one pair with a fresh day from the other.
     */
    private val dayState = selectedDay.flatMapLatest { day ->
        val weekStart = weekStartOf(day)
        combine(
            // Exactly the week on screen: the markers follow the strip, so stepping a
            // week re-queries rather than filtering a wider range in memory.
            observeSessionDays(weekStart, weekStart + WEEK - 1),
            observeDayLog(day),
        ) { markers, log -> DayState(day, markers, log) }
    }

    val state: StateFlow<WorkoutLogUiState> = combine(
        message,
        observeActivities(ActivityFilter.ACTIVE),
        dayState,
    ) { currentMessage, activities, day ->
        WorkoutLogUiState(
            // Split by measurement type once, here, rather than at each of the two
            // places that read them: the picker lists both, and the empty state asks
            // whether there is anything strength-shaped to log at all.
            strengthActivities = activities.filter { it.measurementType == MeasurementType.STRENGTH },
            distanceActivities = activities.filter {
                it.measurementType == MeasurementType.DISTANCE_TIME
            },
            selectedDay = day.day,
            today = today,
            daysWithSessions = day.markers,
            dayLog = day.log,
            isLoading = false,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutLogUiState(selectedDay = today, today = today),
    )

    /**
     * Selects a day.
     *
     * Within the visible week this does not move the strip — the week is derived from
     * the selection, and a day in the same week yields the same week. Only the arrows
     * and the date picker land on a different one.
     *
     * Note this writes nothing. Creating a row for each day the user scrolled past
     * would invent sessions that never happened, and every frequency and streak
     * metric counts rows.
     */
    fun selectDay(day: Long) {
        selectedDay.value = day
    }

    /**
     * The week arrows: the only control that moves the strip a week at a time.
     *
     * Shifts by seven days rather than to the next week's Monday, so the weekday
     * column stays put — press it on a Thursday and you land on the Thursday.
     */
    fun stepWeek(forward: Boolean) = selectDay(selectedDay.value + if (forward) WEEK else -WEEK)

    fun goToToday() = selectDay(today)

    fun consumeMessage() {
        message.value = null
    }

    /**
     * Deletes a whole log. Its entries cascade with it.
     *
     * Unconfirmed here on purpose — the dialog is the screen's, and it has already
     * been through it by the time this runs.
     */
    fun deleteLog(sessionId: Long) {
        viewModelScope.launch {
            deleteSession(sessionId)
            message.value = WorkoutMessage.Deleted
        }
    }
}
