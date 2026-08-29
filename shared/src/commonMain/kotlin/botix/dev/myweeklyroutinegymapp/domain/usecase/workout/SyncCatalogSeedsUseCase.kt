package botix.dev.myweeklyroutinegymapp.domain.usecase.workout

import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.SeedSyncResult
import botix.dev.myweeklyroutinegymapp.domain.model.CategorySeedSyncResult
import botix.dev.myweeklyroutinegymapp.domain.repository.ActivityCategoryRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.CategorySeedSource
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleCatalogRepository
import botix.dev.myweeklyroutinegymapp.domain.repository.MuscleSeedSource

/**
 * Puts the shipped muscle catalogue in place, and keeps its names in the device's
 * language.
 *
 * Run on every start rather than once on install, because it answers two questions
 * and only the first of them is settled by installing:
 *
 * 1. **Is the catalogue there?** New install, or an upgrade from a version that
 *    shipped none — both are the same case, and both are handled by inserting what
 *    the ledger has not seen.
 * 2. **Is it in the right language?** The names live in the database as plain text so
 *    that every query stays a name lookup, which means nothing retranslates them on
 *    its own. This does.
 *
 * It is safe to run at any time and costs nothing when there is nothing to do: the
 * repository reads before it writes, so a start in the same language performs no
 * write and raises no query notification.
 */
class SyncMuscleSeedUseCase(
    private val catalog: MuscleCatalogRepository,
    private val seed: MuscleSeedSource,
    private val clock: Clock,
) {
    suspend operator fun invoke(): SeedSyncResult =
        catalog.syncSeed(seed.groups(), clock.nowMillis())
}

/**
 * The same job for the shipped categories, which the log screen now needs on a fresh
 * install for a different reason than the muscles: without a category there is no
 * "log" flow at all, because choosing one is the first step of it.
 *
 * Separate from [SyncMuscleSeedUseCase] rather than folded into it because the two
 * write different tables under different ledgers and neither should be able to fail
 * the other. `SyncCatalogSeeds` runs both.
 */
class SyncCategorySeedUseCase(
    private val categories: ActivityCategoryRepository,
    private val seed: CategorySeedSource,
    private val clock: Clock,
) {
    suspend operator fun invoke(): CategorySeedSyncResult =
        categories.syncSeed(seed.categories(), clock.nowMillis())
}

/**
 * Both catalogues, in the order the UI needs them.
 *
 * Categories first: the log flow opens with a category picker, so a fresh install
 * that got muscles but not categories would still show nothing to press. Neither call
 * depends on the other's data — the order is about which failure would be visible.
 */
class SyncCatalogSeedsUseCase(
    private val muscles: SyncMuscleSeedUseCase,
    private val categories: SyncCategorySeedUseCase,
) {
    suspend operator fun invoke() {
        categories()
        muscles()
    }
}
