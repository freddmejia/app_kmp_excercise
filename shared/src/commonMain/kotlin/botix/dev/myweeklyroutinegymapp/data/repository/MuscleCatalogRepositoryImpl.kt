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
import botix.dev.myweeklyroutinegymapp.domain.model.SeedSyncResult
import botix.dev.myweeklyroutinegymapp.domain.model.SeededGroup
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
                // The leaves before the group. muscle.group_id has no ON DELETE
                // clause, and the driver turns foreign_keys on, so deleting a group
                // anything still points at fails outright. countSessionsForGroup has
                // already established that none of those leaves has history, which is
                // what makes removing them safe rather than lossy.
                queries.deleteMusclesInGroup(id)
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

    /**
     * One transaction for the whole catalogue.
     *
     * Not one per group: a sync that failed halfway would leave the ledger claiming
     * keys whose rows were rolled back, and the next launch would then skip them
     * forever. All of it or none of it.
     *
     * Reads come first inside each step so the common case — every row present, names
     * already in the right language — performs no writes at all and therefore raises
     * no query notification, which is what keeps a launch from re-emitting every
     * muscle Flow in the app for nothing.
     */
    override suspend fun syncSeed(groups: List<SeededGroup>, now: Long): SeedSyncResult =
        withContext(dispatcher) {
            db.transactionWithResult {
                val applied = queries.selectAppliedSeedKeys().executeAsList().toSet()
                var groupsAdded = 0
                var musclesAdded = 0
                var namesUpdated = 0

                for (group in groups) {
                    val existingGroup = queries.selectGroupBySeedKey(group.key).executeAsOneOrNull()
                    val groupId = when {
                        existingGroup != null -> {
                            if (existingGroup.name != group.name) {
                                queries.renameSeededGroup(group.name, group.key)
                                namesUpdated++
                            }
                            existingGroup.id
                        }
                        // Deleted by the user at some point. Leave it gone — putting
                        // it back every launch is the behaviour that makes a shipped
                        // catalogue feel like something you cannot get rid of.
                        group.key in applied -> null
                        else -> {
                            queries.insertSeededGroup(group.name, now, group.key)
                            // Read before anything else writes. last_insert_rowid() is
                            // per-connection, not per-table, so marking the ledger
                            // first would hand back the ledger row's id and every leaf
                            // would be inserted against a group that does not exist.
                            val id = queries.lastInsertRowId().executeAsOne()
                            queries.markSeedApplied(group.key, now)
                            groupsAdded++
                            id
                        }
                    }

                    for (leaf in group.leaves) {
                        val existingLeaf = queries.selectMuscleBySeedKey(leaf.key).executeAsOneOrNull()
                        when {
                            existingLeaf != null -> {
                                if (existingLeaf.name != leaf.name) {
                                    queries.renameSeededMuscle(leaf.name, leaf.key)
                                    namesUpdated++
                                }
                            }
                            // Same rule as the group, plus the case where the group
                            // itself is gone: a leaf has nowhere to live without one.
                            leaf.key in applied || groupId == null -> Unit
                            else -> {
                                queries.insertSeededMuscle(groupId, leaf.name, now, leaf.key)
                                queries.markSeedApplied(leaf.key, now)
                                musclesAdded++
                            }
                        }
                    }
                }

                SeedSyncResult(groupsAdded, musclesAdded, namesUpdated)
            }
        }

    override suspend fun deleteMuscle(id: Long): DeleteOutcome = withContext(dispatcher) {
        db.transactionWithResult {
            val referencing = queries.countSessionsForMuscle(id).executeAsOne()
            if (referencing > 0) {
                DeleteOutcome.BlockedByHistory(referencing)
            } else {
                queries.deleteMuscle(id)
                DeleteOutcome.Deleted
            }
        }
    }
}
