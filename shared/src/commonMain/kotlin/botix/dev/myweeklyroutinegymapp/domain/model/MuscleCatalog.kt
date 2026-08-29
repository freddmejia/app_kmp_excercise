package botix.dev.myweeklyroutinegymapp.domain.model

/** A muscle group ("Chest", "Back"). Parent of the leaves actually logged against. */
data class MuscleGroup(
    val id: Long,
    val name: String,
    val isArchived: Boolean = false,
    val createdAt: Long,
    /**
     * Set on a group the app shipped, null on one the user made — and null again the
     * moment they rename it, because a name they chose is theirs. Its only job is to
     * mark the rows whose [name] the language sync may rewrite.
     */
    val seedKey: String? = null,
)

/**
 * A leaf muscle. Sessions always reference a leaf, never a group directly — each
 * group ships a "General" leaf so group-level logging still aggregates uniformly.
 */
data class Muscle(
    val id: Long,
    val groupId: Long,
    val name: String,
    val isArchived: Boolean = false,
    val createdAt: Long,
    /** See [MuscleGroup.seedKey]. */
    val seedKey: String? = null,
)

/** A row in the log picker: a leaf with its group name and how often it is used. */
data class MusclePickerItem(
    val muscleId: Long,
    val muscleName: String,
    val groupId: Long,
    val groupName: String,
    val usageCount: Long,
)
