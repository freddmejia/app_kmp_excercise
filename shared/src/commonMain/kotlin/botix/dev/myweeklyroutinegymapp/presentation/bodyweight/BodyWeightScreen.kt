package botix.dev.myweeklyroutinegymapp.presentation.bodyweight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_all
import myweeklyroutinegymapp.shared.generated.resources.common_cancel
import myweeklyroutinegymapp.shared.generated.resources.common_delete
import myweeklyroutinegymapp.shared.generated.resources.common_deleted
import myweeklyroutinegymapp.shared.generated.resources.weight_change
import myweeklyroutinegymapp.shared.generated.resources.weight_change_percent
import myweeklyroutinegymapp.shared.generated.resources.weight_delete_text
import myweeklyroutinegymapp.shared.generated.resources.weight_delete_title
import myweeklyroutinegymapp.shared.generated.resources.weight_empty
import myweeklyroutinegymapp.shared.generated.resources.weight_empty_hint
import myweeklyroutinegymapp.shared.generated.resources.weight_future
import myweeklyroutinegymapp.shared.generated.resources.weight_history
import myweeklyroutinegymapp.shared.generated.resources.weight_kg
import myweeklyroutinegymapp.shared.generated.resources.weight_log
import myweeklyroutinegymapp.shared.generated.resources.weight_mode_daily
import myweeklyroutinegymapp.shared.generated.resources.weight_mode_monthly
import myweeklyroutinegymapp.shared.generated.resources.weight_no_data
import myweeklyroutinegymapp.shared.generated.resources.weight_preset_days
import myweeklyroutinegymapp.shared.generated.resources.weight_rate
import myweeklyroutinegymapp.shared.generated.resources.weight_replace
import myweeklyroutinegymapp.shared.generated.resources.weight_samples
import myweeklyroutinegymapp.shared.generated.resources.weight_trend_falling
import myweeklyroutinegymapp.shared.generated.resources.weight_trend_insufficient
import myweeklyroutinegymapp.shared.generated.resources.weight_trend_rising
import myweeklyroutinegymapp.shared.generated.resources.weight_trend_stable
import myweeklyroutinegymapp.shared.generated.resources.plural_measurements
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.model.ChartMode
import botix.dev.myweeklyroutinegymapp.domain.model.WeightTrend
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.BodyWeightRangePreset
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.common.MessageSnackbar
import botix.dev.myweeklyroutinegymapp.presentation.common.SectionHeader
import botix.dev.myweeklyroutinegymapp.presentation.common.SwipeToDelete
import botix.dev.myweeklyroutinegymapp.presentation.common.WeekStrip
import botix.dev.myweeklyroutinegymapp.presentation.common.asFullDate
import botix.dev.myweeklyroutinegymapp.presentation.common.format1
import botix.dev.myweeklyroutinegymapp.presentation.common.signed1
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymIcons
import org.koin.compose.viewmodel.koinViewModel

/**
 * DI boundary for this screen.
 *
 * The ViewModel is a default parameter rather than resolved in the body, so a test or
 * a preview can hand one in without Koin being started at all.
 */
