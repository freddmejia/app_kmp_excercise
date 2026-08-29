package botix.dev.myweeklyroutinegymapp.domain.usecase.activity

import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategoryUsage
import botix.dev.myweeklyroutinegymapp.domain.model.CategoryDeleted
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use cases for the activity-category catalogue.
 *
 * Grouped in one file for the same reason as [ActivityUseCases]: each is a validation
 * rule plus a single repository call, and splitting them across six files would hide
 * the shared rules rather than clarify them.
 */

sealed interface SaveCategoryResult {
    data class Saved(val id: Long) : SaveCategoryResult

    /** A name of only whitespace is not a name. */
    data object BlankName : SaveCategoryResult

    /** Another non-archived category already holds this name. */
    data object NameTaken : SaveCategoryResult
}

/** Every category, for the management screen's archived/all filters. */
class ObserveCategoriesUseCase(private val repository: ActivityCategoryRepository) {
    operator fun invoke(includeArchived: Boolean = false): Flow<List<ActivityCategory>> =
        if (includeArchived) repository.observeAll() else repository.observeActive()
}

/**
 * Active categories with their activity counts — the management list.
 *
 * The count is derived (a COUNT over activities), never stored, so it cannot drift
 * from the rows it describes.
 */
class ObserveCategoryUsageUseCase(private val repository: ActivityCategoryRepository) {
    operator fun invoke(): Flow<List<ActivityCategoryUsage>> = repository.observeActiveWithUsage()
}

class CreateCategoryUseCase(
    private val repository: ActivityCategoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        name: String,
        icon: String? = null,
        color: String? = null,
    ): SaveCategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveCategoryResult.BlankName
        if (repository.isNameTaken(trimmed)) return SaveCategoryResult.NameTaken

        return SaveCategoryResult.Saved(
            repository.create(
                name = trimmed,
                icon = icon,
                color = color,
                createdAt = clock.nowMillis(),
            ),
        )
    }
}

/**
 * Renames and restyles.
 *
 * Always safe: activities reference the category by id, so a rename relabels them
 * rather than detaching them.
 */
class UpdateCategoryUseCase(private val repository: ActivityCategoryRepository) {
    suspend operator fun invoke(
        id: Long,
        name: String,
        icon: String? = null,
        color: String? = null,
    ): SaveCategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveCategoryResult.BlankName
        // Excluding this row: keeping its own name is not a clash with itself.
        if (repository.isNameTaken(trimmed, excludingId = id)) return SaveCategoryResult.NameTaken

        repository.updateIdentity(id, trimmed, icon, color)
        return SaveCategoryResult.Saved(id)
    }
}

/** Hides the category and returns its activities to uncategorised. Reversible. */
class ArchiveCategoryUseCase(private val repository: ActivityCategoryRepository) {
    suspend operator fun invoke(id: Long, archived: Boolean = true) =
        repository.setArchived(id, archived)
}

/**
 * Hard delete, unrestricted.
 *
 * No blocked branch, unlike [DeleteActivityUseCase]: an activity refuses deletion
 * because sessions reference it and past metrics would break, whereas a category is
 * only a label. The affected activities survive as uncategorised, and the returned
 * count is what lets the screen say so.
 */
class DeleteCategoryUseCase(private val repository: ActivityCategoryRepository) {
    suspend operator fun invoke(id: Long): CategoryDeleted = repository.delete(id)
}

/**
 * Moves one activity between categories; null means uncategorised.
 *
 * Sits on [ActivityRepository] rather than the category one because the row being
 * written is the activity — the category is untouched.
 */
class AssignActivityCategoryUseCase(private val repository: ActivityRepository) {
    suspend operator fun invoke(activityId: Long, categoryId: Long?) =
        repository.setCategory(activityId, categoryId)
}
