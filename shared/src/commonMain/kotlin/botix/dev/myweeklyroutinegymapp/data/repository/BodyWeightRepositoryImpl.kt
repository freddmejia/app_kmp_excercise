package botix.dev.myweeklyroutinegymapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import botix.dev.myweeklyroutinegymapp.data.ioDispatcher
import botix.dev.myweeklyroutinegymapp.data.mapper.toDomain
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightRange
import botix.dev.myweeklyroutinegymapp.domain.repository.BodyWeightRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BodyWeightRepositoryImpl(
    private val db: GymDatabase,
    private val dispatcher: CoroutineDispatcher = ioDispatcher,
) : BodyWeightRepository {

    private val queries get() = db.bodyWeightQueries

    override fun observeAll(): Flow<List<BodyWeightEntry>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<BodyWeightEntry>> =
        queries.selectRange(fromEpochDay, toEpochDay).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeRangeDesc(fromEpochDay: Long, toEpochDay: Long): Flow<List<BodyWeightEntry>> =
        queries.selectRangeDesc(fromEpochDay, toEpochDay).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeLatest(): Flow<BodyWeightEntry?> =
        queries.selectLatest().asFlow().mapToOneOrNull(dispatcher).map { it?.toDomain() }

    // Aggregate query: always exactly one row, even for an empty range.
    override fun observeRangeStats(fromEpochDay: Long, toEpochDay: Long): Flow<BodyWeightRange> =
        queries.selectRangeExtremes(fromEpochDay, toEpochDay).asFlow().mapToOne(dispatcher)
            .map { it.toDomain() }

    override suspend fun getByDay(epochDay: Long): BodyWeightEntry? = withContext(dispatcher) {
        queries.selectByDay(epochDay).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun upsert(epochDay: Long, weightKg: Double, note: String?): Unit =
        withContext(dispatcher) { queries.upsert(epochDay, weightKg, note) }

    override suspend fun update(id: Long, weightKg: Double, note: String?): Unit =
        withContext(dispatcher) { queries.updateById(weightKg, note, id) }

    override suspend fun deleteByDay(epochDay: Long): Unit =
        withContext(dispatcher) { queries.deleteByDay(epochDay) }

    override suspend fun delete(id: Long): Unit =
        withContext(dispatcher) { queries.deleteById(id) }
}
