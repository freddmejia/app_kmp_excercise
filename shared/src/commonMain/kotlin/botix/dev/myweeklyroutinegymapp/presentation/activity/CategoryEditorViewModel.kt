package botix.dev.myweeklyroutinegymapp.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.CreateCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveCategoriesUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.SaveCategoryResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.UpdateCategoryUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a category was not saved. Reported under the field that caused it. */
sealed interface CategoryEditorError {
    /** A name of only whitespace is not a name. */
    data object NameBlank : CategoryEditorError

    /** Another category already holds this name. */
    data object NameTaken : CategoryEditorError
}

data class CategoryEditorUiState(
    val name: String = "",
    val icon: String = "",
    val isLoading: Boolean = true,
    val error: CategoryEditorError? = null,
) {
    /** Inert until there is something to save; a *wrong* name is refused and explained. */
    val canSave: Boolean get() = name.isNotBlank() && !isLoading
}

/**
 * Creating a category, or editing one.
 *
 * [categoryId] is [NEW] for a category that does not exist yet. One screen for both,
 * because the two forms are the same two fields and the same rules — the only
 * difference is which use case the Save button reaches, and that is one branch here
 * rather than a second screen to keep in step.
 */
class CategoryEditorViewModel(
    private val categoryId: Long,
    private val observeCategories: ObserveCategoriesUseCase,
    private val createCategory: CreateCategoryUseCase,
    private val updateCategory: UpdateCategoryUseCase,
) : ViewModel() {

    /**
     * Fired once per save, and never remembered — see `BodyWeightEntryViewModel` for
     * what a remembered one does to a screen that is opened twice.
     */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val _state = MutableStateFlow(CategoryEditorUiState())
    val state: StateFlow<CategoryEditorUiState> = _state.asStateFlow()

    /**
     * Called every time the screen appears, so a second visit starts from what is
     * stored rather than from the last visit's half-typed name.
     */
    fun onOpened() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // Archived ones included: this screen is reached by tapping a row, and a
            // row that can be shown has to be editable.
            val existing = if (categoryId == NEW) {
                null
            } else {
                observeCategories(includeArchived = true).first().firstOrNull { it.id == categoryId }
            }
            _state.update {
                it.copy(
                    name = existing?.name.orEmpty(),
                    icon = existing?.icon.orEmpty(),
                    isLoading = false,
                )
            }
        }
    }

    /** Clears the error as soon as the value changes: it described the old text. */
    fun setName(text: String) = _state.update { it.copy(name = text, error = null) }

    fun setIcon(text: String) = _state.update { it.copy(icon = text, error = null) }

    fun save() {
        viewModelScope.launch {
            val current = _state.value
            // Blank and duplicate names are the use case's rules, not this screen's.
            // Checking them here as well would let the two disagree about which names
            // are allowed, and the domain is the half that cannot be bypassed.
            val icon = current.icon.trim().takeIf { it.isNotBlank() }
            val result = if (categoryId == NEW) {
                createCategory(current.name, icon)
            } else {
                updateCategory(categoryId, current.name, icon)
            }
            when (result) {
                is SaveCategoryResult.Saved -> _saved.tryEmit(Unit)
                SaveCategoryResult.BlankName ->
                    _state.update { it.copy(error = CategoryEditorError.NameBlank) }
                SaveCategoryResult.NameTaken ->
                    _state.update { it.copy(error = CategoryEditorError.NameTaken) }
            }
        }
    }

    companion object {
        /**
         * The id a route carries when it is opening a *new* category.
         *
         * Zero rather than null because the back stack is saved as a flat list of
         * `Long`s, and ids come from `INTEGER PRIMARY KEY AUTOINCREMENT`, which starts
         * at one — so zero is a value no row can ever have.
         */
        const val NEW: Long = 0L
    }
}
