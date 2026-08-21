package botix.dev.myweeklyroutinegymapp.domain.usecase.activity

import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityUsage
import botix.dev.myweeklyroutinegymapp.domain.model.DeleteOutcome
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementTypeChange
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use cases for the activity catalogue.
 *
 * Grouped in one file because most are a validation rule plus a single repository
 * call; splitting them across six files would hide the shared rules rather than
 * clarify them. Each is still its own class, so extracting one later is a move, not
 * a rewrite.
 */

/** Which slice of the catalogue a screen wants. Pickers force [ACTIVE]. */
enum class ActivityFilter { ACTIVE, ARCHIVED, ALL }

sealed interface SaveActivityResult {
    data class Saved(val id: Long) : SaveActivityResult

    /** A name of only whitespace is not a name. */
    data object BlankName : SaveActivityResult

    /** Another non-archived activity already holds this name. */
    data object NameTaken : SaveActivityResult
}

class ObserveActivitiesUseCase(private val repository: ActivityRepository) {
    operator fun invoke(filter: ActivityFilter = ActivityFilter.ACTIVE): Flow<List<Activity>> =
        when (filter) {
            ActivityFilter.ACTIVE -> repository.observeActive()
            ActivityFilter.ARCHIVED -> repository.observeArchived()
            ActivityFilter.ALL -> repository.observeAll()
        }
}

/** Active activities ordered by how often they are logged — the picker's ordering. */
class ObserveActivityPickerUseCase(private val repository: ActivityRepository) {
    operator fun invoke(): Flow<List<ActivityUsage>> = repository.observeActiveByUsage()
}

class CreateActivityUseCase(
    private val repository: ActivityRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        name: String,
        measurementType: MeasurementType,
        icon: String? = null,
        color: String? = null,
    ): SaveActivityResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveActivityResult.BlankName
        if (repository.isNameTaken(trimmed)) return SaveActivityResult.NameTaken

        return SaveActivityResult.Saved(
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
 * Renames and restyles. Always safe with respect to history: sessions reference the
 * activity by id, so past records follow the rename rather than breaking.
 */
class UpdateActivityUseCase(private val repository: ActivityRepository) {
    suspend operator fun invoke(
        id: Long,
        name: String,
        icon: String? = null,
        color: String? = null,
    ): SaveActivityResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveActivityResult.BlankName
        // Excluding this row: keeping its own name is not a clash with itself.
        if (repository.isNameTaken(trimmed, excludingId = id)) return SaveActivityResult.NameTaken

        repository.updateIdentity(id, trimmed, icon, color)
        return SaveActivityResult.Saved(id)
    }
}

/** The normal alternative to deletion: leaves history and past goals intact. */
class ArchiveActivityUseCase(private val repository: ActivityRepository) {
    suspend operator fun invoke(id: Long, archived: Boolean = true) =
        repository.setArchived(id, archived)
}

/**
 * Hard delete, which the repository refuses once sessions reference the activity.
 *
 * The blocked case is returned rather than thrown: the UI answer is to offer
 * archiving instead, which is a normal branch and not an error path.
 */
class DeleteActivityUseCase(private val repository: ActivityRepository) {
    suspend operator fun invoke(id: Long): DeleteOutcome = repository.delete(id)
}

/** Permitted only before the first session; see [MeasurementTypeChange]. */
class ChangeMeasurementTypeUseCase(private val repository: ActivityRepository) {
    suspend operator fun invoke(id: Long, measurementType: MeasurementType): MeasurementTypeChange =
        repository.changeMeasurementType(id, measurementType)
}
