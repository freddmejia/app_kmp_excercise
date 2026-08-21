package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MuscleCatalogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MuscleCatalogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MuscleCatalogContent(
        state = state,
        onBack = onBack,
        onAddGroup = viewModel::addGroup,
        onAddMuscle = viewModel::addMuscle,
        onArchiveMuscle = viewModel::setMuscleArchived,
        onDeleteMuscle = viewModel::removeMuscle,
        onDeleteGroup = viewModel::removeGroup,
        onConsumeMessage = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
fun MuscleCatalogContent(
    state: MuscleCatalogUiState,
    onBack: () -> Unit,
    onAddGroup: (String) -> Unit,
    onAddMuscle: (Long, String) -> Unit,
    onArchiveMuscle: (Long, Boolean) -> Unit,
    onDeleteMuscle: (Long) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var addGroupOpen by remember { mutableStateOf(false) }
    var addMuscleToGroup by remember { mutableStateOf<Long?>(null) }

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
            Text("Muscles", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { addGroupOpen = true }) { Text("Add group") }
                TextButton(onClick = onBack) { Text("Done") }
            }
        }

        state.message?.let { MessageBanner(it, onConsumeMessage) }

        if (state.isEmpty) {
            EmptyState(
                text = "No muscle groups yet.",
                hint = "Add a group such as Chest, then leaves inside it. " +
                    "A \"General\" leaf lets you log the whole group at once.",
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.groups, key = { it.id }) { group ->
                val muscles = state.musclesByGroup[group.id].orEmpty()
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                group.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row {
                                TextButton(onClick = { addMuscleToGroup = group.id }) { Text("Add") }
                                TextButton(onClick = { onDeleteGroup(group.id) }) { Text("Delete") }
                            }
                        }

                        if (muscles.isEmpty()) {
                            // A group with no leaves cannot be logged against — the
                            // session always points at a leaf, never at a group.
                            Text(
                                "No leaves yet — add one before logging this group.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        muscles.forEach { muscle ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(muscle.name, style = MaterialTheme.typography.bodyLarge)
                                Row {
                                    TextButton(onClick = { onArchiveMuscle(muscle.id, true) }) {
                                        Text("Archive")
                                    }
                                    TextButton(onClick = { onDeleteMuscle(muscle.id) }) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addGroupOpen) {
        NameDialog(
            title = "New muscle group",
            onDismiss = { addGroupOpen = false },
            onConfirm = {
                onAddGroup(it)
                addGroupOpen = false
            },
        )
    }

    addMuscleToGroup?.let { groupId ->
        NameDialog(
            title = "New muscle",
            onDismiss = { addMuscleToGroup = null },
            onConfirm = {
                onAddMuscle(groupId, it)
                addMuscleToGroup = null
            },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MessageBanner(message: MuscleCatalogMessage, onDismiss: () -> Unit) {
    val text = when (message) {
        MuscleCatalogMessage.Saved -> "Saved."
        MuscleCatalogMessage.Deleted -> "Deleted."
        MuscleCatalogMessage.BlankName -> "Give it a name."
        is MuscleCatalogMessage.DeleteBlocked ->
            "Cannot delete: ${message.referenceCount} session entry/entries reference it. " +
                "Archive it instead to keep its history."
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
