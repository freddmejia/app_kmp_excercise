package botix.dev.myweeklyroutinegymapp.domain.model

/**
 * Outcome of a hard delete that history may forbid.
 *
 * The rule this expresses is the app's oldest: **deletion is restricted, archiving is
 * the normal path.** It applies to a muscle, because sessions record sets against one
 * and removing it would orphan the volume. It does not apply to a category, which is
 * only the label on a session — see [CategoryDeleted].
 *
 * A type rather than a thrown exception because "you cannot do this yet" is a normal
 * UI state, not an error: the screen offers Archive instead and says why.
 *
 * It lived in `Activity.kt` until activities were removed (see 6.sqm). Nothing else in
 * that file survived, and this is not about activities, so it has a file of its own.
 */
sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome

    /** Referenced by history; archive instead so past metrics survive. */
    data class BlockedByHistory(val referenceCount: Long) : DeleteOutcome
}
