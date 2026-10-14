@file:OptIn(ExperimentalMaterial3Api::class)

package com.offline.app.ui.screens.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.offline.app.data.local.entities.Priority
import com.offline.app.data.local.entities.RecurringInterval
import com.offline.app.data.local.entities.TaskCategory
import com.offline.app.data.local.entities.TaskEntity
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/screens/todo/AddEditTaskSheet.kt
 *
 * Modal bottom sheet used to create a task or edit an existing one.
 *
 * It covers every task field: title, notes, category, priority, recurrence, due
 * date and reminder time. The date picker is the Material 3 calendar dialog; the
 * reminder time uses an hour/minute picker in a centred dialog (which keeps the
 * clock fully visible instead of clipping it in a narrow alert).
 *
 * The composable is stateless with respect to the model: it receives the task to
 * edit (or null to create) and reports the result through [onSave]. Validation and
 * persistence stay in the caller / `TodoViewModel`.
 *
 * @param initialTask the task being edited, or null to create a new one.
 * @param onDismiss   invoked when the sheet is dismissed without saving.
 * @param onSave      invoked with the assembled task; a new task carries id `0`.
 */
@Composable
fun AddEditTaskSheet(
    initialTask: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by rememberSaveable { mutableStateOf(initialTask?.title.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initialTask?.notes.orEmpty()) }
    var category by rememberSaveable {
        mutableStateOf(initialTask?.category ?: TaskCategory.PERSONAL)
    }
    var priority by rememberSaveable {
        mutableStateOf(initialTask?.priority ?: Priority.MEDIUM)
    }
    var recurrence by rememberSaveable {
        mutableStateOf(initialTask?.isRecurring ?: RecurringInterval.NONE)
    }
    var dueDate by rememberSaveable { mutableStateOf(initialTask?.dueDate) }
    var reminderTime by rememberSaveable { mutableStateOf(initialTask?.reminderTime) }

    var titleHasError by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val isEditing = initialTask != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isEditing) "تعديل المهمة" else "مهمة جديدة",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            val titleSupportingText: (@Composable () -> Unit)? = if (titleHasError) {
                { Text(text = "العنوان مطلوب") }
            } else {
                null
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleHasError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("العنوان") },
                singleLine = true,
                isError = titleHasError,
                supportingText = titleSupportingText
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                label = { Text("ملاحظات") },
                minLines = 3
            )

            FieldLabel(text = "التصنيف")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = TaskCategory.entries, key = { it.name }) { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item.arabicLabel()) }
                    )
                }
            }

            FieldLabel(text = "الأولوية")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = Priority.entries, key = { it.name }) { item ->
                    FilterChip(
                        selected = priority == item,
                        onClick = { priority = item },
                        label = { Text(item.arabicLabel()) }
                    )
                }
            }

            FieldLabel(text = "التكرار")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = RecurringInterval.entries, key = { it.name }) { item ->
                    FilterChip(
                        selected = recurrence == item,
                        onClick = { recurrence = item },
                        label = { Text(item.arabicLabel()) }
                    )
                }
            }

            FieldLabel(text = "تاريخ الاستحقاق")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dueDate?.let { formatTaskDate(it) } ?: "اختيار تاريخ"
                    )
                }
                if (dueDate != null) {
                    IconButton(onClick = { dueDate = null }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "مسح تاريخ الاستحقاق"
                        )
                    }
                }
            }

            FieldLabel(text = "وقت التنبيه")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = reminderTime?.let { formatTaskDateTime(it) } ?: "اختيار وقت"
                    )
                }
                if (reminderTime != null) {
                    IconButton(onClick = { reminderTime = null }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "مسح وقت التنبيه"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    val trimmedTitle = title.trim()
                    if (trimmedTitle.isEmpty()) {
                        titleHasError = true
                    } else {
                        val trimmedNotes = notes.trim().takeIf { it.isNotEmpty() }
                        val base = initialTask
                        val result = if (base == null) {
                            TaskEntity(
                                title = trimmedTitle,
                                notes = trimmedNotes,
                                priority = priority,
                                category = category,
                                dueDate = dueDate,
                                isRecurring = recurrence,
                                reminderTime = reminderTime
                            )
                        } else {
                            base.copy(
                                title = trimmedTitle,
                                notes = trimmedNotes,
                                priority = priority,
                                category = category,
                                dueDate = dueDate,
                                isRecurring = recurrence,
                                reminderTime = reminderTime
                            )
                        }
                        onSave(result)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(text = if (isEditing) "حفظ التعديلات" else "إضافة المهمة")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedUtcMillis = datePickerState.selectedDateMillis
                        if (selectedUtcMillis != null) {
                            val selectedLocalDate = Instant.ofEpochMilli(selectedUtcMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            dueDate = selectedLocalDate
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("حسنًا")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val seedMillis = reminderTime ?: dueDate ?: System.currentTimeMillis()
        val seedLocalTime = Instant.ofEpochMilli(seedMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()

        val timePickerState = rememberTimePickerState(
            initialHour = seedLocalTime.hour,
            initialMinute = seedLocalTime.minute,
            is24Hour = false
        )

        Dialog(
            onDismissRequest = { showTimePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "وقت التنبيه",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TimePicker(state = timePickerState)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showTimePicker = false }) {
                                Text("إلغاء")
                            }
                            TextButton(
                                onClick = {
                                    val zoneId = ZoneId.systemDefault()
                                    val baseDate = Instant.ofEpochMilli(seedMillis)
                                        .atZone(zoneId)
                                        .toLocalDate()
                                    reminderTime = baseDate
                                        .atTime(
                                            timePickerState.hour,
                                            timePickerState.minute
                                        )
                                        .atZone(zoneId)
                                        .toInstant()
                                        .toEpochMilli()
                                    showTimePicker = false
                                }
                            ) {
                                Text("حسنًا")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Small uppercase-style section label used between fields. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
