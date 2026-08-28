package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.common.MessageSnackbar
import botix.dev.myweeklyroutinegymapp.presentation.common.ScreenActions
import botix.dev.myweeklyroutinegymapp.presentation.common.SwipeToDelete
import botix.dev.myweeklyroutinegymapp.presentation.common.WeekStrip
import botix.dev.myweeklyroutinegymapp.presentation.common.formatMeasure
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymIcons
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_cancel
import myweeklyroutinegymapp.shared.generated.resources.common_delete
import myweeklyroutinegymapp.shared.generated.resources.common_deleted
import myweeklyroutinegymapp.shared.generated.resources.log_delete_count
import myweeklyroutinegymapp.shared.generated.resources.log_delete_empty
import myweeklyroutinegymapp.shared.generated.resources.log_delete_title
import myweeklyroutinegymapp.shared.generated.resources.log_distance_summary
import myweeklyroutinegymapp.shared.generated.resources.log_hint_new_log
import myweeklyroutinegymapp.shared.generated.resources.log_hint_no_activities
import myweeklyroutinegymapp.shared.generated.resources.log_new
import myweeklyroutinegymapp.shared.generated.resources.log_no_activities_at_all
import myweeklyroutinegymapp.shared.generated.resources.log_nothing_checked
import myweeklyroutinegymapp.shared.generated.resources.log_nothing_day
import myweeklyroutinegymapp.shared.generated.resources.log_nothing_today
import myweeklyroutinegymapp.shared.generated.resources.log_pace
import myweeklyroutinegymapp.shared.generated.resources.log_summary
import myweeklyroutinegymapp.shared.generated.resources.plural_effective_sets
import myweeklyroutinegymapp.shared.generated.resources.plural_muscles
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * What is logged, day by day.
 *
 * This screen reads and deletes. Writing a log is [WorkoutEntryScreen]'s job — it used
 * to happen here, in place: picking an activity replaced the day's list with a muscle
 * picker under the same app bar, so the only sign you had gone anywhere was that the
 * content had changed. The stack says it now.
 */
