package botix.dev.myweeklyroutinegymapp.domain.usecase.activity

import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategoryUsage
import botix.dev.myweeklyroutinegymapp.domain.model.CategoryDeleted
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
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
    /**
     * [measurementType] is the whole point of a category now: it decides which
     * logging form everything filed here opens. It has no default, so a caller has to
     * make the choice rather than inherit STRENGTH by omission.
     */
    suspend operator fun invoke(
        name: String,
        measurementType: MeasurementType,
        icon: String? = null,
        color: String? = null,
    ): SaveCategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveCategoryResult.BlankName
        if (repository.isNameTaken(trimmed)) return SaveCategoryResult.NameTaken

        return SaveCategoryResult.Saved(
            repository.create(
                name = trimmed,
                measurementType = measurementType,
                icon = icon,
                color = color,
                createdAt = clock.nowMillis(),
            ),
        )
    }
}

/**
 * Renames, restyles, and can change what the category measures.
 *
 * The rename half is always safe: activities reference the category by id, so it
 * relabels them rather than detaching them.
 *
 * Changing [MeasurementType] is safe for a different and less obvious reason — it
 * does not reach the activities already filed here. They keep the type they were
 * created with and so does every session under them, so this decides what is filed
 * here *next*. The alternative, rewriting them, would leave a strength activity
 * pointing at distance columns it has no values for.
 */
class UpdateCategoryUseCase(private val repository: ActivityCategoryRepository) {
    suspend operator fun invoke(
        id: Long,
        name: String,
        measurementType: MeasurementType,
        icon: String? = null,
        color: String? = null,
    ): SaveCategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveCategoryResult.BlankName
        // Excluding this row: keeping its own name is not a clash with itself.
        if (repository.isNameTaken(trimmed, excludingId = id)) return SaveCategoryResult.NameTaken

        repository.updateIdentity(id, trimmed, measurementType, icon, color)
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

