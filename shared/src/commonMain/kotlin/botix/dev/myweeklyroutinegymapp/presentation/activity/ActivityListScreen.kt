package botix.dev.myweeklyroutinegymapp.presentation.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.activities_archived_badge
import myweeklyroutinegymapp.shared.generated.resources.activities_empty
import myweeklyroutinegymapp.shared.generated.resources.activities_empty_hint
import myweeklyroutinegymapp.shared.generated.resources.activities_empty_in_category
import myweeklyroutinegymapp.shared.generated.resources.activities_not_trained_day
import myweeklyroutinegymapp.shared.generated.resources.activities_nothing_trained_day
import myweeklyroutinegymapp.shared.generated.resources.activities_nothing_trained_day_hint
import myweeklyroutinegymapp.shared.generated.resources.activities_trained_day
import myweeklyroutinegymapp.shared.generated.resources.activities_uncategorised
import myweeklyroutinegymapp.shared.generated.resources.activities_day_distance
import myweeklyroutinegymapp.shared.generated.resources.activities_day_volume
import myweeklyroutinegymapp.shared.generated.resources.activity_filter_active
import myweeklyroutinegymapp.shared.generated.resources.activity_filter_all
import myweeklyroutinegymapp.shared.generated.resources.activity_filter_archived
import myweeklyroutinegymapp.shared.generated.resources.activity_msg_delete_blocked
import myweeklyroutinegymapp.shared.generated.resources.activity_msg_name_blank
import myweeklyroutinegymapp.shared.generated.resources.activity_msg_name_taken
import myweeklyroutinegymapp.shared.generated.resources.activity_msg_type_locked
import myweeklyroutinegymapp.shared.generated.resources.common_all
import myweeklyroutinegymapp.shared.generated.resources.common_archive
import myweeklyroutinegymapp.shared.generated.resources.common_delete
import myweeklyroutinegymapp.shared.generated.resources.common_deleted
import myweeklyroutinegymapp.shared.generated.resources.common_new
import myweeklyroutinegymapp.shared.generated.resources.common_saved
import myweeklyroutinegymapp.shared.generated.resources.common_unarchive
import myweeklyroutinegymapp.shared.generated.resources.plural_muscles
import myweeklyroutinegymapp.shared.generated.resources.plural_sessions
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityVolume
import botix.dev.myweeklyroutinegymapp.domain.model.CategorySelection
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ActivityFilter
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.common.MessageSnackbar
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityEditorViewModel.Companion.NEW
import botix.dev.myweeklyroutinegymapp.presentation.common.ScreenActions
import botix.dev.myweeklyroutinegymapp.presentation.common.SectionHeader
import botix.dev.myweeklyroutinegymapp.presentation.common.SwipeToDelete
import botix.dev.myweeklyroutinegymapp.presentation.common.formatMeasure
import botix.dev.myweeklyroutinegymapp.presentation.common.WeekStrip
import botix.dev.myweeklyroutinegymapp.presentation.common.asFullDate
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ActivityListScreen(
    onEdit: (activityId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ActivityListContent(
        state = state,
        onSelectFilter = viewModel::setFilter,
        onSelectCategory = viewModel::setCategorySelection,
        onEdit = onEdit,
        onSelectDay = viewModel::selectDay,
        onStepWeek = viewModel::stepWeek,
        onGoToToday = viewModel::goToToday,
        onSetArchived = viewModel::setArchived,
        onDelete = viewModel::delete,
        onConsumeMessage = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
fun ActivityListContent(
    state: ActivityListUiState,
    onSelectFilter: (ActivityFilter) -> Unit,
    onSelectCategory: (CategorySelection) -> Unit,
    /** Zero opens an empty form: there is no activity yet to name. */
    onEdit: (activityId: Long) -> Unit,
    onSelectDay: (Long) -> Unit,
    onStepWeek: (Boolean) -> Unit,
    onGoToToday: () -> Unit,
    onSetArchived: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenActions {
            // Categories moved to Settings with the muscle catalogue: this screen
            // *uses* categories to filter, and the row below is where that happens;
            // editing the list of them is a different job in a different place.
            Button(onClick = { onEdit(NEW) }) { Text(stringResource(Res.string.common_new)) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter == state.filter,
                    onClick = { onSelectFilter(filter) },
                    label = { Text(stringResource(filter.label())) },
                )
            }
        }

        // Hidden until at least one category exists: a row offering only "All" is
        // noise, and it is the state every user starts in.
        if (state.categories.isNotEmpty()) {
            CategoryFilterRow(
                categories = state.categories,
                selection = state.categorySelection,
                onSelect = onSelectCategory,
            )
        }

        // Always on, exactly as on the log screen: the week is context for the whole
        // list, not a mode you opt into.
        WeekStrip(
            selectedDay = state.selectedDay,
            today = state.today,
            visibleDays = state.visibleDays,
            markedDays = state.daysWithSessions,
            onSelectDay = onSelectDay,
            onStepWeek = onStepWeek,
            onGoToToday = onGoToToday,
        )

        MessageSnackbar(state.message?.let { messageText(it) }, onConsumeMessage)

        when {
            state.isLoading -> LoadingState()
            state.activities.isEmpty() -> EmptyState(
                text = stringResource(
                    if (state.categorySelection == CategorySelection.All) {
                        Res.string.activities_empty
                    } else {
                        Res.string.activities_empty_in_category
                    },
                ),
                hint = stringResource(Res.string.activities_empty_hint),
            )

            // A day with no sessions shows nothing, rather than the whole catalogue
            // under a header saying nothing was trained. The header said "empty" while
            // the screen below it was full, and the eye believes the screen.
            //
            // The cost is real and deliberate: on such a day no activity row is on
            // screen, so editing, archiving and deleting one means first picking a day
            // it was trained on. Creating is unaffected — New is in the header, which
            // is why the hint points there rather than at the list.
            state.trainedOnDay.isEmpty() -> EmptyState(
                text = stringResource(
                    Res.string.activities_nothing_trained_day,
                    state.selectedDay.asFullDate(),
                ),
                hint = stringResource(Res.string.activities_nothing_trained_day_hint),
            )

            // weight(1f) so the pinned header above can grow without squeezing
            // the list to nothing.
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Non-empty by the time we get here — the branch above catches the
                // day where nothing was trained.
                val trained = state.trainedOnDay
                val untrained = state.untrainedOnDay

                // Headed only when both halves exist. With everything trained, a
                // header would label a division that is not there.
                if (untrained.isNotEmpty()) {
                    item(key = "trained-header") {
                        SectionHeader(
                            stringResource(
                                Res.string.activities_trained_day,
                                state.selectedDay.asFullDate(),
                            ),
                        )
                    }
                }
                items(trained, key = { "trained-${it.id}" }) { activity ->
                    ActivityRow(
                        activity = activity,
                        categoryName = activity.categoryId?.let { state.categoryNamesById[it] },
                        muscleCount = state.musclesByActivity[activity.id]?.size ?: 0,
                        dayVolume = state.dayVolume[activity.id],
                        onEdit = { onEdit(activity.id) },
                        onSetArchived = onSetArchived,
                        onDelete = onDelete,
                    )
                }

                if (untrained.isNotEmpty()) {
                    item(key = "untrained-header") {
                        SectionHeader(stringResource(Res.string.activities_not_trained_day))
                    }
                }
                // The contrast half: what is still open on a day you did train. It
                // earns its place here precisely because something else on screen was
                // trained — which is why it does not appear on an empty day.
                items(untrained, key = { "untrained-${it.id}" }) { activity ->
                    ActivityRow(
                        activity = activity,
                        categoryName = activity.categoryId?.let { state.categoryNamesById[it] },
                        muscleCount = state.musclesByActivity[activity.id]?.size ?: 0,
                        dayVolume = null,
                        onEdit = { onEdit(activity.id) },
                        onSetArchived = onSetArchived,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }

}

/**
 * Scrollable, because the list is user-generated and has no natural bound — a
 * wrapping [Row] would silently clip the categories that did not fit.
 */
@Composable
private fun CategoryFilterRow(
    categories: List<ActivityCategory>,
    selection: CategorySelection,
    onSelect: (CategorySelection) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selection == CategorySelection.All,
                onClick = { onSelect(CategorySelection.All) },
                label = { Text(stringResource(Res.string.common_all)) },
            )
        }
        // Its own choice, not the absence of one: without it, activities with no
        // category are unreachable as soon as you start filtering.
        item {
            FilterChip(
                selected = selection == CategorySelection.Uncategorised,
                onClick = { onSelect(CategorySelection.Uncategorised) },
                label = { Text(stringResource(Res.string.activities_uncategorised)) },
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selection == CategorySelection.Of(category.id),
                onClick = { onSelect(CategorySelection.Of(category.id)) },
                label = { Text(listOfNotNull(category.icon, category.name).joinToString(" ")) },
            )
        }
    }
}

@Composable
private fun ActivityRow(
    activity: Activity,
    categoryName: String?,
    muscleCount: Int,
    dayVolume: ActivityVolume?,
    onEdit: () -> Unit,
    onSetArchived: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    // Muscles are only offered for STRENGTH. The link's whole job is to scope the
    // strength log picker, and the other two types never open it — an always-visible
    // control that silently does nothing is worse than an absent one.
    val supportsMuscles = activity.measurementType == MeasurementType.STRENGTH
    // Swiping requests the delete; the domain still refuses it while sessions
    // reference the activity, and says so in a snackbar. That refusal is exactly why
    // the row must not animate itself away on release.
    SwipeToDelete(
        label = stringResource(Res.string.common_delete),
        onDelete = { onDelete(activity.id) },
    ) {
        // The card is the button, as everywhere else: tapping it opens the form
        // that holds the name, the icon, the category, the type and the muscles.
        // Category and Muscles used to be two buttons on this row opening two
        // dialogs, each showing one of those fields with the rest out of sight.
        Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = listOfNotNull(activity.icon, activity.name).joinToString(" "),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            // Category and measurement type on one line, in that order:
                            // the category is the label you chose, the type is the
                            // contract the app enforces.
                            text = listOfNotNull(
                                categoryName,
                                activity.measurementType.label(),
                                // Absent rather than "0 muscles": no links means "not
                                // specified", and the picker falls back to everything.
                                if (supportsMuscles && muscleCount > 0) {
                                    pluralStringResource(
                                        Res.plurals.plural_muscles,
                                        muscleCount,
                                        muscleCount.toString(),
                                    )
                                } else {
                                    null
                                },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (activity.isArchived) {
                        AssistChip(onClick = {}, label = { Text(stringResource(Res.string.activities_archived_badge)) })
                    }
                }

                // Present precisely because the activity was trained on the selected
                // day — so there is never a "0 sets" row to explain away.
                //
                // No day count any more: over a single day it was always "1 day",
                // which is the one fact the section header has already stated.
                dayVolume?.let { volume ->
                    Text(
                        // Sets mean nothing for a ride and kilometres mean nothing for
                        // a bench press, so the line follows what was actually
                        // measured rather than the activity's declared type — a
                        // session logged before a type change still reads correctly.
                        if (volume.isDistanceTime) {
                            stringResource(
                                Res.string.activities_day_distance,
                                volume.distanceMeters?.let { (it / 1000.0).formatMeasure() } ?: "0",
                                volume.durationSeconds?.let { (it / 60.0).formatMeasure() } ?: "0",
                                pluralStringResource(
                                    Res.plurals.plural_sessions,
                                    volume.sessionCount.toInt(),
                                    volume.sessionCount.toString(),
                                ),
                            )
                        } else {
                            stringResource(
                                Res.string.activities_day_volume,
                                volume.effectiveSets.toString(),
                                volume.warmupSets.toString(),
                                pluralStringResource(
                                    Res.plurals.plural_sessions,
                                    volume.sessionCount.toInt(),
                                    volume.sessionCount.toString(),
                                ),
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // Archive keeps its button. It is the third thing you can do to an
                // activity and there is no third gesture to give it; hiding it behind
                // one would make it undiscoverable to keep a symmetry nothing asked
                // for.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onSetArchived(activity.id, !activity.isArchived) }) {
                        Text(
                            stringResource(
                                if (activity.isArchived) {
                                    Res.string.common_unarchive
                                } else {
                                    Res.string.common_archive
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** The message's wording. Rendering is [MessageSnackbar]'s job. */
@Composable
private fun messageText(message: ActivityMessage): String = when (message) {
    ActivityMessage.Deleted -> stringResource(Res.string.common_deleted)
    is ActivityMessage.DeleteBlocked -> stringResource(
        Res.string.activity_msg_delete_blocked,
        pluralStringResource(
            Res.plurals.plural_sessions,
            message.sessionCount.toInt(),
            message.sessionCount.toString(),
        ),
    )
}

@Composable
private fun MeasurementType.label(): String = stringResource(labelRes())

/**
 * The enum constant is an English identifier, so the chip reads from a resource
 * rather than from `name` — lowercasing "ARCHIVED" only ever produced English.
 */
private fun ActivityFilter.label(): StringResource = when (this) {
    ActivityFilter.ACTIVE -> Res.string.activity_filter_active
    ActivityFilter.ARCHIVED -> Res.string.activity_filter_archived
    ActivityFilter.ALL -> Res.string.activity_filter_all
}
