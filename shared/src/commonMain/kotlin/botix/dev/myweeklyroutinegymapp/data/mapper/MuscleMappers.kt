package botix.dev.myweeklyroutinegymapp.data.mapper

import botix.dev.myweeklyroutinegymapp.db.Muscle_group
import botix.dev.myweeklyroutinegymapp.db.SelectPickerByUsage
import botix.dev.myweeklyroutinegymapp.db.SelectPickerForActivity
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleGroup
import botix.dev.myweeklyroutinegymapp.domain.model.MusclePickerItem
import botix.dev.myweeklyroutinegymapp.db.Muscle as MuscleEntity
import botix.dev.myweeklyroutinegymapp.domain.model.Muscle as MuscleModel

internal fun Muscle_group.toDomain(): MuscleGroup = MuscleGroup(
    id = id,
    name = name,
    isArchived = is_archived,
    createdAt = created_at,
    seedKey = seed_key,
)

internal fun MuscleEntity.toDomain(): MuscleModel = MuscleModel(
    id = id,
    groupId = group_id,
    name = name,
    isArchived = is_archived,
    createdAt = created_at,
    seedKey = seed_key,
)

internal fun SelectPickerByUsage.toDomain(): MusclePickerItem = MusclePickerItem(
    muscleId = id,
    muscleName = name,
    groupId = group_id,
    groupName = group_name,
    usageCount = usage_count,
)

/**
 * The activity-scoped picker row.
 *
 * Its query projects the same columns in the same order as [SelectPickerByUsage],
 * which is what lets the scoped and unscoped pickers share every consumer below this
 * point — sections, sorting and the checkbox state are all built once.
 */
internal fun SelectPickerForActivity.toDomain(): MusclePickerItem = MusclePickerItem(
    muscleId = id,
    muscleName = name,
    groupId = group_id,
    groupName = group_name,
    usageCount = usage_count,
)
