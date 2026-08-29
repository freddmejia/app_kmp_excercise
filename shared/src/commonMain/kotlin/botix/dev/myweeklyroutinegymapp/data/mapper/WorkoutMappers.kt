package botix.dev.myweeklyroutinegymapp.data.mapper

import botix.dev.myweeklyroutinegymapp.db.DailyVolumeForMuscle
import botix.dev.myweeklyroutinegymapp.db.SelectDaySummaries
import botix.dev.myweeklyroutinegymapp.db.SelectSessionDetailById
import botix.dev.myweeklyroutinegymapp.db.SelectEntriesForSession
import botix.dev.myweeklyroutinegymapp.db.UntrainedMusclesInRange
import botix.dev.myweeklyroutinegymapp.db.VolumePerCategoryInRange
import botix.dev.myweeklyroutinegymapp.db.VolumePerGroupInRange
import botix.dev.myweeklyroutinegymapp.db.VolumePerMuscleInRange
import botix.dev.myweeklyroutinegymapp.db.Workout_session
import botix.dev.myweeklyroutinegymapp.domain.model.CategoryVolume
import botix.dev.myweeklyroutinegymapp.domain.model.DailyVolume
import botix.dev.myweeklyroutinegymapp.domain.model.GroupVolume
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleVolume
import botix.dev.myweeklyroutinegymapp.domain.model.SessionEntry
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.domain.model.UntrainedMuscle
import botix.dev.myweeklyroutinegymapp.domain.model.WorkoutSession
import botix.dev.myweeklyroutinegymapp.db.SessionTotals as SessionTotalsRow
import botix.dev.myweeklyroutinegymapp.domain.model.SessionTotals as SessionTotalsModel

internal fun Workout_session.toDomain(): WorkoutSession = WorkoutSession(
    id = id,
    categoryId = category_id,
    epochDay = date_epoch_day,
    notes = notes,
    createdAt = created_at,
)

/**
 * Entries arrive pre-joined with muscle and group names, so the UI can render a
 * session summary without a second lookup per row.
 */
internal fun SelectEntriesForSession.toDomain(): SessionEntry = SessionEntry(
    id = id,
    sessionId = session_id,
    muscleId = muscle_id,
    muscleName = muscle_name,
    groupId = group_id,
    groupName = group_name,
    effectiveSets = effective_sets,
    warmupSets = warmup_sets,
)

internal fun SessionTotalsRow.toDomain(): SessionTotalsModel = SessionTotalsModel(
    effectiveSets = total_effective,
    warmupSets = total_warmup,
)

internal fun VolumePerMuscleInRange.toDomain(): MuscleVolume = MuscleVolume(
    muscleId = muscle_id,
    muscleName = muscle_name,
    groupId = group_id,
    groupName = group_name,
    effectiveSets = effective_sets,
    warmupSets = warmup_sets,
    distinctDays = distinct_days,
)

internal fun VolumePerGroupInRange.toDomain(): GroupVolume = GroupVolume(
    groupId = group_id,
    groupName = group_name,
    effectiveSets = effective_sets,
    warmupSets = warmup_sets,
    distinctDays = distinct_days,
)

internal fun UntrainedMusclesInRange.toDomain(): UntrainedMuscle = UntrainedMuscle(
    muscleId = muscle_id,
    muscleName = muscle_name,
    groupName = group_name,
)

internal fun DailyVolumeForMuscle.toDomain(): DailyVolume = DailyVolume(
    epochDay = date_epoch_day,
    effectiveSets = effective_sets,
)

internal fun SelectDaySummaries.toDomain(): SessionSummary = SessionSummary(
    sessionId = id,
    categoryId = category_id,
    categoryName = category_name,
    // The query LEFT JOINs the category, so this is null for a log whose category was
    // deleted. Strength is the fallback because it is the form with a picker behind
    // it — the one an orphan can still be opened and corrected in.
    measurementType = measurement_type ?: MeasurementType.STRENGTH,
    epochDay = date_epoch_day,
    notes = notes,
    effectiveSets = effective_sets,
    warmupSets = warmup_sets,
    muscleCount = muscle_count,
    distanceMeters = distance_meters,
    durationSeconds = duration_seconds,
)

internal fun VolumePerCategoryInRange.toDomain(): CategoryVolume = CategoryVolume(
    categoryId = category_id,
    effectiveSets = effective_sets,
    warmupSets = warmup_sets,
    distinctDays = distinct_days,
    sessionCount = session_count,
    distanceMeters = distance_meters,
    durationSeconds = duration_seconds,
)

/**
 * The single-session read. Same columns as [SelectDaySummaries] minus the totals,
 * which the entry screen gets from the session detail instead — so the two agree
 * about what a session *is* without either recomputing the other's numbers.
 */
internal fun SelectSessionDetailById.toDomain(): SessionSummary = SessionSummary(
    sessionId = id,
    categoryId = category_id,
    categoryName = category_name,
    measurementType = measurement_type ?: MeasurementType.STRENGTH,
    epochDay = date_epoch_day,
    notes = notes,
    effectiveSets = 0,
    warmupSets = 0,
    muscleCount = 0,
    distanceMeters = distance_meters,
    durationSeconds = duration_seconds,
)
