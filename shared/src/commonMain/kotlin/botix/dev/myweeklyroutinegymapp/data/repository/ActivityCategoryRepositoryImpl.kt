package botix.dev.myweeklyroutinegymapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import botix.dev.myweeklyroutinegymapp.data.ioDispatcher
import botix.dev.myweeklyroutinegymapp.data.mapper.toDomain
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategoryUsage
import botix.dev.myweeklyroutinegymapp.domain.model.CategoryDeleted
import botix.dev.myweeklyroutinegymapp.domain.model.CategorySeedSyncResult
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.SeededCategory
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ActivityCategoryRepositoryImpl(
    private val db: GymDatabase,
    private val dispatcher: CoroutineDispatcher = ioDispatcher,
) : ActivityCategoryRepository {

    private val queries get() = db.activityCategoryQueries
    private val workoutQueries get() = db.workoutSessionQueries

    override fun observeAll(): Flow<List<ActivityCategory>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeActive(): Flow<List<ActivityCategory>> =
        queries.selectActive().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeArchived(): Flow<List<ActivityCategory>> =
        queries.selectArchived().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeActiveWithUsage(): Flow<List<ActivityCategoryUsage>> =
        queries.selectActiveWithUsage().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<ActivityCategory?> =
        queries.selectById(id).asFlow().mapToOneOrNull(dispatcher).map { it?.toDomain() }

    override suspend fun getById(id: Long): ActivityCategory? = withContext(dispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun create(
        name: String,
        measurementType: MeasurementType,
        icon: String?,
        color: String?,
        createdAt: Long,
    ): Long = withContext(dispatcher) {
        // Insert and id read share a transaction: last_insert_rowid() is
        // connection-scoped, so a concurrent write between the two could otherwise
        // hand back somebody else's id.
        db.transactionWithResult {
            queries.insert(name, icon, color, createdAt, measurementType)
            queries.lastInsertRowId().executeAsOne()
        }
    }

    override suspend fun updateIdentity(
        id: Long,
        name: String,
        measurementType: MeasurementType,
        icon: String?,
        color: String?,
    ): Unit = withContext(dispatcher) {
        queries.updateIdentity(name, icon, color, measurementType, id)
    }

    /**
     * The category half of the shipped catalogue. Same shape as the muscle sync and
     * the same rules — read before writing, so a launch with nothing to do writes
     * nothing; the ledger rather than the row decides what has already been applied.
     */
    override suspend fun syncSeed(
        categories: List<SeededCategory>,
        now: Long,
    ): CategorySeedSyncResult = withContext(dispatcher) {
        db.transactionWithResult {
            val applied = queries.selectAppliedSeedKeys().executeAsList().toSet()
            var added = 0
            var renamed = 0

            for (category in categories) {
                val existing = queries.selectCategoryBySeedKey(category.key).executeAsOneOrNull()
                when {
                    existing != null -> if (existing.name != category.name) {
                        queries.renameSeededCategory(category.name, category.key)
                        renamed++
                    }
                    // Deleted by the user; the ledger keeps it gone.
                    category.key in applied -> Unit
                    else -> {
                        queries.insertSeededCategory(
                            category.name,
                            category.icon,
                            now,
                            category.measurementType,
                            category.key,
                        )
                        queries.markSeedApplied(category.key, now)
                        added++
                    }
                }
            }

            CategorySeedSyncResult(categoriesAdded = added, namesUpdated = renamed)
        }
    }

    /**
     * Archiving takes it out of the New-log dialog and leaves its history alone.
     *
     * It used to detach its activities in the same transaction, because an archived
     * category was a filter you could no longer select and its activities would have
     * been stranded in it. There are no activities now, and what points at a category
     * is *history* — stripping the label off past logs to tidy a picker would be
     * rewriting the record to suit the UI.
     */
    override suspend fun setArchived(id: Long, archived: Boolean): Unit =
        withContext(dispatcher) { queries.setArchived(archived, id) }

    override suspend fun delete(id: Long): CategoryDeleted = withContext(dispatcher) {
        db.transactionWithResult {
            // Counted before the DELETE: the rows are detached by the time it
            // returns, so afterwards this would always read zero.
            val affected = queries.countSessionsIn(id).executeAsOne()
            // Explicit, even though the column declares ON DELETE SET NULL. That
            // action only fires while `PRAGMA foreign_keys` is on, which is a driver
            // setting — true on both shipping platforms, off by default in the JDBC
            // driver the tests use. Doing it here makes the outcome identical
            // everywhere instead of depending on how the connection was opened; the
            // FK action stays as a second line of defence.
            //
            // This is the one place a category delete touches history, and it only
            // ever nulls a label: no session, set, day or distance is removed.
            workoutQueries.clearCategory(id)
            queries.deleteById(id)
            CategoryDeleted(affected)
        }
    }

    override suspend fun sessionCount(id: Long): Long = withContext(dispatcher) {
        queries.countSessionsIn(id).executeAsOne()
    }

    override suspend fun isNameTaken(name: String, excludingId: Long): Boolean =
        withContext(dispatcher) {
            queries.countActiveWithName(name, excludingId).executeAsOne() > 0
        }
}
