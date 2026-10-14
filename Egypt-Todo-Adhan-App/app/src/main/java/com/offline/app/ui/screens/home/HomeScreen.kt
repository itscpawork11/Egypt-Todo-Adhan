@file:OptIn(ExperimentalMaterial3Api::class)

package com.offline.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offline.app.data.local.entities.TaskEntity
import com.offline.app.ui.screens.prayer.prayerClockTime
import com.offline.app.ui.screens.prayer.prayerFullDate
import com.offline.app.ui.screens.todo.AddEditTaskSheet
import com.offline.app.ui.screens.todo.TaskItemCard
import com.offline.app.ui.viewmodels.PrayerUiState
import com.offline.app.ui.viewmodels.PrayerViewModel
import com.offline.app.ui.viewmodels.TodoViewModel
import java.time.LocalDate
import java.time.ZoneId

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/screens/home/HomeScreen.kt
 *
 * Unified dashboard: a compact next-prayer banner with a live countdown at the
 * very top, and today's pending priority tasks immediately below it for instant
 * glanceability.
 *
 * The screen is a pure composition over two existing ViewModels, so it needs no
 * ViewModel of its own:
 *  - [PrayerViewModel] supplies the next prayer, its trigger time and the
 *    one-second countdown; because the countdown is a `StateFlow` driven by the
 *    ViewModel's ticker, the banner updates every second without a local timer.
 *  - [TodoViewModel] supplies the live task list, the pending-task counter and
 *    every task operation (toggle, edit, delete, snooze).
 *
 * Today's slice is derived locally in [selectTodayPendingTasks]: overdue and
 * due-today tasks first, then the highest priorities, then the nearest due date,
 * capped at [HOME_PENDING_TASK_LIMIT] rows. The full list lives on the Tasks tab,
 * reachable through the "عرض الكل" action or the bottom navigation bar.
 *
 * Offline-first: nothing here reads the network, the GPS or shared preferences;
 * every value comes from Room through the ViewModels.
 *
 * @param prayerViewModel        source of the next-prayer banner state.
 * @param todoViewModel          source of the pending tasks and task actions.
 * @param onNavigateToTasks      opens the full tasks destination.
 * @param onNavigateToPrayerSchedule opens the full prayer-times destination.
 * @param modifier               layout modifier applied to the root scaffold.
 */
@Composable
fun HomeScreen(
    prayerViewModel: PrayerViewModel,
    todoViewModel: TodoViewModel,
    onNavigateToTasks: () -> Unit,
    onNavigateToPrayerSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prayerState by prayerViewModel.uiState.collectAsStateWithLifecycle()
    val todoState by todoViewModel.uiState.collectAsStateWithLifecycle()

    val prayerErrorMessage by prayerViewModel.errorMessage.collectAsStateWithLifecycle()
    val todoErrorMessage by todoViewModel.errorMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var isSheetVisible by remember { mutableStateOf(false) }
    var taskBeingEdited by remember { mutableStateOf<TaskEntity?>(null) }

    LaunchedEffect(prayerErrorMessage) {
        val message = prayerErrorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            prayerViewModel.clearError()
        }
    }

    LaunchedEffect(todoErrorMessage) {
        val message = todoErrorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            todoViewModel.clearError()
        }
    }

    val todayLabel = prayerFullDate(System.currentTimeMillis())

    val pendingTasks = remember(todoState.tasks) {
        selectTodayPendingTasks(todoState.tasks)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "الرئيسية",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = todayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "next-prayer-banner") {
                    NextPrayerBanner(
                        uiState = prayerState,
                        onClick = onNavigateToPrayerSchedule
                    )
                }

                item(key = "tasks-header") {
                    TasksSectionHeader(
                        pendingCount = todoState.activeTaskCount,
                        onShowAll = onNavigateToTasks
                    )
                }

                if (pendingTasks.isEmpty()) {
                    item(key = "tasks-empty") {
                        EmptyTasksCard(
                            onAddTask = {
                                taskBeingEdited = null
                                isSheetVisible = true
                            }
                        )
                    }
                } else {
                    items(
                        items = pendingTasks,
                        key = { task -> task.id }
                    ) { task ->
                        TaskItemCard(
                            task = task,
                            onToggleComplete = { todoViewModel.toggleComplete(task) },
                            onEdit = {
                                taskBeingEdited = task
                                isSheetVisible = true
                            },
                            onDelete = { todoViewModel.deleteTask(task) },
                            onSnooze = { minutes -> todoViewModel.snoozeReminder(task, minutes) },
                            snoozeOptionsMinutes = TodoViewModel.SNOOZE_OPTIONS_MINUTES
                        )
                    }

                    item(key = "tasks-show-all") {
                        OutlinedButton(
                            onClick = onNavigateToTasks,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "عرض كل المهام")
                        }
                    }
                }
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
                        todoViewModel.addTask(task)
                    } else {
                        todoViewModel.updateTask(task)
                    }
                    isSheetVisible = false
                    taskBeingEdited = null
                }
            )
        }
    }
}

