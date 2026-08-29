package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.SessionDetail
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.domain.model.SessionTotals
import botix.dev.myweeklyroutinegymapp.domain.model.UntrainedMuscle
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogDistanceTimeResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.LogSetsResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveMusclePickerUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionDetailUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveSessionSummaryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.ObserveUntrainedMusclesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.RemoveMuscleFromSessionUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetMuscleSetsUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SetSessionDistanceTimeUseCase
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

    /** The log was deleted from the day's list while this screen was open. */
    data object SessionNotFound : WorkoutEntryMessage

    /** A distance/time log with neither a distance nor a time in it. */
    data object NothingMeasured : WorkoutEntryMessage
}

/** The two shapes this screen takes, decided by the category, never by the caller. */
enum class WorkoutEntryForm { LOADING, STRENGTH, DISTANCE_TIME }

data class WorkoutEntryUiState(
    /** The day being logged. Fixed for the life of the screen. */
    val epochDay: Long = 0,
    /** The log itself, with its category resolved. Null until the first read lands. */
    val session: SessionSummary? = null,
    val form: WorkoutEntryForm = WorkoutEntryForm.LOADING,

    // --- Strength -----------------------------------------------------------
    val sections: List<MuscleSection> = emptyList(),
    val detail: SessionDetail? = null,
    val untrainedThisWeek: List<UntrainedMuscle> = emptyList(),

    // --- Distance and time --------------------------------------------------
    val distanceInput: String = "",
    val durationInput: String = "",

    val message: WorkoutEntryMessage? = null,
) {
    val totals: SessionTotals get() = detail?.totals ?: SessionTotals(0, 0)

    /** What this log is called in the app bar and at the top of the form. */
    val categoryName: String? get() = session?.categoryName

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
 * One log: the muscles checked off against it, or the distance and time it was
 * measured in.
 *
 * ### Why the session id is the argument
 * It used to be (activity, day), because `StartOrResumeSessionUseCase` found a session
 * by exactly that pair and created one only if it found none — so the pair identified
 * the row and the id was something the database decided afterwards.
 *
 * Neither half of that survives. There are no activities, and a day may hold several
 * logs of the same kind: two Gym sessions on one Tuesday are two rows, told apart by
 * what is in them rather than by a name. So the row is created before this screen
 * opens — by the New-log flow, or by tapping an existing row — and its id is the only
 * thing that identifies it.
 *
 * The screen therefore never creates anything. That is a real simplification: it used
 * to create the session row on arrival for a strength log, which meant "open" and
 * "write" were the same act and a screen opened by mistake left a row behind.
 *
 * ### The two forms are one screen because the category decides which
 * The measurement type is recorded against the category the log was filed under, and
 * it arrives with the session summary. Asking the user to pick "log strength" or "log
 * distance" would make them classify something the app knew before the screen opened.
 *
 * ### Strength writes as you go; distance writes once
 * A strength log is a session row plus a row per muscle, and checking a muscle *is*
 * the write — there is nothing to save afterwards. Distance and time are two numbers
 * that only mean anything together, so they are typed, saved in one go, and the
 * screen closes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutEntryViewModel(
    private val sessionId: Long,
    private val epochDay: Long,
    private val logMuscleSets: LogMuscleSetsUseCase,
    private val setMuscleSets: SetMuscleSetsUseCase,
    private val removeMuscle: RemoveMuscleFromSessionUseCase,
    private val observeSessionDetail: ObserveSessionDetailUseCase,
    private val observeSessionSummary: ObserveSessionSummaryUseCase,
    private val observeMusclePicker: ObserveMusclePickerUseCase,
    private val observeUntrained: ObserveUntrainedMusclesUseCase,
    private val setDistanceTime: SetSessionDistanceTimeUseCase,
) : ViewModel() {

    /**
     * Fired when a distance log has been written, and never remembered.
     *
     * The screen closes on it. A `SharedFlow` with no replay rather than a flag in the
     * state, because this ViewModel is keyed by the session and lives in the activity's
     * store: a flag would still be set the next time the same log was opened, and the
     * screen would close itself on arrival.
     */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val message = MutableStateFlow<WorkoutEntryMessage?>(null)

    /** The distance form's two fields, as typed. Parsed only on save. */
    private val form = MutableStateFlow(DistanceForm())

    private data class DistanceForm(val distance: String = "", val duration: String = "")

    /** The two values this screen owns rather than reads. */
    private data class Local(val form: DistanceForm, val message: WorkoutEntryMessage?)

    val state: StateFlow<WorkoutEntryUiState> = combine(
        observeSessionSummary(sessionId),
        // The whole catalogue. It used to be narrowed to the activity's own muscles
        // with a "show all" escape hatch; without activities there is nothing to
        // narrow by, and a category is too coarse to be a useful default — "Gym" does
        // not mean chest. One list, ordered by how often each muscle is actually used.
        observeMusclePicker(),
        observeSessionDetail(sessionId),
        // Anchored to the day being logged, not to today: filling in last Tuesday,
        // "untrained this week" has to mean the week up to that Tuesday.
        observeUntrained(endDay = epochDay),
        combine(form, message, ::Local),
    ) { session, picker, detail, untrained, local ->
        // A checked muscle is always shown: its sets are already in the totals, so
        // hiding the row would leave volume the user can neither see nor correct.
        val visible = picker.includingChecked(detail?.entries.orEmpty())
        WorkoutEntryUiState(
            epochDay = epochDay,
            session = session,
            form = when {
                session == null -> WorkoutEntryForm.LOADING
                session.measurementType == MeasurementType.STRENGTH -> WorkoutEntryForm.STRENGTH
                else -> WorkoutEntryForm.DISTANCE_TIME
            },
            sections = visible,
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
     * A ViewModel keyed by the session outlives the visit that created it, so a second
     * visit would otherwise open on the first one's half-typed numbers instead of on
     * what is stored. Nothing is created here any more — the row exists before this
     * screen does.
     */
    fun onOpened() {
        message.value = null
        viewModelScope.launch {
            val session = observeSessionSummary(sessionId).first()
            if (session == null) {
                message.value = WorkoutEntryMessage.SessionNotFound
                return@launch
            }
            if (session.measurementType != MeasurementType.STRENGTH) {
                // Metres and seconds back out to km and minutes, because those are the
                // units the fields are labelled in: a 20.5 km ride has to read back as
                // "20.5", not "20500". Nothing is written until Save.
                form.value = DistanceForm(
                    distance = session.distanceMeters?.let { (it / 1000.0).formatMeasure() }.orEmpty(),
                    duration = session.durationSeconds?.let { (it / 60.0).formatMeasure() }.orEmpty(),
                )
            }
        }
    }

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
     *
     * It writes into the session that already exists rather than starting one, which
     * is what makes reopening a ride to fix a number a correction instead of a second
     * ride on the same day.
     */
    fun save() {
        viewModelScope.launch {
            val current = form.value
            val result = setDistanceTime(
                sessionId = sessionId,
                distanceMeters = current.distance.toDecimalOrNull()?.let { (it * 1000).roundToLong() },
                durationSeconds = current.duration.toDecimalOrNull()?.let { (it * 60).roundToLong() },
            )
            when (result) {
                LogDistanceTimeResult.Logged -> _saved.tryEmit(Unit)
                LogDistanceTimeResult.NothingMeasured ->
                    message.value = WorkoutEntryMessage.NothingMeasured
            }
        }
    }

    /** Checking a muscle, or adding to it if it is already checked. */
    fun addSets(muscleId: Long, effectiveSets: Long, warmupSets: Long = 0) {
        viewModelScope.launch {
            message.value = logMuscleSets(sessionId, muscleId, effectiveSets, warmupSets).toMessage()
        }
    }

    /** Correcting an entry to exact values rather than adding to it. */
    fun setSets(muscleId: Long, effectiveSets: Long, warmupSets: Long = 0) {
        viewModelScope.launch {
            message.value = setMuscleSets(sessionId, muscleId, effectiveSets, warmupSets).toMessage()
        }
    }

    fun uncheck(muscleId: Long) {
        viewModelScope.launch {
            removeMuscle(sessionId, muscleId)
            message.value = WorkoutEntryMessage.Removed
        }
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