@Composable
fun WorkoutLogScreen(
    onOpenEntry: (activityId: Long, epochDay: Long, existing: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutLogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WorkoutLogContent(
        state = state,
        onOpenEntry = onOpenEntry,
        onSelectDay = viewModel::selectDay,
        onDeleteLog = viewModel::deleteLog,
        onStepWeek = viewModel::stepWeek,
        onGoToToday = viewModel::goToToday,
        onConsumeMessage = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
fun WorkoutLogContent(
    state: WorkoutLogUiState,
    onOpenEntry: (activityId: Long, epochDay: Long, existing: Boolean) -> Unit,
    onSelectDay: (Long) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onStepWeek: (Boolean) -> Unit,
    onGoToToday: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewLogDialog by remember { mutableStateOf(false) }

    Column(
        // Narrow side margins so the week strip can use the width: seven cells plus
        // two arrows is the widest row on any screen here, and every dp taken off the
        // sides goes straight into the cells. Vertical stays at 16 — it is the gap
        // to the app bar and the tab row, and has no such pressure on it.
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenActions {
            // Creating a log is an explicit act rather than a side effect of picking
            // from a list that filled the screen. The strip decides *when*; this
            // decides *whether*.
            //
            // The Muscles button that used to sit beside it is in Settings now: it
            // opened a catalogue, which is configuration, and it was the one control
            // in this header that had nothing to do with the day on screen.
            Button(onClick = { showNewLogDialog = true }) { Text(stringResource(Res.string.log_new)) }
        }

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

        // weight(1f) from here, because DayLog is its own composable and cannot reach
        // this Column's scope to claim the space.
        //
        // Loading swaps the *content*, never the header: returning early instead would
        // take the New-log button with it.
        if (state.isLoading) {
            LoadingState(Modifier.weight(1f))
        } else {
            DayLog(
                state = state,
                onOpenEntry = onOpenEntry,
                onDeleteLog = onDeleteLog,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showNewLogDialog) {
        NewLogDialog(
            // Both kinds, in one list. Splitting them into two buttons would make the
            // user classify the activity before choosing it, which is the app's job:
            // the type is already recorded, and it decides which form opens next.
            activities = state.strengthActivities + state.distanceActivities,
            onDismiss = { showNewLogDialog = false },
            onPick = { activity ->
                showNewLogDialog = false
                // Whether this is a new log or a correction is something the day's
                // list already knows — picking an activity that was logged today
                // reopens that log rather than starting a second one, and the app bar
                // has to say so before the screen appears rather than a frame later.
                onOpenEntry(
                    activity.id,
                    state.selectedDay,
                    state.dayLog.any { it.activityId == activity.id },
                )
            },
        )
    }
}

/**
 * What is logged on the selected day.
 *
 * A list, not a single session: a day can hold several — different activities, or a
 * morning and an evening. That is only expressible now that the strip picks the day
 * and a button creates the log, rather than the screen inferring both from a picker.
 */
@Composable
private fun DayLog(
    state: WorkoutLogUiState,
    onOpenEntry: (activityId: Long, epochDay: Long, existing: Boolean) -> Unit,
    onDeleteLog: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by remember { mutableStateOf<SessionSummary?>(null) }

    if (state.dayLog.isEmpty()) {
        EmptyState(
            modifier = modifier,
            text = stringResource(
                if (state.isToday) Res.string.log_nothing_today else Res.string.log_nothing_day,
            ),
            hint = stringResource(
                if (state.strengthActivities.isEmpty()) {
                    Res.string.log_hint_no_activities
                } else {
                    Res.string.log_hint_new_log
                },
            ),
        )
        return
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.dayLog, key = { it.sessionId }) { summary ->
            // Swiping asks; the dialog below still decides. The row springs back
            // either way — see `SwipeToDelete`.
            SwipeToDelete(
                label = stringResource(Res.string.common_delete),
                onDelete = { confirmingDelete = summary },
            ) {
                // The row *is* the button. An Open button inside a card that opens
                // is a second, smaller target for what the whole row already does,
                // and it teaches that the rest of the card is inert. The chevron
                // says the same thing without claiming a tap of its own — tapping it
                // is tapping the card, because it is inside it.
                Card(
                    // Both kinds of log open the same way. The row knows which
                    // activity it belongs to, and the entry screen reads the
                    // measurement type — so the list does not have to branch on a
                    // type it would only have to keep in step.
                    onClick = { onOpenEntry(summary.activityId, summary.epochDay, true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        // Centred on the row rather than pinned to its top: the row
                        // is one or two lines tall depending on what is logged, and
                        // an arrow that rides up and down with that reads as loose.
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(summary.activityName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                // Three shapes of session, and each one gets the line
                                // that describes it: a ride measured in km, a strength
                                // session measured in sets, and one started but never
                                // filled in — which says so rather than showing "0
                                // sets", a number that reads like a logged result.
                                when {
                                    summary.isDistanceTime -> summary.distanceTimeLine()
                                    summary.isEmpty -> stringResource(Res.string.log_nothing_checked)
                                    else -> stringResource(
                                        Res.string.log_summary,
                                        summary.effectiveSets.toString(),
                                        summary.warmupSets.toString(),
                                        pluralStringResource(
                                            Res.plurals.plural_muscles,
                                            summary.muscleCount.toInt(),
                                            summary.muscleCount.toString(),
                                        ),
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = GymIcons.ChevronRight,
                            // Decorative. The card carries the click, so a screen
                            // reader announces the row once; describing the arrow
                            // would add a second thing to hear that does nothing.
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    confirmingDelete?.let { summary ->
        // Confirmed because it takes the entries with it, and an accidental tap here
        // would silently destroy a whole session's volume.
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text(stringResource(Res.string.log_delete_title)) },
            text = {
                Text(
                    if (summary.isEmpty) {
                        stringResource(Res.string.log_delete_empty, summary.activityName)
                    } else {
                        stringResource(
                            Res.string.log_delete_count,
                            summary.activityName,
                            pluralStringResource(
                                Res.plurals.plural_effective_sets,
                                summary.effectiveSets.toInt(),
                                summary.effectiveSets.toString(),
                            ),
                            pluralStringResource(
                                Res.plurals.plural_muscles,
                                summary.muscleCount.toInt(),
                                summary.muscleCount.toString(),
                            ),
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteLog(summary.sessionId)
                    confirmingDelete = null
                }) { Text(stringResource(Res.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }
}

/**
 * Choosing what the new log is for.
 *
 * Lists every loggable activity, whatever its measurement type, and picking one opens
 * the screen that fits it — the muscle picker for a strength activity, the distance
 * and time form for the other. This list was filtered to STRENGTH until recently,
 * which is why an activity created as distance-and-time could never be logged: it
 * simply never appeared here, and the screen offered no other way in.
 */
@Composable
private fun NewLogDialog(
    activities: List<Activity>,
    onDismiss: () -> Unit,
    onPick: (Activity) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.log_new)) },
        text = {
            if (activities.isEmpty()) {
                Text(stringResource(Res.string.log_no_activities_at_all))
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    activities.forEach { activity ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(activity) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                listOfNotNull(activity.icon, activity.name).joinToString(" "),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        // Picking one opens the next screen and closes this, so there is nothing to
        // confirm here.
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

/**
 * "20.5 km · 45 min", with the pace appended when both halves are known.
 *
 * Composable because the wording and the unit labels are translated; the arithmetic
 * that produces the pace lives on [SessionSummary], where a test can reach it.
 */
@Composable
private fun SessionSummary.distanceTimeLine(): String {
    val line = stringResource(
        Res.string.log_distance_summary,
        distanceMeters?.let { (it / 1000.0).formatMeasure() } ?: "0",
        durationSeconds?.let { (it / 60.0).formatMeasure() } ?: "0",
    )
    val pace = paceSecondsPerKm ?: return line
    return line + " · " + stringResource(Res.string.log_pace, pace.asMinutesSeconds())
}

/** Seconds as "3:44" — a pace is read in minutes and seconds, never in 224. */
private fun Double.asMinutesSeconds(): String {
    val total = this.roundToInt()
    val seconds = total % 60
    return "${total / 60}:${if (seconds < 10) "0" else ""}$seconds"
}

/** The message's wording. Rendering is [MessageSnackbar]'s job. */
@Composable
private fun messageText(message: WorkoutMessage): String = when (message) {
    WorkoutMessage.Deleted -> stringResource(Res.string.common_deleted)
}
