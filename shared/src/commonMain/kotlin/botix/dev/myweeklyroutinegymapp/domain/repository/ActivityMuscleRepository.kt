package botix.dev.myweeklyroutinegymapp.domain.repository

import botix.dev.myweeklyroutinegymapp.domain.model.MusclePickerItem
import kotlinx.coroutines.flow.Flow

/**
 * The optional link between an activity and the muscles it targets.
 *
 * Its own interface rather than a few methods bolted onto [ActivityRepository] or
 * [MuscleCatalogRepository]: the relationship belongs to neither catalogue, and
 * putting it on one would drag that catalogue's model into the other's.
 *
 * An activity with no links has *no opinion*, which is different from targeting
 * nothing — callers fall back to the full catalogue rather than showing an empty
 * picker.
 */
interface ActivityMuscleRepository {

    /**
     * One activity's muscles as log-picker rows: active only, ordered like the
     * unscoped picker so both can feed the same section grouping.
     *
     * Empty both when nothing is linked and when every link points at an archived
     * muscle. Callers treat the two the same way — fall back to the full catalogue.
     */
    fun observeMusclesFor(activityId: Long): Flow<List<MusclePickerItem>>

    /**
     * Every link in the table, keyed by activity.
     *
     * One query instead of one per activity. The management screen needs both a count
     * per row and the exact set for whichever row is being edited, and this answers
     * both from a single subscription over what is a very small table.
     *
     * Archived muscles are included: the selection form saves what it shows, so
     * filtering them here would quietly drop those links on the next save.
     */
    fun observeAllLinks(): Flow<Map<Long, Set<Long>>>

    /**
     * Replaces an activity's whole selection in one transaction.
     *
     * An empty set is a legitimate value — it clears the link and returns the
     * activity to "no opinion", which is why this takes the set rather than offering
     * add and remove.
     */
    suspend fun setMusclesFor(activityId: Long, muscleIds: Set<Long>)
}
