package botix.dev.myweeklyroutinegymapp.presentation.bodyweight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_all
import myweeklyroutinegymapp.shared.generated.resources.common_delete
import myweeklyroutinegymapp.shared.generated.resources.common_deleted
import myweeklyroutinegymapp.shared.generated.resources.common_ok
import myweeklyroutinegymapp.shared.generated.resources.log_msg_logged
import myweeklyroutinegymapp.shared.generated.resources.weight_already
import myweeklyroutinegymapp.shared.generated.resources.weight_change
import myweeklyroutinegymapp.shared.generated.resources.weight_change_percent
import myweeklyroutinegymapp.shared.generated.resources.weight_empty
import myweeklyroutinegymapp.shared.generated.resources.weight_empty_hint
import myweeklyroutinegymapp.shared.generated.resources.weight_future
import myweeklyroutinegymapp.shared.generated.resources.weight_history
import myweeklyroutinegymapp.shared.generated.resources.weight_input_label
import myweeklyroutinegymapp.shared.generated.resources.weight_kg
import myweeklyroutinegymapp.shared.generated.resources.weight_log
import myweeklyroutinegymapp.shared.generated.resources.weight_mode_daily
import myweeklyroutinegymapp.shared.generated.resources.weight_mode_monthly
import myweeklyroutinegymapp.shared.generated.resources.weight_msg_invalid
import myweeklyroutinegymapp.shared.generated.resources.weight_no_data
import myweeklyroutinegymapp.shared.generated.resources.weight_one_per_day
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
import botix.dev.myweeklyroutinegymapp.presentation.common.WeekStrip
import botix.dev.myweeklyroutinegymapp.presentation.common.asFullDate
import botix.dev.myweeklyroutinegymapp.presentation.common.format1
import botix.dev.myweeklyroutinegymapp.presentation.common.signed1
import org.koin.compose.viewmodel.koinViewModel

/**
 * DI boundary for this screen.
 *
 * The ViewModel is a default parameter rather than resolved in the body, so a test or
 * a preview can hand one in without Koin being started at all.
 */
@Composable
fun BodyWeightScreen(
    modifier: Modifier = Modifier,
    viewModel: BodyWeightViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BodyWeightContent(
        state = state,
        onSelectPreset = viewModel::selectPreset,
        onLog = { weight -> viewModel.log(weight) },
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
 */
@Composable
fun BodyWeightContent(
    state: BodyWeightUiState,
    onSelectPreset: (BodyWeightRangePreset) -> Unit,
    onLog: (Double?) -> Unit,
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

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Pinned, like the app bar above them: the strip and the presets say what
        // you are looking at *and* control it, so scrolling them away would leave the
        // readings below unattributed. This screen has no actions of its own, so the
        // title in the bar is the whole of its header.
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
        )
        PresetRow(state.preset, onSelectPreset)

        MessageSnackbar(state.message?.let { messageText(it) }, onConsumeMessage)

        // weight(1f), not "whatever is left over": a plain nested list is measured
        // with the remaining constraints and collapses to nothing once the header
        // grows — which is exactly what adding the strip did to the history here.
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "input") {
                WeightInput(
                    selectedDay = state.selectedDay,
                    existing = state.entryForSelectedDay,
                    isFuture = state.selectedDayIsFuture,
                    onLog = onLog,
                )
            }

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
                items(state.history, key = { it.id }) { entry ->
                    HistoryRow(entry, onDelete)
                }
            }
        }
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
 * The logging form, for whichever day the strip has selected.
 *
 * [existing] is what that day already holds. There is one reading per day, so saving
 * replaces rather than adds — and the warning is rendered from state, before the
 * button is pressed, instead of being discovered afterwards.
 */
@Composable
private fun WeightInput(
    selectedDay: Long,
    existing: BodyWeightEntry?,
    isFuture: Boolean,
    onLog: (Double?) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(Res.string.weight_input_label, selectedDay.asFullDate())) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    // Unparseable input is handed over as null: the ViewModel already
                    // treats that as invalid, so the screen needs no rule of its own.
                    onLog(input.trim().replace(',', '.').toDoubleOrNull())
                    input = ""
                },
                // Disabled, and the use case refuses it too. The control says no
                // before you press; the domain says no if anything gets past it.
                enabled = !isFuture,
            ) {
                Text(
                    stringResource(
                        if (existing == null) Res.string.weight_log else Res.string.weight_replace,
                    ),
                )
            }
        }
        Text(
            text = when {
                isFuture -> stringResource(Res.string.weight_future)
                existing != null ->
                    stringResource(Res.string.weight_already, existing.weightKg.format1())

                else -> stringResource(Res.string.weight_one_per_day)
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isFuture -> MaterialTheme.colorScheme.error
                existing != null -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun HistoryRow(entry: BodyWeightEntry, onDelete: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                stringResource(Res.string.weight_kg, entry.weightKg.format1()),
                style = MaterialTheme.typography.bodyLarge,
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
        // Unrestricted here, unlike the catalogue deletes: nothing references
        // a measurement, so removing one cannot corrupt another metric.
        TextButton(onClick = { onDelete(entry.id) }) { Text(stringResource(Res.string.common_delete)) }
    }
}

/** The message's wording. Rendering is [MessageSnackbar]'s job. */
@Composable
private fun messageText(message: BodyWeightMessage): String = when (message) {
    BodyWeightMessage.Logged -> stringResource(Res.string.log_msg_logged)
    BodyWeightMessage.Deleted -> stringResource(Res.string.common_deleted)
    BodyWeightMessage.InvalidWeight -> stringResource(Res.string.weight_msg_invalid)
    BodyWeightMessage.FutureDay -> stringResource(Res.string.weight_future)
}
