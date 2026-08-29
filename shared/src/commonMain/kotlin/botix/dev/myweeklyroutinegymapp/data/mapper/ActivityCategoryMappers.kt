package botix.dev.myweeklyroutinegymapp.data.mapper

import botix.dev.myweeklyroutinegymapp.db.SelectActiveWithUsage
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategoryUsage
import botix.dev.myweeklyroutinegymapp.db.Activity_category as ActivityCategoryEntity

/**
 * SQLDelight row -> domain model, for the category catalogue.
 *
 * The generated type is aliased because it shares a name with its domain
 * counterpart — same convention as [ActivityMappers]. Column translation only: no
 * derived values, so a mapper can never disagree with the query that fed it.
 */
internal fun ActivityCategoryEntity.toDomain(): ActivityCategory = ActivityCategory(
    id = id,
    name = name,
    icon = icon,
    color = color,
    isArchived = is_archived,
    createdAt = created_at,
)

internal fun SelectActiveWithUsage.toDomain(): ActivityCategoryUsage = ActivityCategoryUsage(
    category = ActivityCategory(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isArchived = is_archived,
        createdAt = created_at,
    ),
    activityCount = activity_count,
)
