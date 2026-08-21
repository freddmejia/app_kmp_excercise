package botix.dev.myweeklyroutinegymapp.domain.repository

import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityUsage
import botix.dev.myweeklyroutinegymapp.domain.model.DeleteOutcome
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementTypeChange
import kotlinx.coroutines.flow.Flow

/**
 * The activity catalogue.
 *
 * Reads are [Flow]s over a single observable query, so a write re-emits and every
 * consumer — management screen, workout picker, goals picker — refreshes together
 * with no manual invalidation.
 */
interface ActivityRepository {

    fun observeAll(): Flow<List<Activity>>

    fun observeActive(): Flow<List<Activity>>

    fun observeArchived(): Flow<List<Activity>>

    /** Active activities, most-used first: the default ordering for pickers. */
    fun observeActiveByUsage(): Flow<List<ActivityUsage>>

    fun observeById(id: Long): Flow<Activity?>

    suspend fun getById(id: Long): Activity?

    /** @return the id of the new row. */
    suspend fun create(
        name: String,
        measurementType: MeasurementType,
        icon: String? = null,
        color: String? = null,
        categoryId: Long? = null,
        isSeed: Boolean = false,
        createdAt: Long,
    ): Long

    /**
     * Renames, restyles and recategorises. [MeasurementType] is deliberately not
     * editable here — history references the row by id, so this is always safe.
     */
    suspend fun updateIdentity(
        id: Long,
        name: String,
        icon: String?,
        color: String?,
        categoryId: Long?,
    )

    /** Reassigns the category alone; null moves the activity to uncategorised. */
    suspend fun setCategory(id: Long, categoryId: Long?)

    /** Allowed only while the activity has no sessions; see [MeasurementTypeChange]. */
    suspend fun changeMeasurementType(id: Long, measurementType: MeasurementType): MeasurementTypeChange

    /** The normal alternative to deletion: keeps history and past goals intact. */
    suspend fun setArchived(id: Long, archived: Boolean)

    /** Hard delete, permitted only with no referencing sessions. */
    suspend fun delete(id: Long): DeleteOutcome

    suspend fun sessionCount(id: Long): Long

    /**
     * Whether another *non-archived* activity already uses [name].
     *
     * Lets the form report a duplicate as a field error rather than letting the
     * unique index throw. Pass the row being edited as [excludingId] so renaming an
     * activity to its own current name is not treated as a clash.
     */
    suspend fun isNameTaken(name: String, excludingId: Long = 0L): Boolean
}
