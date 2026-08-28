package botix.dev.myweeklyroutinegymapp.presentation.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.activity_category_label
import myweeklyroutinegymapp.shared.generated.resources.activity_measure_question
import myweeklyroutinegymapp.shared.generated.resources.activity_msg_name_blank
import myweeklyroutinegymapp.shared.generated.resources.activity_msg_name_taken
import myweeklyroutinegymapp.shared.generated.resources.activity_msg_type_locked
import myweeklyroutinegymapp.shared.generated.resources.activity_muscles_hint
import myweeklyroutinegymapp.shared.generated.resources.activity_muscles_optional
import myweeklyroutinegymapp.shared.generated.resources.activity_no_muscles_hint
import myweeklyroutinegymapp.shared.generated.resources.common_all
import myweeklyroutinegymapp.shared.generated.resources.common_icon_optional
import myweeklyroutinegymapp.shared.generated.resources.common_name
import myweeklyroutinegymapp.shared.generated.resources.common_none
import myweeklyroutinegymapp.shared.generated.resources.common_save
import myweeklyroutinegymapp.shared.generated.resources.measure_distance
import myweeklyroutinegymapp.shared.generated.resources.measure_simple
import myweeklyroutinegymapp.shared.generated.resources.measure_strength
import myweeklyroutinegymapp.shared.generated.resources.plural_sessions
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Creating or editing one activity, on its own screen.
 *
 * It replaces three dialogs. The creation form had already outgrown one — it was
 * capped at 520.dp and scrolling inside a box that was itself floating over the list —
 * and the same four fields were then edited *elsewhere*, through a category picker and
 * a muscle checklist that each showed one field with the other three out of sight.
 * They are one form because they describe one thing.
 */
@Composable
fun ActivityEditorScreen(
    activityId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityEditorViewModel = koinViewModel(
        key = "activity-editor-$activityId",
        parameters = { parametersOf(activityId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onOpened()
        viewModel.saved.collect { onDone() }
    }

    ActivityEditorContent(
        state = state,
        onNameChange = viewModel::setName,
        onIconChange = viewModel::setIcon,
        onTypeChange = viewModel::setType,
        onCategoryChange = viewModel::setCategory,
        onToggleMuscle = viewModel::toggleMuscle,
        onToggleGroup = viewModel::toggleGroup,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
fun ActivityEditorContent(
    state: ActivityEditorUiState,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onTypeChange: (MeasurementType) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onToggleMuscle: (Long) -> Unit,
    onToggleGroup: (Set<Long>, Boolean) -> Unit,
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
            // Under the field rather than in a snackbar over the list: every one of
            // these is a reason this form was not accepted, and it has to stay
            // readable while what caused it is corrected.
            supportingText = state.error?.let { error -> { ErrorText(error) } },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.icon,
            onValueChange = onIconChange,
            label = { Text(stringResource(Res.string.common_icon_optional)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Hidden until a category exists: a row offering only "None" is noise, and it
        // is the state every user starts in.
        if (state.categories.isNotEmpty()) {
            // Each labelled block is its own column. The 16.dp between sections is
            // what separates them, and a label sitting that far from the control it
            // names reads as a heading for everything below rather than for one row.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.activity_category_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.categoryId == null,
                            onClick = { onCategoryChange(null) },
                            label = { Text(stringResource(Res.string.common_none)) },
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = state.categoryId == category.id,
                            onClick = { onCategoryChange(category.id) },
                            label = {
                                Text(
                                    listOfNotNull(category.icon, category.name)
                                        .joinToString(" "),
                                )
                            },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.activity_measure_question),
                style = MaterialTheme.typography.labelLarge,
            )
            // Offered on an existing activity too, but the domain has the last word:
            // the type is fixed once sessions exist under it, and the refusal comes
            // back as the error above rather than as a control that is quietly inert.
            //
            // Unlike the categories above, this list is fixed — each value is a
            // logging form and a set of metrics that exist in code, not user data.
            MeasurementType.entries.forEach { option ->
                FilterChip(
                    selected = option == state.type,
                    onClick = { onTypeChange(option) },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }

        if (state.showsMuscles) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.activity_muscles_optional),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(Res.string.activity_muscles_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MuscleChecklist(
                    sections = state.muscleSections,
                    selected = state.muscleIds,
                    onToggle = onToggleMuscle,
                    onToggleGroup = onToggleGroup,
                )
            }
        }

        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.common_save))
        }
    }
}

@Composable
private fun ErrorText(error: ActivityEditorError) {
    Text(
        when (error) {
            ActivityEditorError.NameBlank -> stringResource(Res.string.activity_msg_name_blank)
            ActivityEditorError.NameTaken -> stringResource(Res.string.activity_msg_name_taken)
            is ActivityEditorError.TypeLocked -> stringResource(
                Res.string.activity_msg_type_locked,
                pluralStringResource(
                    Res.plurals.plural_sessions,
                    error.sessionCount.toInt(),
                    error.sessionCount.toString(),
                ),
            )
        },
    )
}

/**
 * The muscle picker, grouped exactly like the log screen's own and fed from the same
 * sections, so what you tick here reads the same way as the screen where it takes
 * effect.
 */
@Composable
private fun MuscleChecklist(
    sections: List<MuscleSection>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onToggleGroup: (Set<Long>, Boolean) -> Unit,
) {
    if (sections.isEmpty()) {
        Text(
            stringResource(Res.string.activity_no_muscles_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sections.forEach { section ->
            val ids = section.muscles.map { it.muscleId }.toSet()
            val allSelected = ids.isNotEmpty() && selected.containsAll(ids)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    section.groupName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                // Ticking a group is a shortcut for ticking its leaves, not a link to
                // the group itself: sessions reference leaves, so the stored set must
                // too (TRAINING.md §3).
                TextButton(onClick = { onToggleGroup(ids, !allSelected) }) {
                    Text(
                        stringResource(
                            if (allSelected) Res.string.common_none else Res.string.common_all,
                        ),
                    )
                }
            }
            section.muscles.forEach { muscle ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(muscle.muscleId) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = muscle.muscleId in selected,
                        onCheckedChange = { onToggle(muscle.muscleId) },
                    )
                    Text(muscle.muscleName, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * The enum constant is an English identifier, so the chip reads from a resource rather
 * than from `name`.
 */
internal fun MeasurementType.labelRes(): StringResource = when (this) {
    MeasurementType.STRENGTH -> Res.string.measure_strength
    MeasurementType.DISTANCE_TIME -> Res.string.measure_distance
    MeasurementType.SIMPLE -> Res.string.measure_simple
}
