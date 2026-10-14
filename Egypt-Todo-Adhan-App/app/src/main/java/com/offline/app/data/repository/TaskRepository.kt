package com.offline.app.data.repository

import android.content.Context
import com.offline.app.core.alarm.AlarmScheduler
import com.offline.app.data.local.AppDatabase
import com.offline.app.data.local.dao.TaskDao
import com.offline.app.data.local.entities.Priority
import com.offline.app.data.local.entities.RecurringInterval
import com.offline.app.data.local.entities.TaskCategory
import com.offline.app.data.local.entities.TaskEntity
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Destination:
 * app/src/main/java/com/offline/app/data/repository/TaskRepository.kt
 *
 * Repository abstraction over [TaskDao].
 *
 * It exposes plain [Flow]s (no Room types leak upward), holds the recurring-task
 * business rule, and keeps task reminder alarms synchronized with the database by
 * driving [AlarmScheduler].
 */
interface TaskRepository {

    // ----- Reactive reads -----

    /** All non-deleted tasks: unfinished first, newest first. */
    fun observeAllTasks(): Flow<List<TaskEntity>>

    /** Tasks due inside the [startOfDayMillis, endOfDayMillis) window. */
    fun observeTasksByDate(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<TaskEntity>>

    /** Unfinished tasks whose due date is before [nowMillis]. */
    fun observeOverdueTasks(nowMillis: Long = System.currentTimeMillis()): Flow<List<TaskEntity>>

    /** All non-deleted tasks ordered by priority (HIGH > MEDIUM > LOW). */
    fun observeTasksSortedByPriority(): Flow<List<TaskEntity>>

    /** Non-deleted tasks of a single category. */
    fun observeTasksByCategory(category: TaskCategory): Flow<List<TaskEntity>>

    /** Non-deleted tasks of a single priority. */
    fun observeTasksByPriority(priority: Priority): Flow<List<TaskEntity>>

    /** Non-deleted tasks filtered by completion state. */
    fun observeTasksByCompletion(completed: Boolean): Flow<List<TaskEntity>>

    /** Non-deleted recurring tasks. */
    fun observeRecurringTasks(): Flow<List<TaskEntity>>

    /** Soft-deleted tasks, for a "recently deleted" screen. */
    fun observeDeletedTasks(): Flow<List<TaskEntity>>

    /** A single task, including deleted ones. */
    fun observeTaskById(id: Long): Flow<TaskEntity?>

    /** Number of active (non-deleted, unfinished) tasks. */
    fun observeActiveTaskCount(): Flow<Int>

    /** Title-only projection of all non-deleted tasks, for lightweight UI lists. */
    fun observeTaskTitles(): Flow<List<String>>

    // ----- One-shot reads -----

    suspend fun getTaskById(id: Long): TaskEntity?

    /** Tasks with a future reminder, used to re-arm alarms after boot. */
    suspend fun getTasksWithReminders(): List<TaskEntity>

    // ----- Writes -----

    suspend fun insertTask(task: TaskEntity): Long

    suspend fun insertTasks(tasks: List<TaskEntity>): List<Long>

    suspend fun updateTask(task: TaskEntity)

    suspend fun updateTasks(tasks: List<TaskEntity>)

    /** Toggles completion; when completing a recurring task, spawns the next one. */
    suspend fun setTaskCompleted(id: Long, completed: Boolean)

    // ----- Deletion -----

    suspend fun softDeleteTask(id: Long)

    suspend fun softDeleteTasks(ids: List<Long>)

    suspend fun restoreTask(id: Long)

    suspend fun purgeSoftDeletedTasks()

    suspend fun deleteTaskPermanently(id: Long)

    suspend fun deleteCompletedTasks()

    suspend fun deleteAllTasks()

    // ----- Recurring computation -----

    /**
     * Computes the next occurrence of a recurring [task], or null when the task
     * does not recur. The returned copy has a fresh id (0 = unsaved), is not
     * completed, not deleted, and carries advanced due/reminder timestamps.
     */
    fun nextRecurrence(task: TaskEntity): TaskEntity?
}

/**
 * Default [TaskRepository] implementation backed by [TaskDao] and [AlarmScheduler].
 *
 * @param taskDao the Room data source.
 * @param appContext an application context used only for alarm scheduling.
 */
class DefaultTaskRepository(
    private val taskDao: TaskDao,
    private val appContext: Context
) : TaskRepository {

    private val applicationContext: Context = appContext.applicationContext

    // =====================================================================
    // Reactive reads
    // =====================================================================

    override fun observeAllTasks(): Flow<List<TaskEntity>> = taskDao.observeAllTasks()

    override fun observeTasksByDate(
        startOfDayMillis: Long,
        endOfDayMillis: Long
    ): Flow<List<TaskEntity>> = taskDao.observeTasksByDate(startOfDayMillis, endOfDayMillis)

    override fun observeOverdueTasks(nowMillis: Long): Flow<List<TaskEntity>> =
        taskDao.observeOverdueTasks(nowMillis)

    override fun observeTasksSortedByPriority(): Flow<List<TaskEntity>> =
        taskDao.observeTasksSortedByPriority()

    override fun observeTasksByCategory(category: TaskCategory): Flow<List<TaskEntity>> =
        taskDao.observeTasksByCategory(category)

    override fun observeTasksByPriority(priority: Priority): Flow<List<TaskEntity>> =
        taskDao.observeTasksByPriority(priority)

    override fun observeTasksByCompletion(completed: Boolean): Flow<List<TaskEntity>> =
        taskDao.observeTasksByCompletion(completed)

    override fun observeRecurringTasks(): Flow<List<TaskEntity>> = taskDao.observeRecurringTasks()

    override fun observeDeletedTasks(): Flow<List<TaskEntity>> = taskDao.observeDeletedTasks()

    override fun observeTaskById(id: Long): Flow<TaskEntity?> = taskDao.observeTaskById(id)

    override fun observeActiveTaskCount(): Flow<Int> = taskDao.observeActiveTaskCount()

    override fun observeTaskTitles(): Flow<List<String>> =
        taskDao.observeAllTasks().map { tasks -> tasks.map { it.title } }

    // =====================================================================
    // One-shot reads
    // =====================================================================

    override suspend fun getTaskById(id: Long): TaskEntity? = taskDao.getTaskById(id)

    override suspend fun getTasksWithReminders(): List<TaskEntity> = taskDao.getTasksWithReminders()

    // =====================================================================
    // Writes
    // =====================================================================

    override suspend fun insertTask(task: TaskEntity): Long {
        val id = taskDao.insert(task)
        scheduleReminderIfNeeded(task.copy(id = id))
        return id
    }

    override suspend fun insertTasks(tasks: List<TaskEntity>): List<Long> {
        val ids = taskDao.insertAll(tasks)
        tasks.forEachIndexed { index, task ->
            val id = ids.getOrNull(index) ?: return@forEachIndexed
            scheduleReminderIfNeeded(task.copy(id = id))
        }
        return ids
    }

    override suspend fun updateTask(task: TaskEntity) {
        taskDao.update(task)
        AlarmScheduler.cancelTaskReminder(applicationContext, task.id)
        scheduleReminderIfNeeded(task)
    }

    override suspend fun updateTasks(tasks: List<TaskEntity>) {
        taskDao.updateAll(tasks)
        tasks.forEach { task ->
            AlarmScheduler.cancelTaskReminder(applicationContext, task.id)
            scheduleReminderIfNeeded(task)
        }
    }

    override suspend fun setTaskCompleted(id: Long, completed: Boolean) {
        taskDao.setCompleted(id, completed)

        if (completed) {
            // Completing a task cancels its pending reminder.
            AlarmScheduler.cancelTaskReminder(applicationContext, id)
            val task = taskDao.getTaskById(id)
            if (task != null) {
                val next = nextRecurrence(task)
                if (next != null) {
                    insertTask(next)
                }
            }
        } else {
            // Re-opening a task re-arms its reminder.
            val task = taskDao.getTaskById(id)
            if (task != null) {
                scheduleReminderIfNeeded(task)
            }
        }
    }

    // =====================================================================
    // Deletion
    // =====================================================================

    override suspend fun softDeleteTask(id: Long) {
        AlarmScheduler.cancelTaskReminder(applicationContext, id)
        taskDao.softDeleteById(id)
    }

    override suspend fun softDeleteTasks(ids: List<Long>) {
        ids.forEach { AlarmScheduler.cancelTaskReminder(applicationContext, it) }
        taskDao.softDeleteByIds(ids)
    }

    override suspend fun restoreTask(id: Long) {
        taskDao.restoreById(id)
        val task = taskDao.getTaskById(id)
        if (task != null) {
            scheduleReminderIfNeeded(task)
        }
    }

    override suspend fun purgeSoftDeletedTasks() {
        taskDao.purgeSoftDeleted()
    }

    override suspend fun deleteTaskPermanently(id: Long) {
        AlarmScheduler.cancelTaskReminder(applicationContext, id)
        taskDao.deleteById(id)
    }

    override suspend fun deleteCompletedTasks() {
        // Read the completed rows first so their alarms can be cleared before the
        // rows disappear; a completed task can still carry a pending reminder.
        val completedTasks = taskDao.observeTasksByCompletion(completed = true).first()
        completedTasks.forEach { task ->
            AlarmScheduler.cancelTaskReminder(applicationContext, task.id)
        }
        taskDao.deleteCompleted()
    }

    override suspend fun deleteAllTasks() {
        taskDao.deleteAll()
    }

    // =====================================================================
    // Recurring computation
    // =====================================================================

    override fun nextRecurrence(task: TaskEntity): TaskEntity? {
        val interval = task.isRecurring
        if (interval == RecurringInterval.NONE) return null

        val zoneId = ZoneId.systemDefault()
        val nextDue = task.dueDate?.let { advance(it, interval, zoneId) }
        val nextReminder = task.reminderTime?.let { advance(it, interval, zoneId) }

        // A recurring task must carry at least one schedule anchor.
        if (nextDue == null && nextReminder == null) return null

        return task.copy(
            id = 0L,
            dueDate = nextDue,
            reminderTime = nextReminder,
            isCompleted = false,
            isDeleted = false,
            createdAt = System.currentTimeMillis()
        )
    }

    // =====================================================================
    // Internal helpers
    // =====================================================================

    /**
     * Schedules a reminder alarm when the task is active and its reminder time is
     * still in the future. No-op otherwise.
     */
    private fun scheduleReminderIfNeeded(task: TaskEntity) {
        if (task.isCompleted || task.isDeleted) return
        val reminder = task.reminderTime ?: return
        if (reminder <= System.currentTimeMillis()) return
        AlarmScheduler.scheduleTaskReminder(
            context = applicationContext,
            taskId = task.id,
            taskTitle = task.title,
            triggerMillis = reminder
        )
    }

    /** Advances an epoch-millis instant by one [interval] in [zoneId]. */
    private fun advance(
        epochMillis: Long,
        interval: RecurringInterval,
        zoneId: ZoneId
    ): Long {
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        val next = when (interval) {
            RecurringInterval.DAILY -> zoned.plusDays(1)
            RecurringInterval.WEEKLY -> zoned.plusWeeks(1)
            RecurringInterval.MONTHLY -> zoned.plusMonths(1)
            RecurringInterval.NONE -> zoned
        }
        return next.toInstant().toEpochMilli()
    }

    companion object {
        /**
         * Builds a [DefaultTaskRepository] from the process-wide database.
         * Kept as a factory so the data layer can be wired without a DI framework.
         */
        fun create(context: Context): TaskRepository {
            val appContext = context.applicationContext
            return DefaultTaskRepository(
                taskDao = AppDatabase.getInstance(appContext).taskDao(),
                appContext = appContext
            )
        }
    }
}
