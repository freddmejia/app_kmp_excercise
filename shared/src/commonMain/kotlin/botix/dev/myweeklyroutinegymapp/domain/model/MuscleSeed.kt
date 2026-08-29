package botix.dev.myweeklyroutinegymapp.domain.model

/**
 * The muscle catalogue the app ships, as the sync sees it: names already resolved
 * into the device's language.
 *
 * Two names per row, and they do different jobs. [SeededGroup.key] is an identifier
 * that never changes and is never shown — it is how a row is recognised as the same
 * row across launches, languages and app versions. [SeededGroup.name] is text for a
 * human, in whatever language the device is in right now, and it is expected to
 * change under the same key.
 *
 * Keeping them apart is what lets the catalogue follow the device language without
 * any query knowing about resources: the seeded rows are stored with plain names like
 * every other row, and the sync rewrites those names when the language changes.
 */
data class SeededGroup(
    val key: String,
    val name: String,
    val leaves: List<SeededMuscle>,
)

data class SeededMuscle(
    val key: String,
    val name: String,
)

/**
 * What the sync did. Reported rather than logged so a test can assert on it, and so
 * "ran, changed nothing" is distinguishable from "did not run".
 */
data class SeedSyncResult(
    val groupsAdded: Int = 0,
    val musclesAdded: Int = 0,
    val namesUpdated: Int = 0,
) {
    val changedAnything: Boolean get() = groupsAdded > 0 || musclesAdded > 0 || namesUpdated > 0
}

/**
 * A category the app ships, with its name already resolved into the device's
 * language. Same two-names rule as [SeededGroup]: [key] identifies the row forever,
 * [name] is text for a human and is expected to change under it.
 *
 * [measurementType] is not translated and never changes — it is the contract the
 * category exists to carry, and a shipped category that changed how it measures would
 * reinterpret every session logged under it.
 */
data class SeededCategory(
    val key: String,
    val name: String,
    val measurementType: MeasurementType,
    val icon: String? = null,
)

/** What [SeededCategory] syncing did. Flat, because categories have no leaves. */
data class CategorySeedSyncResult(
    val categoriesAdded: Int = 0,
    val namesUpdated: Int = 0,
) {
    val changedAnything: Boolean get() = categoriesAdded > 0 || namesUpdated > 0
}
