package botix.dev.myweeklyroutinegymapp.domain.usecase.workout

import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.DailyVolume
import botix.dev.myweeklyroutinegymapp.domain.model.GroupVolume
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleVolume
import botix.dev.myweeklyroutinegymapp.domain.model.SessionDetail
import botix.dev.myweeklyroutinegymapp.domain.model.SessionTotals
import botix.dev.myweeklyroutinegymapp.domain.model.UntrainedMuscle
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

sealed interface StartSessionResult {
    data class Started(val sessionId: Long) : StartSessionResult

    data object ActivityNotFound : StartSessionResult

    /**
     * This flow captures muscles and sets, which only means anything for a strength
     * activity. A distance or presence activity uses its own form.
     */
    data object NotAStrengthActivity : StartSessionResult
}

sealed interface LogSetsResult {
    data object Logged : LogSetsResult

    /** Fewer than one effective set is equivalent to not having checked the muscle. */
    data object InvalidSets : LogSetsResult
}

/**
 * Opens the session for an activity on a day, reusing the existing one if the day was
 * already started.
 *
 * The strength check lives here rather than in the repository because it is a rule
 * about *which flow applies*, not about storage integrity — the schema is happy to
 * hold the row either way.
 */
class StartOrResumeSessionUseCase(
    private val workouts: WorkoutRepository,
    private val activities: ActivityRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(activityId: Long, epochDay: Long = clock.today()): StartSessionResult {
        val activity = activities.getById(activityId) ?: return StartSessionResult.ActivityNotFound
        if (activity.measurementType != MeasurementType.STRENGTH) {
            return StartSessionResult.NotAStrengthActivity
        }
        return StartSessionResult.Started(
            workouts.startOrResumeSession(activityId, epochDay, clock.nowMillis()),
        )
    }
}

/**
 * Checks a muscle, or adds to it when it is already checked in this session.
 *
 * Accumulating is deliberate: training a muscle at two points in the same day is one
 * entry with the sets summed, not two entries.
 */
class LogMuscleSetsUseCase(private val workouts: WorkoutRepository) {
    suspend operator fun invoke(
        sessionId: Long,
        muscleId: Long,
        effectiveSets: Long,
        warmupSets: Long = 0,
    ): LogSetsResult {
        if (effectiveSets < 1 || warmupSets < 0) return LogSetsResult.InvalidSets
        workouts.addSets(sessionId, muscleId, effectiveSets, warmupSets)
        return LogSetsResult.Logged
    }
}

/** Corrects an entry to exact values, where [LogMuscleSetsUseCase] would add to them. */
class SetMuscleSetsUseCase(private val workouts: WorkoutRepository) {
    suspend operator fun invoke(
        sessionId: Long,
        muscleId: Long,
        effectiveSets: Long,
        warmupSets: Long = 0,
    ): LogSetsResult {
        if (effectiveSets < 1 || warmupSets < 0) return LogSetsResult.InvalidSets
        workouts.setSets(sessionId, muscleId, effectiveSets, warmupSets)
        return LogSetsResult.Logged
    }
}

/** Unchecking a muscle: the entry stops existing. */
class RemoveMuscleFromSessionUseCase(private val workouts: WorkoutRepository) {
    suspend operator fun invoke(sessionId: Long, muscleId: Long) =
        workouts.removeMuscle(sessionId, muscleId)
}

/** Header, entries and totals for one session, always from a single emission. */
class ObserveSessionDetailUseCase(private val workouts: WorkoutRepository) {
    operator fun invoke(sessionId: Long): Flow<SessionDetail> = combine(
        workouts.observeSession(sessionId),
        workouts.observeEntries(sessionId),
        workouts.observeTotals(sessionId),
    ) { session, entries, totals ->
        SessionDetail(session, entries, totals)
    }
}

/**
 * The picker, grouped into sections.
 *
 * The query already orders by group then usage, so grouping preserves that order —
 * `groupBy` on a `List` keeps first-seen order, which is what makes this safe.
 */
class ObserveMusclePickerUseCase(private val catalog: MuscleCatalogRepository) {
    operator fun invoke(): Flow<List<MuscleSection>> = catalog.observePicker().map { items ->
        items.groupBy { it.groupId to it.groupName }
            .map { (group, muscles) ->
                MuscleSection(groupId = group.first, groupName = group.second, muscles = muscles)
            }
    }
}

/** Volume per group over a window; the default window is the trailing week. */
class ObserveGroupVolumeUseCase(
    private val workouts: WorkoutRepository,
    private val clock: Clock,
) {
    operator fun invoke(windowDays: Long = 7): Flow<List<GroupVolume>> {
        val today = clock.today()
        return workouts.observeVolumePerGroup(today - windowDays + 1, today)
    }

    operator fun invoke(fromEpochDay: Long, toEpochDay: Long): Flow<List<GroupVolume>> =
        workouts.observeVolumePerGroup(fromEpochDay, toEpochDay)
}

/** Volume per individual muscle over a window. */
class ObserveMuscleVolumeUseCase(
    private val workouts: WorkoutRepository,
    private val clock: Clock,
) {
    operator fun invoke(windowDays: Long = 7): Flow<List<MuscleVolume>> {
        val today = clock.today()
        return workouts.observeVolumePerMuscle(today - windowDays + 1, today)
    }
}

/** What went untouched in the window — the "what am I neglecting" read. */
class ObserveUntrainedMusclesUseCase(
    private val workouts: WorkoutRepository,
    private val clock: Clock,
) {
    operator fun invoke(windowDays: Long = 7): Flow<List<UntrainedMuscle>> {
        val today = clock.today()
        return workouts.observeUntrainedMuscles(today - windowDays + 1, today)
    }
}

/**
 * Day-by-day effective volume for one muscle.
 *
 * Progression here is measured in sets, not load: the flow does not capture weight,
 * so there is no e1RM to trend.
 */
class ObserveMuscleProgressionUseCase(
    private val workouts: WorkoutRepository,
    private val clock: Clock,
) {
    operator fun invoke(muscleId: Long, windowDays: Long = 56): Flow<List<DailyVolume>> {
        val today = clock.today()
        return workouts.observeDailyVolume(muscleId, today - windowDays + 1, today)
    }
}

/** Distinct days an activity was trained: feeds streaks, gaps and goal windows. */
class ObserveTrainedDaysUseCase(
    private val workouts: WorkoutRepository,
    private val clock: Clock,
) {
    operator fun invoke(activityId: Long, windowDays: Long = 28): Flow<List<Long>> {
        val today = clock.today()
        return workouts.observeTrainedDays(activityId, today - windowDays + 1, today)
    }
}

/** Totals for a session without its entries, for a compact summary row. */
class ObserveSessionTotalsUseCase(private val workouts: WorkoutRepository) {
    operator fun invoke(sessionId: Long): Flow<SessionTotals> = workouts.observeTotals(sessionId)
}
