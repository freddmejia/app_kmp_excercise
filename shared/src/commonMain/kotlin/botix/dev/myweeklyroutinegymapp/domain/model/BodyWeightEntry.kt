package botix.dev.myweeklyroutinegymapp.domain.model

/**
 * One body-weight measurement. At most one per day: logging the same day again
 * overwrites rather than adding a second row.
 */
data class BodyWeightEntry(
    val id: Long,
    /** Days since the Unix epoch. Plain [Long] to keep the domain dependency-free. */
    val epochDay: Long,
    /** Canonical unit is kg; any lb conversion belongs in the UI. */
    val weightKg: Double,
    val note: String? = null,
)

/** Min/max and sample size over a queried range, for the dispersion reading. */
data class BodyWeightRange(
    val minKg: Double?,
    val maxKg: Double?,
    val sampleCount: Long,
) {
    /** A trend needs more than one point; the UI shows "not enough data" below this. */
    val hasTrend: Boolean get() = sampleCount > 1
}
