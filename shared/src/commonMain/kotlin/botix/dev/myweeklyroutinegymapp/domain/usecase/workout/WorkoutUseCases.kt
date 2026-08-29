package botix.dev.myweeklyroutinegymapp.domain.usecase.workout

import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.DailyVolume
import botix.dev.myweeklyroutinegymapp.domain.model.GroupVolume
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleVolume
import botix.dev.myweeklyroutinegymapp.domain.model.CategoryVolume
import botix.dev.myweeklyroutinegymapp.domain.model.SessionDetail
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.domain.model.SessionTotals
import botix.dev.myweeklyroutinegymapp.domain.model.UntrainedMuscle
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

sealed interface StartSessionResult {
    data class Started(val sessionId: Long) : StartSessionResult

    /** The category was deleted between the dialog listing it and this being called. */
    data object CategoryNotFound : StartSessionResult
}

/**
 * The outcome of logging a distance/time session.
 *
 * A separate result type from [StartSessionResult] because this flow finishes in one
 * step: there is no session left open to add to, so "started" would be a lie.
 */
sealed interface LogDistanceTimeResult {
    data object Logged : LogDistanceTimeResult

    /**
     * Neither measure was given, or one of them was not positive.
     *
     * A ride of no distance in no time is not a training record — it is an empty form
     * submitted. One of the two alone is allowed: a swim with no distance measured,
     * or a route walked without timing it, are both real things to log.
     */
    data object NothingMeasured : LogDistanceTimeResult
}

sealed interface LogSetsResult {
    data object Logged : LogSetsResult

    /** Fewer than one effective set is equivalent to not having checked the muscle. */
    data object InvalidSets : LogSetsResult
}

/**
 * Starts a log: one session, on one day, under one category.
 *
 * ### It always creates
 * It took an activity and found-or-created by (activity, day). Neither half survives:
 * there are no activities, and a day may hold several logs of the same kind — two Gym
 * sessions on one Tuesday are two rows, told apart by what is in them rather than by a
 * name. Reopening is tapping the row.
 *
 * ### It no longer asks what the category measures
 * There used to be a guard here refusing anything but strength, because this opened a
 * session for muscles to be checked into while a distance log was written in one go by
 * a different use case. Both shapes start with a row now, and the form that follows is
 * chosen from the category's measurement type — so a guard here would only be able to
 * refuse work it is not doing.
 *
 * The category still has to exist. That is the one thing that can be wrong by the time
 * this runs: the dialog listed it, and it could have been deleted since.
 */
class StartSessionUseCase(
    private val workouts: WorkoutRepository,
    private val categories: ActivityCategoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(categoryId: Long, epochDay: Long = clock.today()): StartSessionResult {
        categories.getById(categoryId) ?: return StartSessionResult.CategoryNotFound
        return StartSessionResult.Started(
            workouts.startSession(categoryId, epochDay, clock.nowMillis()),
        )
    }
}


/**
 * Corrects a distance/time session that already exists.
 *
 * Separate from [LogDistanceTimeUseCase] now that starting always creates: without
 * this, reopening a ride to fix "30" to "31" would add a second ride to the day.
 */
class SetSessionDistanceTimeUseCase(private val workouts: WorkoutRepository) {
    suspend operator fun invoke(
        sessionId: Long,
        distanceMeters: Long?,
        durationSeconds: Long?,
    ): LogDistanceTimeResult {
        val measures = measured(distanceMeters, durationSeconds)
            ?: return LogDistanceTimeResult.NothingMeasured
        workouts.setDistanceTime(sessionId, measures.first, measures.second)
        return LogDistanceTimeResult.Logged
    }
}

/**
 * The pair to store, or null when neither measure is real.
 *
 * Zero is rejected rather than stored: it reads as "measured, and it was none", which
 * is never what was meant. Either measure alone is a real record — a swim nobody
 * measured, a route nobody timed — so this asks for one of the two, not both.
 */
