package botix.dev.myweeklyroutinegymapp.domain.usecase.workout

import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.DeleteOutcome
import botix.dev.myweeklyroutinegymapp.domain.model.Muscle
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleGroup
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import kotlinx.coroutines.flow.Flow

/**
 * Managing the muscle hierarchy.
 *
 * Same discipline as the activity catalogue — rename freely, archive rather than
 * delete once history exists — because the reasoning is identical: entries point at
 * ids, so a rename is invisible to past sessions but a delete is not.
 */

sealed interface SaveMuscleResult {
    data class Saved(val id: Long) : SaveMuscleResult

    data object BlankName : SaveMuscleResult
}

class ObserveMuscleGroupsUseCase(private val catalog: MuscleCatalogRepository) {
    operator fun invoke(includeArchived: Boolean = false): Flow<List<MuscleGroup>> =
        if (includeArchived) catalog.observeGroups() else catalog.observeActiveGroups()
}

class ObserveMusclesUseCase(private val catalog: MuscleCatalogRepository) {
    operator fun invoke(includeArchived: Boolean = false): Flow<List<Muscle>> =
        if (includeArchived) catalog.observeMuscles() else catalog.observeActiveMuscles()

    fun inGroup(groupId: Long): Flow<List<Muscle>> = catalog.observeMusclesInGroup(groupId)
}

class CreateMuscleGroupUseCase(
    private val catalog: MuscleCatalogRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(name: String): SaveMuscleResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveMuscleResult.BlankName
        return SaveMuscleResult.Saved(catalog.createGroup(trimmed, clock.nowMillis()))
    }
}

class CreateMuscleUseCase(
    private val catalog: MuscleCatalogRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(groupId: Long, name: String): SaveMuscleResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveMuscleResult.BlankName
        return SaveMuscleResult.Saved(catalog.createMuscle(groupId, trimmed, clock.nowMillis()))
    }
}

class RenameMuscleUseCase(private val catalog: MuscleCatalogRepository) {
    suspend operator fun invoke(id: Long, name: String): SaveMuscleResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveMuscleResult.BlankName
        catalog.renameMuscle(id, trimmed)
        return SaveMuscleResult.Saved(id)
    }

    suspend fun group(id: Long, name: String): SaveMuscleResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveMuscleResult.BlankName
        catalog.renameGroup(id, trimmed)
        return SaveMuscleResult.Saved(id)
    }
}

/** Leaves the picker, keeps its historical volume. */
class ArchiveMuscleUseCase(private val catalog: MuscleCatalogRepository) {
    suspend operator fun invoke(id: Long, archived: Boolean = true) =
        catalog.setMuscleArchived(id, archived)

    suspend fun group(id: Long, archived: Boolean = true) =
        catalog.setGroupArchived(id, archived)
}

/** Refused while sessions reference the muscle — or, for a group, any of its leaves. */
class DeleteMuscleUseCase(private val catalog: MuscleCatalogRepository) {
    suspend operator fun invoke(id: Long): DeleteOutcome = catalog.deleteMuscle(id)

    suspend fun group(id: Long): DeleteOutcome = catalog.deleteGroup(id)
}
