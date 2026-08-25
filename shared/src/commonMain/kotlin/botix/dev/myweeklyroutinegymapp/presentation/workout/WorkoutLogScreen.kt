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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.unit.dp
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_cancel
import myweeklyroutinegymapp.shared.generated.resources.common_close
import myweeklyroutinegymapp.shared.generated.resources.common_delete
import myweeklyroutinegymapp.shared.generated.resources.common_ok
import myweeklyroutinegymapp.shared.generated.resources.common_open
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
import myweeklyroutinegymapp.shared.generated.resources.log_title
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
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.SessionEntry
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.common.MessageSnackbar
import botix.dev.myweeklyroutinegymapp.presentation.common.ScreenHeader
import botix.dev.myweeklyroutinegymapp.presentation.common.WeekStrip
import botix.dev.myweeklyroutinegymapp.presentation.common.SectionHeader
import botix.dev.myweeklyroutinegymapp.presentation.common.format1
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
    onStepWeek: (Boolean) -> Unit,
    onGoToToday: () -> Unit,
    onCheck: (Long) -> Unit,
    onSetSets: (Long, Long, Long) -> Unit,
    onUncheck: (Long) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewLogDialog by remember { mutableStateOf(false) }

    Column(
        // Narrow side margins so the week strip can use the width: seven cells plus
        // two arrows is the widest row on any screen here, and every dp taken off the
        // sides goes straight into the cells. Vertical stays at 16 — it is the gap
        // to the app bar and the tab row, and has no such pressure on it.
        modifier = modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(Res.string.log_title)) {
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
            state.sessionId == null -> DayLog(state, onOpenExisting, onDeleteLog, Modifier.weight(1f))
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
            activities = state.strengthActivities,
            onDismiss = { showNewLogDialog = false },
            onPick = { activityId ->
                onOpenSession(activityId)
                showNewLogDialog = false
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(summary.activityName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        // An untouched session says so instead of showing "0 sets",
                        // which reads like a logged result rather than an empty start.
                        if (summary.isEmpty) {
                            stringResource(Res.string.log_nothing_checked)
                        } else {
                            stringResource(
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onOpenExisting(summary) }) {
                            Text(stringResource(Res.string.common_open))
                        }
                        TextButton(onClick = { confirmingDelete = summary }) {
                            Text(stringResource(Res.string.common_delete))
                        }
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

/** Choosing what the new log is for. Strength only — no other type uses this form. */
@Composable
private fun NewLogDialog(
    activities: List<Activity>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.log_new)) },
        text = {
            if (activities.isEmpty()) {
                Text(stringResource(Res.string.log_no_strength_activities))
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    activities.forEach { activity ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(activity.id) }
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
        // Picking one creates the log and closes, so there is nothing left to confirm.
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

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
