package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.SessionDetail
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.domain.model.SessionTotals
import botix.dev.myweeklyroutinegymapp.domain.model.UntrainedMuscle
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveActivityUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogDistanceTimeResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogDistanceTimeUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogSetsResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.MusclePickerView
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveActivityMuscleSectionsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveDayLogUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclePickerUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDetailUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveUntrainedMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.RemoveMuscleFromSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartOrResumeSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.StartSessionResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.includingChecked
import botix.dev.myweeklyroutinegymapp.presentation.common.formatMeasure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/** What the entry screen has to announce. */
sealed interface WorkoutEntryMessage {
    data object Logged : WorkoutEntryMessage
    data object Removed : WorkoutEntryMessage

    /** Effective sets below one; the muscle is effectively unchecked. */
    data object InvalidSets : WorkoutEntryMessage

    /** The activity was deleted while this screen was open. */
    data object ActivityNotFound : WorkoutEntryMessage

    /** A distance/time log with neither a distance nor a time in it. */
    data object NothingMeasured : WorkoutEntryMessage
}

/** The two shapes this screen takes, decided by the activity, never by the caller. */
enum class WorkoutEntryForm { LOADING, STRENGTH, DISTANCE_TIME }

data class WorkoutEntryUiState(
    /** The day being logged. Fixed for the life of the screen. */
    val epochDay: Long = 0,
    val activity: Activity? = null,
    val form: WorkoutEntryForm = WorkoutEntryForm.LOADING,
    /** Written on arrival for a strength log; null until then, and for the other form. */
    val sessionId: Long? = null,

    // --- Strength -----------------------------------------------------------
    val sections: List<MuscleSection> = emptyList(),
    /**
     * True when [sections] is this activity's own muscle list rather than the whole
     * catalogue. The screen shows it, because the two are indistinguishable once
     * rendered and the user has to know which one they are looking at.
     */
    val isScopedToActivity: Boolean = false,
    /** Set by the user to see everything anyway; the link is a default, not a limit. */
    val showAllMuscles: Boolean = false,
    val detail: SessionDetail? = null,
    val untrainedThisWeek: List<UntrainedMuscle> = emptyList(),

    // --- Distance and time --------------------------------------------------
    val distanceInput: String = "",
    val durationInput: String = "",

    val message: WorkoutEntryMessage? = null,
) {
    val totals: SessionTotals get() = detail?.totals ?: SessionTotals(0, 0)

    /**
     * Whether the distance form has anything worth writing.
     *
     * Either measure alone is a real record — a swim nobody measured, a route nobody
     * timed — so this asks for one of the two, not both. The use case owns the rule;
     * this only keeps the button from promising what it would refuse.
     */
    val canSave: Boolean
        get() = distanceInput.isNotBlank() || durationInput.isNotBlank()
}

