@file:OptIn(ExperimentalMaterial3Api::class)

package com.offline.app.ui.screens.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offline.app.data.local.entities.TaskCategory
import com.offline.app.data.local.entities.TaskEntity
import com.offline.app.ui.viewmodels.TodoSortOrder
import com.offline.app.ui.viewmodels.TodoUiState
import com.offline.app.ui.viewmodels.TodoViewModel

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/screens/todo/TodoScreen.kt
 *
 * Full task-management dashboard.
 *
 * Layout (top to bottom):
 *  - a Material 3 top app bar with the screen title and a sort menu,
 *  - a search field with an inline clear button,
 *  - a horizontally scrolling filter strip: "all" + every category + a
 *    "completed" toggle chip,
 *  - a compact counter of active vs. total tasks,
 *  - the animated task list, where each row supports swipe-to-delete in either
 *    direction and exposes an overflow menu for edit/delete,
 *  - a floating action button that opens the create sheet.
 *
 * The screen is a thin, stateless projection of [TodoViewModel]: it only reads
 * [TodoUiState] and forwards user intents. No business logic and no I/O live
 * here. The whole subtree is forced into right-to-left layout because the app is
 * Arabic-first.
 *
 * @param viewModel the tasks ViewModel, created by the caller with
 *                  `viewModel(factory = TodoViewModel.factory(repository))`.
 * @param modifier  layout modifier applied to the root.
 */
@Composable
fun TodoScreen(
    viewModel: TodoViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var isSheetVisible by remember { mutableStateOf(false) }
    var taskBeingEdited by remember { mutableStateOf<TaskEntity?>(null) }

    LaunchedEffect(errorMessage) {
        val message = errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TodoTopBar(
                    sortOrder = uiState.sortOrder,
                    onSortOrderSelected = viewModel::setSortOrder
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        taskBeingEdited = null
                        isSheetVisible = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "إضافة مهمة"
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                SearchAndFilters(
                    uiState = uiState,
                    onQueryChange = viewModel::setSearchQuery,
                    onCategoryChange = viewModel::setSelectedCategory,
                    onToggleShowCompleted = viewModel::toggleShowCompleted
                )

                TaskCounter(
                    activeTaskCount = uiState.activeTaskCount,
                    totalTaskCount = uiState.totalTaskCount,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                TaskList(
                    uiState = uiState,
                    onToggleComplete = viewModel::toggleComplete,
                    onEdit = { task ->
                        taskBeingEdited = task
                        isSheetVisible = true
                    },
                    onDelete = viewModel::deleteTask,
                    onSnooze = viewModel::snoozeReminder,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (isSheetVisible) {
            AddEditTaskSheet(
                initialTask = taskBeingEdited,
                onDismiss = {
                    isSheetVisible = false
                    taskBeingEdited = null
                },
                onSave = { task ->
                    if (task.id == 0L) {
                        viewModel.addTask(task)
                    } else {
                        viewModel.updateTask(task)
                    }
                    isSheetVisible = false
                    taskBeingEdited = null
                }
            )
        }
    }
}

// =====================================================================
// Top app bar
// =====================================================================

@Composable
private fun TodoTopBar(
    sortOrder: TodoSortOrder,
    onSortOrderSelected: (TodoSortOrder) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = "المهام",
                style = MaterialTheme.typography.titleLarge
            )
        },
        actions = {
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "ترتيب المهام"
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    TodoSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(text = order.arabicLabel()) },
                            trailingIcon = {
                                if (order == sortOrder) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = {
                                sortMenuExpanded = false
                                onSortOrderSelected(order)
                            }
                        )
                    }
                }
            }
        }
    )
}

// =====================================================================
// Search + filters
// =====================================================================

@Composable
private fun SearchAndFilters(
    uiState: TodoUiState,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (TaskCategory?) -> Unit,
    onToggleShowCompleted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = "ابحث في المهام") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "مسح البحث"
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item(key = "all") {
                FilterChip(
                    selected = uiState.selectedCategory == null,
                    onClick = { onCategoryChange(null) },
                    label = { Text(text = "الكل") }
                )
            }
            items(items = TaskCategory.entries, key = { it.name }) { category ->
                FilterChip(
                    selected = uiState.selectedCategory == category,
                    onClick = { onCategoryChange(category) },
                    label = { Text(text = category.arabicLabel()) }
                )
            }
            item(key = "completed") {
                FilterChip(
                    selected = uiState.showCompleted,
                    onClick = { onToggleShowCompleted() },
                    label = { Text(text = "المنجزة") },
                    leadingIcon = {
                        if (uiState.showCompleted) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TaskCounter(
    activeTaskCount: Int,
    totalTaskCount: Int,
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier,
        text = when {
            totalTaskCount == 0 -> "لا توجد مهام بعد"
            activeTaskCount == 0 -> "أنجزت جميع المهام ($totalTaskCount)"
            else -> "$activeTaskCount مهمة نشطة من أصل $totalTaskCount"
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// =====================================================================
// Task list
// =====================================================================

@Composable
private fun TaskList(
    uiState: TodoUiState,
    onToggleComplete: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onSnooze: (TaskEntity, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        uiState.tasks.isEmpty() -> {
            EmptyState(
                hasActiveFilters = uiState.searchQuery.isNotEmpty() ||
                    uiState.selectedCategory != null,
                modifier = modifier
            )
        }

        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = uiState.tasks,
                    key = { task -> task.id }
                ) { task ->
                    SwipeableTaskRow(
                        task = task,
                        onToggleComplete = { onToggleComplete(task) },
                        onEdit = { onEdit(task) },
                        onDelete = { onDelete(task) },
                        onSnooze = { minutes -> onSnooze(task, minutes) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    hasActiveFilters: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (hasActiveFilters) {
                    "لا توجد مهام مطابقة للتصفية"
                } else {
                    "أضف مهمتك الأولى بالزر أدناه"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SwipeableTaskRow(
    task: TaskEntity,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd,
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }

                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            DeleteBackground(direction = dismissState.dismissDirection)
        }
    ) {
        TaskItemCard(
            task = task,
            onToggleComplete = onToggleComplete,
            onEdit = onEdit,
            onDelete = onDelete,
            onSnooze = onSnooze,
            snoozeOptionsMinutes = TodoViewModel.SNOOZE_OPTIONS_MINUTES
        )
    }
}

@Composable
private fun DeleteBackground(
    direction: SwipeToDismissBoxValue
) {
    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "حذف المهمة",
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

// =====================================================================
// Presentation helper for sort orders
// =====================================================================

private fun TodoSortOrder.arabicLabel(): String = when (this) {
    TodoSortOrder.PRIORITY -> "حسب الأولوية"
    TodoSortOrder.DUE_DATE -> "حسب تاريخ الاستحقاق"
    TodoSortOrder.CREATED_AT -> "حسب تاريخ الإضافة"
    TodoSortOrder.TITLE -> "أبجديًا"
}
