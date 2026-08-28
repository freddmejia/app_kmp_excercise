package botix.dev.myweeklyroutinegymapp.presentation.bodyweight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import botix.dev.myweeklyroutinegymapp.presentation.common.LoadingState
import botix.dev.myweeklyroutinegymapp.presentation.common.asFullDate
import botix.dev.myweeklyroutinegymapp.presentation.common.format1
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_save
import myweeklyroutinegymapp.shared.generated.resources.weight_already
import myweeklyroutinegymapp.shared.generated.resources.weight_future
import myweeklyroutinegymapp.shared.generated.resources.weight_input_label
import myweeklyroutinegymapp.shared.generated.resources.weight_msg_invalid
import myweeklyroutinegymapp.shared.generated.resources.weight_note_label
import myweeklyroutinegymapp.shared.generated.resources.weight_one_per_day
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The create-or-edit screen for one day's reading.
 *
 * It replaces the input row that used to sit at the top of the history list. That row
 * was a field, a button and a warning line competing with a chart and a list for the
 * same screen — always present, whether or not anyone was logging anything, and never
 * big enough to be comfortable. Logging is a deliberate act with its own screen now,
 * and the list is only a list.
 *
 * ### The key is the day
 * [epochDay] is the whole argument, because a day holds at most one reading (see
 * [BodyWeightEntryViewModel]). The ViewModel is keyed by it as well: without that,
 * opening Tuesday and then Wednesday would find the first ViewModel still in the
 * store and show Tuesday's numbers under Wednesday's date.
 */
@Composable
fun BodyWeightEntryScreen(
    epochDay: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BodyWeightEntryViewModel = koinViewModel(
        key = "body-weight-entry-$epochDay",
        parameters = { parametersOf(epochDay) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // One effect, in this order, on purpose. The reload comes first because a
    // ViewModel keyed by day outlives the visit that created it, and the second visit
    // must start from the database rather than from what the first one left behind.
    // Then the save signal, which is a flow with no replay: the screen leaves when a
    // save happens *while it is open*, and can no longer be closed on arrival by one
    // that happened last time.
    //
    // Saving is the end of this screen. There is no confirmation to read here — the
    // list behind it already shows the new value by the time this closes, and a
    // snackbar raised from a composition being torn down is cancelled with it.
    LaunchedEffect(Unit) {
        viewModel.onOpened()
        viewModel.saved.collect { onDone() }
    }

    BodyWeightEntryContent(
        state = state,
        onWeightChange = viewModel::setWeight,
        onNoteChange = viewModel::setNote,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

/**
 * Stateless on purpose: everything it draws arrives as [state], so it renders
 * identically in a preview, a screenshot test and the running app.
 */
@Composable
fun BodyWeightEntryContent(
    state: BodyWeightEntryUiState,
    onWeightChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingState(modifier)
        return
    }

    Column(
        // Scrollable rather than fixed: the form is short, but a large font scale and
        // an open keyboard together can leave the button below the fold, and a Save
        // button you cannot reach is the one failure this screen cannot afford.
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // The app bar says whether this is a new reading or a correction; it does
            // not say *which day*, and the day is the thing that makes this reading
            // different from every other one. So it leads the screen.
            Text(
                text = state.epochDay.asFullDate(),
                style = MaterialTheme.typography.headlineSmall,
            )
            // The same three-way line the old inline form carried, kept because it is
            // the only warning that arrives *before* the press: one reading per day
            // means saving is sometimes a replacement, and being told so afterwards
            // would be being told too late.
            Text(
                text = when {
                    state.isFuture -> stringResource(Res.string.weight_future)
                    state.existingKg != null ->
                        stringResource(Res.string.weight_already, state.existingKg.format1())

                    else -> stringResource(Res.string.weight_one_per_day)
                },
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    state.isFuture -> MaterialTheme.colorScheme.error
                    state.existingKg != null -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        OutlinedTextField(
            value = state.weightInput,
            onValueChange = onWeightChange,
            label = { Text(stringResource(Res.string.weight_input_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = state.error != null,
            // Under the field it belongs to, rather than in a snackbar over the list:
            // this is a reason the form was not accepted, and it has to stay readable
            // while the value that caused it is corrected.
            supportingText = state.error?.let { error ->
                {
                    Text(
                        text = when (error) {
                            BodyWeightEntryError.InvalidWeight ->
                                stringResource(Res.string.weight_msg_invalid)

                            BodyWeightEntryError.FutureDay ->
                                stringResource(Res.string.weight_future)
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // The note column has been in the schema and the row layout since the start,
        // with nothing anywhere to write one. This is that missing half.
        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChange,
            label = { Text(stringResource(Res.string.weight_note_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onSave,
            // Inert until there is something to save, and on a day that can hold a
            // reading at all. A wrong number is a different matter — that gets
            // pressed, refused, and explained under the field.
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.common_save))
        }
    }
}
