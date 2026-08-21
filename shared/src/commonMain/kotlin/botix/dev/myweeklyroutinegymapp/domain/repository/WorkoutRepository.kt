package botix.dev.myweeklyroutinegymapp.domain.repository

import botix.dev.myweeklyroutinegymapp.domain.model.DailyVolume
import botix.dev.myweeklyroutinegymapp.domain.model.GroupVolume
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleVolume
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityVolume
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

    /** The session for an activity on a given day, if one was already started. */
    suspend fun findSession(activityId: Long, epochDay: Long): WorkoutSession?

    /**
     * Returns the existing session for that activity and day, or creates one.
     *
     * Logging is day-oriented, so callers should not create a second session for a
     * day that already has one.
     */
    suspend fun startOrResumeSession(activityId: Long, epochDay: Long, createdAt: Long): Long

    suspend fun updateNotes(sessionId: Long, notes: String?)

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
    fun observeVolumePerActivity(fromEpochDay: Long, toEpochDay: Long): Flow<List<ActivityVolume>>

    /** Which muscles went untouched in the window — the "what am I neglecting" read. */
    fun observeUntrainedMuscles(fromEpochDay: Long, toEpochDay: Long): Flow<List<UntrainedMuscle>>

    fun observeDailyVolume(muscleId: Long, fromEpochDay: Long, toEpochDay: Long): Flow<List<DailyVolume>>

    /** Distinct days an activity was trained: feeds streaks, gaps and goal windows. */
    fun observeTrainedDays(activityId: Long, fromEpochDay: Long, toEpochDay: Long): Flow<List<Long>>
}
