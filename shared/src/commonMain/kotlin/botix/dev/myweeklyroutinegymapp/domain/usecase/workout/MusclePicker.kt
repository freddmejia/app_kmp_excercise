package botix.dev.myweeklyroutinegymapp.domain.usecase.workout

import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.MusclePickerItem
import botix.dev.myweeklyroutinegymapp.domain.model.SessionEntry

/**
 * Shaping the muscle list the log screen offers.
 *
 * These lived in `ActivityMuscleUseCases.kt`, alongside the machinery that narrowed
 * the picker to one activity's muscles. That narrowing is gone with activities — a
 * category is too coarse to be a useful default, since "Gym" does not mean chest — so
 * the picker is always the whole catalogue, ordered by how often each muscle is used.
 * What survived is the grouping and the one rule below, neither of which was ever
 * about activities.
 */

/**
 * Groups picker rows into their sections.
 *
 * Kept as a function rather than inlined so the ordering rule — and the "General leaf
 * sorts with its siblings" behaviour that depends on it — exists in one place. The
 * query already orders by group then usage, and `groupBy` on a `List` preserves
 * first-seen order, which is what makes this safe.
 */
internal fun List<MusclePickerItem>.toSections(): List<MuscleSection> =
    groupBy { it.groupId to it.groupName }
        .map { (group, muscles) ->
            MuscleSection(groupId = group.first, groupName = group.second, muscles = muscles)
        }

/**
 * Puts back any muscle that is checked in this session but missing from the picker.
 *
 * Only one thing can cause that now: the muscle was **archived** after being logged.
 * The picker query filters archived leaves out, so without this the row disappears
 * while its sets keep counting toward the session totals — the numbers say five sets
 * and nothing on screen accounts for two of them, and there is no way to correct or
 * uncheck the entry at all.
 *
 * The picker decides *what to offer*. It never decides what is already recorded.
 */
fun List<MuscleSection>.includingChecked(entries: List<SessionEntry>): List<MuscleSection> {
    if (entries.isEmpty()) return this

    val shown = flatMap { section -> section.muscles.map { it.muscleId } }.toSet()
    val missing = entries.filterNot { it.muscleId in shown }
    if (missing.isEmpty()) return this

    val extra = missing.map {
        MusclePickerItem(
            muscleId = it.muscleId,
            muscleName = it.muscleName,
            groupId = it.groupId,
            groupName = it.groupName,
            // Ordering only, and these rows are pinned by being checked rather than
            // ranked by use, so the real count would not change where they land.
            usageCount = 0,
        )
    }
    val byGroup = extra.groupBy { it.groupId }
    val merged = map { section ->
        byGroup[section.groupId]
            ?.let { section.copy(muscles = section.muscles + it) }
            ?: section
    }
    // A whole group can be missing too, when every leaf of it was archived.
    val known = map { it.groupId }.toSet()
    return merged + extra.filterNot { it.groupId in known }.toSections()
}
