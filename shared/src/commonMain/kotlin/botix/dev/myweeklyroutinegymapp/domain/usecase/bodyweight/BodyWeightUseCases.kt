package botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight

import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightChart
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.repository.BodyWeightRepository
import kotlinx.coroutines.flow.Flow
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
}

/**
 * The body-weight screen's single source: raw points, the smoothed series and the
 * derived summary, recomputed whenever a measurement changes.
 *
 * One use case rather than three, because chart and summary must always describe the
 * same range — computing them from separate Flows would let them disagree mid-update.
 */
class ObserveBodyWeightChartUseCase(
    private val repository: BodyWeightRepository,
    private val clock: Clock,
) {
    operator fun invoke(preset: BodyWeightRangePreset = BodyWeightRangePreset.LAST_30_DAYS): Flow<BodyWeightChart> {
        val today = clock.today()
        val from = preset.days?.let { today - it + 1 } ?: Long.MIN_VALUE / 2
        return invoke(from, today)
    }

    operator fun invoke(fromEpochDay: Long, toEpochDay: Long): Flow<BodyWeightChart> =
        repository.observeRange(fromEpochDay, toEpochDay).map { entries ->
            buildChart(entries, rangeDays = toEpochDay - fromEpochDay + 1)
        }
}

/** History list, newest first. */
class ObserveBodyWeightHistoryUseCase(
    private val repository: BodyWeightRepository,
    private val clock: Clock,
) {
    operator fun invoke(preset: BodyWeightRangePreset = BodyWeightRangePreset.LAST_30_DAYS): Flow<List<BodyWeightEntry>> {
        val today = clock.today()
        val from = preset.days?.let { today - it + 1 } ?: Long.MIN_VALUE / 2
        return repository.observeRangeDesc(from, today)
    }
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