// =====================================================================
// Private building blocks
// =====================================================================

/**
 * Compact banner for the upcoming prayer.
 *
 * The right-hand column shows `uiState.countdown.formatted` (HH:MM:SS), which
 * the ViewModel recomputes once per second. While the very first calculation is
 * still running a small progress indicator is shown instead of the digits.
 */
@Composable
private fun NextPrayerBanner(
    uiState: PrayerUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "الصلاة القادمة • ${uiState.governorate.arabicName}",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = uiState.nextPrayerLabel.ifEmpty { "غير محددة" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                val nextPrayer = uiState.nextPrayer
                Text(
                    text = if (nextPrayer != null) {
                        "تبدأ في ${prayerClockTime(nextPrayer.triggerMillis)}"
                    } else {
                        "لم يتم تحديد الوقت بعد"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (uiState.currentPrayerLabel.isNotEmpty()) {
                    Text(
                        text = "الوقت الحالي: ${uiState.currentPrayerLabel}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 3.dp
                    )
                } else {
                    Text(
                        text = uiState.countdown.formatted,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "متبقٍ",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/** Section header for the today's-tasks block, with a "show all" action. */
@Composable
private fun TasksSectionHeader(
    pendingCount: Int,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "مهام اليوم",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (pendingCount == 0) {
                    "لا توجد مهام معلّقة"
                } else {
                    "$pendingCount مهمة معلّقة"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = onShowAll) {
            Text(text = "عرض الكل")
        }
    }
}

/** Shown when there is no pending task to highlight. */
@Composable
private fun EmptyTasksCard(
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "لا توجد مهام معلّقة لليوم",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "أضف مهمة جديدة لتظهر هنا مباشرة",
                style = MaterialTheme.typography.labelMedium
            )
            Button(onClick = onAddTask) {
                Text(text = "إضافة مهمة")
            }
        }
    }
}

// =====================================================================
// Today's pending-task selection
// =====================================================================

/**
 * Picks the tasks worth showing on the dashboard.
 *
 * Ordering (most important first):
 *  1. overdue or due-today tasks,
 *  2. tasks due after today,
 *  3. tasks without a due date.
 *
 * Ties are broken by priority (HIGH first), then by the nearest due date, then
 * by the most recently created task. Completed tasks are always excluded.
 */
private fun selectTodayPendingTasks(tasks: List<TaskEntity>): List<TaskEntity> {
    val zoneId = ZoneId.systemDefault()
    val startOfTomorrow = LocalDate.now(zoneId)
        .plusDays(1)
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    return tasks.asSequence()
        .filter { task -> !task.isCompleted }
        .sortedWith(
            compareBy<TaskEntity> { task ->
                val dueDate = task.dueDate
                when {
                    dueDate == null -> 2
                    dueDate < startOfTomorrow -> 0
                    else -> 1
                }
            }
                .thenByDescending { task -> task.priority.weight }
                .thenBy { task -> task.dueDate ?: Long.MAX_VALUE }
                .thenByDescending { task -> task.createdAt }
        )
        .take(HOME_PENDING_TASK_LIMIT)
        .toList()
}

/** Maximum number of pending task rows rendered on the dashboard. */
private const val HOME_PENDING_TASK_LIMIT: Int = 5
