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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.model.WeightTrend
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.BodyWeightRangePreset
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
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
        Text("Body weight", style = MaterialTheme.typography.headlineSmall)

        SummaryCard(state)
        WeightChart(state.chart)
        PresetRow(state.preset, onSelectPreset)
        WeightInput(onLog)

        state.message?.let { MessageBanner(it, onConsumeMessage) }

        if (state.history.isEmpty()) {
            EmptyState(
                text = "No measurements in this range.",
                hint = "One reading per day — logging the same day again replaces it.",
            )
        } else {
            HistoryList(state.history, onDelete)
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
                text = summary.currentKg?.let { "${it.format1()} kg" } ?: "No data",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            // Every figure is nullable by design — an empty range has no current
            // weight, and rendering 0.0 would read as a real measurement.
            summary.changeKg?.let { change ->
                Text(
                    "Change: ${change.signed1()} kg" +
                        (summary.changePercent?.let { " (${it.signed1()}%)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = when (summary.trend) {
                    WeightTrend.RISING -> "Trend: rising"
                    WeightTrend.FALLING -> "Trend: falling"
                    WeightTrend.STABLE -> "Trend: stable"
                    WeightTrend.INSUFFICIENT_DATA -> "Trend: not enough data yet"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            summary.ratePerWeekKg?.let {
                Text("Rate: ${it.signed1()} kg/week", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${summary.sampleCount} measurement(s) · ${state.chart.mode.name.lowercase()} view",
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
                label = { Text(preset.days?.let { "${it}d" } ?: "All") },
            )
        }
    }
}

@Composable
private fun WeightInput(onLog: (Double?) -> Unit) {
    var input by remember { mutableStateOf("") }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("kg") },
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
        ) {
            Text("Log")
        }
    }
}

@Composable
private fun HistoryList(entries: List<BodyWeightEntry>, onDelete: (Long) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(entries, key = { it.id }) { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("${entry.weightKg.format1()} kg", style = MaterialTheme.typography.bodyLarge)
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
                TextButton(onClick = { onDelete(entry.id) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun MessageBanner(message: BodyWeightMessage, onDismiss: () -> Unit) {
    val text = when (message) {
        BodyWeightMessage.Logged -> "Logged."
        BodyWeightMessage.Deleted -> "Deleted."
        BodyWeightMessage.InvalidWeight -> "Enter a weight greater than zero."
        is BodyWeightMessage.WillOverwrite ->
            "That day already has ${message.existing.weightKg.format1()} kg — saving replaces it."
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}
