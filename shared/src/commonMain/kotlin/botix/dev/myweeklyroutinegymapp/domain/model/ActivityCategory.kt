package botix.dev.myweeklyroutinegymapp.domain.model

/**
 * The kind of training a session is: "Gym", "Swim", "Bicycle".
 *
 * It **carries** the [MeasurementType], which reverses what this file used to say.
 * The two were kept orthogonal on the reasoning that a measurement type is a closed
 * contract while a category is free-form organisation — true in the abstract, and in
 * practice it made the user say the same thing twice: "Running" the category and
 * `DISTANCE_TIME` the type were one fact split across two questions, asked before
 * anything could be logged.
 *
 * Picking a category is now how you say what you did. The type is still closed and
 * still decided in code; the user reaches it by choosing a category instead of
 * choosing from a second list beside it.
 */
data class ActivityCategory(
    val id: Long,
    val name: String,
    /** Decides which logging form an activity filed here opens. */
    val measurementType: MeasurementType = MeasurementType.STRENGTH,
    val icon: String? = null,
    val color: String? = null,
    val isArchived: Boolean = false,
    /** Epoch millis. Used for a stable "recently created" ordering. */
    val createdAt: Long,
    /** Set on a category the app shipped; see [MuscleGroup.seedKey] for the rules. */
    val seedKey: String? = null,
)

/** A category plus how many logs have been recorded under it — the list's subtitle. */
data class ActivityCategoryUsage(
    val category: ActivityCategory,
    val sessionCount: Long,
)

/**
 * Outcome of deleting a category.
 *
 * Unlike [DeleteOutcome] this has no blocked case. Deleting a muscle is refused
 * because sessions record sets against it and the volume would be orphaned; a
 * category is only the label on a session, and `ON DELETE SET NULL` strips it while
 * every set, day and distance under it survives.
 *
 * [strippedCount] is how many logs lose their label, carried so the UI can say what
 * actually happened rather than reporting a silent success.
 */
data class CategoryDeleted(val strippedCount: Long)
