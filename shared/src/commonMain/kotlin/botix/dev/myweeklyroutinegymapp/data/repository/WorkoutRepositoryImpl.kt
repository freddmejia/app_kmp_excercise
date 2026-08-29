package botix.dev.myweeklyroutinegymapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import botix.dev.myweeklyroutinegymapp.data.ioDispatcher
import botix.dev.myweeklyroutinegymapp.data.mapper.toDomain
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.DailyVolume
import botix.dev.myweeklyroutinegymapp.domain.model.GroupVolume
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleVolume
import botix.dev.myweeklyroutinegymapp.domain.model.CategoryVolume
import botix.dev.myweeklyroutinegymapp.domain.model.SessionEntry
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.domain.model.SessionTotals
import botix.dev.myweeklyroutinegymapp.domain.model.UntrainedMuscle
import botix.dev.myweeklyroutinegymapp.domain.model.WorkoutSession
import botix.dev.myweeklyroutinegymapp.domain.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WorkoutRepositoryImpl(
    private val db: GymDatabase,
    private val dispatcher: CoroutineDispatcher = ioDispatcher,
) : WorkoutRepository {

    private val queries get() = db.workoutSessionQueries

    override fun observeSessionsInRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<WorkoutSession>> =
        queries.selectSessionsInRange(fromEpochDay, toEpochDay).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeDaySummaries(epochDay: Long): Flow<List<SessionSummary>> =
        queries.selectDaySummaries(epochDay).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeSession(id: Long): Flow<WorkoutSession?> =
        queries.selectSessionById(id).asFlow().mapToOneOrNull(dispatcher).map { it?.toDomain() }

    override fun observeEntries(sessionId: Long): Flow<List<SessionEntry>> =
        queries.selectEntriesForSession(sessionId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeTotals(sessionId: Long): Flow<SessionTotals> =
        queries.sessionTotals(sessionId).asFlow().mapToOne(dispatcher).map { it.toDomain() }

    override fun observeSessionSummary(sessionId: Long): Flow<SessionSummary?> =
        queries.selectSessionDetailById(sessionId).asFlow().mapToOneOrNull(dispatcher)
            .map { it?.toDomain() }

    override fun observeDayMuscles(epochDay: Long): Flow<Map<Long, List<String>>> =
        queries.selectDayMuscles(epochDay).asFlow().mapToList(dispatcher).map { rows ->
            // Grouped here rather than in SQL: SQLite has no array type, and
            // group_concat would hand back a string the UI would have to split — which
            // breaks on the first muscle name containing the separator.
            rows.groupBy({ it.session_id }, { it.muscle_name })
        }

    override suspend fun getSession(id: Long): WorkoutSession? = withContext(dispatcher) {
        queries.selectSessionById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun startSession(categoryId: Long?, epochDay: Long, createdAt: Long): Long =
        withContext(dispatcher) {
            // Still a transaction, but for the ordinary reason now rather than to
            // deduplicate: last_insert_rowid() is connection-scoped, so a concurrent
            // write between the insert and the read would hand back another row's id.
            db.transactionWithResult {
                queries.insertSession(categoryId, epochDay, null, createdAt)
                queries.lastInsertRowId().executeAsOne()
            }
        }

    override suspend fun updateNotes(sessionId: Long, notes: String?): Unit =
        withContext(dispatcher) { queries.updateSessionNotes(notes, sessionId) }

    override suspend fun setDistanceTime(
        sessionId: Long,
        distanceMeters: Long?,
        durationSeconds: Long?,
    ): Unit = withContext(dispatcher) {
        queries.setSessionDistanceTime(distanceMeters, durationSeconds, sessionId)
    }

    override suspend fun deleteSession(id: Long): Unit = withContext(dispatcher) {
        // Entries first, in the same transaction, rather than relying on the FK's
        // ON DELETE CASCADE — that action depends on a driver-level pragma.
        db.transaction {
            queries.deleteEntriesForSession(id)
            queries.deleteSession(id)
        }
    }

    override suspend fun addSets(
        sessionId: Long,
        muscleId: Long,
        effectiveSets: Long,
        warmupSets: Long,
    ) = withContext(dispatcher) {
        // Read-then-write, because UPSERT needs SQLite 3.24 (API 29+) and minSdk is
        // 24. The transaction is what makes the pair atomic; without it two
        // concurrent checks of the same muscle would both see "absent" and the
        // second insert would fail on UNIQUE(session_id, muscle_id).
        db.transaction {
            val existing = queries.selectEntry(sessionId, muscleId).executeAsOneOrNull()
            if (existing == null) {
                queries.insertEntry(sessionId, muscleId, effectiveSets, warmupSets)
            } else {
                queries.addToEntry(effectiveSets, warmupSets, sessionId, muscleId)
            }
        }
    }

    override suspend fun setSets(
        sessionId: Long,
        muscleId: Long,
        effectiveSets: Long,
        warmupSets: Long,
    ): Unit = withContext(dispatcher) {
        queries.setEntrySets(effectiveSets, warmupSets, sessionId, muscleId)
    }

    override suspend fun removeMuscle(sessionId: Long, muscleId: Long): Unit =
        withContext(dispatcher) { queries.deleteEntry(sessionId, muscleId) }

    // ----- metrics -----

    override fun observeVolumePerMuscle(fromEpochDay: Long, toEpochDay: Long): Flow<List<MuscleVolume>> =
        queries.volumePerMuscleInRange(fromEpochDay, toEpochDay).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeVolumePerCategory(
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<CategoryVolume>> =
        queries.volumePerCategoryInRange(fromEpochDay, toEpochDay).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeVolumePerGroup(fromEpochDay: Long, toEpochDay: Long): Flow<List<GroupVolume>> =
        queries.volumePerGroupInRange(fromEpochDay, toEpochDay).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeUntrainedMuscles(fromEpochDay: Long, toEpochDay: Long): Flow<List<UntrainedMuscle>> =
        queries.untrainedMusclesInRange(fromEpochDay, toEpochDay).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeDailyVolume(
        muscleId: Long,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<DailyVolume>> =
        queries.dailyVolumeForMuscle(muscleId, fromEpochDay, toEpochDay).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeTrainedDays(
        categoryId: Long,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<Long>> =
        queries.trainedDaysForCategory(categoryId, fromEpochDay, toEpochDay).asFlow().mapToList(dispatcher)
}
