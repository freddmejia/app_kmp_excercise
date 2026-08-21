package botix.dev.myweeklyroutinegymapp.domain.repository

import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightRange
import kotlinx.coroutines.flow.Flow

/**
 * Body-weight measurements.
 *
 * The only repository here that permits unrestricted deletion: nothing references a
 * measurement, so removing one cannot corrupt another screen's metrics.
 */
interface BodyWeightRepository {

    fun observeAll(): Flow<List<BodyWeightEntry>>

    /** Ascending — chart order. Both bounds inclusive. */
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<BodyWeightEntry>>

    /** Descending — history-list order. */
    fun observeRangeDesc(fromEpochDay: Long, toEpochDay: Long): Flow<List<BodyWeightEntry>>

    fun observeLatest(): Flow<BodyWeightEntry?>

    fun observeRangeStats(fromEpochDay: Long, toEpochDay: Long): Flow<BodyWeightRange>

    suspend fun getByDay(epochDay: Long): BodyWeightEntry?

    /**
     * One measurement per day: logging a day that already exists overwrites it.
     *
     * The row's id changes on overwrite. That is safe here only because nothing
     * references a body-weight id — do not copy this pattern to a referenced table.
     */
    suspend fun upsert(epochDay: Long, weightKg: Double, note: String? = null)

    suspend fun update(id: Long, weightKg: Double, note: String?)

    suspend fun deleteByDay(epochDay: Long)

    suspend fun delete(id: Long)
}
