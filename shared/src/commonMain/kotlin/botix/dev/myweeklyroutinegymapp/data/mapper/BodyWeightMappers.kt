package botix.dev.myweeklyroutinegymapp.data.mapper

import botix.dev.myweeklyroutinegymapp.db.Body_weight
import botix.dev.myweeklyroutinegymapp.db.SelectRangeExtremes
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightRange

internal fun Body_weight.toDomain(): BodyWeightEntry = BodyWeightEntry(
    id = id,
    epochDay = date_epoch_day,
    weightKg = weight_kg,
    note = note,
)

/**
 * min/max stay nullable: an empty range has no extremes, and 0.0 would read as a
 * real measurement of zero.
 */
internal fun SelectRangeExtremes.toDomain(): BodyWeightRange = BodyWeightRange(
    minKg = min_kg,
    maxKg = max_kg,
    sampleCount = sample_count,
)
