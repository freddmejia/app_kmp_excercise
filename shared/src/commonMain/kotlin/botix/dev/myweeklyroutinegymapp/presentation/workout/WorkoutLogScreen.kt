package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.SessionEntry
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
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
    onCheck: (Long) -> Unit,
    onSetSets: (Long, Long, Long) -> Unit,
    onUncheck: (Long) -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Log workout", style = MaterialTheme.typography.headlineSmall)
            Row {
                TextButton(onClick = onManageMuscles) { Text("Muscles") }
                if (state.sessionId != null) {
                    TextButton(onClick = onCloseSession) { Text("Close") }
                }
            }
        }

        state.message?.let { MessageBanner(it, onConsumeMessage) }

        if (state.sessionId == null) {
            ActivityPicker(state, onOpenSession)
        } else {
            OpenSession(state, onCheck, onSetSets, onUncheck)
        }
    }
}

@Composable
private fun ActivityPicker(state: WorkoutLogUiState, onOpenSession: (Long) -> Unit) {
    if (state.strengthActivities.isEmpty()) {
        EmptyState(
            text = "No strength activities yet.",
            hint = "Create one on the Activities tab — only STRENGTH activities use this form.",
        )
        return
    }

    SectionHeader("Pick an activity for today")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.strengthActivities, key = { it.id }) { activity ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(activity.name, style = MaterialTheme.typography.titleMedium)
                    FilledTonalButton(onClick = { onOpenSession(activity.id) }) { Text("Start") }
                }
            }
        }
    }
}

@Composable
private fun OpenSession(
    state: WorkoutLogUiState,
    onCheck: (Long) -> Unit,
    onSetSets: (Long, Long, Long) -> Unit,
    onUncheck: (Long) -> Unit,
) {
    val entriesByMuscle = state.detail?.entries.orEmpty().associateBy { it.muscleId }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TotalsCard(state) }

        if (state.sections.isEmpty()) {
            item {
                EmptyState(
                    text = "No muscles in the catalogue.",
                    hint = "Use \"Muscles\" above to add groups and leaves before logging.",
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
            item(key = "untrained-header") { SectionHeader("Not trained in the last 7 days") }
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
                "${totals.effectiveSets} effective set(s)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // Warmup is tracked but deliberately excluded from volume, so it is
            // reported beside the total rather than folded into it.
            Text("${totals.warmupSets} warmup", style = MaterialTheme.typography.bodyMedium)
            totals.warmupRatio?.let {
                Text(
                    "Warmup ratio ${it.format1()}",
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        // Dropping below one effective set means the muscle was not
                        // trained, which the schema rejects — so unchecking is the
                        // correct interpretation, not an error.
                        if (entry.effectiveSets <= 1) {
                            onUncheck()
                        } else {
                            onSetSets(entry.effectiveSets - 1, entry.warmupSets)
                        }
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                ) { Text("−") }

                Text(
                    "${entry.effectiveSets}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                OutlinedButton(
                    onClick = { onSetSets(entry.effectiveSets + 1, entry.warmupSets) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                ) { Text("+") }
            }
        }
    }
}

@Composable
private fun MessageBanner(message: WorkoutMessage, onDismiss: () -> Unit) {
    val text = when (message) {
        WorkoutMessage.Logged -> "Logged."
        WorkoutMessage.Removed -> "Removed."
        WorkoutMessage.InvalidSets -> "A checked muscle needs at least one effective set."
        WorkoutMessage.ActivityNotFound -> "That activity no longer exists."
        WorkoutMessage.NotAStrengthActivity -> "This form only fits strength activities."
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
