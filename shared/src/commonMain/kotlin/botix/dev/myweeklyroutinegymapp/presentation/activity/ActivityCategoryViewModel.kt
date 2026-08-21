package botix.dev.myweeklyroutinegymapp.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategoryUsage
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ArchiveCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.CreateCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.DeleteCategoryUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.ObserveCategoryUsageUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.SaveCategoryResult
import botix.dev.myweeklyroutinegymapp.domain.usecase.activity.UpdateCategoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One-shot feedback for the category screen.
 *
 * Carries counts rather than formatted strings, so the wording — and its
 * translation — stays in the UI layer.
 */
sealed interface CategoryMessage {
    data object Saved : CategoryMessage
    data object NameBlank : CategoryMessage
    data object NameTaken : CategoryMessage

    /**
     * Deleted, and [uncategorisedCount] activities moved to uncategorised.
     *
     * Reported rather than silently succeeding: the activities are still there, and
     * a user who expected the delete to take them with it needs to know it did not.
     */
    data class Deleted(val uncategorisedCount: Long) : CategoryMessage

    /** Archived, with the same detachment side effect as a delete. */
    data class Archived(val uncategorisedCount: Long) : CategoryMessage
}

data class ActivityCategoryUiState(
    val categories: List<ActivityCategoryUsage> = emptyList(),
    val isLoading: Boolean = true,
    val message: CategoryMessage? = null,
) {
    val isEmpty: Boolean get() = categories.isEmpty()
}

/**
 * Managing the category catalogue.
 *
 * Separate from [ActivityListViewModel] for the same reason the muscle catalogue is
 * separate from the log screen: this is a catalogue editor, and folding it into the
 * list screen would tie its lifetime to a screen that has different concerns.
 */
class ActivityCategoryViewModel(
    private val observeCategoryUsage: ObserveCategoryUsageUseCase,
    private val createCategory: CreateCategoryUseCase,
    private val updateCategory: UpdateCategoryUseCase,
    private val archiveCategory: ArchiveCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
) : ViewModel() {

    private val message = MutableStateFlow<CategoryMessage?>(null)

    val state: StateFlow<ActivityCategoryUiState> = combine(
        observeCategoryUsage(),
        message,
    ) { categories, currentMessage ->
        ActivityCategoryUiState(
            categories = categories,
            isLoading = false,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        // Keeps the query alive briefly across a config change, so a rotation does
        // not re-query and flash an empty list.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityCategoryUiState(),
    )

    /** Call after showing [ActivityCategoryUiState.message] so it is not shown twice. */
    fun consumeMessage() {
        message.value = null
    }

    fun create(name: String, icon: String? = null, color: String? = null) {
        viewModelScope.launch {
            message.value = createCategory(name, icon, color).toMessage()
        }
    }

    fun rename(id: Long, name: String, icon: String? = null, color: String? = null) {
        viewModelScope.launch {
            message.value = updateCategory(id, name, icon, color).toMessage()
        }
    }

    /**
     * Archiving detaches this category's activities, so the count is read from the
     * list already on screen — after the write it would be zero, and reporting zero
     * would hide the side effect entirely.
     */
    fun setArchived(id: Long, archived: Boolean) {
        val affected = state.value.categories.firstOrNull { it.category.id == id }?.activityCount ?: 0L
        viewModelScope.launch {
            archiveCategory(id, archived)
            if (archived) message.value = CategoryMessage.Archived(affected)
        }
    }

    /**
     * Deletes outright. There is no blocked branch to handle, unlike an activity:
     * nothing references a category except the label on its activities.
     */
    fun delete(id: Long) {
        viewModelScope.launch {
            message.value = CategoryMessage.Deleted(deleteCategory(id).uncategorisedCount)
        }
    }

    private fun SaveCategoryResult.toMessage(): CategoryMessage = when (this) {
        is SaveCategoryResult.Saved -> CategoryMessage.Saved
        SaveCategoryResult.BlankName -> CategoryMessage.NameBlank
        SaveCategoryResult.NameTaken -> CategoryMessage.NameTaken
    }
}
