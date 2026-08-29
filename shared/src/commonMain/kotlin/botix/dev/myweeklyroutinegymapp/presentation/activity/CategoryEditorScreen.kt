package botix.dev.myweeklyroutinegymapp.presentation.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import myweeklyroutinegymapp.shared.generated.resources.category_msg_name_blank
import myweeklyroutinegymapp.shared.generated.resources.category_msg_name_taken
import myweeklyroutinegymapp.shared.generated.resources.common_icon_optional
import myweeklyroutinegymapp.shared.generated.resources.common_name
import myweeklyroutinegymapp.shared.generated.resources.common_save
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Creating or editing one category, on its own screen.
 *
 * It was a dialog. A dialog is the right shape for a question with one answer, and
 * this is a form — two fields, rules that can refuse it, and an error that has to stay
 * readable while the value that caused it is corrected. It also had no title of its
 * own once dismissed, so there was nothing on screen saying whether you were adding or
 * editing except the text already in the fields.
 */
@Composable
fun CategoryEditorScreen(
    categoryId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryEditorViewModel = koinViewModel(
        key = "category-editor-$categoryId",
        parameters = { parametersOf(categoryId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reload first, then listen for the save — the order this app's editors all use.
    // See `BodyWeightEntryScreen` for what each half is guarding against.
    LaunchedEffect(Unit) {
        viewModel.onOpened()
        viewModel.saved.collect { onDone() }
    }

    CategoryEditorContent(
        state = state,
        onNameChange = viewModel::setName,
        onIconChange = viewModel::setIcon,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
fun CategoryEditorContent(
    state: CategoryEditorUiState,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
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
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(Res.string.common_name)) },
            singleLine = true,
            isError = state.error != null,
            supportingText = state.error?.let { error ->
                {
                    Text(
                        when (error) {
                            CategoryEditorError.NameBlank ->
                                stringResource(Res.string.category_msg_name_blank)

                            CategoryEditorError.NameTaken ->
                                stringResource(Res.string.category_msg_name_taken)
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.icon,
            onValueChange = onIconChange,
            label = { Text(stringResource(Res.string.common_icon_optional)) },
            singleLine = true,
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
 * The two things the app bar cannot tell apart from inside this screen: an empty form
 * (Save disabled, because a blank name is not a category) and one opened onto a
 * category that already exists.
 */
@PreviewLightDark
@Composable
private fun CategoryEditorContentPreview() {
    PreviewSurface {
        CategoryEditorContent(
            state = CategoryEditorUiState(name = "Mobility", icon = "🧘", isLoading = false),
            onNameChange = {},
            onIconChange = {},
            onSave = {},
        )
    }
}

@Preview(name = "New — nothing typed yet")
@Composable
private fun CategoryEditorContentNewPreview() {
    PreviewSurface {
        CategoryEditorContent(
            state = CategoryEditorUiState(isLoading = false),
            onNameChange = {},
            onIconChange = {},
            onSave = {},
        )
    }
}

/**
 * The refusals, which are the reason this stopped being a dialog: the message sits
 * under the field that caused it and stays there while the value is corrected.
 */
@Preview(name = "Name blank")
@Composable
private fun CategoryEditorContentNameBlankPreview() {
    PreviewSurface {
        CategoryEditorContent(
            state = CategoryEditorUiState(
                name = "   ",
                isLoading = false,
                error = CategoryEditorError.NameBlank,
            ),
            onNameChange = {},
            onIconChange = {},
            onSave = {},
        )
    }
}

@Preview(name = "Name taken")
@Composable
private fun CategoryEditorContentNameTakenPreview() {
    PreviewSurface {
        CategoryEditorContent(
            state = CategoryEditorUiState(
                name = "Cardio",
                isLoading = false,
                error = CategoryEditorError.NameTaken,
            ),
            onNameChange = {},
            onIconChange = {},
            onSave = {},
        )
    }
}

@Preview(heightDp = 200)
@Composable
private fun CategoryEditorContentLoadingPreview() {
    PreviewSurface {
        CategoryEditorContent(
            state = CategoryEditorUiState(),
            onNameChange = {},
            onIconChange = {},
            onSave = {},
        )
    }
}
