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
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_add
import myweeklyroutinegymapp.shared.generated.resources.common_archive
import myweeklyroutinegymapp.shared.generated.resources.common_cancel
import myweeklyroutinegymapp.shared.generated.resources.common_delete
import myweeklyroutinegymapp.shared.generated.resources.common_deleted
import myweeklyroutinegymapp.shared.generated.resources.common_done
import myweeklyroutinegymapp.shared.generated.resources.common_name
import myweeklyroutinegymapp.shared.generated.resources.common_ok
import myweeklyroutinegymapp.shared.generated.resources.common_saved
import myweeklyroutinegymapp.shared.generated.resources.muscle_msg_delete_blocked
import myweeklyroutinegymapp.shared.generated.resources.muscle_msg_name_blank
import myweeklyroutinegymapp.shared.generated.resources.muscle_new
import myweeklyroutinegymapp.shared.generated.resources.muscle_new_group
import myweeklyroutinegymapp.shared.generated.resources.muscles_add_group
import myweeklyroutinegymapp.shared.generated.resources.muscles_empty
import myweeklyroutinegymapp.shared.generated.resources.muscles_empty_hint
import myweeklyroutinegymapp.shared.generated.resources.muscles_no_leaves
import myweeklyroutinegymapp.shared.generated.resources.muscles_title
import myweeklyroutinegymapp.shared.generated.resources.plural_session_entries
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.common.MessageSnackbar
import botix.dev.myweeklyroutinegymapp.presentation.common.ScreenHeader
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
        ScreenHeader(title = stringResource(Res.string.muscles_title)) {
            Button(onClick = { addGroupOpen = true }) { Text(stringResource(Res.string.muscles_add_group)) }
            TextButton(onClick = onBack) { Text(stringResource(Res.string.common_done)) }
        }

        MessageSnackbar(state.message?.let { messageText(it) }, onConsumeMessage)

        if (state.isEmpty) {
            EmptyState(
                text = stringResource(Res.string.muscles_empty),
                hint = stringResource(Res.string.muscles_empty_hint),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                                TextButton(onClick = { addMuscleToGroup = group.id }) {
                                    Text(stringResource(Res.string.common_add))
                                }
                                TextButton(onClick = { onDeleteGroup(group.id) }) {
                                    Text(stringResource(Res.string.common_delete))
                                }
                            }
                        }

                        if (muscles.isEmpty()) {
                            // A group with no leaves cannot be logged against — the
                            // session always points at a leaf, never at a group.
                            Text(
                                stringResource(Res.string.muscles_no_leaves),
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
                                        Text(stringResource(Res.string.common_archive))
                                    }
                                    TextButton(onClick = { onDeleteMuscle(muscle.id) }) {
                                        Text(stringResource(Res.string.common_delete))
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
            title = stringResource(Res.string.muscle_new_group),
            onDismiss = { addGroupOpen = false },
            onConfirm = {
                onAddGroup(it)
                addGroupOpen = false
            },
        )
    }

    addMuscleToGroup?.let { groupId ->
        NameDialog(
            title = stringResource(Res.string.muscle_new),
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
                label = { Text(stringResource(Res.string.common_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text(stringResource(Res.string.common_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

/** The message's wording. Rendering is [MessageSnackbar]'s job. */
@Composable
private fun messageText(message: MuscleCatalogMessage): String = when (message) {
    MuscleCatalogMessage.Saved -> stringResource(Res.string.common_saved)
    MuscleCatalogMessage.Deleted -> stringResource(Res.string.common_deleted)
    MuscleCatalogMessage.BlankName -> stringResource(Res.string.muscle_msg_name_blank)
    is MuscleCatalogMessage.DeleteBlocked -> stringResource(
        Res.string.muscle_msg_delete_blocked,
        pluralStringResource(
            Res.plurals.plural_session_entries,
            message.referenceCount.toInt(),
            message.referenceCount.toString(),
        ),
    )
}
