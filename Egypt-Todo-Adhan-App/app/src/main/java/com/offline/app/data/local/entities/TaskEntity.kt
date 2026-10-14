package com.offline.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.offline.app.data.local.converters.Converters

/**
 * Destination:
 * app/src/main/java/com/offline/app/data/local/entities/TaskEntity.kt
 *
 * Table: "tasks"
 *
 * A single user task. Fully offline: every field is a primitive, String or a
 * Room-converted enum, so no network type is required.
 *
 * Deletion model (see TaskDao):
 *  - Soft delete sets [isDeleted] = true and the row is filtered out of every
 *    read query while remaining recoverable.
 *  - Permanent delete removes the row from the table.
 *
 * @param id Auto-generated primary key. 0 means "not yet inserted".
 * @param title Short task title. Required.
 * @param notes Optional free-form notes.
 * @param priority Task priority (HIGH / MEDIUM / LOW).
 * @param category Task category (WORK / PERSONAL / WORSHIP / ROUTINE).
 * @param dueDate Optional due date as epoch millis (UTC).
 * @param isCompleted Completion flag.
 * @param isRecurring Recurrence rule (NONE / DAILY / WEEKLY / MONTHLY).
 * @param reminderTime Optional reminder trigger time as epoch millis (UTC).
 * @param createdAt Creation timestamp as epoch millis (UTC).
 * @param isDeleted Soft-delete marker; true hides the task from all reads.
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["is_deleted"]),
        Index(value = ["is_completed"]),
        Index(value = ["due_date"]),
        Index(value = ["reminder_time"]),
        Index(value = ["category"]),
        Index(value = ["priority"])
    ]
)
@TypeConverters(Converters::class)
data class TaskEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "priority")
    val priority: Priority = Priority.MEDIUM,

    @ColumnInfo(name = "category")
    val category: TaskCategory = TaskCategory.PERSONAL,

    @ColumnInfo(name = "due_date")
    val dueDate: Long? = null,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "is_recurring")
    val isRecurring: RecurringInterval = RecurringInterval.NONE,

    @ColumnInfo(name = "reminder_time")
    val reminderTime: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false
)
