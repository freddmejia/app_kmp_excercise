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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.common.MessageSnackbar
import botix.dev.myweeklyroutinegymapp.presentation.common.SectionHeader
import botix.dev.myweeklyroutinegymapp.presentation.common.SwipeToDelete
import botix.dev.myweeklyroutinegymapp.presentation.common.WeekStrip
import botix.dev.myweeklyroutinegymapp.presentation.common.formatMeasure
import botix.dev.myweeklyroutinegymapp.presentation.preview.PREVIEW_TODAY
import botix.dev.myweeklyroutinegymapp.presentation.preview.PreviewSurface
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewCategories
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewDayLog
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewDayMuscles
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewDaysWithSessions
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymIcons
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_cancel
import myweeklyroutinegymapp.shared.generated.resources.common_delete
import myweeklyroutinegymapp.shared.generated.resources.common_deleted
import myweeklyroutinegymapp.shared.generated.resources.log_delete_count
import myweeklyroutinegymapp.shared.generated.resources.log_delete_empty
import myweeklyroutinegymapp.shared.generated.resources.log_delete_measures
import myweeklyroutinegymapp.shared.generated.resources.log_delete_title
import myweeklyroutinegymapp.shared.generated.resources.log_distance_summary
import myweeklyroutinegymapp.shared.generated.resources.log_hint_new_log
import myweeklyroutinegymapp.shared.generated.resources.log_msg_category_missing
import myweeklyroutinegymapp.shared.generated.resources.log_new
import myweeklyroutinegymapp.shared.generated.resources.log_no_categories
import myweeklyroutinegymapp.shared.generated.resources.log_nothing_checked
import myweeklyroutinegymapp.shared.generated.resources.log_nothing_day
import myweeklyroutinegymapp.shared.generated.resources.log_nothing_today
import myweeklyroutinegymapp.shared.generated.resources.log_pace
import myweeklyroutinegymapp.shared.generated.resources.log_pick_category
import myweeklyroutinegymapp.shared.generated.resources.log_summary
import myweeklyroutinegymapp.shared.generated.resources.log_uncategorised
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
    onOpenEntry: (sessionId: Long, epochDay: Long, existing: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutLogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The log is created here and opened as `existing = false`, which is what makes
    // the app bar say "New log" the moment the form appears rather than a frame later.
    LaunchedEffect(Unit) {
        viewModel.created.collect { sessionId ->
            onOpenEntry(sessionId, state.selectedDay, false)
        }
    }

    WorkoutLogContent(
        state = state,
        onOpenEntry = onOpenEntry,
        onNewLog = viewModel::startLog,
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
    onOpenEntry: (sessionId: Long, epochDay: Long, existing: Boolean) -> Unit,
    /** Starts a log of that kind on the selected day. The screen opens once it exists. */
    onNewLog: (categoryId: Long) -> Unit,
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
        WeekStrip(
            selectedDay = state.selectedDay,
            today = state.today,
            visibleDays = state.visibleDays,
            markedDays = state.daysWithSessions,
            onSelectDay = onSelectDay,
            onStepWeek = onStepWeek,
            onGoToToday = onGoToToday,
        ) {
            // On the strip's own date row, opposite the date and centred against it —
            // the same slot and the same alignment the body-weight screen uses, so the
            // one button that creates something sits in the same place on every tab
            // that has a day strip.
            //
            // It belongs beside the date rather than above it because the date is its
            // subject: this creates a log *for the selected day*, and the day it will
            // land on is then the thing directly next to the button that lands it.
            //
            // Creating a log is still an explicit act rather than a side effect of
            // picking from a list that filled the screen. The strip decides *when*;
            // this decides *whether*.
            //
            // The Muscles button that used to sit beside it is in Preferences now: it
            // opened a catalogue, which is configuration, and it was the one control
            // in this header that had nothing to do with the day on screen.
            Button(onClick = { showNewLogDialog = true }) { Text(stringResource(Res.string.log_new)) }
        }

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
        CategoryPickerDialog(
            categories = state.categories,
            onDismiss = { showNewLogDialog = false },
            onPick = { category ->
                showNewLogDialog = false
                // The category is the whole question this dialog asks, and the only
                // one asked before the form: it decides which form opens, and it is
                // what the log is called afterwards. It used to list every activity in
                // the app, which meant you could only log something you had already
                // created on another tab.
                onNewLog(category.id)
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
                if (state.categories.isEmpty()) {
                    Res.string.log_no_categories
                } else {
                    Res.string.log_hint_new_log
                },
            ),
        )
        return
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.dayLog, key = { it.sessionId }) { summary ->
            val muscles = state.musclesBySession[summary.sessionId].orEmpty()
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
                    // Both kinds of log open the same way. The row carries the
                    // session id, and the entry screen reads the measurement type from
                    // it — so the list does not have to branch on a type it would only
                    // have to keep in step.
                    onClick = { onOpenEntry(summary.sessionId, summary.epochDay, true) },
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
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                // Null once its category is deleted. The log still
                                // happened, so it keeps its row and gets a name that
                                // says what it now is.
                                summary.categoryName
                                    ?: stringResource(Res.string.log_uncategorised),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            // Three shapes of session, and each says what it is in the
                            // terms it was measured in.
                            //
                            // The set counts are gone from here. "3 effective · 1
                            // warmup · 2 muscles" was three numbers describing a
                            // session it did not name, and the numbers are on the
                            // screen you open next anyway. What a strength log needs
                            // the list to say is *what was worked* — which is also the
                            // only thing telling two Gym logs on one day apart, now
                            // that neither carries a name.
                            when {
                                summary.isDistanceTime -> Text(
                                    summary.distanceTimeLine(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                muscles.isEmpty() -> Text(
                                    // Started and left. It says so rather than showing
                                    // "0 sets", a number that reads like a result.
                                    stringResource(Res.string.log_nothing_checked),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                else -> MusclePills(muscles)
                            }
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
                // Falls back for a log whose category was deleted: the dialog still
                // has to name what it is about to destroy.
                val name = summary.categoryName
                    ?: stringResource(Res.string.log_uncategorised)
                Text(
                    if (summary.isEmpty) {
                        stringResource(Res.string.log_delete_empty, name)
                    } else if (summary.isDistanceTime) {
                        // A ride has no sets and no muscles, so the strength wording
                        // read "0 effective sets across 0 muscles will be removed" —
                        // true, and a description of the wrong thing. Name what it
                        // actually holds.
                        stringResource(Res.string.log_delete_measures, name, summary.distanceTimeLine())
                    } else {
                        stringResource(
                            Res.string.log_delete_count,
                            name,
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
/**
 * Step one of logging: what kind of training was it.
 *
 * A dialog rather than a screen because it asks one question with a handful of
 * answers and no state worth going back to — and because the app ships three
 * categories, so this list is never empty on a fresh install the way the activity
 * list it replaces always was.
 */
@Composable
private fun CategoryPickerDialog(
    categories: List<ActivityCategory>,
    onDismiss: () -> Unit,
    onPick: (ActivityCategory) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.log_pick_category)) },
        text = {
            if (categories.isEmpty()) {
                // Only reachable by deleting all three shipped categories, which the
                // seed ledger then honours. Says where to get one back.
                Text(stringResource(Res.string.log_no_categories))
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(category) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                listOfNotNull(category.icon, category.name).joinToString(" "),
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
 * What a strength log worked, one pill per muscle.
 *
 * A [FlowRow] because the count is unbounded — a session can touch a dozen muscles —
 * and a single line would clip the ones that did not fit, which is the failure mode
 * that once deleted a button from this screen entirely (2026-08-25 §6). Pills wrap to
 * a second line instead.
 *
 * They are labels, not controls: tapping one does nothing a tap on the card does not
 * already do, and making them clickable would put two targets where the row means one.
 */
@Composable
private fun MusclePills(muscles: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        muscles.forEach { name ->
            Surface(
                shape = MaterialTheme.shapes.small,
                // The step the app uses for a chip that is on but not selected: it has
                // to read as a label sitting on the card, not as a button.
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
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
    WorkoutMessage.CategoryNotFound -> stringResource(Res.string.log_msg_category_missing)
}

// --- Previews ---------------------------------------------------------------

private val previewLogState = WorkoutLogUiState(
    categories = previewCategories,
    selectedDay = PREVIEW_TODAY,
    today = PREVIEW_TODAY,
    daysWithSessions = previewDaysWithSessions,
    dayLog = previewDayLog,
    musclesBySession = previewDayMuscles,
    isLoading = false,
)

/**
 * A day holding all three shapes of session at once: a strength log with volume, a ride
 * with a distance and a pace, and one that was started and never filled in.
 *
 * The third is the one to look at. It says so in words rather than showing "0 sets",
 * because a zero on this line reads like a result somebody logged.
 */
@PreviewLightDark
@Composable
private fun WorkoutLogContentPreview() {
    PreviewSurface {
        WorkoutLogContent(
            state = previewLogState,
            onOpenEntry = { _, _, _ -> },
            onNewLog = {},
            onSelectDay = {},
            onDeleteLog = {},
            onStepWeek = {},
            onGoToToday = {},
            onConsumeMessage = {},
        )
    }
}

/** Today with nothing on it. The hint points at the New-log button in the header. */
@Preview(name = "Nothing logged today")
@Composable
private fun WorkoutLogContentEmptyTodayPreview() {
    PreviewSurface {
        WorkoutLogContent(
            state = previewLogState.copy(dayLog = emptyList()),
            onOpenEntry = { _, _, _ -> },
            onNewLog = {},
            onSelectDay = {},
            onDeleteLog = {},
            onStepWeek = {},
            onGoToToday = {},
            onConsumeMessage = {},
        )
    }
}

/**
 * A fresh install: no activities at all, so the hint sends the user to the Activities
 * tab instead of to a button that would open an empty picker.
 */
@Preview(name = "No activities yet")
@Composable
private fun WorkoutLogContentNoActivitiesPreview() {
    PreviewSurface {
        WorkoutLogContent(
            state = WorkoutLogUiState(
                selectedDay = PREVIEW_TODAY,
                today = PREVIEW_TODAY,
                isLoading = false,
            ),
            onOpenEntry = { _, _, _ -> },
            onNewLog = {},
            onSelectDay = {},
            onDeleteLog = {},
            onStepWeek = {},
            onGoToToday = {},
            onConsumeMessage = {},
        )
    }
}

/**
 * Loading swaps the *content*, never the header — returning early would take the
 * New-log button with it, and this preview is what proves the button is still there.
 */
@Preview(name = "Loading")
@Composable
private fun WorkoutLogContentLoadingPreview() {
    PreviewSurface {
        WorkoutLogContent(
            state = WorkoutLogUiState(selectedDay = PREVIEW_TODAY, today = PREVIEW_TODAY),
            onOpenEntry = { _, _, _ -> },
            onNewLog = {},
            onSelectDay = {},
            onDeleteLog = {},
            onStepWeek = {},
            onGoToToday = {},
            onConsumeMessage = {},
        )
    }
}

/**
 * The day's list on its own.
 *
 * The delete confirmation lives behind a swipe and local state, so a static render
 * cannot show it — run the preview and drag a row left.
 */
@PreviewLightDark
@Composable
private fun DayLogPreview() {
    PreviewSurface {
        DayLog(
            state = previewLogState,
            onOpenEntry = { _, _, _ -> },
            onDeleteLog = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** A browsed day with nothing on it phrases the empty state differently from today. */
@Preview(name = "Empty, not today")
@Composable
private fun DayLogEmptyPreview() {
    PreviewSurface {
        DayLog(
            state = previewLogState.copy(dayLog = emptyList(), selectedDay = PREVIEW_TODAY - 3),
            onOpenEntry = { _, _, _ -> },
            onDeleteLog = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Step one of logging.
 *
 * This dialog used to list every activity in the app, which meant you could only log
 * something you had already created on another tab — and, earlier still, it was
 * filtered to strength, so a distance activity could never be logged at all. It asks
 * for the *kind* of training now, which is a question with three shipped answers on a
 * fresh install and no prerequisite anywhere.
 */
@PreviewLightDark
@Composable
private fun CategoryPickerDialogPreview() {
    PreviewSurface {
        CategoryPickerDialog(
            categories = previewCategories.filter { !it.isArchived },
            onDismiss = {},
            onPick = {},
        )
    }
}

/**
 * Only reachable by deleting all three shipped categories — the seed ledger honours
 * that rather than putting them back — so this is a state the user chose, and the
 * text has to say how to undo it.
 */
@Preview(name = "No categories")
@Composable
private fun CategoryPickerDialogEmptyPreview() {
    PreviewSurface {
        CategoryPickerDialog(categories = emptyList(), onDismiss = {}, onPick = {})
    }
}

/**
 * The distance line and the pace formatter.
 *
 * A pace is read as "5:14", never as 314 seconds, and the guards matter: a ride saved
 * with only one of the two measures, or with a zero distance, has no pace at all rather
 * than an "Infinity /km".
 */
@Preview(name = "en")
@Preview(name = "es", locale = "es")
@Composable
private fun DistanceLinesPreview() {
    val samples = listOf(
        previewDayLog[1],
        previewDayLog[1].copy(durationSeconds = null),
        previewDayLog[1].copy(distanceMeters = 0),
        previewDayLog[1].copy(distanceMeters = 21_097, durationSeconds = 6_614),
    )
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader(text = "distanceTimeLine")
            samples.forEach { summary ->
                Text(text = summary.distanceTimeLine(), style = MaterialTheme.typography.bodySmall)
            }
            SectionHeader(text = "WorkoutMessage")
            Text(
                text = messageText(WorkoutMessage.Deleted),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
