package botix.dev.myweeklyroutinegymapp.domain

/**
 * Supplies "now" to the domain.
 *
 * An interface rather than a direct call to a system clock so use cases stay
 * deterministic under test: a fake can pin today to a fixed day and assert on
 * ranges and streaks without the result changing at midnight.
 */
interface Clock {

    /** Epoch millis, for `created_at` audit columns. */
    fun nowMillis(): Long

    /**
     * Today as days since the Unix epoch, in the user's **local** time zone.
     *
     * Local, not UTC: a session logged at 23:00 belongs to the day the user
     * experienced, not to tomorrow because a UTC offset rolled over.
     */
    fun today(): Long
}
