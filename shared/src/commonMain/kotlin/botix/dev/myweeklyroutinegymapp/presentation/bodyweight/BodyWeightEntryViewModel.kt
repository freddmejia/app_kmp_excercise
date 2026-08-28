package botix.dev.myweeklyroutinegymapp.presentation.bodyweight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.GetBodyWeightForDayUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.presentation.common.formatMeasure
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the editor can refuse to save, and why.
 *
 * Separate from [BodyWeightMessage], which is the *list's* vocabulary: these are not
 * announcements about something that happened, they are reasons a form is not
 * accepted, and they belong beside the field that caused them rather than in a
 * snackbar that slides over it.
 */
sealed interface BodyWeightEntryError {
    /** Zero, negative or unparseable. */
    data object InvalidWeight : BodyWeightEntryError

    /** The day has not happened yet. */
    data object FutureDay : BodyWeightEntryError
}

data class BodyWeightEntryUiState(
    /** The day this reading belongs to. Fixed for the life of the screen — see below. */
    val epochDay: Long = 0,
    val weightInput: String = "",
    val note: String = "",
    /** What the day already holds, if anything: this is an edit rather than a create. */
    val existingKg: Double? = null,
    val isFuture: Boolean = false,
    val isLoading: Boolean = true,
    val error: BodyWeightEntryError? = null,
) {
    /**
     * Whether Save can be pressed at all.
     *
     * Blank is not an error worth showing — the user has not finished typing — so the
     * button is simply inert until there is something to save. A *wrong* value is
     * different: that gets pressed, refused, and explained.
     */
    val canSave: Boolean get() = weightInput.isNotBlank() && !isFuture && !isLoading
}

/**
 * The create-or-edit form for one day's body weight.
 *
 * ### Why the day is the argument, and not an entry id
 * `body_weight.date_epoch_day` is UNIQUE: a day holds at most one reading, and saving
 * a day that already has one replaces it. So "edit reading 12" and "log Tuesday" are
 * the same operation on the same row, and taking the day as the key means the screen
 * cannot be opened onto a row that has meanwhile been deleted — it would simply be a
 * create. It also survives the id churn the upsert causes: `INSERT OR REPLACE` deletes
 * and re-inserts, so the id of a reading changes every time it is corrected.
 *
 * The day is therefore fixed once, by whoever opened the screen. Moving a reading to
 * another day is a different operation (it would have to delete the old row) and the
 * strip on the list is where a day is chosen.
 *
 * ### One reading per day is a UI fact here, not just a database one
 * [BodyWeightEntryUiState.existingKg] is loaded before the form is shown, so the
 * screen can say it is about to replace something *before* the user commits, rather
 * than reporting it afterwards.
 */
class BodyWeightEntryViewModel(
    private val epochDay: Long,
    private val getForDay: GetBodyWeightForDayUseCase,
    private val logBodyWeight: LogBodyWeightUseCase,
    clock: Clock,
) : ViewModel() {

    /**
     * Fired once per save, and never remembered.
     *
     * This was a `Boolean` in the state, and that was a bug with teeth. A ViewModel
     * lives in the store of whatever owns it — here the activity — so the instance
     * for a given day is still there the *next* time that day is opened. A flag
     * saying "this was saved" therefore came back set, the screen read it as its own
     * arrival, and closed itself: tapping a reading that had ever been saved did
     * nothing at all, with no error to see.
     *
     * A `SharedFlow` with no replay cannot do that. An emission with nobody
     * collecting is dropped rather than kept, so what reaches the screen is only what
     * happened while it was on screen.
     */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val _state = MutableStateFlow(
        BodyWeightEntryUiState(
            epochDay = epochDay,
            // Resolved once, like every other `today` in this app: a clock read per
            // recomposition could change the answer under a form already on screen.
            isFuture = epochDay > clock.today(),
        ),
    )
    val state: StateFlow<BodyWeightEntryUiState> = _state.asStateFlow()

    /**
     * Loads the day, and is called every time the screen appears rather than once per
     * instance — for the same reason [saved] is not a flag. A retained ViewModel would
     * otherwise hand the next visit the last one's half-typed number instead of what
     * is actually stored.
     */
    fun onOpened() {
        // Synchronous, before the read: the form must not show the previous visit's
        // text for the frame or two it takes to answer.
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val existing = getForDay(epochDay)
            _state.update {
                it.copy(
                    // formatMeasure, not format1: the user typed "89", and prefilling
                    // "89.0" claims a precision they never gave — and would hand it
                    // back to them to delete before they can type.
                    weightInput = existing?.weightKg?.formatMeasure() ?: "",
                    note = existing?.note.orEmpty(),
                    existingKg = existing?.weightKg,
                    isLoading = false,
                )
            }
        }
    }

    /** Clears the error as soon as the value changes: it described the old text. */
    fun setWeight(text: String) = _state.update { it.copy(weightInput = text, error = null) }

    fun setNote(text: String) = _state.update { it.copy(note = text) }

    fun save() {
        viewModelScope.launch {
            val current = _state.value
            // Parsed on submit, not per keystroke: "20." has to be typeable on the
            // way to "20.5". The comma is folded to a point because it is the decimal
            // separator in three of the four locales this app ships.
            val kg = current.weightInput.trim().replace(',', '.').toDoubleOrNull()
            if (kg == null) {
                _state.update { it.copy(error = BodyWeightEntryError.InvalidWeight) }
                return@launch
            }
            // The domain still decides. The form blocks what it can see (a future day
            // disables the button, blank text keeps it inert), and the use case
            // refuses anything that gets past that.
            when (logBodyWeight(kg, epochDay, current.note)) {
                LogBodyWeightResult.Logged -> {
                    _state.update { it.copy(error = null) }
                    _saved.tryEmit(Unit)
                }

                LogBodyWeightResult.InvalidWeight ->
                    _state.update { it.copy(error = BodyWeightEntryError.InvalidWeight) }

                LogBodyWeightResult.FutureDay ->
                    _state.update { it.copy(error = BodyWeightEntryError.FutureDay) }
            }
        }
    }
}
