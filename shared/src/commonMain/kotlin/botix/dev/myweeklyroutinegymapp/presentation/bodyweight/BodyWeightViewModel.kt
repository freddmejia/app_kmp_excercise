package botix.dev.myweeklyroutinegymapp.presentation.bodyweight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightChart
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.BodyWeightRangePreset
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.DeleteBodyWeightUseCase
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

/**
 * What this screen has to announce.
 *
 * One member, because this screen now only reads and deletes: logging moved to
 * [BodyWeightEntryViewModel], and the things that can go wrong while logging are
 * reasons a *form* was refused rather than news about the list — they are reported
 * where they happen, under the field, as [BodyWeightEntryError].
 */
sealed interface BodyWeightMessage {
    data object Deleted : BodyWeightMessage
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
     * What the selected day already holds, if anything.
     *
     * The strip's log button reads this: one measurement per day means opening the
     * editor on a day that has one is a correction, not a second reading, and the
     * button says so before it is pressed rather than after.
     */
    val entryForSelectedDay: BodyWeightEntry? get() = weekEntries[selectedDay]

    /** You cannot have weighed yourself on a day that has not happened. */
    val selectedDayIsFuture: Boolean get() = selectedDay > today
}

@OptIn(ExperimentalCoroutinesApi::class)
class BodyWeightViewModel(
    private val observeChart: ObserveBodyWeightChartUseCase,
    private val observeHistory: ObserveBodyWeightHistoryUseCase,
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

    fun delete(id: Long) {
        viewModelScope.launch {
            deleteBodyWeight(id)
            message.value = BodyWeightMessage.Deleted
        }
    }
}
