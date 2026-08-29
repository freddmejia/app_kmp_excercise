package botix.dev.myweeklyroutinegymapp.domain.repository

import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategoryUsage
import botix.dev.myweeklyroutinegymapp.domain.model.CategoryDeleted
import botix.dev.myweeklyroutinegymapp.domain.model.CategorySeedSyncResult
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.SeededCategory
import kotlinx.coroutines.flow.Flow

/**
 * The activity-category catalogue.
 *
 * Same reactive contract as [ActivityRepository]: reads are [Flow]s over a single
 * observable query, so creating a category makes it appear in the management list
 * *and* in the activity form's selector without either being told to refresh.
 */
interface ActivityCategoryRepository {

    fun observeAll(): Flow<List<ActivityCategory>>

    fun observeActive(): Flow<List<ActivityCategory>>

    fun observeArchived(): Flow<List<ActivityCategory>>

    /** Active categories with the number of logs recorded under each. */
    fun observeActiveWithUsage(): Flow<List<ActivityCategoryUsage>>

    fun observeById(id: Long): Flow<ActivityCategory?>

    suspend fun getById(id: Long): ActivityCategory?

    /** @return the id of the new row. */
    suspend fun create(
        name: String,
        measurementType: MeasurementType,
        icon: String? = null,
        color: String? = null,
        createdAt: Long,
    ): Long

    /**
     * Changing [measurementType] here does **not** rewrite the activities already
     * filed in the category. They keep the type they were created with, and so does
     * every session under them; the new type applies to what is filed here next. A
     * category edit that reinterpreted history would silently change what past
     * numbers mean.
     */
    suspend fun updateIdentity(
        id: Long,
        name: String,
        measurementType: MeasurementType,
        icon: String?,
        color: String?,
    )

    /**
     * Hides the category without losing it, and without touching its history: past
     * logs keep the label, they simply cannot gain new ones.
     */
    suspend fun setArchived(id: Long, archived: Boolean)

    /**
     * Hard delete, always permitted.
     *
     * Its logs survive with no label; see [CategoryDeleted] for why this is
     * unrestricted while deleting a muscle is not.
     */
    suspend fun delete(id: Long): CategoryDeleted

    /** How many logs a delete would strip the label from. */
    suspend fun sessionCount(id: Long): Long

    /**
     * Whether another *non-archived* category already uses [name].
     *
     * Pass the row being edited as [excludingId] so renaming a category to its own
     * current name is not treated as a clash.
     */
    suspend fun isNameTaken(name: String, excludingId: Long = 0L): Boolean

    /**
     * Brings the shipped categories into line with [categories], in one transaction.
     *
     * Same contract as `MuscleCatalogRepository.syncSeed`: idempotent, never
     * resurrects what the user deleted, never renames what the user renamed, never
     * touches a category they made. A shipped category's measurement type is written
     * once, at insert, and never revised — see [SeededCategory].
     */
    suspend fun syncSeed(categories: List<SeededCategory>, now: Long): CategorySeedSyncResult
}
