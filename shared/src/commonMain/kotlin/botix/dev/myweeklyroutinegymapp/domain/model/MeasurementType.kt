package botix.dev.myweeklyroutinegymapp.domain.model

/**
 * How an activity is measured. Closed at three values by design: the name of an
 * activity is free text, but the measurement type is the contract that decides
 * which logging form applies and which metrics are possible downstream.
 *
 * Immutable once the activity has at least one session — changing it would strand
 * existing session data in an incompatible model.
 */
enum class MeasurementType {
    /** Exercise + sets. Feeds volume and per-muscle/group frequency. */
    STRENGTH,

    /** Distance + duration (+ optional HR). Feeds weekly km, pace, aerobic efficiency. */
    DISTANCE_TIME,

    /** Presence only — "I did it that day". Feeds frequency, nothing numeric. */
    SIMPLE,
}
