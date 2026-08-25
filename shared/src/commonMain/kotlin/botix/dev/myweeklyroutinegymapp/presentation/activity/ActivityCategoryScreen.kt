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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import myweeklyroutinegymapp.shared.generated.resources.categories_empty
import myweeklyroutinegymapp.shared.generated.resources.categories_empty_hint
import myweeklyroutinegymapp.shared.generated.resources.categories_title
import myweeklyroutinegymapp.shared.generated.resources.category_delete_count
import myweeklyroutinegymapp.shared.generated.resources.category_delete_none
import myweeklyroutinegymapp.shared.generated.resources.category_delete_title
import myweeklyroutinegymapp.shared.generated.resources.category_edit_title
import myweeklyroutinegymapp.shared.generated.resources.category_msg_archived_count
import myweeklyroutinegymapp.shared.generated.resources.category_msg_deleted_count
import myweeklyroutinegymapp.shared.generated.resources.category_msg_name_blank
import myweeklyroutinegymapp.shared.generated.resources.category_msg_name_taken
import myweeklyroutinegymapp.shared.generated.resources.category_new_title
import myweeklyroutinegymapp.shared.generated.resources.category_no_activities
import myweeklyroutinegymapp.shared.generated.resources.common_archive
import myweeklyroutinegymapp.shared.generated.resources.common_archived
import myweeklyroutinegymapp.shared.generated.resources.common_cancel
import myweeklyroutinegymapp.shared.generated.resources.common_delete
import myweeklyroutinegymapp.shared.generated.resources.common_deleted
import myweeklyroutinegymapp.shared.generated.resources.common_done
import myweeklyroutinegymapp.shared.generated.resources.common_icon_optional
import myweeklyroutinegymapp.shared.generated.resources.common_name
import myweeklyroutinegymapp.shared.generated.resources.common_new
import myweeklyroutinegymapp.shared.generated.resources.common_ok
import myweeklyroutinegymapp.shared.generated.resources.common_save
import myweeklyroutinegymapp.shared.generated.resources.common_saved
import myweeklyroutinegymapp.shared.generated.resources.plural_activities
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategoryUsage
import botix.dev.myweeklyroutinegymapp.presentation.common.EmptyState
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.common.MessageSnackbar
import botix.dev.myweeklyroutinegymapp.presentation.common.ScreenHeader
import botix.dev.myweeklyroutinegymapp.presentation.common.SwipeToDelete
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ActivityCategoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityCategoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ActivityCategoryContent(
        state = state,
        onBack = onBack,
        onCreate = { name, icon -> viewModel.create(name, icon) },
        onRename = { id, name, icon -> viewModel.rename(id, name, icon) },
        onArchive = { id -> viewModel.setArchived(id, true) },
        onDelete = viewModel::delete,
        onConsumeMessage = viewModel::consumeMessage,
        modifier = modifier,
    )
}

/**
 * Stateless half, so it stays previewable and testable — the screen above resolves
 * real dependencies and cannot be either.
 */
