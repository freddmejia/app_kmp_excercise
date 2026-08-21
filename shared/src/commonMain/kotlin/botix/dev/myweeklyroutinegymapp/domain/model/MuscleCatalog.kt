package botix.dev.myweeklyroutinegymapp.domain.model

/** A muscle group ("Chest", "Back"). Parent of the leaves actually logged against. */
data class MuscleGroup(
    val id: Long,
    val name: String,
    val isArchived: Boolean = false,
    val createdAt: Long,
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
)

/** A row in the log picker: a leaf with its group name and how often it is used. */
data class MusclePickerItem(
    val muscleId: Long,
    val muscleName: String,
    val groupId: Long,
    val groupName: String,
    val usageCount: Long,
)
