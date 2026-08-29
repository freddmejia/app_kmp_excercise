package botix.dev.myweeklyroutinegymapp.domain.repository

import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategoryUsage
import botix.dev.myweeklyroutinegymapp.domain.model.CategoryDeleted
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

    /** Active categories with the number of active activities in each. */
    fun observeActiveWithUsage(): Flow<List<ActivityCategoryUsage>>

    fun observeById(id: Long): Flow<ActivityCategory?>

    suspend fun getById(id: Long): ActivityCategory?

    /** @return the id of the new row. */
    suspend fun create(
        name: String,
        icon: String? = null,
        color: String? = null,
        createdAt: Long,
    ): Long

    suspend fun updateIdentity(id: Long, name: String, icon: String?, color: String?)

    /**
     * Hides the category without losing it.
     *
     * Activities in it are moved to uncategorised, because an archived category is
     * absent from every picker — leaving them pointed at it would put those rows in a
     * filter the user can no longer select.
     */
    suspend fun setArchived(id: Long, archived: Boolean)

    /**
     * Hard delete, always permitted.
     *
     * Its activities survive as uncategorised; see [CategoryDeleted] for why this is
     * unrestricted while deleting an activity is not.
     */
    suspend fun delete(id: Long): CategoryDeleted

    /** How many activities a delete would leave uncategorised. */
    suspend fun activityCount(id: Long): Long

    /**
     * Whether another *non-archived* category already uses [name].
     *
     * Pass the row being edited as [excludingId] so renaming a category to its own
     * current name is not treated as a clash.
     */
    suspend fun isNameTaken(name: String, excludingId: Long = 0L): Boolean
}
