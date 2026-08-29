package botix.dev.myweeklyroutinegymapp.domain.model

/**
 * A session header: one piece of training, on one day.
 *
 * The entries hanging off it ([SessionEntry]) carry the data; the header fixes *which
 * kind of training* and *which day*. It used to fix which **activity** — a named,
 * reusable thing you had to create first — and that concept is gone (see 6.sqm).
 */
data class WorkoutSession(
    val id: Long,
    /**
     * Which kind of training, and therefore which form it opens.
     *
     * Null is a real state, not a bug: deleting a category strips the label off its
     * logs rather than deleting them, because the sets are the history and the name is
     * not.
     */
    val categoryId: Long? = null,
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

/**
 * One session as the day view lists it: which kind of training, and what it adds up
 * to.
 *
 * A read model, assembled by a single query rather than by fetching each session's
 * entries — the card shows totals, so loading the entries to add them up would be a
 * query per row for numbers SQLite can produce in the same pass.
 */
data class SessionSummary(
    val sessionId: Long,
    val categoryId: Long? = null,
    /**
     * Null when the category was deleted after this was logged. The screen supplies
     * its own wording for that; the model does not invent a name, because inventing
     * one here would make an orphan indistinguishable from a category actually called
     * whatever we chose.
     */
    val categoryName: String? = null,
    /**
     * What this session was measured by, carried from the category rather than
     * inferred from which columns are null — an empty distance log and an empty
     * strength log look identical from the numbers alone.
     */
    val measurementType: MeasurementType = MeasurementType.STRENGTH,
    val epochDay: Long,
    val notes: String? = null,
    val effectiveSets: Long,
    val warmupSets: Long,
    /** How many muscles are checked. Zero means the session was started and left. */
    val muscleCount: Long,
    /** Metres, for a [MeasurementType.DISTANCE_TIME] session. Null for a strength one. */
    val distanceMeters: Long? = null,
    /** Seconds, for a [MeasurementType.DISTANCE_TIME] session. Null for a strength one. */
    val durationSeconds: Long? = null,
) {
    /**
     * A session measured by distance and time rather than by muscles.
     *
     * Decided by what the row holds, not by looking the activity's type up again: a
     * summary that carries either measure describes that kind of session, and asking
     * two tables the same question is how they come to disagree.
     */
    val isDistanceTime: Boolean get() = distanceMeters != null || durationSeconds != null

    /** Nothing logged yet — the card says so rather than showing a bare "0". */
    val isEmpty: Boolean get() = if (isDistanceTime) false else muscleCount == 0L

    /** Seconds per kilometre, the number a runner or a cyclist actually compares. */
    val paceSecondsPerKm: Double?
        get() {
            val metres = distanceMeters ?: return null
            val seconds = durationSeconds ?: return null
            // Both guards matter: a ride saved with only one of the two measures, or
            // with a zero distance, would otherwise divide by zero and render as
            // "Infinity /km".
            return if (metres <= 0L || seconds <= 0L) null else seconds * 1000.0 / metres
        }
}

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

/**
 * What one kind of training added up to over a window — a week.
 *
 * Only exists for categories actually trained in the window. There is no "zero" row:
 * not being in the result is what "not trained" means.
 */
data class CategoryVolume(
    val categoryId: Long?,
    val effectiveSets: Long,
    val warmupSets: Long,
    /** Distinct days trained, not sessions — twice in a day is still one day. */
    val distinctDays: Long,
    val sessionCount: Long,
    /** Metres over the window, summed. Null when nothing in it measured distance. */
    val distanceMeters: Long? = null,
    /** Seconds over the window, summed. Null when nothing in it measured time. */
    val durationSeconds: Long? = null,
) {
    /** Show kilometres and minutes instead of set counts. */
    val isDistanceTime: Boolean get() = distanceMeters != null || durationSeconds != null
}

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
