package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.SessionDetail
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.domain.model.SessionTotals
import botix.dev.myweeklyroutinegymapp.domain.model.UntrainedMuscle
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ActivityFilter
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveActivitiesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.DeleteSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.MusclePickerView
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.includingChecked
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogSetsResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityMuscleSectionsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclePickerUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayLogUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDaysUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDetailUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveUntrainedMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.RemoveMuscleFromSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartOrResumeSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartSessionResult
import botix.dev.myweeklyroutinegymapp.presentation.common.WEEK
import botix.dev.myweeklyroutinegymapp.presentation.common.weekOf
import botix.dev.myweeklyroutinegymapp.presentation.common.weekStartOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    /**
     * True when [sections] is this activity's own muscle list rather than the whole
     * catalogue. The screen shows it, because the two are indistinguishable once
     * rendered and the user has to know which one they are looking at.
     */
    val isScopedToActivity: Boolean = false,
    /** Set by the user to see everything anyway; the link is a default, not a limit. */
    val showAllMuscles: Boolean = false,
    /** The day being logged or browsed, as days since the Unix epoch. */
    val selectedDay: Long = 0,
    /** Marked distinctly in the strip, and the day the screen opens on. */
    val today: Long = 0,
    /** Days in [visibleDays] that already have a session, for the strip's markers. */
    val daysWithSessions: Set<Long> = emptySet(),
    /** What is logged on [selectedDay]. A day can hold several sessions. */
    val dayLog: List<SessionSummary> = emptyList(),
    val detail: SessionDetail? = null,
    val untrainedThisWeek: List<UntrainedMuscle> = emptyList(),
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

    val totals: SessionTotals get() = detail?.totals ?: SessionTotals(0, 0)

    /** Which muscles are currently checked — what the picker renders its ticks from. */
    val checkedMuscleIds: Set<Long>
        get() = detail?.entries?.map { it.muscleId }?.toSet().orEmpty()
}

/** The day-scoped reads, kept together so they can never describe different days. */
private data class DayState(
    val day: Long,
    val markers: Set<Long>,
    val log: List<SessionSummary>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLogViewModel(
    private val startOrResumeSession: StartOrResumeSessionUseCase,
    private val logMuscleSets: LogMuscleSetsUseCase,
    private val setMuscleSets: SetMuscleSetsUseCase,
    private val removeMuscle: RemoveMuscleFromSessionUseCase,
    private val observeSessionDetail: ObserveSessionDetailUseCase,
    private val observeMusclePicker: ObserveMusclePickerUseCase,
    private val observeActivityMuscleSections: ObserveActivityMuscleSectionsUseCase,
    private val observeUntrained: ObserveUntrainedMusclesUseCase,
    private val observeActivities: ObserveActivitiesUseCase,
    private val observeSessionDays: ObserveSessionDaysUseCase,
    private val observeDayLog: ObserveDayLogUseCase,
    private val deleteSession: DeleteSessionUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val sessionId = MutableStateFlow<Long?>(null)
    private val selectedActivityId = MutableStateFlow<Long?>(null)
    private val showAllMuscles = MutableStateFlow(false)
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

    /**
     * The picker, narrowed to the open activity's muscles unless the user asked for
     * everything.
     *
     * flatMapLatest so switching activity cancels the previous query rather than
     * leaving two collectors racing. With no activity open there is nothing to scope
     * by, so it falls through to the full catalogue.
     */
    private val pickerView = combine(selectedActivityId, showAllMuscles, ::Pair)
        .flatMapLatest { (activityId, showAll) ->
            if (activityId == null || showAll) {
                observeMusclePicker().map { MusclePickerView(it, isScopedToActivity = false) }
            } else {
                observeActivityMuscleSections(activityId)
            }
        }

    val state: StateFlow<WorkoutLogUiState> = combine(
        sessionId,
        message,
        pickerView,
        // No session open yet means no detail to observe — emit null rather than
        // querying for a row that cannot exist.
        sessionId.flatMapLatest { id -> if (id == null) flowOf(null) else observeSessionDetail(id) },
        // Anchored to the day on screen: while browsing an old date, "the last 7
        // days" has to mean the week up to it, not the week up to today.
        selectedDay.flatMapLatest { day -> observeUntrained(endDay = day) },
    ) { id, currentMessage, picker, detail, untrained ->
        // A checked muscle is always shown, even when it falls outside the activity's
        // scope: its sets are already in the totals, so hiding the row would leave
        // volume the user can neither see nor correct.
        val visible = picker.includingChecked(detail?.entries.orEmpty())
        WorkoutLogUiState(
            sessionId = id,
            sections = visible.sections,
            isScopedToActivity = picker.isScopedToActivity,
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
    }.combine(showAllMuscles) { partial, showAll ->
        partial.copy(showAllMuscles = showAll)
    }.combine(dayState) { partial, day ->
        partial.copy(
            selectedDay = day.day,
            today = today,
            daysWithSessions = day.markers,
            dayLog = day.log,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutLogUiState(selectedDay = today, today = today),
    )

    /**
     * Selects a day, and returns to that day's list.
     *
     * Within the visible week this does not move the strip — the week is derived from
     * the selection, and a day in the same week yields the same week. Only the arrows
     * and the date picker land on a different one.
     *
     * Closing the open session is the point rather than a side effect: the day view
     * lists everything logged on the day, so landing there shows more than guessing
     * which session the user meant would. Reopening one is a tap.
     *
     * Note this writes nothing. Creating a row for each day the user scrolled past
     * would invent sessions that never happened, and every frequency and streak
     * metric counts rows.
     */
    fun selectDay(day: Long) {
        selectedDay.value = day
        sessionId.value = null
        selectedActivityId.value = null
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
     * Opens today's session for an activity, or resumes it if the day was already
     * started. Rejects non-strength activities before any row is written.
     */
    fun openSession(activityId: Long, epochDay: Long? = null) {
        viewModelScope.launch {
            // Defaults to whatever the strip is showing, not to today: the whole point
            // of selecting a day is that Start applies to it.
            val result = startOrResumeSession(activityId, epochDay ?: selectedDay.value)
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

    /**
     * Widens the picker past this activity's muscles, or narrows it back.
     *
     * The link is a default, never a restriction: an unplanned muscle has to be
     * loggable without editing the activity first.
     */
    fun setShowAllMuscles(value: Boolean) {
        showAllMuscles.value = value
    }

    /** Leaves the session, returning the screen to the activity picker. */
    fun closeSession() {
        sessionId.value = null
        selectedActivityId.value = null
        // Reset, so the next activity opens scoped to its own muscles rather than
        // inheriting a widening the user asked for on a different one.
        showAllMuscles.value = false
    }

    /** Opens an existing log from the day list. No write: the session already exists. */
    fun openExisting(summary: SessionSummary) {
        selectedActivityId.value = summary.activityId
        sessionId.value = summary.sessionId
    }

    /**
     * Deletes a whole log. Its entries cascade with it.
     *
     * Closes the session first when it is the one on screen, so the UI never holds an
     * id that no longer resolves.
     */
    fun deleteLog(sessionId: Long) {
        if (this.sessionId.value == sessionId) closeSession()
        viewModelScope.launch { deleteSession(sessionId) }
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
