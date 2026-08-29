package botix.dev.myweeklyroutinegymapp.data

import botix.dev.myweeklyroutinegymapp.domain.model.SeededGroup
import botix.dev.myweeklyroutinegymapp.domain.model.SeededMuscle
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleSeedSource
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_abs_lower
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_abs_upper
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_adductors
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_arms
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_back
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_biceps
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_calves
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_chest
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_chest_lower
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_chest_middle
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_chest_upper
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_core
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_delt_front
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_delt_rear
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_delt_side
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_forearms
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_general
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_glutes
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_hamstrings
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_lats
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_legs
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_lower_back
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_neck
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_obliques
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_quads
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_rhomboids
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_shoulders
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_traps
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_triceps
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * The muscle catalogue every install starts with.
 *
 * ### Why it exists
 * The hierarchy used to start empty, so the first thing anyone had to do was invent
 * one — and nothing in the app can be logged until a group has a leaf. This is the
 * list a new user would have built, already built.
 *
 * ### How coarse it is, and why
 * One leaf per muscle people actually train *separately*. There is no "vastus
 * medialis" here, not because it is not a muscle, but because logging it apart from
 * the rest of the quads is a distinction almost nobody makes set by set — and every
 * leaf that is never used is a row to scroll past on every log. Anyone who does want
 * that split can add it: these rows are ordinary rows, editable and deletable like
 * any other.
 *
 * ### The "General" leaf
 * Every group ships one. Sessions reference a leaf, never a group, so without it
 * "I trained chest" would have nowhere to go and you would be forced to pick a region
 * you may not have been thinking in. It is also why `MuscleCatalog.sq` says group
 * volume aggregates uniformly: there is no special group-level row, just a leaf that
 * happens to mean the whole group.
 *
 * ### Keys
 * A key names a *row*, a resource names its *text*, and the two are not the same
 * thing — which is why all seven General leaves share [muscle_seed_general] while
 * each has its own key. The key is written down here forever: change one and the next
 * launch treats it as a new muscle and inserts a duplicate beside the old one.
 *
 * ### Order
 * Every catalogue query sorts by name, so this list's order is not what the user
 * sees — the app shows Arms, Back, Chest, Core, Legs, Neck, Shoulders. That is
 * deliberate and not worth a `sort_order` column: a user-made group has no natural
 * position among shipped ones, and alphabetical is the one ordering that interleaves
 * the two coherently.
 */
class ResourceMuscleSeed : MuscleSeedSource {

    override suspend fun groups(): List<SeededGroup> = catalogue.map { group ->
        SeededGroup(
            key = group.key,
            name = getString(group.name),
            leaves = group.leaves.map { leaf ->
                SeededMuscle(key = leaf.key, name = getString(leaf.name))
            },
        )
    }

    private data class Entry(val key: String, val name: StringResource)

    private data class Group(val key: String, val name: StringResource, val leaves: List<Entry>)

    private companion object {
        /** The `General` leaf of [group], spelled once. */
        fun general(group: String) = Entry("$group.general", Res.string.muscle_seed_general)

        val catalogue = listOf(
            Group(
                key = "chest",
                name = Res.string.muscle_seed_chest,
                leaves = listOf(
                    general("chest"),
                    Entry("chest.upper", Res.string.muscle_seed_chest_upper),
                    Entry("chest.middle", Res.string.muscle_seed_chest_middle),
                    Entry("chest.lower", Res.string.muscle_seed_chest_lower),
                ),
            ),
            Group(
                key = "back",
                name = Res.string.muscle_seed_back,
                leaves = listOf(
                    general("back"),
                    Entry("back.lats", Res.string.muscle_seed_lats),
                    Entry("back.traps", Res.string.muscle_seed_traps),
                    Entry("back.rhomboids", Res.string.muscle_seed_rhomboids),
                    // Lower back rather than "erector spinae": it is the name people
                    // use for what they feel the day after deadlifts.
                    Entry("back.lower", Res.string.muscle_seed_lower_back),
                ),
            ),
            Group(
                key = "shoulders",
                name = Res.string.muscle_seed_shoulders,
                leaves = listOf(
                    general("shoulders"),
                    // The three delt heads are the one head-by-head split that earns
                    // its place: they are trained by different movements on different
                    // days, and lagging rear delts is the thing people track.
                    Entry("shoulders.front", Res.string.muscle_seed_delt_front),
                    Entry("shoulders.side", Res.string.muscle_seed_delt_side),
                    Entry("shoulders.rear", Res.string.muscle_seed_delt_rear),
                ),
            ),
            Group(
                key = "arms",
                name = Res.string.muscle_seed_arms,
                leaves = listOf(
                    general("arms"),
                    Entry("arms.biceps", Res.string.muscle_seed_biceps),
                    Entry("arms.triceps", Res.string.muscle_seed_triceps),
                    Entry("arms.forearms", Res.string.muscle_seed_forearms),
                ),
            ),
            Group(
                key = "legs",
                name = Res.string.muscle_seed_legs,
                leaves = listOf(
                    general("legs"),
                    Entry("legs.quads", Res.string.muscle_seed_quads),
                    Entry("legs.hamstrings", Res.string.muscle_seed_hamstrings),
                    Entry("legs.glutes", Res.string.muscle_seed_glutes),
                    Entry("legs.adductors", Res.string.muscle_seed_adductors),
                    Entry("legs.calves", Res.string.muscle_seed_calves),
                ),
            ),
            Group(
                key = "core",
                name = Res.string.muscle_seed_core,
                leaves = listOf(
                    general("core"),
                    Entry("core.abs_upper", Res.string.muscle_seed_abs_upper),
                    Entry("core.abs_lower", Res.string.muscle_seed_abs_lower),
                    Entry("core.obliques", Res.string.muscle_seed_obliques),
                ),
            ),
            // One leaf, because a group with none cannot be logged against at all and
            // there is no second neck movement worth separating.
            Group(
                key = "neck",
                name = Res.string.muscle_seed_neck,
                leaves = listOf(general("neck")),
            ),
        )
    }
}
