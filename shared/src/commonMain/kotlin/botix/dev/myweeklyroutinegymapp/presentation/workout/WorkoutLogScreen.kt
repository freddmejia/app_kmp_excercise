package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_cancel
import myweeklyroutinegymapp.shared.generated.resources.common_close
import myweeklyroutinegymapp.shared.generated.resources.common_delete
import myweeklyroutinegymapp.shared.generated.resources.common_ok
import myweeklyroutinegymapp.shared.generated.resources.log_delete_count
import myweeklyroutinegymapp.shared.generated.resources.log_delete_empty
import myweeklyroutinegymapp.shared.generated.resources.log_delete_title
import myweeklyroutinegymapp.shared.generated.resources.log_effective
import myweeklyroutinegymapp.shared.generated.resources.log_hint_new_log
import myweeklyroutinegymapp.shared.generated.resources.log_hint_no_activities
import myweeklyroutinegymapp.shared.generated.resources.log_msg_activity_missing
import myweeklyroutinegymapp.shared.generated.resources.log_msg_invalid_sets
import myweeklyroutinegymapp.shared.generated.resources.log_msg_logged
import myweeklyroutinegymapp.shared.generated.resources.log_msg_not_strength
import myweeklyroutinegymapp.shared.generated.resources.log_msg_removed
import myweeklyroutinegymapp.shared.generated.resources.log_new
import myweeklyroutinegymapp.shared.generated.resources.common_save
import myweeklyroutinegymapp.shared.generated.resources.log_distance_km
import myweeklyroutinegymapp.shared.generated.resources.log_distance_summary
import myweeklyroutinegymapp.shared.generated.resources.log_pace
import myweeklyroutinegymapp.shared.generated.resources.log_distance_time_title
import myweeklyroutinegymapp.shared.generated.resources.log_duration_min
import myweeklyroutinegymapp.shared.generated.resources.log_msg_nothing_measured
import myweeklyroutinegymapp.shared.generated.resources.log_no_activities_at_all
import myweeklyroutinegymapp.shared.generated.resources.log_no_muscles
import myweeklyroutinegymapp.shared.generated.resources.log_no_muscles_hint
import myweeklyroutinegymapp.shared.generated.resources.log_no_strength_activities
import myweeklyroutinegymapp.shared.generated.resources.log_nothing_checked
import myweeklyroutinegymapp.shared.generated.resources.log_nothing_day
import myweeklyroutinegymapp.shared.generated.resources.log_nothing_today
import myweeklyroutinegymapp.shared.generated.resources.log_scope_activity
import myweeklyroutinegymapp.shared.generated.resources.log_scope_all
import myweeklyroutinegymapp.shared.generated.resources.log_show_activity
import myweeklyroutinegymapp.shared.generated.resources.log_show_all
import myweeklyroutinegymapp.shared.generated.resources.log_summary
import myweeklyroutinegymapp.shared.generated.resources.log_totals_warmup
import myweeklyroutinegymapp.shared.generated.resources.log_untrained_header
import myweeklyroutinegymapp.shared.generated.resources.log_warmup
import myweeklyroutinegymapp.shared.generated.resources.log_warmup_ratio
import myweeklyroutinegymapp.shared.generated.resources.muscles_title
import myweeklyroutinegymapp.shared.generated.resources.plural_effective_sets
import myweeklyroutinegymapp.shared.generated.resources.plural_muscles
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.SessionEntry
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.common.MessageSnackbar
import botix.dev.myweeklyroutinegymapp.presentation.common.ScreenActions
import botix.dev.myweeklyroutinegymapp.presentation.common.SwipeToDelete
import botix.dev.myweeklyroutinegymapp.presentation.common.WeekStrip
import botix.dev.myweeklyroutinegymapp.presentation.common.SectionHeader
import botix.dev.myweeklyroutinegymapp.presentation.common.format1
import botix.dev.myweeklyroutinegymapp.presentation.common.formatMeasure
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WorkoutLogScreen(
    onManageMuscles: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutLogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WorkoutLogContent(
        state = state,
        onManageMuscles = onManageMuscles,
        onOpenSession = { viewModel.openSession(it) },
        onCloseSession = viewModel::closeSession,
        onCheck = { muscleId -> viewModel.addSets(muscleId, effectiveSets = 1) },
        onSetSets = { muscleId, effective, warmup -> viewModel.setSets(muscleId, effective, warmup) },
        onUncheck = viewModel::uncheck,
        onSetShowAllMuscles = viewModel::setShowAllMuscles,
        onSelectDay = viewModel::selectDay,
        onOpenExisting = viewModel::openExisting,
        onDeleteLog = viewModel::deleteLog,
        onLogDistanceTime = viewModel::logDistanceTime,
        onStepWeek = viewModel::stepWeek,
        onGoToToday = viewModel::goToToday,
        onConsumeMessage = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
fun WorkoutLogContent(
    state: WorkoutLogUiState,
    onManageMuscles: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onCloseSession: () -> Unit,
    onSetShowAllMuscles: (Boolean) -> Unit,
    onSelectDay: (Long) -> Unit,
    onOpenExisting: (SessionSummary) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onLogDistanceTime: (Long, Double?, Double?) -> Unit,
    onStepWeek: (Boolean) -> Unit,
    onGoToToday: () -> Unit,
    onCheck: (Long) -> Unit,
    onSetSets: (Long, Long, Long) -> Unit,
    onUncheck: (Long) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewLogDialog by remember { mutableStateOf(false) }
    // The distance/time form's target, or null when it is closed.
    var distanceTarget by remember { mutableStateOf<DistanceTarget?>(null) }

    Column(
        // Narrow side margins so the week strip can use the width: seven cells plus
        // two arrows is the widest row on any screen here, and every dp taken off the
        // sides goes straight into the cells. Vertical stays at 16 — it is the gap
        // to the app bar and the tab row, and has no such pressure on it.
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenActions {
            TextButton(onClick = onManageMuscles) { Text(stringResource(Res.string.muscles_title)) }
            if (state.sessionId != null) {
                TextButton(onClick = onCloseSession) { Text(stringResource(Res.string.common_close)) }
            } else {
                // Creating a log is now an explicit act rather than a side effect
                // of picking from a list that filled the screen. The strip decides
                // *when*; this decides *whether*.
                Button(onClick = { showNewLogDialog = true }) { Text(stringResource(Res.string.log_new)) }
            }
        }

        // Above both branches: the day is a filter on the whole screen, not something
        // that belongs to an open session. Moving it while a session is open follows
        // that activity to the new day rather than closing the screen.
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

        // weight(1f) from here, because DayLog and OpenSession are their own
        // composables and cannot reach this Column's scope to claim the space.
        //
        // Loading swaps the *content*, never the header: returning early instead
        // would take the title and the New-log button with it, which is the one
        // difference the Activities screen never had.
        when {
            state.isLoading -> LoadingState(Modifier.weight(1f))
            state.sessionId == null -> DayLog(
                state = state,
                onOpenExisting = { summary ->
                    // A distance session has no muscles to pick, so the strength flow
                    // would only reject it. It reopens the form it was written with,
                    // filled in — which is also how it gets corrected.
                    val activity = state.distanceActivities.firstOrNull {
                        it.id == summary.activityId
                    }
                    if (summary.isDistanceTime && activity != null) {
                        distanceTarget = DistanceTarget(activity, summary)
                    } else {
                        onOpenExisting(summary)
                    }
                },
                onDeleteLog = onDeleteLog,
                modifier = Modifier.weight(1f),
            )
            else -> OpenSession(
                state,
                onSetShowAllMuscles,
                onCheck,
                onSetSets,
                onUncheck,
                Modifier.weight(1f),
            )
        }
    }

    if (showNewLogDialog) {
        NewLogDialog(
            // Both kinds, in one list. Splitting them into two buttons would make the
            // user classify the activity before choosing it, which is the app's job:
            // the type is already recorded, and it decides which form opens next.
            strengthActivities = state.strengthActivities,
            distanceActivities = state.distanceActivities,
            onDismiss = { showNewLogDialog = false },
            onPickStrength = { activityId ->
                onOpenSession(activityId)
                showNewLogDialog = false
            },
            onPickDistance = { activity ->
                showNewLogDialog = false
                distanceTarget = DistanceTarget(activity, existing = null)
            },
        )
    }

    // Distance and time are captured in one dialog and written in one go, so unlike a
    // strength session there is no open session for the screen to fall into.
    distanceTarget?.let { target ->
        DistanceTimeDialog(
            activity = target.activity,
            existing = target.existing,
            onDismiss = { distanceTarget = null },
            onConfirm = { km, minutes ->
                onLogDistanceTime(target.activity.id, km, minutes)
                distanceTarget = null
            },
        )
    }
}

/**
 * What the distance/time dialog is being opened for.
 *
 * [existing] is null for a new log and set when an already-logged session is being
 * reopened, which is what lets one dialog serve both — the fields start empty or start
 * filled, and saving writes the same way either way.
 */
private data class DistanceTarget(val activity: Activity, val existing: SessionSummary?)

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
    onOpenExisting: (SessionSummary) -> Unit,
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
                    onClick = { onOpenExisting(summary) },
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
 * the form that fits it — the muscle picker for a strength activity, the distance and
 * time dialog for the other. Until now this list was filtered to STRENGTH, which is
 * why an activity created as distance-and-time could never be logged: it simply never
 * appeared here, and the screen offered no other way in.
 */
@Composable
private fun NewLogDialog(
    strengthActivities: List<Activity>,
    distanceActivities: List<Activity>,
    onDismiss: () -> Unit,
    onPickStrength: (Long) -> Unit,
    onPickDistance: (Activity) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.log_new)) },
        text = {
            // Strength first, then distance: grouping by kind keeps a long catalogue
            // scannable without headers for what is often one or two rows.
            val activities = strengthActivities + distanceActivities
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
                                .clickable {
                                    if (activity.measurementType == MeasurementType.STRENGTH) {
                                        onPickStrength(activity.id)
                                    } else {
                                        onPickDistance(activity)
                                    }
                                }
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
        // Picking one opens the next step and closes this, so there is nothing to
        // confirm here.
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

/**
 * Distance and time for one session, in kilometres and minutes.
 *
 * The two fields are individually optional and one of them is required — a swim with
 * no distance measured and a route walked without timing it are both real records; an
 * empty form is not. The use case owns that rule and this only reports it, so the two
 * cannot drift into disagreeing about what counts as measured.
 *
 * The text is parsed on submit rather than validated as it is typed: rejecting
 * keystrokes makes "20." impossible to pass through on the way to "20.5", and the
 * decimal separator varies by locale, so filtering characters is a trap.
 */
@Composable
private fun DistanceTimeDialog(
    activity: Activity,
    existing: SessionSummary?,
    onDismiss: () -> Unit,
    onConfirm: (Double?, Double?) -> Unit,
) {
    // Metres and seconds back out to km and minutes, because those are the units the
    // fields are labelled in — reopening a 20.5 km ride has to show "20.5", not
    // "20500". A whole number keeps no decimal, so 45 minutes reads back as "45".
    var distance by remember {
        mutableStateOf(existing?.distanceMeters?.let { (it / 1000.0).formatMeasure() }.orEmpty())
    }
    var duration by remember {
        mutableStateOf(existing?.durationSeconds?.let { (it / 60.0).formatMeasure() }.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    Res.string.log_distance_time_title,
                    listOfNotNull(activity.icon, activity.name).joinToString(" "),
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = distance,
                    onValueChange = { distance = it },
                    label = { Text(stringResource(Res.string.log_distance_km)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text(stringResource(Res.string.log_duration_min)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(distance.toDecimalOrNull(), duration.toDecimalOrNull()) },
            ) { Text(stringResource(Res.string.common_save)) }
        },
        dismissButton = {
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

/**
 * Blank, or nonsense, reads as "not given" — which the use case then rules on.
 *
 * The comma is folded to a point because it is the decimal separator in three of this
 * app's four locales, and several soft keyboards offer only one of the two.
 */
private fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Composable
private fun OpenSession(
    state: WorkoutLogUiState,
    onSetShowAllMuscles: (Boolean) -> Unit,
    onCheck: (Long) -> Unit,
    onSetSets: (Long, Long, Long) -> Unit,
    onUncheck: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entriesByMuscle = state.detail?.entries.orEmpty().associateBy { it.muscleId }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TotalsCard(state) }

        // Shown whenever the list is narrowed, and also once it has been widened, so
        // "why am I seeing everything?" has an answer on screen either way.
        if (state.isScopedToActivity || state.showAllMuscles) {
            item(key = "scope-banner") {
                MuscleScopeBanner(
                    isScoped = state.isScopedToActivity,
                    onSetShowAllMuscles = onSetShowAllMuscles,
                )
            }
        }

        if (state.sections.isEmpty()) {
            item {
                EmptyState(
                    text = stringResource(Res.string.log_no_muscles),
                    hint = stringResource(Res.string.log_no_muscles_hint),
                )
            }
        }

        state.sections.forEach { section ->
            item(key = "group-${section.groupId}") { SectionHeader(section.groupName) }
            items(section.muscles, key = { "muscle-${it.muscleId}" }) { muscle ->
                MuscleRow(
                    name = muscle.muscleName,
                    entry = entriesByMuscle[muscle.muscleId],
                    onCheck = { onCheck(muscle.muscleId) },
                    onSetSets = { effective, warmup -> onSetSets(muscle.muscleId, effective, warmup) },
                    onUncheck = { onUncheck(muscle.muscleId) },
                )
            }
        }

        if (state.untrainedThisWeek.isNotEmpty()) {
            item(key = "untrained-header") {
                SectionHeader(stringResource(Res.string.log_untrained_header))
            }
            item(key = "untrained-body") {
                Text(
                    state.untrainedThisWeek.joinToString { "${it.groupName} · ${it.muscleName}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(state: WorkoutLogUiState) {
    val totals = state.totals
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                pluralStringResource(
                    Res.plurals.plural_effective_sets,
                    totals.effectiveSets.toInt(),
                    totals.effectiveSets.toString(),
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // Warmup is tracked but deliberately excluded from volume, so it is
            // reported beside the total rather than folded into it.
            Text(
                stringResource(Res.string.log_totals_warmup, totals.warmupSets.toString()),
                style = MaterialTheme.typography.bodyMedium,
            )
            totals.warmupRatio?.let {
                Text(
                    stringResource(Res.string.log_warmup_ratio, it.format1()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MuscleRow(
    name: String,
    entry: SessionEntry?,
    onCheck: () -> Unit,
    onSetSets: (Long, Long) -> Unit,
    onUncheck: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The checkbox is not a stored field: ticking it creates the entry, and
            // clearing it deletes the entry. Existence *is* the check.
            Checkbox(
                checked = entry != null,
                onCheckedChange = { checked -> if (checked) onCheck() else onUncheck() },
            )
            Text(name, style = MaterialTheme.typography.bodyLarge)
        }

        if (entry != null) {
            // Stacked rather than side by side: two steppers plus their labels do not
            // fit across a phone, and squeezing them would cost the labels — which are
            // the only thing telling the two counters apart.
            Column(
                modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SetsStepper(
                    label = stringResource(Res.string.log_effective),
                    value = entry.effectiveSets,
                    // Dropping below one effective set means the muscle was not
                    // trained, which the schema rejects — so unchecking is the correct
                    // interpretation, not an error. Never disabled for that reason.
                    onDecrement = {
                        if (entry.effectiveSets <= 1) {
                            onUncheck()
                        } else {
                            onSetSets(entry.effectiveSets - 1, entry.warmupSets)
                        }
                    },
                    onIncrement = { onSetSets(entry.effectiveSets + 1, entry.warmupSets) },
                )
                SetsStepper(
                    label = stringResource(Res.string.log_warmup),
                    value = entry.warmupSets,
                    // Zero warmup is a normal entry, not an absent one, so this floor
                    // is a disabled button rather than the uncheck the effective
                    // stepper does. Warmup never decides whether the muscle was
                    // trained; it stays out of volume entirely.
                    //
                    // Clamped as well as disabled. The use case rejects a negative
                    // with InvalidSets, whose message talks about effective sets — so
                    // if the button were ever reachable the user would be told
                    // something untrue. Cheaper to make the floor unreachable.
                    onDecrement = {
                        onSetSets(entry.effectiveSets, maxOf(0, entry.warmupSets - 1))
                    },
                    onIncrement = { onSetSets(entry.effectiveSets, entry.warmupSets + 1) },
                    decrementEnabled = entry.warmupSets > 0,
                )
            }
        }
    }
}

/**
 * One labelled counter. Shared by both set kinds so they cannot drift apart visually —
 * the label is what distinguishes them, and identical controls make that reliable.
 */
@Composable
private fun SetsStepper(
    label: String,
    value: Long,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        OutlinedButton(
            onClick = onDecrement,
            enabled = decrementEnabled,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { Text("−") }

        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        OutlinedButton(
            onClick = onIncrement,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) { Text("+") }
    }
}

/** The message's wording. Rendering is [MessageSnackbar]'s job. */
@Composable
private fun messageText(message: WorkoutMessage): String = when (message) {
    WorkoutMessage.Logged -> stringResource(Res.string.log_msg_logged)
    WorkoutMessage.Removed -> stringResource(Res.string.log_msg_removed)
    WorkoutMessage.InvalidSets -> stringResource(Res.string.log_msg_invalid_sets)
    WorkoutMessage.ActivityNotFound -> stringResource(Res.string.log_msg_activity_missing)
    WorkoutMessage.NotAStrengthActivity -> stringResource(Res.string.log_msg_not_strength)
    WorkoutMessage.NothingMeasured -> stringResource(Res.string.log_msg_nothing_measured)
}

/**
 * Says which muscle list is on screen, and lets the user swap.
 *
 * The activity's linked muscles are a starting point, never a restriction — an
 * unplanned muscle has to be loggable without going back to edit the activity first.
 */
@Composable
private fun MuscleScopeBanner(
    isScoped: Boolean,
    onSetShowAllMuscles: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (isScoped) Res.string.log_scope_activity else Res.string.log_scope_all,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSetShowAllMuscles(isScoped) }) {
                Text(
                    stringResource(
                        if (isScoped) Res.string.log_show_all else Res.string.log_show_activity,
                    ),
                )
            }
        }
    }
}
