package botix.dev.myweeklyroutinegymapp.domain.repository

import botix.dev.myweeklyroutinegymapp.domain.model.SeededGroup

/**
 * Where the shipped catalogue comes from.
 *
 * An interface for one reason: resolving a name means reading a string resource,
 * which is a platform concern, and the domain has no business importing one. The real
 * implementation reads `Res.string.muscle_seed_*`; a test hands over a fixed list and
 * can change it between calls to stand in for a language change.
 *
 * `suspend` because that is what resource lookup is on Compose Multiplatform.
 */
fun interface MuscleSeedSource {
    suspend fun groups(): List<SeededGroup>
}