/**
 * One log: the muscles checked off against an activity, or the distance and time it
 * was measured in.
 *
 * ### Why (activity, day) is the argument, and not a session id
 * `StartOrResumeSessionUseCase` looks a session up by exactly that pair and creates
 * one only if it finds nothing, so the pair already identifies the row — the id is
 * something the database decides afterwards. Taking the pair means the same screen
 * opens a new log and reopens an existing one with no branch at the call site, and a
 * log deleted from another screen degrades to a fresh one instead of a dangling id.
 *
 * ### The two forms are one screen because the activity decides which
 * The measurement type is already recorded against the activity. Asking the user to
 * pick "log strength" or "log distance" before choosing what they did would make them
 * classify something the app has known since the activity was created.
 *
 * ### Strength writes as you go; distance writes once
 * A strength log is a session row plus a row per muscle, and checking a muscle *is*
 * the write — there is nothing to save afterwards, which is why the session row is
 * created on arrival. Distance and time are two numbers that only mean anything
 * together, so they are typed, saved in one go, and the screen closes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutEntryViewModel(
    private val activityId: Long,
    private val epochDay: Long,
    private val observeActivity: ObserveActivityUseCase,
    private val startOrResumeSession: StartOrResumeSessionUseCase,
    private val logMuscleSets: LogMuscleSetsUseCase,
    private val setMuscleSets: SetMuscleSetsUseCase,
    private val removeMuscle: RemoveMuscleFromSessionUseCase,
    private val observeSessionDetail: ObserveSessionDetailUseCase,
    private val observeMusclePicker: ObserveMusclePickerUseCase,
    private val observeActivityMuscleSections: ObserveActivityMuscleSectionsUseCase,
    private val observeUntrained: ObserveUntrainedMusclesUseCase,
    private val observeDayLog: ObserveDayLogUseCase,
    private val logDistanceTime: LogDistanceTimeUseCase,
) : ViewModel() {

    /**
     * Fired when a distance log has been written, and never remembered.
     *
     * The screen closes on it. A `SharedFlow` with no replay rather than a flag in the
     * state, because this ViewModel is keyed by (activity, day) and lives in the
     * activity's store: a flag would still be set the next time the same log was
     * opened, and the screen would close itself on arrival.
     */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val sessionId = MutableStateFlow<Long?>(null)
    private val showAllMuscles = MutableStateFlow(false)
    private val message = MutableStateFlow<WorkoutEntryMessage?>(null)

    /** The distance form's two fields, as typed. Parsed only on save. */
    private val form = MutableStateFlow(DistanceForm())

    private data class DistanceForm(val distance: String = "", val duration: String = "")

    /**
     * The four values this screen owns rather than reads.
     *
     * Folded into one flow because `combine` tops out at five sources and the reads
     * already claim four of them — and because they change together often enough that
     * four separate stages would recompute the same state four times.
     */
    private data class Local(
        val showAllMuscles: Boolean,
        val form: DistanceForm,
        val message: WorkoutEntryMessage?,
        val sessionId: Long?,
    )

    /**
     * The picker, narrowed to this activity's muscles unless the user asked for
     * everything.
     *
     * flatMapLatest so widening cancels the narrow query rather than leaving two
     * collectors racing.
     */
    private val pickerView = showAllMuscles.flatMapLatest { showAll ->
        if (showAll) {
            observeMusclePicker().map { MusclePickerView(it, isScopedToActivity = false) }
        } else {
            observeActivityMuscleSections(activityId)
        }
    }

    val state: StateFlow<WorkoutEntryUiState> = combine(
        observeActivity(activityId),
        pickerView,
        // Nothing to observe until the session row exists — emit null rather than
        // querying for a row that cannot be there yet.
        sessionId.flatMapLatest { id -> if (id == null) flowOf(null) else observeSessionDetail(id) },
        // Anchored to the day being logged, not to today: filling in last Tuesday,
        // "untrained this week" has to mean the week up to that Tuesday.
        observeUntrained(endDay = epochDay),
        combine(showAllMuscles, form, message, sessionId, ::Local),
    ) { activity, picker, detail, untrained, local ->
        // A checked muscle is always shown, even when it falls outside the activity's
        // scope: its sets are already in the totals, so hiding the row would leave
        // volume the user can neither see nor correct.
        val visible = picker.includingChecked(detail?.entries.orEmpty())
        WorkoutEntryUiState(
            epochDay = epochDay,
            activity = activity,
            form = when (activity?.measurementType) {
                null -> WorkoutEntryForm.LOADING
                MeasurementType.STRENGTH -> WorkoutEntryForm.STRENGTH
                else -> WorkoutEntryForm.DISTANCE_TIME
            },
            sessionId = local.sessionId,
            sections = visible.sections,
            isScopedToActivity = picker.isScopedToActivity,
            showAllMuscles = local.showAllMuscles,
            detail = detail,
            untrainedThisWeek = untrained,
            distanceInput = local.form.distance,
            durationInput = local.form.duration,
            message = local.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutEntryUiState(epochDay = epochDay),
    )

    /**
     * Called every time the screen appears, not once per instance.
     *
     * A ViewModel keyed by (activity, day) outlives the visit that created it, so a
     * second visit would otherwise open on the first one's half-typed numbers instead
     * of on what is stored. For the strength form this is also where the session row
     * is created or resumed.
     */
    fun onOpened() {
        message.value = null
        viewModelScope.launch {
            val activity = activity() ?: run {
                message.value = WorkoutEntryMessage.ActivityNotFound
                return@launch
            }
            if (activity.measurementType == MeasurementType.STRENGTH) {
                when (val result = startOrResumeSession(activityId, epochDay)) {
                    is StartSessionResult.Started -> sessionId.value = result.sessionId
                    StartSessionResult.ActivityNotFound ->
                        message.value = WorkoutEntryMessage.ActivityNotFound
                    // The type changed under a screen that was already open; the day
                    // list re-reads it as a distance log either way.
                    StartSessionResult.NotAStrengthActivity -> Unit
                }
            } else {
                // Metres and seconds back out to km and minutes, because those are the
                // units the fields are labelled in: a 20.5 km ride has to read back as
                // "20.5", not "20500". Nothing is written until Save.
                val existing = existingSession()
                form.value = DistanceForm(
                    distance = existing?.distanceMeters?.let { (it / 1000.0).formatMeasure() }.orEmpty(),
                    duration = existing?.durationSeconds?.let { (it / 60.0).formatMeasure() }.orEmpty(),
                )
            }
        }
    }

    /** Read once, straight from the source: the state flow may not have started yet. */
    private suspend fun activity(): Activity? = observeActivity(activityId).first()

    /** This activity's log on this day, if it has one. The day's list already knows. */
    private suspend fun existingSession(): SessionSummary? =
        observeDayLog(epochDay).first().firstOrNull { it.activityId == activityId }

    fun consumeMessage() {
        message.value = null
    }

    fun setDistance(text: String) = form.update { it.copy(distance = text) }

    fun setDuration(text: String) = form.update { it.copy(duration = text) }

    /**
     * Writes the distance log and closes the screen.
     *
     * Kilometres and minutes in, metres and seconds out: the database holds the
     * canonical units, and the conversion belongs on the boundary rather than in the
     * form. Rounded, not truncated — 20.55 km is 20550 m, and dropping the remainder
     * would quietly shorten every ride by up to a metre.
     */
    fun save() {
        viewModelScope.launch {
            val current = form.value
            val result = logDistanceTime(
                activityId = activityId,
                distanceMeters = current.distance.toDecimalOrNull()?.let { (it * 1000).roundToLong() },
                durationSeconds = current.duration.toDecimalOrNull()?.let { (it * 60).roundToLong() },
                epochDay = epochDay,
            )
            when (result) {
                LogDistanceTimeResult.Logged -> _saved.tryEmit(Unit)
                LogDistanceTimeResult.NothingMeasured ->
                    message.value = WorkoutEntryMessage.NothingMeasured
                LogDistanceTimeResult.ActivityNotFound ->
                    message.value = WorkoutEntryMessage.ActivityNotFound
                // Reachable only if the activity's type changed under an open form.
                LogDistanceTimeResult.NotADistanceTimeActivity ->
                    message.value = WorkoutEntryMessage.ActivityNotFound
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

    fun uncheck(muscleId: Long) {
        val id = sessionId.value ?: return
        viewModelScope.launch {
            removeMuscle(id, muscleId)
            message.value = WorkoutEntryMessage.Removed
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

    private fun LogSetsResult.toMessage(): WorkoutEntryMessage = when (this) {
        LogSetsResult.Logged -> WorkoutEntryMessage.Logged
        LogSetsResult.InvalidSets -> WorkoutEntryMessage.InvalidSets
    }
}

/**
 * Blank, or nonsense, reads as "not given" — which the use case then rules on.
 *
 * The comma is folded to a point because it is the decimal separator in three of this
 * app's four locales, and several soft keyboards offer only one of the two.
 */
internal fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
