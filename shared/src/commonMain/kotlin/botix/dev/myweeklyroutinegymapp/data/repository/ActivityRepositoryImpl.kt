package botix.dev.myweeklyroutinegymapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import botix.dev.myweeklyroutinegymapp.data.ioDispatcher
import botix.dev.myweeklyroutinegymapp.data.mapper.toDomain
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityUsage
import botix.dev.myweeklyroutinegymapp.domain.model.DeleteOutcome
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementTypeChange
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ActivityRepositoryImpl(
    private val db: GymDatabase,
    private val dispatcher: CoroutineDispatcher = ioDispatcher,
) : ActivityRepository {

    private val queries get() = db.activityQueries

    override fun observeAll(): Flow<List<Activity>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeActive(): Flow<List<Activity>> =
        queries.selectActive().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeArchived(): Flow<List<Activity>> =
        queries.selectArchived().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeActiveByUsage(): Flow<List<ActivityUsage>> =
        queries.selectActiveByUsage().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<Activity?> =
        queries.selectById(id).asFlow().mapToOneOrNull(dispatcher).map { it?.toDomain() }

    override suspend fun getById(id: Long): Activity? = withContext(dispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun create(
        name: String,
        measurementType: MeasurementType,
        icon: String?,
        color: String?,
        isSeed: Boolean,
        createdAt: Long,
    ): Long = withContext(dispatcher) {
        // Insert and id read share a transaction: last_insert_rowid() is
        // connection-scoped, so a concurrent write between the two could otherwise
        // hand back somebody else's id.
        db.transactionWithResult {
            queries.insert(name, measurementType, icon, color, isSeed, createdAt)
            queries.lastInsertRowId().executeAsOne()
        }
    }

    override suspend fun updateIdentity(id: Long, name: String, icon: String?, color: String?): Unit =
        withContext(dispatcher) { queries.updateIdentity(name, icon, color, id) }

    override suspend fun changeMeasurementType(
        id: Long,
        measurementType: MeasurementType,
    ): MeasurementTypeChange = withContext(dispatcher) {
        // Check and write in one transaction, so a session logged in between cannot
        // slip past the guard.
        db.transactionWithResult {
            val sessions = queries.countSessionsFor(id).executeAsOne()
            if (sessions > 0) {
                MeasurementTypeChange.BlockedByHistory(sessions)
            } else {
                queries.updateMeasurementType(measurementType, id)
                MeasurementTypeChange.Changed
            }
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean): Unit =
        withContext(dispatcher) { queries.setArchived(archived, id) }

    override suspend fun delete(id: Long): DeleteOutcome = withContext(dispatcher) {
        db.transactionWithResult {
            val sessions = queries.countSessionsFor(id).executeAsOne()
            if (sessions > 0) {
                DeleteOutcome.BlockedByHistory(sessions)
            } else {
                queries.deleteById(id)
                DeleteOutcome.Deleted
            }
        }
    }

    override suspend fun sessionCount(id: Long): Long = withContext(dispatcher) {
        queries.countSessionsFor(id).executeAsOne()
    }

    override suspend fun isNameTaken(name: String, excludingId: Long): Boolean =
        withContext(dispatcher) {
            queries.countActiveWithName(name, excludingId).executeAsOne() > 0
        }
}
