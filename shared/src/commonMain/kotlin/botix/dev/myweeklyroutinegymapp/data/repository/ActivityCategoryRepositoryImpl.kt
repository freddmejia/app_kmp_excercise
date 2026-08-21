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
    private val activityQueries get() = db.activityQueries

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
        icon: String?,
        color: String?,
        createdAt: Long,
    ): Long = withContext(dispatcher) {
        // Insert and id read share a transaction: last_insert_rowid() is
        // connection-scoped, so a concurrent write between the two could otherwise
        // hand back somebody else's id.
        db.transactionWithResult {
            queries.insert(name, icon, color, createdAt)
            queries.lastInsertRowId().executeAsOne()
        }
    }

    override suspend fun updateIdentity(id: Long, name: String, icon: String?, color: String?): Unit =
        withContext(dispatcher) { queries.updateIdentity(name, icon, color, id) }

    override suspend fun setArchived(id: Long, archived: Boolean): Unit = withContext(dispatcher) {
        db.transaction {
            queries.setArchived(archived, id)
            // Archiving removes it from every picker. Its activities would otherwise
            // sit in a filter that can no longer be selected, so they go back to
            // uncategorised in the same transaction — never one without the other.
            if (archived) activityQueries.clearCategory(id)
        }
    }

    override suspend fun delete(id: Long): CategoryDeleted = withContext(dispatcher) {
        db.transactionWithResult {
            // Counted before the DELETE: the rows are detached by the time it
            // returns, so afterwards this would always read zero.
            val affected = queries.countActivitiesIn(id).executeAsOne()
            // Explicit, even though the column declares ON DELETE SET NULL. That
            // action only fires while `PRAGMA foreign_keys` is on, which is a driver
            // setting — true on both shipping platforms, off by default in the JDBC
            // driver the tests use. Detaching the rows here makes the outcome the
            // same everywhere instead of depending on how the connection was opened;
            // the FK action stays as a second line of defence.
            activityQueries.clearCategory(id)
            queries.deleteById(id)
            CategoryDeleted(affected)
        }
    }

    override suspend fun activityCount(id: Long): Long = withContext(dispatcher) {
        queries.countActivitiesIn(id).executeAsOne()
    }

    override suspend fun isNameTaken(name: String, excludingId: Long): Boolean =
        withContext(dispatcher) {
            queries.countActiveWithName(name, excludingId).executeAsOne() > 0
        }
}