private fun measured(distanceMeters: Long?, durationSeconds: Long?): Pair<Long?, Long?>? {
    val distance = distanceMeters?.takeIf { it > 0L }
    val duration = durationSeconds?.takeIf { it > 0L }
    return if (distance == null && duration == null) null else distance to duration
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
    // Grouping lives in toSections(), shared with the activity-scoped picker, so the
    // two cannot drift into different orderings of the same catalogue.
    operator fun invoke(): Flow<List<MuscleSection>> =
        catalog.observePicker().map { it.toSections() }
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

/**
 * Per-activity volume over an explicit range — the Activities screen's week.
 *
 * Takes the range rather than a trailing window because the screen navigates to
 * arbitrary weeks, including future ones. A "last N days" shape could not express
 * the week the user is actually looking at.
 */
class ObserveCategoryVolumeUseCase(private val workouts: WorkoutRepository) {
    operator fun invoke(fromEpochDay: Long, toEpochDay: Long): Flow<List<CategoryVolume>> =
        workouts.observeVolumePerCategory(fromEpochDay, toEpochDay)
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
    /**
     * [endDay] closes the window, defaulting to today.
     *
     * Parameterised so the log screen can anchor it to the day being viewed: while
     * you are looking at last Tuesday, "not trained in the last 7 days" means the
     * week up to that Tuesday. Measured against today it would describe a week the
     * screen is not showing.
     */
    operator fun invoke(
        windowDays: Long = 7,
        endDay: Long = clock.today(),
    ): Flow<List<UntrainedMuscle>> =
        workouts.observeUntrainedMuscles(endDay - windowDays + 1, endDay)
}

/**
 * Which days in a range already have a session, for the day strip's markers.
 *
 * Any activity, not just the selected one: the marker answers "did I train that
 * day", which is the question you are asking while scanning dates for one to open.
 */
class ObserveSessionDaysUseCase(private val workouts: WorkoutRepository) {
    operator fun invoke(fromEpochDay: Long, toEpochDay: Long): Flow<Set<Long>> =
        workouts.observeSessionsInRange(fromEpochDay, toEpochDay)
            .map { sessions -> sessions.map { it.epochDay }.toSet() }
}

/**
 * Everything logged on one day, for the day view's list.
 *
 * A day can hold more than one session — different activities, or a morning and an
 * evening — so this is a list, not the single "current session" the screen used to
 * assume.
 */
/**
 * One log, with its category resolved — what the entry screen opens on.
 *
 * Emits null when the session is gone, which is a state the screen has to handle: the
 * day's list can delete a log while its form is open behind it.
 */
class ObserveSessionSummaryUseCase(private val workouts: WorkoutRepository) {
    operator fun invoke(sessionId: Long): Flow<SessionSummary?> =
        workouts.observeSessionSummary(sessionId)
}

/** The muscles worked in each of a day's logs, for the cards to list. */
class ObserveDayMusclesUseCase(private val workouts: WorkoutRepository) {
    operator fun invoke(epochDay: Long): Flow<Map<Long, List<String>>> =
        workouts.observeDayMuscles(epochDay)
}

class ObserveDayLogUseCase(private val workouts: WorkoutRepository) {
    operator fun invoke(epochDay: Long): Flow<List<SessionSummary>> =
        workouts.observeDaySummaries(epochDay)
}

/**
 * Removes a whole session and the entries under it.
 *
 * Necessary once creating a log is an explicit act: starting one and changing your
 * mind leaves a real row, and without this it would sit in the day list forever
 * counting toward every frequency metric. `session_muscle` cascades from
 * `workout_session`, so its entries go with it.
 */
class DeleteSessionUseCase(private val workouts: WorkoutRepository) {
    suspend operator fun invoke(sessionId: Long) = workouts.deleteSession(sessionId)
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
    operator fun invoke(categoryId: Long, windowDays: Long = 28): Flow<List<Long>> {
        val today = clock.today()
        return workouts.observeTrainedDays(categoryId, today - windowDays + 1, today)
    }
}

/** Totals for a session without its entries, for a compact summary row. */
class ObserveSessionTotalsUseCase(private val workouts: WorkoutRepository) {
    operator fun invoke(sessionId: Long): Flow<SessionTotals> = workouts.observeTotals(sessionId)
}
