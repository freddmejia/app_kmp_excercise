package botix.dev.myweeklyroutinegymapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import botix.dev.myweeklyroutinegymapp.data.ioDispatcher
import botix.dev.myweeklyroutinegymapp.data.mapper.toDomain
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.domain.model.MusclePickerItem
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityMuscleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ActivityMuscleRepositoryImpl(
    private val db: GymDatabase,
    private val dispatcher: CoroutineDispatcher = ioDispatcher,
) : ActivityMuscleRepository {

    private val queries get() = db.activityMuscleQueries

    override fun observeMusclesFor(activityId: Long): Flow<List<MusclePickerItem>> =
        queries.selectPickerForActivity(activityId)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeAllLinks(): Flow<Map<Long, Set<Long>>> =
        queries.selectAllLinks()
            .asFlow()
            .mapToList(dispatcher)
            .map { rows ->
                rows.groupBy({ it.activity_id }, { it.muscle_id })
                    .mapValues { (_, ids) -> ids.toSet() }
            }

    override suspend fun setMusclesFor(activityId: Long, muscleIds: Set<Long>): Unit =
        withContext(dispatcher) {
            // Delete-then-insert inside one transaction rather than diffing. The set
            // is a handful of rows, and a single transaction means no observer ever
            // sees the half-applied state where the old links are gone and the new
            // ones are not yet written.
            db.transaction {
                queries.deleteAllForActivity(activityId)
                muscleIds.forEach { queries.insertLink(activityId, it) }
            }
        }
}
