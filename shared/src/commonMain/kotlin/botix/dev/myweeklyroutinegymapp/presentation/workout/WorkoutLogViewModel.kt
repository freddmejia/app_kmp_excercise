package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveCategoriesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.DeleteSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartSessionResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayLogUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDaysUseCase
import botix.dev.myweeklyroutinegymapp.presentation.common.WEEK
import botix.dev.myweeklyroutinegymapp.presentation.common.weekOf
import botix.dev.myweeklyroutinegymapp.presentation.common.weekStartOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** The category was deleted between the dialog listing it and the tap landing. */
    data object CategoryNotFound : WorkoutMessage
}

data class WorkoutLogUiState(
    /** Active categories — the New-log dialog's whole list. */
    val categories: List<ActivityCategory> = emptyList(),
    /** The day being logged or browsed, as days since the Unix epoch. */
    val selectedDay: Long = 0,
    /** Marked distinctly in the strip, and the day the screen opens on. */
    val today: Long = 0,
    /** Days in [visibleDays] that already have a session, for the strip's markers. */
    val daysWithSessions: Set<Long> = emptySet(),
    /** What is logged on [selectedDay]. A day can hold several sessions. */
    val dayLog: List<SessionSummary> = emptyList(),
    /**
     * The muscles each log worked, by name — what the cards list instead of counting.
     *
     * Absent for a distance log and for one nothing has been checked into yet, which
     * the card renders as its own line rather than as an empty row of pills.
     */
    val musclesBySession: Map<Long, List<String>> = emptyMap(),
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
    val muscles: Map<Long, List<String>>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLogViewModel(
    private val observeCategories: ObserveCategoriesUseCase,
    private val observeSessionDays: ObserveSessionDaysUseCase,
    private val observeDayLog: ObserveDayLogUseCase,
    private val observeDayMuscles: ObserveDayMusclesUseCase,
    private val startSession: StartSessionUseCase,
    private val deleteSession: DeleteSessionUseCase,
    clock: Clock,
) : ViewModel() {

    /**
     * The log just created, for the screen to open.
     *
     * A `SharedFlow` rather than state: opening is a one-off navigation, and a value
     * left in the state would reopen the same log every time this screen recomposed
     * after coming back from it.
     */
    private val _created = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val created: SharedFlow<Long> = _created.asSharedFlow()

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
            observeDayMuscles(day),
        ) { markers, log, muscles -> DayState(day, markers, log, muscles) }
    }

    val state: StateFlow<WorkoutLogUiState> = combine(
        message,
        observeCategories(),
        dayState,
    ) { currentMessage, categories, day ->
        WorkoutLogUiState(
            // What the New-log dialog offers: the question it asks is "what kind of
            // training", and a category is the answer. It used to list every activity
            // in the app, which meant nothing could be logged until something had been
            // created on another tab first.
            categories = categories,
            selectedDay = day.day,
            today = today,
            daysWithSessions = day.markers,
            dayLog = day.log,
            musclesBySession = day.muscles,
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

    /**
     * Starts a log of the chosen kind on the selected day, and asks the screen to open
     * it.
     *
     * The write happens here rather than on the form, and that is a change worth
     * naming: the form used to create its own session on arrival, so "open" and
     * "write" were the same act and a screen opened by mistake left a row behind.
     * Pressing New log and picking a category is the explicit act, so the row belongs
     * here.
     *
     * An abandoned log is still a real row — it shows in the day's list saying nothing
     * was recorded, and it can be deleted from there. That is the point: it is visible,
     * where a row created invisibly by opening a screen was not.
     */
    fun startLog(categoryId: Long) {
        viewModelScope.launch {
            when (val result = startSession(categoryId, selectedDay.value)) {
                is StartSessionResult.Started -> _created.tryEmit(result.sessionId)
                StartSessionResult.CategoryNotFound ->
                    message.value = WorkoutMessage.CategoryNotFound
            }
        }
    }

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
