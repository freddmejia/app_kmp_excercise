package botix.dev.myweeklyroutinegymapp.domain.usecase.workout

import botix.dev.myweeklyroutinegymapp.domain.model.MusclePickerItem
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.SessionEntry
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityMuscleRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The optional activity -> muscle link.
 *
 * Lives in the workout package rather than the activity one because what the link
 * *does* is scope the log picker; the activity screen only edits it.
 */

/**
 * The log picker, narrowed to an activity's muscles when it has any.
 *
 * [isScopedToActivity] is part of the result rather than something the screen infers
 * from list sizes: "these are this activity's muscles" and "this activity has no
 * opinion so here is everything" look identical once rendered, and the user has to
 * be able to tell which one they are looking at.
 */
data class MusclePickerView(
    val sections: List<MuscleSection>,
    val isScopedToActivity: Boolean,
)

/**
 * Groups picker rows into their sections.
 *
 * Shared by the scoped and unscoped paths so the ordering rule — and the "General
 * leaf sorts with its siblings" behaviour that depends on it — exists once.
 */
internal fun List<MusclePickerItem>.toSections(): List<MuscleSection> =
    groupBy { it.groupId to it.groupName }
        .map { (group, muscles) ->
            MuscleSection(groupId = group.first, groupName = group.second, muscles = muscles)
        }

class ObserveActivityMuscleSectionsUseCase(
    private val links: ActivityMuscleRepository,
    private val catalog: MuscleCatalogRepository,
) {
    /**
     * Falls back to the whole catalogue when the activity links nothing.
     *
     * No links means "not specified", not "targets no muscles" — returning an empty
     * picker would make the activity unloggable, which is the opposite of what an
     * optional field should be able to do.
     *
     * The fallback also covers a subtler case: every linked muscle being archived.
     * The scoped query filters those out, so without this the picker would go empty
     * and the screen would look broken rather than degraded.
     */
    operator fun invoke(activityId: Long): Flow<MusclePickerView> =
        combine(links.observeMusclesFor(activityId), catalog.observePicker()) { scoped, all ->
            if (scoped.isEmpty()) {
                MusclePickerView(all.toSections(), isScopedToActivity = false)
            } else {
                MusclePickerView(scoped.toSections(), isScopedToActivity = true)
            }
        }
}

/**
 * Every activity's linked muscle ids.
 *
 * One subscription serves both the "targets N muscles" subtitle and the tick state of
 * whichever row the user opens, so the screen needs no second query when the dialog
 * opens and no re-query when it closes.
 */
class ObserveActivityMuscleLinksUseCase(private val links: ActivityMuscleRepository) {
    operator fun invoke(): Flow<Map<Long, Set<Long>>> = links.observeAllLinks()
}

/**
 * Replaces an activity's selection.
 *
 * An empty set is a legitimate value — it clears the link and returns the activity to
 * "no opinion", which is why this takes the whole set rather than offering add/remove.
 */
class SetActivityMusclesUseCase(private val links: ActivityMuscleRepository) {
    suspend operator fun invoke(activityId: Long, muscleIds: Set<Long>) =
        links.setMusclesFor(activityId, muscleIds)
}

/**
 * Puts back any muscle that is checked in the session but outside the activity's
 * scope.
 *
 * Without this, logging a muscle the activity does not link and then narrowing the
 * picker hides the row while its sets keep counting toward the totals — the numbers
 * say five sets and nothing on screen accounts for two of them, and there is no way
 * to correct or uncheck the entry short of widening the list again.
 *
 * Scoping is a filter on *what to offer*, never on what is already recorded.
 */
fun MusclePickerView.includingChecked(entries: List<SessionEntry>): MusclePickerView {
    if (!isScopedToActivity || entries.isEmpty()) return this

    val shown = sections.flatMap { section -> section.muscles.map { it.muscleId } }.toSet()
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

    val merged = (sections.flatMap { it.muscles } + extra).toSections()
    // Same ordering as the queries produce, so a re-added row does not jump around
    // relative to the ones that were already there.
    return copy(sections = merged.sortedBy { it.groupName })
}
