package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.domain.model.SessionEntry
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.FormActionBar
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.common.MessageSnackbar
import botix.dev.myweeklyroutinegymapp.presentation.common.SectionHeader
import botix.dev.myweeklyroutinegymapp.presentation.common.asFullDate
import botix.dev.myweeklyroutinegymapp.presentation.common.format1
import botix.dev.myweeklyroutinegymapp.presentation.preview.PREVIEW_TODAY
import botix.dev.myweeklyroutinegymapp.presentation.preview.PreviewSurface
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewDistanceSession
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewEmptySessionDetail
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewMuscleSections
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewScopedMuscleSections
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewSessionDetail
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewSessionEntries
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewStrengthSession
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewUntrainedMuscles
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_save
import myweeklyroutinegymapp.shared.generated.resources.log_distance_km
import myweeklyroutinegymapp.shared.generated.resources.log_duration_min
import myweeklyroutinegymapp.shared.generated.resources.log_effective
import myweeklyroutinegymapp.shared.generated.resources.log_msg_session_missing
import myweeklyroutinegymapp.shared.generated.resources.log_uncategorised
import myweeklyroutinegymapp.shared.generated.resources.log_msg_invalid_sets
import myweeklyroutinegymapp.shared.generated.resources.log_msg_logged
import myweeklyroutinegymapp.shared.generated.resources.log_msg_nothing_measured
import myweeklyroutinegymapp.shared.generated.resources.log_msg_removed
import myweeklyroutinegymapp.shared.generated.resources.log_no_muscles
import myweeklyroutinegymapp.shared.generated.resources.log_no_muscles_hint
import myweeklyroutinegymapp.shared.generated.resources.log_scope_activity
import myweeklyroutinegymapp.shared.generated.resources.log_scope_all
import myweeklyroutinegymapp.shared.generated.resources.log_show_activity
import myweeklyroutinegymapp.shared.generated.resources.log_show_all
import myweeklyroutinegymapp.shared.generated.resources.log_totals_warmup
import myweeklyroutinegymapp.shared.generated.resources.log_untrained_header
import myweeklyroutinegymapp.shared.generated.resources.log_warmup
import myweeklyroutinegymapp.shared.generated.resources.log_warmup_ratio
import myweeklyroutinegymapp.shared.generated.resources.plural_effective_sets
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One log, on its own screen: what was trained, or how far and how long.
 *
 * It used to be neither. A strength session took over the log screen in place — same
 * app bar, same week strip, a Close button where New log had been — so the only sign
 * you had gone anywhere was that the content had changed under you. A distance log was
 * a dialog, which is a different shape again for the same act. Both are this screen
 * now: pushed onto the stack, named in the app bar, and left by the back arrow.
 *
 * ### One screen, two forms
 * The activity's measurement type decides which. It is recorded against the activity
 * already, so making the user choose the form before choosing the activity would ask
 * them to classify something the app has known since the activity was created.
 */
