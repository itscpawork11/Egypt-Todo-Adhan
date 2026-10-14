package com.offline.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.offline.app.data.local.entities.Priority
import com.offline.app.data.local.entities.RecurringInterval
import com.offline.app.data.local.entities.TaskCategory
import com.offline.app.data.local.entities.TaskEntity
import com.offline.app.data.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/viewmodels/TodoViewModel.kt
 *
 * Presentation model for the tasks (todo) screen.
 *
 * Responsibilities:
 *  - Expose the task list as a single [TodoUiState] [StateFlow].
 *  - Apply the three UI filters inside the flow pipeline: free-text search,
 *    category filter and sort order (priority is the default sort).
 *  - Wrap every [TaskRepository] write in an error-safe coroutine so the UI can
 *    surface a message instead of crashing.
 *
 * The ViewModel is 100% offline: it only talks to [TaskRepository], which reads
 * Room and drives the exact-alarm scheduler. No network and no I/O happen here.
 */

/** Available sort orders for the task list. */
enum class TodoSortOrder {
    /** Unfinished first, then HIGH > MEDIUM > LOW, then nearest due date. Default. */
    PRIORITY,

    /** Unfinished first, then nearest due date (tasks without a due date last). */
    DUE_DATE,

    /** Unfinished first, then most recently created. */
    CREATED_AT,

    /** Unfinished first, then title (case-insensitive). */
    TITLE
}

/**
 * Immutable snapshot rendered by the tasks screen.
 *
 * @param isLoading            True until the first database emission arrives.
 * @param tasks                The filtered, sorted task list.
 * @param searchQuery          The active free-text filter (title/notes substring).
 * @param selectedCategory     The active category filter, or null for "all".
 * @param sortOrder            The active sort order.
 * @param showCompleted        Whether completed tasks are included.
 * @param activeTaskCount      Number of unfinished, non-deleted tasks overall.
 * @param totalTaskCount       Number of non-deleted tasks overall.
 */
data class TodoUiState(
    val isLoading: Boolean = true,
    val tasks: List<TaskEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: TaskCategory? = null,
    val sortOrder: TodoSortOrder = TodoSortOrder.PRIORITY,
    val showCompleted: Boolean = false,
    val activeTaskCount: Int = 0,
    val totalTaskCount: Int = 0
)

/**
 * ViewModel backing the tasks (todo) screen.
 *
 * @param taskRepository the data source for tasks and their reminder alarms.
 */
class TodoViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    // =====================================================================
    // Filter inputs
    // =====================================================================

    private val searchQuery: MutableStateFlow<String> = MutableStateFlow("")

    private val selectedCategory: MutableStateFlow<TaskCategory?> = MutableStateFlow(null)

    private val sortOrder: MutableStateFlow<TodoSortOrder> = MutableStateFlow(TodoSortOrder.PRIORITY)

    private val showCompleted: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // =====================================================================
    // One-shot error surface
    // =====================================================================

    private val _errorMessage: MutableStateFlow<String?> = MutableStateFlow(null)

    /** Latest error text, or null when there is nothing to show. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // =====================================================================
    // UI state
    // =====================================================================

    /**
     * The rendered list state. Every filter change re-runs the pipeline without
     * touching the database, because the base list is already a live Room flow.
     */
    val uiState: StateFlow<TodoUiState> = combine(
        taskRepository.observeAllTasks(),
        searchQuery,
        selectedCategory,
        sortOrder,
        showCompleted
    ) { tasks, query, category, sort, includeCompleted ->
        val filtered = tasks.asSequence()
            .filter { includeCompleted || !it.isCompleted }
            .filter { category == null || it.category == category }
            .filter { matchesQuery(it, query) }
            .toList()

        val sorted = sortTasks(filtered, sort)

        TodoUiState(
            isLoading = false,
            tasks = sorted,
            searchQuery = query,
            selectedCategory = category,
            sortOrder = sort,
            showCompleted = includeCompleted,
            activeTaskCount = tasks.count { !it.isCompleted },
            totalTaskCount = tasks.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MILLIS),
        initialValue = TodoUiState()
    )

    // =====================================================================
    // Filter operations
    // =====================================================================

    /** Sets the free-text search filter (matches title and notes). */
    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    /** Sets the category filter; null means "all categories". */
    fun setSelectedCategory(category: TaskCategory?) {
        selectedCategory.value = category
    }

    /** Sets the sort order. */
    fun setSortOrder(order: TodoSortOrder) {
        sortOrder.value = order
    }

    /** Includes or excludes completed tasks. */
    fun setShowCompleted(show: Boolean) {
        showCompleted.value = show
    }

    /** Reverses [setShowCompleted]. */
    fun toggleShowCompleted() {
        showCompleted.value = !showCompleted.value
    }

    /** Clears every filter back to its default. */
    fun clearFilters() {
        searchQuery.value = ""
        selectedCategory.value = null
        sortOrder.value = TodoSortOrder.PRIORITY
        showCompleted.value = false
    }

    // =====================================================================
    // Task operations
    // =====================================================================

    /**
     * Inserts a new task. A blank title is rejected with an error message.
     *
     * @param title        required, non-blank short title.
     * @param notes        optional free-form notes.
     * @param priority     task priority (defaults to MEDIUM).
     * @param category     task category (defaults to PERSONAL).
     * @param dueDate      optional due date as epoch millis.
     * @param isRecurring  recurrence rule (defaults to NONE).
     * @param reminderTime optional reminder time as epoch millis; a future value
     *                     is scheduled as an exact alarm by the repository.
     */
    fun addTask(
        title: String,
        notes: String? = null,
        priority: Priority = Priority.MEDIUM,
        category: TaskCategory = TaskCategory.PERSONAL,
        dueDate: Long? = null,
        isRecurring: RecurringInterval = RecurringInterval.NONE,
        reminderTime: Long? = null
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            _errorMessage.value = "عنوان المهمة مطلوب."
            return
        }

        launchGuarded {
            taskRepository.insertTask(
                TaskEntity(
                    title = trimmedTitle,
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    priority = priority,
                    category = category,
                    dueDate = dueDate,
                    isRecurring = isRecurring,
                    reminderTime = reminderTime,
                    isCompleted = false,
                    isDeleted = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Inserts an already-built entity (used when editing is not needed). */
    fun addTask(task: TaskEntity) {
        val trimmedTitle = task.title.trim()
        if (trimmedTitle.isEmpty()) {
            _errorMessage.value = "عنوان المهمة مطلوب."
            return
        }
        launchGuarded {
            taskRepository.insertTask(task.copy(title = trimmedTitle))
        }
    }

    /** Replaces an existing task by primary key and re-syncs its reminder. */
    fun updateTask(task: TaskEntity) {
        launchGuarded {
            taskRepository.updateTask(task)
        }
    }

    /** Flips the completion flag of [task], spawning the next occurrence if it recurs. */
    fun toggleComplete(task: TaskEntity) {
        launchGuarded {
            taskRepository.setTaskCompleted(task.id, !task.isCompleted)
        }
    }

    /** Sets the completion flag of a task to an explicit value. */
    fun setCompleted(taskId: Long, completed: Boolean) {
        launchGuarded {
            taskRepository.setTaskCompleted(taskId, completed)
        }
    }

    /** Soft-deletes [task]; it stays recoverable until purged. */
    fun deleteTask(task: TaskEntity) {
        launchGuarded {
            taskRepository.softDeleteTask(task.id)
        }
    }

    /** Soft-deletes a task by its id. */
    fun deleteTask(taskId: Long) {
        launchGuarded {
            taskRepository.softDeleteTask(taskId)
        }
    }

    /** Restores a previously soft-deleted task. */
    fun restoreTask(taskId: Long) {
        launchGuarded {
            taskRepository.restoreTask(taskId)
        }
    }

    /** Permanently removes every soft-deleted task. */
    fun purgeDeletedTasks() {
        launchGuarded {
            taskRepository.purgeSoftDeletedTasks()
        }
    }

    /**
     * Postpones [task] to fire [minutes] from now, re-arming its exact alarm.
     * A non-positive [minutes] is rejected with an error message.
     */
    fun snoozeReminder(task: TaskEntity, minutes: Int) {
        if (minutes <= 0) {
            _errorMessage.value = "مدة التأجيل يجب أن تكون أكبر من صفر."
            return
        }
        val newReminder = System.currentTimeMillis() + minutes.toLong() * MILLIS_PER_MINUTE
        snoozeReminderUntil(task, newReminder)
    }

    /** Postpones [task] to an explicit absolute reminder time (epoch millis). */
    fun snoozeReminderUntil(task: TaskEntity, triggerMillis: Long) {
        if (triggerMillis <= System.currentTimeMillis()) {
            _errorMessage.value = "وقت التأجيل يجب أن يكون في المستقبل."
            return
        }
        launchGuarded {
            taskRepository.updateTask(task.copy(reminderTime = triggerMillis))
        }
    }

    /** Dismisses the current error message. */
    fun clearError() {
        _errorMessage.value = null
    }

    // =====================================================================
    // Internal helpers
    // =====================================================================

    /**
     * Runs [block] in [viewModelScope], translating any failure into
     * [errorMessage]. Cancellation is always rethrown so structured concurrency
     * is preserved.
     */
    private fun launchGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _errorMessage.value = throwable.message ?: "حدث خطأ غير متوقع."
            }
        }
    }

    private fun matchesQuery(task: TaskEntity, query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim()
        val inTitle = task.title.contains(needle, ignoreCase = true)
        val inNotes = task.notes?.contains(needle, ignoreCase = true) == true
        return inTitle || inNotes
    }

    private fun sortTasks(tasks: List<TaskEntity>, order: TodoSortOrder): List<TaskEntity> {
        return when (order) {
            TodoSortOrder.PRIORITY -> tasks.sortedWith(PRIORITY_COMPARATOR)
            TodoSortOrder.DUE_DATE -> tasks.sortedWith(DUE_DATE_COMPARATOR)
            TodoSortOrder.CREATED_AT -> tasks.sortedWith(CREATED_AT_COMPARATOR)
            TodoSortOrder.TITLE -> tasks.sortedWith(TITLE_COMPARATOR)
        }
    }

    companion object {

        private const val SUBSCRIBE_TIMEOUT_MILLIS: Long = 5_000L

        private const val MILLIS_PER_MINUTE: Long = 60_000L

        /** Offsets offered by the snooze action, in minutes. */
        val SNOOZE_OPTIONS_MINUTES: List<Int> = listOf(5, 10, 15, 30, 60)

        private val PRIORITY_COMPARATOR: Comparator<TaskEntity> =
            compareBy<TaskEntity> { it.isCompleted }
                .thenByDescending { it.priority.weight }
                .thenBy { it.dueDate ?: Long.MAX_VALUE }
                .thenByDescending { it.createdAt }

        private val DUE_DATE_COMPARATOR: Comparator<TaskEntity> =
            compareBy<TaskEntity> { it.isCompleted }
                .thenBy { it.dueDate ?: Long.MAX_VALUE }
                .thenByDescending { it.createdAt }

        private val CREATED_AT_COMPARATOR: Comparator<TaskEntity> =
            compareBy<TaskEntity> { it.isCompleted }
                .thenByDescending { it.createdAt }

        private val TITLE_COMPARATOR: Comparator<TaskEntity> =
            compareBy<TaskEntity> { it.isCompleted }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }

        /**
         * Builds a [TodoViewModel] factory bound to [taskRepository], for use with
         * `viewModel(factory = TodoViewModel.factory(repository))`.
         */
        fun factory(taskRepository: TaskRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TodoViewModel(taskRepository) }
        }
    }
}
