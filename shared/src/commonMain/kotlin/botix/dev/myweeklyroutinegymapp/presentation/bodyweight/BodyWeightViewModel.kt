package botix.dev.myweeklyroutinegymapp.presentation.bodyweight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightChart
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.BodyWeightRangePreset
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.DeleteBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.GetBodyWeightForDayUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightChartUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.ObserveBodyWeightHistoryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BodyWeightMessage {
    data object Logged : BodyWeightMessage

    /** Zero, negative or unparseable: the form keeps the value and shows the error. */
    data object InvalidWeight : BodyWeightMessage

    data object Deleted : BodyWeightMessage

    /** That day already has a reading; saving will replace it. */
    data class WillOverwrite(val existing: BodyWeightEntry) : BodyWeightMessage
}

data class BodyWeightUiState(
    val chart: BodyWeightChart = BodyWeightChart.empty(),
    val history: List<BodyWeightEntry> = emptyList(),
    val preset: BodyWeightRangePreset = BodyWeightRangePreset.LAST_30_DAYS,
    val isLoading: Boolean = true,
    val message: BodyWeightMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BodyWeightViewModel(
    private val observeChart: ObserveBodyWeightChartUseCase,
    private val observeHistory: ObserveBodyWeightHistoryUseCase,
    private val logBodyWeight: LogBodyWeightUseCase,
    private val getForDay: GetBodyWeightForDayUseCase,
    private val deleteBodyWeight: DeleteBodyWeightUseCase,
) : ViewModel() {

    private val preset = MutableStateFlow(BodyWeightRangePreset.LAST_30_DAYS)
    private val message = MutableStateFlow<BodyWeightMessage?>(null)

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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BodyWeightUiState(),
    )

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
            val result = if (epochDay == null) {
                logBodyWeight(weightKg, note = note)
            } else {
                logBodyWeight(weightKg, epochDay, note)
            }
            message.value = when (result) {
                LogBodyWeightResult.Logged -> BodyWeightMessage.Logged
                LogBodyWeightResult.InvalidWeight -> BodyWeightMessage.InvalidWeight
            }
        }
    }

    /**
     * Checks whether a day is already recorded, so the form can confirm before
     * replacing it rather than overwriting silently.
     */
    fun checkForOverwrite(epochDay: Long) {
        viewModelScope.launch {
            val existing = getForDay(epochDay)
            if (existing != null) message.value = BodyWeightMessage.WillOverwrite(existing)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            deleteBodyWeight(id)
            message.value = BodyWeightMessage.Deleted
        }
    }
}
