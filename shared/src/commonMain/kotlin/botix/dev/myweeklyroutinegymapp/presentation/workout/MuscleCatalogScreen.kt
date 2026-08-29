package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_add
import myweeklyroutinegymapp.shared.generated.resources.common_archive
import myweeklyroutinegymapp.shared.generated.resources.common_cancel
import myweeklyroutinegymapp.shared.generated.resources.common_delete
import myweeklyroutinegymapp.shared.generated.resources.common_deleted
import myweeklyroutinegymapp.shared.generated.resources.common_name
import myweeklyroutinegymapp.shared.generated.resources.common_saved
import myweeklyroutinegymapp.shared.generated.resources.muscle_edit
import myweeklyroutinegymapp.shared.generated.resources.muscle_edit_group
import myweeklyroutinegymapp.shared.generated.resources.muscle_msg_delete_blocked
import myweeklyroutinegymapp.shared.generated.resources.muscle_msg_name_blank
import myweeklyroutinegymapp.shared.generated.resources.muscle_new
import myweeklyroutinegymapp.shared.generated.resources.muscle_new_group
import myweeklyroutinegymapp.shared.generated.resources.muscles_add_group
import myweeklyroutinegymapp.shared.generated.resources.muscles_empty
import myweeklyroutinegymapp.shared.generated.resources.muscles_empty_hint
import myweeklyroutinegymapp.shared.generated.resources.muscles_no_leaves
import myweeklyroutinegymapp.shared.generated.resources.plural_session_entries
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.common.MessageSnackbar
import botix.dev.myweeklyroutinegymapp.presentation.common.ScreenActions
import botix.dev.myweeklyroutinegymapp.presentation.common.SectionHeader
import botix.dev.myweeklyroutinegymapp.presentation.preview.PreviewSurface
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewMuscleGroups
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewMusclesByGroup
import botix.dev.myweeklyroutinegymapp.presentation.workout.MuscleEditorViewModel.Companion.NEW
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MuscleCatalogScreen(
    onEditGroup: (groupId: Long) -> Unit,
    onEditMuscle: (groupId: Long, muscleId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MuscleCatalogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MuscleCatalogContent(
        state = state,
        onEditGroup = onEditGroup,
        onEditMuscle = onEditMuscle,
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
    /** Zero opens an empty form: there is no group yet to name. */
    onEditGroup: (groupId: Long) -> Unit,
    /** A muscle id of zero adds a new leaf to the group instead of renaming one. */
    onEditMuscle: (groupId: Long, muscleId: Long) -> Unit,
    onArchiveMuscle: (Long, Boolean) -> Unit,
    onDeleteMuscle: (Long) -> Unit,
    onDeleteGroup: (Long) -> Unit,
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
        ScreenActions {
            Button(onClick = { onEditGroup(NEW) }) {
                Text(stringResource(Res.string.muscles_add_group))
            }
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
                            // The name is the button, the way a row is elsewhere:
                            // tapping it renames the group. It is a bare Text rather
                            // than a card of its own — this one already sits inside a
                            // card that holds the group's leaves — so it needs the
                            // click label a card would have carried for it.
                            Text(
                                group.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(
                                        onClickLabel = stringResource(Res.string.muscle_edit_group),
                                        role = Role.Button,
                                        onClick = { onEditGroup(group.id) },
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                            Row {
                                TextButton(onClick = { onEditMuscle(group.id, NEW) }) {
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
                                Text(
                                    muscle.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable(
                                            onClickLabel = stringResource(Res.string.muscle_edit),
                                            role = Role.Button,
                                            onClick = { onEditMuscle(group.id, muscle.id) },
                                        )
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                )
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

// --- Previews ---------------------------------------------------------------

/**
 * The catalogue as it normally looks: groups as cards, leaves inside them, and every
 * name a button that opens the editor.
 *
 * Nothing here says "tap the name to rename it" in text — the click label does, for a
 * screen reader — so this preview is where that affordance has to be judged by eye.
 */
@PreviewLightDark
@Composable
private fun MuscleCatalogContentPreview() {
    PreviewSurface {
        MuscleCatalogContent(
            state = MuscleCatalogUiState(
                groups = previewMuscleGroups,
                musclesByGroup = previewMusclesByGroup,
                isLoading = false,
            ),
            onEditGroup = {},
            onEditMuscle = { _, _ -> },
            onArchiveMuscle = { _, _ -> },
            onDeleteMuscle = {},
            onDeleteGroup = {},
            onConsumeMessage = {},
        )
    }
}

/**
 * A group with no leaves. It cannot be logged against at all — a session always points
 * at a leaf, never at a group — so the card says so rather than looking merely short.
 */
@Preview(name = "Group with no leaves")
@Composable
private fun MuscleCatalogContentEmptyGroupPreview() {
    PreviewSurface {
        MuscleCatalogContent(
            state = MuscleCatalogUiState(
                groups = previewMuscleGroups.take(1),
                musclesByGroup = emptyMap(),
                isLoading = false,
            ),
            onEditGroup = {},
            onEditMuscle = { _, _ -> },
            onArchiveMuscle = { _, _ -> },
            onDeleteMuscle = {},
            onDeleteGroup = {},
            onConsumeMessage = {},
        )
    }
}

@Preview(name = "Empty catalogue")
@Composable
private fun MuscleCatalogContentEmptyPreview() {
    PreviewSurface {
        MuscleCatalogContent(
            state = MuscleCatalogUiState(isLoading = false),
            onEditGroup = {},
            onEditMuscle = { _, _ -> },
            onArchiveMuscle = { _, _ -> },
            onDeleteMuscle = {},
            onDeleteGroup = {},
            onConsumeMessage = {},
        )
    }
}

@Preview(name = "Loading", heightDp = 200)
@Composable
private fun MuscleCatalogContentLoadingPreview() {
    PreviewSurface {
        MuscleCatalogContent(
            state = MuscleCatalogUiState(),
            onEditGroup = {},
            onEditMuscle = { _, _ -> },
            onArchiveMuscle = { _, _ -> },
            onDeleteMuscle = {},
            onDeleteGroup = {},
            onConsumeMessage = {},
        )
    }
}

/**
 * Every message, including the refusal: a muscle referenced by history cannot be
 * deleted, and the count is what makes that refusal explicable rather than arbitrary.
 */
@Preview(name = "en")
@Preview(name = "es", locale = "es")
@Composable
private fun MuscleCatalogMessagesPreview() {
    val messages = listOf(
        MuscleCatalogMessage.Saved,
        MuscleCatalogMessage.BlankName,
        MuscleCatalogMessage.Deleted,
        MuscleCatalogMessage.DeleteBlocked(referenceCount = 1),
        MuscleCatalogMessage.DeleteBlocked(referenceCount = 37),
    )
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader(text = "MuscleCatalogMessage")
            messages.forEach { message ->
                Text(text = messageText(message), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