@Composable
fun ActivityCategoryContent(
    state: ActivityCategoryUiState,
    onBack: () -> Unit,
    onCreate: (String, String?) -> Unit,
    onRename: (Long, String, String?) -> Unit,
    onArchive: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var createOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ActivityCategoryUsage?>(null) }
    var confirmingDelete by remember { mutableStateOf<ActivityCategoryUsage?>(null) }

    if (state.isLoading) {
        LoadingState(modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(Res.string.categories_title)) {
            Button(onClick = { createOpen = true }) { Text(stringResource(Res.string.common_new)) }
            TextButton(onClick = onBack) { Text(stringResource(Res.string.common_done)) }
        }

        MessageSnackbar(state.message?.let { messageText(it) }, onConsumeMessage)

        if (state.isEmpty) {
            EmptyState(
                text = stringResource(Res.string.categories_empty),
                hint = stringResource(Res.string.categories_empty_hint),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.categories, key = { it.category.id }) { usage ->
                CategoryRow(
                    usage = usage,
                    onEdit = { editing = usage },
                    onArchive = { onArchive(usage.category.id) },
                    onDelete = { confirmingDelete = usage },
                )
            }
        }
    }

    if (createOpen) {
        CategoryFormDialog(
            title = stringResource(Res.string.category_new_title),
            initialName = "",
            initialIcon = "",
            onDismiss = { createOpen = false },
            onConfirm = { name, icon ->
                onCreate(name, icon)
                createOpen = false
            },
        )
    }

    editing?.let { usage ->
        CategoryFormDialog(
            title = stringResource(Res.string.category_edit_title),
            initialName = usage.category.name,
            initialIcon = usage.category.icon.orEmpty(),
            onDismiss = { editing = null },
            onConfirm = { name, icon ->
                onRename(usage.category.id, name, icon)
                editing = null
            },
        )
    }

    confirmingDelete?.let { usage ->
        // Confirmed rather than immediate, because the consequence is invisible
        // otherwise: the activities survive, but they lose this label silently.
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = {
                Text(stringResource(Res.string.category_delete_title, usage.category.name))
            },
            text = {
                Text(
                    if (usage.activityCount == 0L) {
                        stringResource(Res.string.category_delete_none)
                    } else {
                        stringResource(
                            Res.string.category_delete_count,
                            pluralStringResource(
                                Res.plurals.plural_activities,
                                usage.activityCount.toInt(),
                                usage.activityCount.toString(),
                            ),
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(usage.category.id)
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

/**
 * One category.
 *
 * Same shape as a workout-log row: the card is the button, a swipe to the left asks to
 * delete, and the trailing chevron says the row opens something. Two of the three
 * buttons are therefore gone — Edit was what tapping the card now does, and Delete was
 * what the swipe now does.
 *
 * [onArchive] keeps its button. It is the third thing you can do to a category and
 * there is no third gesture to give it; hiding it behind one would make it
 * undiscoverable to keep a symmetry nothing asked for.
 */
@Composable
private fun CategoryRow(
    usage: ActivityCategoryUsage,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    SwipeToDelete(
        label = stringResource(Res.string.common_delete),
        onDelete = onDelete,
    ) {
        Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = listOfNotNull(usage.category.icon, usage.category.name)
                            .joinToString(" "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (usage.activityCount == 0L) {
                            stringResource(Res.string.category_no_activities)
                        } else {
                            pluralStringResource(
                                Res.plurals.plural_activities,
                                usage.activityCount.toInt(),
                                usage.activityCount.toString(),
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onArchive) {
                        Text(stringResource(Res.string.common_archive))
                    }
                }
                Icon(
                    imageVector = GymIcons.ChevronRight,
                    // Decorative: the card carries the click, so the row is announced
                    // once rather than once for itself and once for an arrow.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryFormDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.common_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text(stringResource(Res.string.common_icon_optional)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            // Blank name is not blocked here: the use case owns that rule, and the
            // banner reports it. Duplicating the check would let the two disagree.
            TextButton(onClick = { onConfirm(name, icon.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(Res.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

/** The message's wording. Rendering is [MessageSnackbar]'s job. */
@Composable
private fun messageText(message: CategoryMessage): String = when (message) {
    CategoryMessage.Saved -> stringResource(Res.string.common_saved)
    CategoryMessage.NameBlank -> stringResource(Res.string.category_msg_name_blank)
    CategoryMessage.NameTaken -> stringResource(Res.string.category_msg_name_taken)
    is CategoryMessage.Deleted -> when (message.uncategorisedCount) {
        0L -> stringResource(Res.string.common_deleted)
        else -> stringResource(
            Res.string.category_msg_deleted_count,
            pluralStringResource(
                Res.plurals.plural_activities,
                message.uncategorisedCount.toInt(),
                message.uncategorisedCount.toString(),
            ),
        )
    }

    is CategoryMessage.Archived -> when (message.uncategorisedCount) {
        0L -> stringResource(Res.string.common_archived)
        else -> stringResource(
            Res.string.category_msg_archived_count,
            pluralStringResource(
                Res.plurals.plural_activities,
                message.uncategorisedCount.toInt(),
                message.uncategorisedCount.toString(),
            ),
        )
    }
}
