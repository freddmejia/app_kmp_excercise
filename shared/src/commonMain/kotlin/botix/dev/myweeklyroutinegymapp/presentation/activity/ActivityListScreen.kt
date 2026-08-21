package botix.dev.myweeklyroutinegymapp.presentation.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.domain.model.Activity
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ActivityFilter
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ActivityListScreen(
    modifier: Modifier = Modifier,
    viewModel: ActivityListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ActivityListContent(
        state = state,
        onSelectFilter = viewModel::setFilter,
        onCreate = viewModel::create,
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
    onCreate: (String, MeasurementType) -> Unit,
    onSetArchived: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Activities", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { showCreateDialog = true }) { Text("New") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter == state.filter,
                    onClick = { onSelectFilter(filter) },
                    label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        state.message?.let { message ->
            MessageBanner(message = message, onDismiss = onConsumeMessage)
        }

        when {
            state.isLoading -> LoadingState()
            state.activities.isEmpty() -> EmptyState(
                text = "No activities here.",
                hint = "An activity declares what you do and how it is measured.",
            )

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.activities, key = { it.id }) { activity ->
                    ActivityRow(
                        activity = activity,
                        onSetArchived = onSetArchived,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateActivityDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, type ->
                onCreate(name, type)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun ActivityRow(
    activity: Activity,
    onSetArchived: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                        text = activity.measurementType.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (activity.isArchived) {
                    AssistChip(onClick = {}, label = { Text("Archived") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onSetArchived(activity.id, !activity.isArchived) }) {
                    Text(if (activity.isArchived) "Unarchive" else "Archive")
                }
                // Offered even when it will be refused: the refusal carries the
                // session count, which is what tells you to archive instead.
                TextButton(onClick = { onDelete(activity.id) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun CreateActivityDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, MeasurementType) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MeasurementType.STRENGTH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Text("How is it measured?", style = MaterialTheme.typography.labelLarge)
                // Chosen once, at creation: the type becomes immutable as soon as the
                // activity has a session, so this is the moment that matters.
                MeasurementType.entries.forEach { option ->
                    FilterChip(
                        selected = option == type,
                        onClick = { type = option },
                        label = { Text(option.label()) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, type) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MessageBanner(message: ActivityMessage, onDismiss: () -> Unit) {
    val text = when (message) {
        ActivityMessage.NameBlank -> "Give the activity a name."
        ActivityMessage.NameTaken -> "Another active activity already uses that name."
        ActivityMessage.Saved -> "Saved."
        ActivityMessage.Deleted -> "Deleted."
        is ActivityMessage.DeleteBlocked ->
            "Cannot delete: ${message.sessionCount} session(s) reference it. Archive it instead."

        is ActivityMessage.MeasurementTypeLocked ->
            "Type is locked: ${message.sessionCount} session(s) already recorded."
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

private fun MeasurementType.label(): String = when (this) {
    MeasurementType.STRENGTH -> "Strength — muscles and sets"
    MeasurementType.DISTANCE_TIME -> "Distance and time"
    MeasurementType.SIMPLE -> "Simple — did it that day"
}
