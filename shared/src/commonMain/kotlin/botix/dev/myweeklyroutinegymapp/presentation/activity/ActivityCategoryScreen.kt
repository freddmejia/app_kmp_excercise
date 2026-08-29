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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.categories_empty
import myweeklyroutinegymapp.shared.generated.resources.categories_empty_hint
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
import myweeklyroutinegymapp.shared.generated.resources.common_icon_optional
import myweeklyroutinegymapp.shared.generated.resources.common_name
import myweeklyroutinegymapp.shared.generated.resources.common_new
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
import botix.dev.myweeklyroutinegymapp.presentation.activity.CategoryEditorViewModel.Companion.NEW
import botix.dev.myweeklyroutinegymapp.presentation.common.ScreenActions
import botix.dev.myweeklyroutinegymapp.presentation.common.SectionHeader
import botix.dev.myweeklyroutinegymapp.presentation.common.SwipeToDelete
import botix.dev.myweeklyroutinegymapp.presentation.preview.PreviewSurface
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewCategoryUsage
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ActivityCategoryScreen(
    onEdit: (categoryId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityCategoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ActivityCategoryContent(
        state = state,
        onEdit = onEdit,
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
    /** Zero opens an empty form: there is no category yet to name. */
    onEdit: (categoryId: Long) -> Unit,
    onArchive: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by remember { mutableStateOf<ActivityCategoryUsage?>(null) }

    if (state.isLoading) {
        LoadingState(modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenActions {
            Button(onClick = { onEdit(NEW) }) { Text(stringResource(Res.string.common_new)) }
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
                    onEdit = { onEdit(usage.category.id) },
                    onArchive = { onArchive(usage.category.id) },
                    onDelete = { confirmingDelete = usage },
                )
            }
        }
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

// --- Previews ---------------------------------------------------------------

/**
 * The populated list.
 *
 * The delete confirmation is behind local state and a swipe, so a static render cannot
 * reach it — run the preview and drag a row left to see the dialog.
 */
@PreviewLightDark
@Composable
private fun ActivityCategoryContentPreview() {
    PreviewSurface {
        ActivityCategoryContent(
            state = ActivityCategoryUiState(
                categories = previewCategoryUsage,
                isLoading = false,
            ),
            onEdit = {},
            onArchive = {},
            onDelete = {},
            onConsumeMessage = {},
        )
    }
}

@Preview(name = "Empty")
@Composable
private fun ActivityCategoryContentEmptyPreview() {
    PreviewSurface {
        ActivityCategoryContent(
            state = ActivityCategoryUiState(categories = emptyList(), isLoading = false),
            onEdit = {},
            onArchive = {},
            onDelete = {},
            onConsumeMessage = {},
        )
    }
}

@Preview(name = "Loading", heightDp = 200)
@Composable
private fun ActivityCategoryContentLoadingPreview() {
    PreviewSurface {
        ActivityCategoryContent(
            state = ActivityCategoryUiState(),
            onEdit = {},
            onArchive = {},
            onDelete = {},
            onConsumeMessage = {},
        )
    }
}

/**
 * A row with an icon and a count, and one with neither.
 *
 * Zero is the case to look at: it reads "No activities" rather than "0 activities",
 * and it is the only count that makes deleting the category a no-op for everything
 * else.
 */
@PreviewLightDark
@Composable
private fun CategoryRowPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryRow(
                usage = previewCategoryUsage[0].let {
                    it.copy(category = it.category.copy(icon = "🏋"))
                },
                onEdit = {},
                onArchive = {},
                onDelete = {},
            )
            CategoryRow(usage = previewCategoryUsage[1], onEdit = {}, onArchive = {}, onDelete = {})
            CategoryRow(usage = previewCategoryUsage[2], onEdit = {}, onArchive = {}, onDelete = {})
        }
    }
}

/**
 * Every message this screen can raise, as text.
 *
 * A snackbar shows one at a time and only after an action, so this is the only place
 * the whole set can be read together — which is what catches a plural that does not
 * agree, or a count-carrying message that reads worse than the plain one it replaces.
 */
@Preview(name = "en")
@Preview(name = "es", locale = "es")
@Composable
private fun CategoryMessagesPreview() {
    val messages = listOf(
        CategoryMessage.Saved,
        CategoryMessage.NameBlank,
        CategoryMessage.NameTaken,
        CategoryMessage.Deleted(uncategorisedCount = 0),
        CategoryMessage.Deleted(uncategorisedCount = 1),
        CategoryMessage.Deleted(uncategorisedCount = 6),
        CategoryMessage.Archived(uncategorisedCount = 0),
        CategoryMessage.Archived(uncategorisedCount = 3),
    )
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader(text = "CategoryMessage")
            messages.forEach { message ->
                Text(text = messageText(message), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
