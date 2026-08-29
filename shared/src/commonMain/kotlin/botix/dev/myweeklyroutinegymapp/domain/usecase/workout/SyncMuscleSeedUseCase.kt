package botix.dev.myweeklyroutinegymapp.domain.usecase.workout

import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.model.SeedSyncResult
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
