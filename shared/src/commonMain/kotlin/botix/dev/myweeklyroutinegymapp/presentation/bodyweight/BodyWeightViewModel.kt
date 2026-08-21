package botix.dev.myweeklyroutinegymapp.presentation.bodyweight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightChart
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.BodyWeightRangePreset
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.DeleteBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightChartUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightHistoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightWeekUseCase
import botix.dev.myweeklyroutinegymapp.presentation.common.WEEK
import botix.dev.myweeklyroutinegymapp.presentation.common.weekOf
import botix.dev.myweeklyroutinegymapp.presentation.common.weekStartOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BodyWeightMessage {
    data object Logged : BodyWeightMessage

    /** Zero, negative or unparseable: the form keeps the value and shows the error. */
    data object InvalidWeight : BodyWeightMessage

    data object Deleted : BodyWeightMessage

    /** The selected day has not happened yet. */
    data object FutureDay : BodyWeightMessage
}

data class BodyWeightUiState(
    val chart: BodyWeightChart = BodyWeightChart.empty(),
    val history: List<BodyWeightEntry> = emptyList(),
    val preset: BodyWeightRangePreset = BodyWeightRangePreset.LAST_30_DAYS,
    val selectedDay: Long = 0,
    val today: Long = 0,
    /** The visible week's measurements, keyed by day — the strip's dots come from this. */
    val weekEntries: Map<Long, BodyWeightEntry> = emptyMap(),
    val isLoading: Boolean = true,
    val message: BodyWeightMessage? = null,
) {
    val visibleDays: List<Long> get() = weekOf(selectedDay)

    val daysWithEntry: Set<Long> get() = weekEntries.keys

    /**
     * What is already recorded for the day being logged, if anything.
     *
     * Exposed as state rather than fetched when the button is pressed: the warning
     * has to be readable *before* committing, and one measurement per day means
     * saving replaces rather than adds.
     */
    val entryForSelectedDay: BodyWeightEntry? get() = weekEntries[selectedDay]

    /** You cannot have weighed yourself on a day that has not happened. */
    val selectedDayIsFuture: Boolean get() = selectedDay > today
}

@OptIn(ExperimentalCoroutinesApi::class)
class BodyWeightViewModel(
    private val observeChart: ObserveBodyWeightChartUseCase,
    private val observeHistory: ObserveBodyWeightHistoryUseCase,
    private val logBodyWeight: LogBodyWeightUseCase,
    private val deleteBodyWeight: DeleteBodyWeightUseCase,
    private val observeWeek: ObserveBodyWeightWeekUseCase,
    clock: Clock,
) : ViewModel() {

    private val preset = MutableStateFlow(BodyWeightRangePreset.LAST_30_DAYS)
    private val message = MutableStateFlow<BodyWeightMessage?>(null)

    /** Resolved once, so the strip cannot shift under the user mid-session. */
    private val today = clock.today()
    private val selectedDay = MutableStateFlow(today)

    /** The visible week's readings, indexed by day for both the dots and the form. */
    private val weekEntries = selectedDay.flatMapLatest { day ->
        val start = weekStartOf(day)
        observeWeek(start, start + WEEK - 1).map { entries -> entries.associateBy { it.epochDay } }
    }

    val state: StateFlow<BodyWeightUiState> = combine(
        preset,
        message,
        preset.flatMapLatest { observeChart(it) },
        preset.flatMapLatest { observeHistory(it) },
    ) { currentPreset, currentMessage, chart, history ->
        BodyWeightUiState(
            chart = chart,
            history = history,
            preset = currentPreset,
            isLoading = false,
            message = currentMessage,
        )
    }.combine(selectedDay) { partial, day ->
        partial.copy(selectedDay = day, today = today)
    }.combine(weekEntries) { partial, entries ->
        partial.copy(weekEntries = entries)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BodyWeightUiState(selectedDay = today, today = today),
    )

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

    fun selectPreset(value: BodyWeightRangePreset) {
        preset.value = value
    }

    fun consumeMessage() {
        message.value = null
    }

    /**
     * Records a measurement. [weightKg] is nullable so the screen can hand over an
     * unparseable field directly and get the same validation as an out-of-range
     * number, instead of inventing its own rule for "not a number".
     */
    fun log(weightKg: Double?, epochDay: Long? = null, note: String? = null) {
        viewModelScope.launch {
            if (weightKg == null) {
                message.value = BodyWeightMessage.InvalidWeight
                return@launch
            }
            // Defaults to the day on the strip, not to today: selecting a day is the
            // whole point of being able to select one.
            val result = logBodyWeight(weightKg, epochDay ?: selectedDay.value, note)
            message.value = when (result) {
                LogBodyWeightResult.Logged -> BodyWeightMessage.Logged
                LogBodyWeightResult.InvalidWeight -> BodyWeightMessage.InvalidWeight
                LogBodyWeightResult.FutureDay -> BodyWeightMessage.FutureDay
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            deleteBodyWeight(id)
            message.value = BodyWeightMessage.Deleted
        }
    }
}
