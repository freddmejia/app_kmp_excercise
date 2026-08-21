package botix.dev.myweeklyroutinegymapp.data.mapper

import botix.dev.myweeklyroutinegymapp.db.Muscle_group
import botix.dev.myweeklyroutinegymapp.db.SelectPickerByUsage
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleGroup
import botix.dev.myweeklyroutinegymapp.domain.model.MusclePickerItem
import botix.dev.myweeklyroutinegymapp.db.Muscle as MuscleEntity
import botix.dev.myweeklyroutinegymapp.domain.model.Muscle as MuscleModel

internal fun Muscle_group.toDomain(): MuscleGroup = MuscleGroup(
    id = id,
    name = name,
    isArchived = is_archived,
    createdAt = created_at,
)

internal fun MuscleEntity.toDomain(): MuscleModel = MuscleModel(
    id = id,
    groupId = group_id,
    name = name,
    isArchived = is_archived,
    createdAt = created_at,
)

internal fun SelectPickerByUsage.toDomain(): MusclePickerItem = MusclePickerItem(
    muscleId = id,
    muscleName = name,
    groupId = group_id,
    groupName = group_name,
    usageCount = usage_count,
)
