package botix.dev.myweeklyroutinegymapp.data.mapper

import botix.dev.myweeklyroutinegymapp.db.SelectActiveByUsage
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityUsage
import botix.dev.myweeklyroutinegymapp.db.Activity as ActivityEntity

/**
 * SQLDelight row -> domain model.
 *
 * The generated types are aliased to `…Entity` here because several of them share a
 * name with their domain counterpart. Keeping the alias local to the mappers is what
 * lets the rest of the codebase refer to plain [Activity] and mean the domain one.
 *
 * These translate snake_case columns to camelCase properties and nothing else — no
 * derived values, so a mapper can never disagree with the query that fed it.
 */
internal fun ActivityEntity.toDomain(): Activity = Activity(
    id = id,
    name = name,
    measurementType = measurement_type,
    icon = icon,
    color = color,
    isArchived = is_archived,
    isSeed = is_seed,
    createdAt = created_at,
)

internal fun SelectActiveByUsage.toDomain(): ActivityUsage = ActivityUsage(
    activity = Activity(
        id = id,
        name = name,
        measurementType = measurement_type,
        icon = icon,
        color = color,
        isArchived = is_archived,
        isSeed = is_seed,
        createdAt = created_at,
    ),
    sessionCount = session_count,
)
