package botix.dev.myweeklyroutinegymapp.domain.repository

import botix.dev.myweeklyroutinegymapp.domain.model.DeleteOutcome
import botix.dev.myweeklyroutinegymapp.domain.model.Muscle
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleGroup
import botix.dev.myweeklyroutinegymapp.domain.model.MusclePickerItem
import botix.dev.myweeklyroutinegymapp.domain.model.SeedSyncResult
import botix.dev.myweeklyroutinegymapp.domain.model.SeededGroup
import kotlinx.coroutines.flow.Flow

/**
 * The editable muscle hierarchy (groups and their leaves).
 *
 * Same discipline as the activity catalogue: reference by id, rename freely, archive
 * rather than delete once history exists. A single observable query backs both the
 * management screen and the log picker, so a new muscle shows up in both at once.
 */
interface MuscleCatalogRepository {

    fun observeGroups(): Flow<List<MuscleGroup>>

    fun observeActiveGroups(): Flow<List<MuscleGroup>>

    fun observeMuscles(): Flow<List<Muscle>>

    fun observeActiveMuscles(): Flow<List<Muscle>>

    fun observeMusclesInGroup(groupId: Long): Flow<List<Muscle>>

    /** The log picker: active leaves with their group, most-used first within each group. */
    fun observePicker(): Flow<List<MusclePickerItem>>

    suspend fun getMuscle(id: Long): Muscle?

    suspend fun createGroup(name: String, createdAt: Long): Long

    suspend fun renameGroup(id: Long, name: String)

    suspend fun setGroupArchived(id: Long, archived: Boolean)

    /** Blocked while any leaf of the group has history. */
    suspend fun deleteGroup(id: Long): DeleteOutcome

    suspend fun createMuscle(groupId: Long, name: String, createdAt: Long): Long

    suspend fun renameMuscle(id: Long, name: String)

    /** Archiving removes a muscle from the picker but preserves its historical volume. */
    suspend fun setMuscleArchived(id: Long, archived: Boolean)

    /** Blocked while any session references this muscle. */
    suspend fun deleteMuscle(id: Long): DeleteOutcome

    /**
     * Brings the shipped catalogue into line with [groups], in one transaction.
     *
     * Idempotent and non-destructive, in both directions:
     *
     * - a seed row that has never been inserted is inserted;
     * - a seed row that was inserted and then deleted stays deleted, because the
     *   ledger remembers the key rather than the row;
     * - a seed row still carrying its key has its name brought to the current
     *   language, and one the user renamed no longer carries a key so it is left
     *   alone;
     * - nothing the user created is read, moved or touched.
     */
    suspend fun syncSeed(groups: List<SeededGroup>, now: Long): SeedSyncResult
}