@Composable
fun BodyWeightScreen(
    onOpenEntry: (epochDay: Long, existing: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BodyWeightViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BodyWeightContent(
        state = state,
        onSelectPreset = viewModel::selectPreset,
        onOpenEntry = onOpenEntry,
        onSelectDay = viewModel::selectDay,
        onStepWeek = viewModel::stepWeek,
        onGoToToday = viewModel::goToToday,
        onDelete = viewModel::delete,
        onConsumeMessage = viewModel::consumeMessage,
        modifier = modifier,
    )
}

/**
 * Stateless on purpose: everything it draws arrives as [state], so it renders
 * identically in a preview, a screenshot test and the running app.
 *
 * This screen reads and this screen alone: the summary, the chart, and the readings
 * that produced them. Writing one is [BodyWeightEntryScreen]'s job. It used to carry
 * a permanent input row above the chart, which meant the two things a person comes
 * here to do — *record today* and *look at the trend* — were fighting for the same
 * space, and neither had enough of it.
 */
@Composable
fun BodyWeightContent(
    state: BodyWeightUiState,
    onSelectPreset: (BodyWeightRangePreset) -> Unit,
    onOpenEntry: (epochDay: Long, existing: Boolean) -> Unit,
    onSelectDay: (Long) -> Unit,
    onStepWeek: (Boolean) -> Unit,
    onGoToToday: () -> Unit,
    onDelete: (Long) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(modifier)
        return
    }

    var confirmingDelete by remember { mutableStateOf<BodyWeightEntry?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Pinned, like the app bar above them: the strip and the presets say what you
        // are looking at *and* control it, so scrolling them away would leave the
        // readings below unattributed.
        //
        // The strip picks the day a reading belongs to. The presets are the chart's
        // own range: a week is the unit you *log* in, months are the unit you read a
        // trend in, and collapsing the two would cost the trend its point.
        WeekStrip(
            selectedDay = state.selectedDay,
            today = state.today,
            visibleDays = state.visibleDays,
            markedDays = state.daysWithEntry,
            onSelectDay = onSelectDay,
            onStepWeek = onStepWeek,
            onGoToToday = onGoToToday,
        ) {
            // Opposite the date, because the date is its subject: this button logs
            // *that day*. It says Replace rather than Log when the day already holds
            // a reading — there is one per day, so the second one is a correction,
            // and the button should not promise otherwise before it is pressed.
            Button(
                onClick = { onOpenEntry(state.selectedDay, state.entryForSelectedDay != null) },
                // A day that has not happened cannot have been weighed. The use case
                // refuses it too; this is the half that says so before the tap.
                enabled = !state.selectedDayIsFuture,
            ) {
                Text(
                    stringResource(
                        if (state.entryForSelectedDay == null) {
                            Res.string.weight_log
                        } else {
                            Res.string.weight_replace
                        },
                    ),
                )
            }
        }

        // Only when it applies, and only because the button above it just went inert:
        // a control that stops working without saying why is the thing this line
        // exists to prevent.
        if (state.selectedDayIsFuture) {
            Text(
                text = stringResource(Res.string.weight_future),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        PresetRow(state.preset, onSelectPreset)

        MessageSnackbar(state.message?.let { messageText(it) }, onConsumeMessage)

        // weight(1f), not "whatever is left over": a plain nested list is measured
        // with the remaining constraints and collapses to nothing once the header
        // grows — which is exactly what adding the strip did to the history here.
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") { SummaryCard(state) }
            item(key = "chart") { WeightChart(state.chart) }

            if (state.history.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        text = stringResource(Res.string.weight_empty),
                        hint = stringResource(Res.string.weight_empty_hint),
                    )
                }
            } else {
                item(key = "history-header") { SectionHeader(stringResource(Res.string.weight_history)) }
                itemsIndexed(state.history, key = { _, entry -> entry.id }) { index, entry ->
                    HistoryRow(
                        entry = entry,
                        // The list is newest first, so the reading this one should be
                        // compared against is the *next* item, not the previous one.
                        // Null for the oldest row on screen: the difference from a
                        // reading that is outside the range would be a number the
                        // user cannot see the other half of.
                        previous = state.history.getOrNull(index + 1),
                        onOpen = { onOpenEntry(entry.epochDay, true) },
                        onDelete = { confirmingDelete = entry },
                    )
                }
            }
        }
    }

    confirmingDelete?.let { entry ->
        // Confirmed, even though the delete itself is unrestricted. A swipe is easier
        // to do by accident than a button was to press, and nothing here can be
        // undone — the reading is simply gone, and only the person who stood on the
        // scale that morning can put it back.
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text(stringResource(Res.string.weight_delete_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.weight_delete_text,
                        entry.weightKg.format1(),
                        entry.epochDay.asFullDate(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(entry.id)
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

@Composable
private fun SummaryCard(state: BodyWeightUiState) {
    val summary = state.chart.summary
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = summary.currentKg?.let { stringResource(Res.string.weight_kg, it.format1()) }
                    ?: stringResource(Res.string.weight_no_data),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            // Every figure is nullable by design — an empty range has no current
            // weight, and rendering 0.0 would read as a real measurement.
            summary.changeKg?.let { change ->
                Text(
                    stringResource(Res.string.weight_change, change.signed1()) +
                        (
                            summary.changePercent
                                ?.let { stringResource(Res.string.weight_change_percent, it.signed1()) }
                                ?: ""
                            ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = stringResource(
                    when (summary.trend) {
                        WeightTrend.RISING -> Res.string.weight_trend_rising
                        WeightTrend.FALLING -> Res.string.weight_trend_falling
                        WeightTrend.STABLE -> Res.string.weight_trend_stable
                        WeightTrend.INSUFFICIENT_DATA -> Res.string.weight_trend_insufficient
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            summary.ratePerWeekKg?.let {
                Text(
                    stringResource(Res.string.weight_rate, it.signed1()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(
                    Res.string.weight_samples,
                    pluralStringResource(
                        Res.plurals.plural_measurements,
                        summary.sampleCount,
                        summary.sampleCount.toString(),
                    ),
                    // The enum name is an English identifier, so it is mapped rather
                    // than lowercased into the sentence.
                    stringResource(
                        if (state.chart.mode == ChartMode.DAILY) {
                            Res.string.weight_mode_daily
                        } else {
                            Res.string.weight_mode_monthly
                        },
                    ),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PresetRow(
    selected: BodyWeightRangePreset,
    onSelectPreset: (BodyWeightRangePreset) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BodyWeightRangePreset.entries.forEach { preset ->
            FilterChip(
                selected = preset == selected,
                onClick = { onSelectPreset(preset) },
                label = {
                    Text(
                        preset.days?.let { stringResource(Res.string.weight_preset_days, it.toString()) }
                            ?: stringResource(Res.string.common_all),
                    )
                },
            )
        }
    }
}

/**
 * One reading.
 *
 * A card that is itself the button, and a swipe that asks to delete it — the same two
 * gestures the log and the category rows use, so a row behaves the same way wherever
 * it is met. The Delete button it replaces was permanently on screen, on every row,
 * for the rarest thing anyone does here.
 *
 * [previous] is the next-older reading, and the difference between the two is the
 * whole reason a list of weights is worth reading at all: 89.0 kg says little, and
 * "89.0, up 0.4 from the last one" says what happened. It is drawn in the muted
 * on-surface colour rather than a red or a green, because this app has no idea
 * whether gaining is good news.
 */
@Composable
private fun HistoryRow(
    entry: BodyWeightEntry,
    previous: BodyWeightEntry?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    SwipeToDelete(
        label = stringResource(Res.string.common_delete),
        onDelete = onDelete,
    ) {
        Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                // Centred rather than pinned to the top: the row is two or three
                // lines depending on whether it carries a note, and an arrow that
                // rides up and down with that reads as loose.
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(Res.string.weight_kg, entry.weightKg.format1()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // The date was implicit while every reading was "today"; with a
                    // day selector it is the thing that tells two rows apart.
                    Text(
                        entry.epochDay.asFullDate(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entry.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                previous?.let {
                    Text(
                        stringResource(
                            Res.string.weight_kg,
                            (entry.weightKg - it.weightKg).signed1(),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = GymIcons.ChevronRight,
                    // Decorative. The card carries the click, so a screen reader
                    // announces the row once; describing the arrow would add a
                    // second thing to hear that does nothing.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The message's wording. Rendering is [MessageSnackbar]'s job. */
@Composable
private fun messageText(message: BodyWeightMessage): String = when (message) {
    BodyWeightMessage.Deleted -> stringResource(Res.string.common_deleted)
}
