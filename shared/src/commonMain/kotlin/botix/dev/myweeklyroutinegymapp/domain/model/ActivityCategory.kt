package botix.dev.myweeklyroutinegymapp.domain.model

/**
 * A user-created grouping for activities: "Cardio", "Running", "Mobility".
 *
 * Orthogonal to [MeasurementType], and that separation is the whole point. The
 * measurement type is a closed contract — it decides which logging form applies and
 * which metrics exist downstream, so it cannot be user-extended without code. A
 * category constrains nothing and feeds no metric, so it can be free-form data.
 *
 * "Running" is therefore a category; the activity inside it is measured as
 * [MeasurementType.DISTANCE_TIME].
 */
data class ActivityCategory(
    val id: Long,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val isArchived: Boolean = false,
    /** Epoch millis. Used for a stable "recently created" ordering. */
    val createdAt: Long,
)

/** A category plus how many active activities sit in it — the management list's subtitle. */
data class ActivityCategoryUsage(
    val category: ActivityCategory,
    val activityCount: Long,
)

/**
 * Which slice of the catalogue a category filter selects.
 *
 * [Uncategorised] is a real choice rather than the absence of one: activities with no
 * category are otherwise unreachable once you start filtering, and after deleting a
 * category they are exactly the rows you want to find.
 */
sealed interface CategorySelection {
    data object All : CategorySelection
    data object Uncategorised : CategorySelection
    data class Of(val id: Long) : CategorySelection
}

/**
 * Outcome of deleting a category.
 *
 * Unlike [DeleteOutcome] this has no blocked case. Deleting an activity or a muscle
 * is refused because sessions reference them and past metrics would break; nothing
 * references a category except the activities' own label, which `ON DELETE SET NULL`
 * turns back into "uncategorised".
 *
 * [uncategorisedCount] is carried so the UI can say what actually happened rather
 * than reporting a silent success.
 */
data class CategoryDeleted(val uncategorisedCount: Long)
