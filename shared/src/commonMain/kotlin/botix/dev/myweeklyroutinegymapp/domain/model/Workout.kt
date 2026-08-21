package botix.dev.myweeklyroutinegymapp.domain.model

/**
 * A strength session header. The entries hanging off it ([SessionEntry]) are what
 * carry the data; the session itself only fixes *which activity* and *which day*.
 */
data class WorkoutSession(
    val id: Long,
    /** Must reference an activity of type [MeasurementType.STRENGTH]. */
    val activityId: Long,
    val epochDay: Long,
    val notes: String? = null,
    val createdAt: Long,
)

/**
 * One muscle worked in a session. The existence of this row *is* the check —
 * "I trained this muscle that day" — so there is no separate boolean field.
 *
 * A muscle appears once per session; working it again the same day adds to
 * [effectiveSets] rather than creating a second entry.
 */
data class SessionEntry(
    val id: Long,
    val sessionId: Long,
    val muscleId: Long,
    val muscleName: String,
    val groupId: Long,
    val groupName: String,
    /** At least 1. No effective set means the muscle was not really trained. */
    val effectiveSets: Long,
    /** Warmup work: tracked for session quality, deliberately excluded from volume. */
    val warmupSets: Long = 0,
)

/** Totals for one session, as shown in its summary. */
data class SessionTotals(
    val effectiveSets: Long,
    val warmupSets: Long,
) {
    /**
     * Warmup relative to real work. Null when there is no effective volume, since
     * the ratio would be meaningless rather than zero.
     */
    val warmupRatio: Double?
        get() = if (effectiveSets == 0L) null else warmupSets.toDouble() / effectiveSets
}

/** Volume for a single muscle over a window. */
data class MuscleVolume(
    val muscleId: Long,
    val muscleName: String,
    val groupId: Long,
    val groupName: String,
    val effectiveSets: Long,
    val warmupSets: Long,
    /** Distinct days trained — the frequency metric, not a count of entries. */
    val distinctDays: Long,
)

/** Volume for a whole group: the sum of all its leaves, "General" included. */
data class GroupVolume(
    val groupId: Long,
    val groupName: String,
    val effectiveSets: Long,
    val warmupSets: Long,
    val distinctDays: Long,
)

/** A muscle with no effective sets in the queried window. */
data class UntrainedMuscle(
    val muscleId: Long,
    val muscleName: String,
    val groupName: String,
)

/** One day's effective volume for a muscle, for week-over-week progression. */
data class DailyVolume(
    val epochDay: Long,
    val effectiveSets: Long,
)

/**
 * The log picker as the UI draws it: collapsible sections, one per muscle group.
 *
 * Grouping happens once here rather than in each screen, so the "General leaf sorts
 * with its siblings" rule cannot be implemented two different ways.
 */
data class MuscleSection(
    val groupId: Long,
    val groupName: String,
    val muscles: List<MusclePickerItem>,
)

/**
 * Everything the log screen renders for a single session.
 *
 * Bundled into one model so header, entries and totals always come from the same
 * emission — collected separately they could briefly disagree after a write.
 */
data class SessionDetail(
    val session: WorkoutSession?,
    val entries: List<SessionEntry>,
    val totals: SessionTotals,
) {
    /** True before any muscle is checked; the screen shows its empty state. */
    val isEmpty: Boolean get() = entries.isEmpty()
}
