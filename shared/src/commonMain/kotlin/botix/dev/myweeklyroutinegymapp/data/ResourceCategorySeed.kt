package botix.dev.myweeklyroutinegymapp.data

import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.SeededCategory
import botix.dev.myweeklyroutinegymapp.domain.repository.CategorySeedSource
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.category_seed_bicycle
import myweeklyroutinegymapp.shared.generated.resources.category_seed_gym
import myweeklyroutinegymapp.shared.generated.resources.category_seed_running
import myweeklyroutinegymapp.shared.generated.resources.category_seed_swim
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * The four categories every install starts with.
 *
 * ### What ships
 * Gym opens the muscles-and-sets form; Swim, Bicycle and Running open the
 * distance-and-time one. Between them every logging path the app has is reachable on a
 * fresh install without creating anything, which is the whole point: the first thing a
 * new user does should be logging a workout, not building the vocabulary to describe
 * one.
 *
 * Three of the four sharing `DISTANCE_TIME` is not redundancy. They are different
 * *training* and they accumulate separate history — "how far did I run this month" is
 * not answerable if a run and a ride land in one bucket. Having several also shows
 * that the same form serves more than one thing, which is the generalisation a user
 * needs before they think to add their own.
 *
 * ### The measurement type is not translated and never revised
 * A shipped category's name follows the device language like every seeded name; its
 * measurement type is written once, at insert, and left alone forever after. Changing
 * it in a later version would reinterpret every session already logged under it —
 * the numbers would stay and their meaning would move.
 *
 * Everything else about the keys, the ledger and the rename-releases-ownership rule
 * is the same as [ResourceMuscleSeed], which documents it.
 */
class ResourceCategorySeed : CategorySeedSource {

    override suspend fun categories(): List<SeededCategory> = catalogue.map { entry ->
        SeededCategory(
            key = entry.key,
            name = getString(entry.name),
            measurementType = entry.measurementType,
        )
    }

    private data class Entry(
        val key: String,
        val name: StringResource,
        val measurementType: MeasurementType,
    )

    private companion object {
        val catalogue = listOf(
            Entry("gym", Res.string.category_seed_gym, MeasurementType.STRENGTH),
            Entry("swim", Res.string.category_seed_swim, MeasurementType.DISTANCE_TIME),
            Entry("bicycle", Res.string.category_seed_bicycle, MeasurementType.DISTANCE_TIME),
            Entry("running", Res.string.category_seed_running, MeasurementType.DISTANCE_TIME),
        )
    }
}
