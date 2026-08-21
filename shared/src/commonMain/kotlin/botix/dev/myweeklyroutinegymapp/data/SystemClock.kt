package botix.dev.myweeklyroutinegymapp.data

import botix.dev.myweeklyroutinegymapp.domain.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * The real clock, reading the device time and the device's current time zone.
 *
 * Lives in `data` rather than `domain` so the domain keeps no dependency on a time
 * library, and so tests can substitute a fixed [Clock] without touching production
 * wiring.
 */
@OptIn(ExperimentalTime::class)
class SystemClock : Clock {

    override fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    /**
     * Resolves the zone on every call rather than caching it: the device zone can
     * change while the app is running (travel, DST) and a cached one would keep
     * dating entries to the old day.
     */
    override fun today(): Long =
        kotlin.time.Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toEpochDays()
}
