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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
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
