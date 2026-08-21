package botix.dev.myweeklyroutinegymapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import botix.dev.myweeklyroutinegymapp.data.ioDispatcher
import botix.dev.myweeklyroutinegymapp.data.mapper.toDomain
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.DeleteOutcome
import botix.dev.myweeklyroutinegymapp.domain.model.Muscle
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleGroup
import botix.dev.myweeklyroutinegymapp.domain.model.MusclePickerItem
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MuscleCatalogRepositoryImpl(
    private val db: GymDatabase,
    private val dispatcher: CoroutineDispatcher = ioDispatcher,
) : MuscleCatalogRepository {

    private val queries get() = db.muscleCatalogQueries

    override fun observeGroups(): Flow<List<MuscleGroup>> =
        queries.selectAllGroups().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeActiveGroups(): Flow<List<MuscleGroup>> =
        queries.selectActiveGroups().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeMuscles(): Flow<List<Muscle>> =
        queries.selectAllMuscles().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeActiveMuscles(): Flow<List<Muscle>> =
        queries.selectActiveMuscles().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeMusclesInGroup(groupId: Long): Flow<List<Muscle>> =
        queries.selectMusclesByGroup(groupId).asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observePicker(): Flow<List<MusclePickerItem>> =
        queries.selectPickerByUsage().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getMuscle(id: Long): Muscle? = withContext(dispatcher) {
        queries.selectMuscleById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun createGroup(name: String, createdAt: Long): Long = withContext(dispatcher) {
        db.transactionWithResult {
            queries.insertGroup(name, createdAt)
            queries.lastInsertRowId().executeAsOne()
        }
    }

    override suspend fun renameGroup(id: Long, name: String): Unit =
        withContext(dispatcher) { queries.renameGroup(name, id) }

    override suspend fun setGroupArchived(id: Long, archived: Boolean): Unit =
        withContext(dispatcher) { queries.setGroupArchived(archived, id) }

    override suspend fun deleteGroup(id: Long): DeleteOutcome = withContext(dispatcher) {
        db.transactionWithResult {
            // Counts sessions across every leaf of the group, not just the group row:
            // deleting a group with trained leaves would orphan their volume.
            val referencing = queries.countSessionsForGroup(id).executeAsOne()
            if (referencing > 0) {
                DeleteOutcome.BlockedByHistory(referencing)
            } else {
                // Before the leaves go, so no activity keeps a link to a muscle that
                // no longer exists. Explicit for the same reason as everywhere else:
                // ON DELETE CASCADE depends on a driver-level pragma.
                db.activityMuscleQueries.deleteAllForGroup(id)
                queries.deleteGroup(id)
                DeleteOutcome.Deleted
            }
        }
    }

    override suspend fun createMuscle(groupId: Long, name: String, createdAt: Long): Long =
        withContext(dispatcher) {
            db.transactionWithResult {
                queries.insertMuscle(groupId, name, createdAt)
                queries.lastInsertRowId().executeAsOne()
            }
        }

    override suspend fun renameMuscle(id: Long, name: String): Unit =
        withContext(dispatcher) { queries.renameMuscle(name, id) }

    override suspend fun setMuscleArchived(id: Long, archived: Boolean): Unit =
        withContext(dispatcher) { queries.setMuscleArchived(archived, id) }

    override suspend fun deleteMuscle(id: Long): DeleteOutcome = withContext(dispatcher) {
        db.transactionWithResult {
            val referencing = queries.countSessionsForMuscle(id).executeAsOne()
            if (referencing > 0) {
                DeleteOutcome.BlockedByHistory(referencing)
            } else {
                db.activityMuscleQueries.deleteAllForMuscle(id)
                queries.deleteMuscle(id)
                DeleteOutcome.Deleted
            }
        }
    }
}