@Composable
fun WorkoutEntryScreen(
    sessionId: Long,
    epochDay: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutEntryViewModel = koinViewModel(
        // Keyed by what the screen is *about*. Without this the store hands back the
        // ViewModel built for the first log ever opened, with its session and day
        // baked into the constructor, for every log after it.
        key = "workout-entry-$sessionId-$epochDay",
        parameters = { parametersOf(sessionId, epochDay) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reload first, then listen — the same order and for the same reasons as the
    // body-weight editor. A ViewModel keyed by the session outlives the visit that
    // created it, so the second visit must start from the database; and the save
    // signal has no replay, so a save from the first visit cannot close the second one
    // the moment it opens.
    LaunchedEffect(Unit) {
        viewModel.onOpened()
        viewModel.saved.collect { onDone() }
    }

    WorkoutEntryContent(
        state = state,
        onCheck = { muscleId -> viewModel.addSets(muscleId, effectiveSets = 1) },
        onSetSets = { muscleId, effective, warmup -> viewModel.setSets(muscleId, effective, warmup) },
        onUncheck = viewModel::uncheck,
        onDistanceChange = viewModel::setDistance,
        onDurationChange = viewModel::setDuration,
        onSave = viewModel::save,
        onConsumeMessage = viewModel::consumeMessage,
        modifier = modifier,
    )
}

/**
 * Stateless on purpose: everything it draws arrives as [state], so it renders
 * identically in a preview, a screenshot test and the running app.
 */
@Composable
fun WorkoutEntryContent(
    state: WorkoutEntryUiState,
    onCheck: (Long) -> Unit,
    onSetSets: (Long, Long, Long) -> Unit,
    onUncheck: (Long) -> Unit,
    onDistanceChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onSave: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The app bar says whether this is a new log or an old one; it does not say
            // *what* is being logged or *when*. Those two are what tell this log apart
            // from every other, so they lead the screen — the same job the date does on
            // the body-weight editor.
            state.session?.let { session ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        // Null when the category was deleted after this was logged.
                        // The log still happened and its sets still count, so it gets
                        // a heading that says what it is rather than an empty gap.
                        text = session.categoryName
                            ?: stringResource(Res.string.log_uncategorised),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = state.epochDay.asFullDate(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            MessageSnackbar(state.message?.let { messageText(it) }, onConsumeMessage)

            when (state.form) {
                WorkoutEntryForm.LOADING -> LoadingState(Modifier.weight(1f))

                WorkoutEntryForm.STRENGTH -> StrengthLog(
                    state = state,
                    onCheck = onCheck,
                    onSetSets = onSetSets,
                    onUncheck = onUncheck,
                    modifier = Modifier.weight(1f),
                )

                WorkoutEntryForm.DISTANCE_TIME -> DistanceTimeLog(
                    state = state,
                    onDistanceChange = onDistanceChange,
                    onDurationChange = onDurationChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Only the distance form commits with a button. A strength log writes as it is
        // filled in — checking a muscle *is* the write — so there is nothing there to
        // save, and a permanently inert Save bar under it would be claiming otherwise.
        if (state.form == WorkoutEntryForm.DISTANCE_TIME) {
            FormActionBar {
                Button(
                    onClick = onSave,
                    // Inert while both fields are empty. Anything else the form cannot
                    // judge — the use case rules on it and the answer comes back as a
                    // message.
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.common_save))
                }
            }
        }
    }
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
private fun DistanceTimeLog(
    state: WorkoutEntryUiState,
    onDistanceChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // Only the fields are in here now. Save is pinned to the bottom of the
        // *screen* by [WorkoutEntryContent], edge to edge, rather than sitting inside
        // this form's own side margins — a bar with margins does not read as a bar.
        //
        // It used to be the last child of this scrolling column, on the reasoning that
        // scrolling kept it reachable at a large font scale, which had it backwards:
        // scrolling the button guarantees there is a position where it is off-screen.
        // See `FormActionBar`.
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.distanceInput,
            onValueChange = onDistanceChange,
            label = { Text(stringResource(Res.string.log_distance_km)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.durationInput,
            onValueChange = onDurationChange,
            label = { Text(stringResource(Res.string.log_duration_min)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The muscle picker for a strength log.
 *
 * There is no Save button here, and that is not an omission: checking a muscle writes
 * the row, and the counters write on every press. The session exists from the moment
 * this screen opens — which is what lets a log be filled in across a day rather than
 * in one sitting.
 */
@Composable
private fun StrengthLog(
    state: WorkoutEntryUiState,
    onCheck: (Long) -> Unit,
    onSetSets: (Long, Long, Long) -> Unit,
    onUncheck: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entriesByMuscle = state.detail?.entries.orEmpty().associateBy { it.muscleId }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TotalsCard(state) }

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
private fun TotalsCard(state: WorkoutEntryUiState) {
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
private fun messageText(message: WorkoutEntryMessage): String = when (message) {
    WorkoutEntryMessage.Logged -> stringResource(Res.string.log_msg_logged)
    WorkoutEntryMessage.Removed -> stringResource(Res.string.log_msg_removed)
    WorkoutEntryMessage.InvalidSets -> stringResource(Res.string.log_msg_invalid_sets)
    WorkoutEntryMessage.SessionNotFound -> stringResource(Res.string.log_msg_session_missing)
    WorkoutEntryMessage.NothingMeasured -> stringResource(Res.string.log_msg_nothing_measured)
}

// --- Previews ---------------------------------------------------------------

/** A strength log, with the whole muscle catalogue offered. */
private val previewStrengthState = WorkoutEntryUiState(
    epochDay = PREVIEW_TODAY,
    session = previewStrengthSession,
    form = WorkoutEntryForm.STRENGTH,
    sections = previewScopedMuscleSections,
    detail = previewSessionDetail,
    untrainedThisWeek = previewUntrainedMuscles,
)

private val previewDistanceState = WorkoutEntryUiState(
    epochDay = PREVIEW_TODAY,
    session = previewDistanceSession,
    form = WorkoutEntryForm.DISTANCE_TIME,
    distanceInput = "8.4",
    durationInput = "44",
)

/**
 * The strength form. There is no Save button and that is not an omission — checking a
 * muscle *is* the write, and the session row was created when the screen opened.
 */
@PreviewLightDark
@Composable
private fun WorkoutEntryContentStrengthPreview() {
    PreviewSurface {
        WorkoutEntryContent(
            state = previewStrengthState,
            onCheck = {},
            onSetSets = { _, _, _ -> },
            onUncheck = {},
            onDistanceChange = {},
            onDurationChange = {},
            onSave = {},
            onConsumeMessage = {},
        )
    }
}

/**
 * The distance form, from the same composable and the same state class. Which one
 * appears is decided by the activity's measurement type alone — the user is never asked
 * to classify something the app recorded when the activity was created.
 */
@PreviewLightDark
@Composable
private fun WorkoutEntryContentDistancePreview() {
    PreviewSurface {
        WorkoutEntryContent(
            state = previewDistanceState,
            onCheck = {},
            onSetSets = { _, _, _ -> },
            onUncheck = {},
            onDistanceChange = {},
            onDurationChange = {},
            onSave = {},
            onConsumeMessage = {},
        )
    }
}

@Preview(name = "Loading", heightDp = 240)
@Composable
private fun WorkoutEntryContentLoadingPreview() {
    PreviewSurface {
        WorkoutEntryContent(
            state = WorkoutEntryUiState(epochDay = PREVIEW_TODAY),
            onCheck = {},
            onSetSets = { _, _, _ -> },
            onUncheck = {},
            onDistanceChange = {},
            onDurationChange = {},
            onSave = {},
            onConsumeMessage = {},
        )
    }
}

/** An empty form: both fields blank, so Save is inert. */
@Preview(name = "Distance — nothing entered", heightDp = 320)
@Composable
private fun DistanceTimeLogEmptyPreview() {
    PreviewSurface {
        DistanceTimeLog(
            state = previewDistanceState.copy(distanceInput = "", durationInput = ""),
            onDistanceChange = {},
            onDurationChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * One measure only. Both fields are individually optional and one of them is required —
 * a swim with no distance and a route walked without timing it are both real records —
 * so this has to be a savable state, not a half-filled one.
 */
@Preview(name = "Distance — time only", heightDp = 320)
@Composable
private fun DistanceTimeLogTimeOnlyPreview() {
    PreviewSurface {
        DistanceTimeLog(
            state = previewDistanceState.copy(distanceInput = ""),
            onDistanceChange = {},
            onDurationChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * The picker widened to the whole catalogue.
 *
 * The banner is shown in both directions — narrowed *and* widened — so "why am I seeing
 * everything?" has an answer on screen either way.
 */
@Preview(name = "Strength — all muscles")
@Composable
private fun StrengthLogAllMusclesPreview() {
    PreviewSurface {
        StrengthLog(
            state = previewStrengthState.copy(
                sections = previewMuscleSections,
            ),
            onCheck = {},
            onSetSets = { _, _, _ -> },
            onUncheck = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** No catalogue at all: the hint points at Preferences, where muscles are created. */
@Preview(name = "Strength — no muscles")
@Composable
private fun StrengthLogNoMusclesPreview() {
    PreviewSurface {
        StrengthLog(
            state = previewStrengthState.copy(
                sections = emptyList(),
                detail = previewEmptySessionDetail,
                untrainedThisWeek = emptyList(),
            ),
            onCheck = {},
            onSetSets = { _, _, _ -> },
            onUncheck = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Totals with volume, and totals at zero.
 *
 * Warmup is reported beside the effective total rather than folded into it, because it
 * is deliberately excluded from volume. The ratio disappears at zero effective sets —
 * it would be meaningless there rather than zero.
 */
@PreviewLightDark
@Composable
private fun TotalsCardPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TotalsCard(previewStrengthState)
            TotalsCard(previewStrengthState.copy(detail = previewEmptySessionDetail))
        }
    }
}

/** Both directions of the banner, which are two different sentences and two buttons. */
/**
 * Unchecked, checked, and checked with no warmup.
 *
 * The last is the one that matters: the warmup stepper's decrement is disabled at zero,
 * while the effective stepper's decrement at one is *enabled* and unchecks the muscle
 * instead — dropping below one effective set means it was not trained, which is a
 * correct interpretation rather than an error.
 */
@PreviewLightDark
@Composable
private fun MuscleRowPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MuscleRow(
                name = "Lower chest",
                entry = null,
                onCheck = {},
                onSetSets = { _, _ -> },
                onUncheck = {},
            )
            MuscleRow(
                name = "Upper chest",
                entry = previewSessionEntries[1],
                onCheck = {},
                onSetSets = { _, _ -> },
                onUncheck = {},
            )
            MuscleRow(
                name = "General",
                entry = previewSessionEntries[0].copy(effectiveSets = 1, warmupSets = 0),
                onCheck = {},
                onSetSets = { _, _ -> },
                onUncheck = {},
            )
        }
    }
}

@Preview
@Composable
private fun SetsStepperPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SetsStepper(label = "Effective", value = 6, onDecrement = {}, onIncrement = {})
            SetsStepper(
                label = "Warmup",
                value = 0,
                onDecrement = {},
                onIncrement = {},
                decrementEnabled = false,
            )
        }
    }
}

/** Every message this screen can raise, in one place, per locale. */
@Preview(name = "en")
@Preview(name = "es", locale = "es")
@Composable
private fun WorkoutEntryMessagesPreview() {
    val messages = listOf(
        WorkoutEntryMessage.Logged,
        WorkoutEntryMessage.Removed,
        WorkoutEntryMessage.InvalidSets,
        WorkoutEntryMessage.SessionNotFound,
        WorkoutEntryMessage.NothingMeasured,
    )
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader(text = "WorkoutEntryMessage")
            messages.forEach { message ->
                Text(text = messageText(message), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
