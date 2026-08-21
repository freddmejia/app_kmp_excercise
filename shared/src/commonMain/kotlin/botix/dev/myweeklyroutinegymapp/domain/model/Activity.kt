package botix.dev.myweeklyroutinegymapp.domain.model

/**
 * A user activity: identity plus how it is measured. Upstream of workouts and
 * goals — both consume this list.
 *
 * Holds no session or progress data; that boundary is what keeps
 * [measurementType] meaningful as a contract.
 */
data class Activity(
    val id: Long,
    val name: String,
    val measurementType: MeasurementType,
    val icon: String? = null,
    val color: String? = null,
    /** Optional [ActivityCategory]; null means uncategorised, which is a normal state. */
    val categoryId: Long? = null,
    val isArchived: Boolean = false,
    val isSeed: Boolean = false,
    /** Epoch millis. Used for a stable "recently created" ordering. */
    val createdAt: Long,
)

/** An activity plus how often it has been logged — the picker's default ordering. */
data class ActivityUsage(
    val activity: Activity,
    val sessionCount: Long,
)

/**
 * Result of trying to change an activity's [MeasurementType].
 *
 * Modelled as a type rather than a thrown exception because "you cannot do this
 * yet" is a normal UI state here, not an error: the form disables the field.
 */
sealed interface MeasurementTypeChange {
    data object Changed : MeasurementTypeChange

    /** Immutable once sessions exist — changing it would strand them in an incompatible model. */
    data class BlockedByHistory(val sessionCount: Long) : MeasurementTypeChange
}

/**
 * Result of trying to hard-delete something that history may reference.
 *
 * The specs are explicit that deletion is restricted and archiving is the normal
 * path, so callers are made to handle the blocked case.
 */
sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome

    /** Referenced by history; archive instead so past metrics survive. */
    data class BlockedByHistory(val referenceCount: Long) : DeleteOutcome
}
