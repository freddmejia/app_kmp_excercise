package botix.dev.myweeklyroutinegymapp.presentation.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.preview.PreviewSurface
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_name
import myweeklyroutinegymapp.shared.generated.resources.common_save
import myweeklyroutinegymapp.shared.generated.resources.muscle_msg_name_blank
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One name, on its own screen: a muscle group or a leaf inside one, new or renamed.
 *
 * A single field is the strongest case a dialog has, and this is still the wrong place
 * for one — the app bar is what says whether you are adding a group, adding a leaf to
 * *this* group, or renaming something, and a dialog has no bar to say it in. The old
 * one had a title, an "Add" button and nothing else: renaming was simply unreachable,
 * even though the domain has always supported it.
 */
@Composable
fun MuscleEditorScreen(
    groupId: Long,
    muscleId: Long,
    isGroup: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MuscleEditorViewModel = koinViewModel(
        key = "muscle-editor-$isGroup-$groupId-$muscleId",
        parameters = { parametersOf(groupId, muscleId, isGroup) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onOpened()
        viewModel.saved.collect { onDone() }
    }

    MuscleEditorContent(
        state = state,
        onNameChange = viewModel::setName,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
fun MuscleEditorContent(
    state: MuscleEditorUiState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Which group a leaf is going into. The app bar says "New muscle"; it does not
        // say *where*, and every group's list looks the same once you are in the form.
        if (!state.isGroup && state.groupName.isNotBlank()) {
            Text(
                text = state.groupName,
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(Res.string.common_name)) },
            singleLine = true,
            isError = state.isNameBlank,
            supportingText = if (state.isNameBlank) {
                { Text(stringResource(Res.string.muscle_msg_name_blank)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.common_save))
        }
    }
}

// --- Previews ---------------------------------------------------------------

/**
 * The four cases one field stands in for.
 *
 * They are worth previewing separately because the *screen* is nearly identical in all
 * four and the difference is carried by the app bar, which is not in this composable.
 * The one thing that does change here is the group heading: a leaf shows where it will
 * land, a group has nowhere to land, and an empty group name shows nothing rather than
 * an empty line.
 */
@PreviewLightDark
@Composable
private fun MuscleEditorContentNewLeafPreview() {
    PreviewSurface {
        MuscleEditorContent(
            state = MuscleEditorUiState(groupName = "Chest", isGroup = false, isLoading = false),
            onNameChange = {},
            onSave = {},
        )
    }
}

@Preview(name = "Rename a leaf")
@Composable
private fun MuscleEditorContentRenameLeafPreview() {
    PreviewSurface {
        MuscleEditorContent(
            state = MuscleEditorUiState(
                name = "Upper chest",
                groupName = "Chest",
                isGroup = false,
                isLoading = false,
            ),
            onNameChange = {},
            onSave = {},
        )
    }
}

@Preview(name = "New group")
@Composable
private fun MuscleEditorContentNewGroupPreview() {
    PreviewSurface {
        MuscleEditorContent(
            state = MuscleEditorUiState(isGroup = true, isLoading = false),
            onNameChange = {},
            onSave = {},
        )
    }
}

@Preview(name = "Rename a group")
@Composable
private fun MuscleEditorContentRenameGroupPreview() {
    PreviewSurface {
        MuscleEditorContent(
            state = MuscleEditorUiState(name = "Back", isGroup = true, isLoading = false),
            onNameChange = {},
            onSave = {},
        )
    }
}

/** Blank is the only refusal here — there is no uniqueness rule on a muscle name. */
@Preview(name = "Name blank")
@Composable
private fun MuscleEditorContentBlankPreview() {
    PreviewSurface {
        MuscleEditorContent(
            state = MuscleEditorUiState(
                name = "",
                groupName = "Legs",
                isLoading = false,
                isNameBlank = true,
            ),
            onNameChange = {},
            onSave = {},
        )
    }
}

@Preview(heightDp = 200)
@Composable
private fun MuscleEditorContentLoadingPreview() {
    PreviewSurface {
        MuscleEditorContent(state = MuscleEditorUiState(), onNameChange = {}, onSave = {})
    }
}
