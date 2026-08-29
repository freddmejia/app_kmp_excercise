package botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight

import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightChart
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.repository.BodyWeightRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** The preset ranges the screen offers, in days. `null` means "everything". */
enum class BodyWeightRangePreset(val days: Long?) {
    LAST_30_DAYS(30),
    LAST_3_MONTHS(90),
    LAST_6_MONTHS(182),
    LAST_YEAR(365),
    ALL(null),
}

sealed interface LogBodyWeightResult {
    data object Logged : LogBodyWeightResult

    /** Zero or negative is not a body weight; the form blocks saving. */
    data object InvalidWeight : LogBodyWeightResult

    /**
     * The day has not happened yet.
     *
     * You cannot have weighed yourself tomorrow, and the day strip makes future days
     * reachable, so this is a rule the domain has to hold rather than a shape the UI
     * happens not to offer.
     */
    data object FutureDay : LogBodyWeightResult
}

/**
 * The last day any reading covers, never earlier than today.
 *
 * Every range here is a *lookback*: the preset says how far back to read, and says
 * nothing about the far end. Ending the window at today would hide readings dated
 * ahead of it — data the user entered, in rows they could then neither see nor
 * delete. Whatever exists stays reachable.
 */
private fun rangeEnd(today: Long, latest: BodyWeightEntry?): Long =
    maxOf(today, latest?.epochDay ?: today)

/**
 * The body-weight screen's single source: raw points, the smoothed series and the
 * derived summary, recomputed whenever a measurement changes.
 *
 * One use case rather than three, because chart and summary must always describe the
 * same range — computing them from separate Flows would let them disagree mid-update.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveBodyWeightChartUseCase(
    private val repository: BodyWeightRepository,
    private val clock: Clock,
) {
    operator fun invoke(
        preset: BodyWeightRangePreset = BodyWeightRangePreset.LAST_30_DAYS,
    ): Flow<BodyWeightChart> = repository.observeLatest().flatMapLatest { latest ->
        val today = clock.today()
        val from = preset.days?.let { today - it + 1 } ?: Long.MIN_VALUE / 2
        invoke(from, rangeEnd(today, latest))
    }

    operator fun invoke(fromEpochDay: Long, toEpochDay: Long): Flow<BodyWeightChart> =
        repository.observeRange(fromEpochDay, toEpochDay).map { entries ->
            buildChart(entries, rangeDays = toEpochDay - fromEpochDay + 1)
        }
}

/** History list, newest first. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveBodyWeightHistoryUseCase(
    private val repository: BodyWeightRepository,
    private val clock: Clock,
) {
    operator fun invoke(
        preset: BodyWeightRangePreset = BodyWeightRangePreset.LAST_30_DAYS,
    ): Flow<List<BodyWeightEntry>> = repository.observeLatest().flatMapLatest { latest ->
        val today = clock.today()
        val from = preset.days?.let { today - it + 1 } ?: Long.MIN_VALUE / 2
        repository.observeRangeDesc(from, rangeEnd(today, latest))
    }
}

/**
 * The measurements inside one explicit range — the week the strip is showing.
 *
 * Separate from [ObserveBodyWeightHistoryUseCase], which answers a *preset* ending
 * today. The strip navigates to arbitrary weeks, including future ones, which a
 * "last N days" shape cannot express.
 */
class ObserveBodyWeightWeekUseCase(private val repository: BodyWeightRepository) {
    operator fun invoke(fromEpochDay: Long, toEpochDay: Long): Flow<List<BodyWeightEntry>> =
        repository.observeRange(fromEpochDay, toEpochDay)
}

/**
 * Records a measurement, defaulting to today.
 *
 * Logging a day that already has one overwrites it — one measurement per day is the
 * rule, so this is an update, not a second reading.
 */
class LogBodyWeightUseCase(
    private val repository: BodyWeightRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        weightKg: Double,
        epochDay: Long = clock.today(),
        note: String? = null,
    ): LogBodyWeightResult {
        if (weightKg <= 0.0 || weightKg.isNaN()) return LogBodyWeightResult.InvalidWeight
        // Refused rather than clamped to today: silently moving the reading to a day
        // the user did not choose would be worse than telling them no.
        if (epochDay > clock.today()) return LogBodyWeightResult.FutureDay
        repository.upsert(epochDay, weightKg, note?.trim()?.ifEmpty { null })
        return LogBodyWeightResult.Logged
    }
}

/** Whether a given day already has a measurement, so the form can warn before overwriting. */
class GetBodyWeightForDayUseCase(private val repository: BodyWeightRepository) {
    suspend operator fun invoke(epochDay: Long): BodyWeightEntry? = repository.getByDay(epochDay)
}

/**
 * Unrestricted, unlike the catalogue deletes.
 *
 * Nothing references a measurement, so removing one cannot corrupt another screen's
 * metrics — there is no history here to protect.
 */
class DeleteBodyWeightUseCase(private val repository: BodyWeightRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
