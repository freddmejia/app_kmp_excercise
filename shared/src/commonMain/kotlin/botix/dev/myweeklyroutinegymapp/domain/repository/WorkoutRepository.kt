package botix.dev.myweeklyroutinegymapp.domain.repository

import botix.dev.myweeklyroutinegymapp.domain.model.DailyVolume
import botix.dev.myweeklyroutinegymapp.domain.model.GroupVolume
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleVolume
import botix.dev.myweeklyroutinegymapp.domain.model.CategoryVolume
import botix.dev.myweeklyroutinegymapp.domain.model.SessionEntry
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.domain.model.SessionTotals
import botix.dev.myweeklyroutinegymapp.domain.model.UntrainedMuscle
import botix.dev.myweeklyroutinegymapp.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

/**
 * Strength sessions and the metrics derived from them.
 *
 * This repository produces raw data only — sets per muscle per day. Rates, trends
 * and goal evaluation are computed above it, from these series.
 */
interface WorkoutRepository {

    fun observeSessionsInRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<WorkoutSession>>

    /** Every session logged on one day, with its activity name and totals. */
    fun observeDaySummaries(epochDay: Long): Flow<List<SessionSummary>>

    fun observeSession(id: Long): Flow<WorkoutSession?>

    fun observeEntries(sessionId: Long): Flow<List<SessionEntry>>

    fun observeTotals(sessionId: Long): Flow<SessionTotals>

    suspend fun getSession(id: Long): WorkoutSession?

    /**
     * One session with its category name and measurement type, as a Flow.
     *
     * Null once the row is gone. The entry screen reads this rather than the raw
     * [WorkoutSession] because it needs the category's name for the heading and its
     * measurement type to choose a form, and joining once beats two lookups that can
     * disagree.
     */
    fun observeSessionSummary(sessionId: Long): Flow<SessionSummary?>

    /**
     * The muscles each of a day's logs worked, by name, keyed by session.
     *
     * Names rather than counts, because the day card lists them: with no activity name
     * on a session, the muscles are what tell two Gym logs on one day apart.
     */
    fun observeDayMuscles(epochDay: Long): Flow<Map<Long, List<String>>>

    /**
     * Starts a log. Always a new row.
     *
     * It used to find-or-create by (activity, day), because an activity could only be
     * logged once a day and reopening was the common case. Neither holds now: a day
     * can hold two Gym sessions — a morning and an evening, or chest and then legs —
     * and they are told apart by what is in them rather than by a name. Reopening is
     * tapping the row, which carries the session id.
     *
     * [categoryId] may be null only in the sense that the column allows it; callers
     * pass what the user picked.
     */
    suspend fun startSession(categoryId: Long?, epochDay: Long, createdAt: Long): Long

    suspend fun updateNotes(sessionId: Long, notes: String?)

    /**
     * Records what a distance/time session covered, in metres and seconds.
     *
     * Replaces rather than accumulates: a second ride on the same day is a second
     * session — startSession always makes one — so the only reason to write these
     * twice is to correct them.
     */
    suspend fun setDistanceTime(sessionId: Long, distanceMeters: Long?, durationSeconds: Long?)

    /** Deleting a session removes its entries via the cascade. */
    suspend fun deleteSession(id: Long)

    /**
     * Checks a muscle, or adds to it if already checked in this session.
     *
     * Runs read-then-write in a single transaction: a one-statement upsert would
     * need SQLite 3.24 (API 29+) and minSdk here is 24.
     *
     * @param effectiveSets must be >= 1; the schema rejects anything lower.
     */
    suspend fun addSets(sessionId: Long, muscleId: Long, effectiveSets: Long, warmupSets: Long = 0)

    /** Overwrites an entry's counters rather than adding to them. */
    suspend fun setSets(sessionId: Long, muscleId: Long, effectiveSets: Long, warmupSets: Long)

    /** Unchecking a muscle: the entry ceases to exist. */
    suspend fun removeMuscle(sessionId: Long, muscleId: Long)

    // ----- metrics -----

    fun observeVolumePerMuscle(fromEpochDay: Long, toEpochDay: Long): Flow<List<MuscleVolume>>

    fun observeVolumePerGroup(fromEpochDay: Long, toEpochDay: Long): Flow<List<GroupVolume>>

    /** Per-activity totals over a window; activities with no session are absent. */
    fun observeVolumePerCategory(fromEpochDay: Long, toEpochDay: Long): Flow<List<CategoryVolume>>

    /** Which muscles went untouched in the window — the "what am I neglecting" read. */
    fun observeUntrainedMuscles(fromEpochDay: Long, toEpochDay: Long): Flow<List<UntrainedMuscle>>

    fun observeDailyVolume(muscleId: Long, fromEpochDay: Long, toEpochDay: Long): Flow<List<DailyVolume>>

    /** Distinct days an activity was trained: feeds streaks, gaps and goal windows. */
    fun observeTrainedDays(categoryId: Long, fromEpochDay: Long, toEpochDay: Long): Flow<List<Long>>
}
