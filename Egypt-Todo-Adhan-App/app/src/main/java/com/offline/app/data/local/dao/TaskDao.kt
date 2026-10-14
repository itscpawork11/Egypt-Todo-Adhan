package com.offline.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offline.app.data.local.entities.Priority
import com.offline.app.data.local.entities.RecurringInterval
import com.offline.app.data.local.entities.TaskCategory
import com.offline.app.data.local.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Destination:
 * app/src/main/java/com/offline/app/data/local/dao/TaskDao.kt
 *
 * Reactive DAO for the "tasks" table.
 *
 * All read methods return [Flow] so the UI re-renders automatically when the
 * underlying data changes (100% offline, no polling, no network).
 *
 * Every read query filters `is_deleted = 0`, implementing soft delete. Rows are
 * only physically removed by the explicit permanent-delete methods.
 *
 * Priority ordering is done with a CASE expression because priorities are stored
 * as TEXT names; a plain `ORDER BY priority` would sort alphabetically.
 */
@Dao
interface TaskDao {

    // =====================================================================
    // Reactive reads
    // =====================================================================

    /** All non-deleted tasks: unfinished first, newest first within each group. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_deleted = 0
        ORDER BY is_completed ASC, created_at DESC
        """
    )
    fun observeAllTasks(): Flow<List<TaskEntity>>

    /** Non-deleted tasks whose due date falls inside the given [start,end) window. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_deleted = 0
          AND due_date IS NOT NULL
          AND due_date >= :startOfDayMillis
          AND due_date < :endOfDayMillis
        ORDER BY is_completed ASC, due_date ASC
        """
    )
    fun observeTasksByDate(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<TaskEntity>>

    /** Unfinished tasks whose due date is before [nowMillis]. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_deleted = 0
          AND is_completed = 0
          AND due_date IS NOT NULL
          AND due_date < :nowMillis
        ORDER BY due_date ASC
        """
    )
    fun observeOverdueTasks(nowMillis: Long): Flow<List<TaskEntity>>

    /** All non-deleted tasks ordered by priority (HIGH > MEDIUM > LOW). */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_deleted = 0
        ORDER BY
            is_completed ASC,
            CASE priority
                WHEN 'HIGH' THEN 0
                WHEN 'MEDIUM' THEN 1
                WHEN 'LOW' THEN 2
                ELSE 3
            END ASC,
            created_at DESC
        """
    )
    fun observeTasksSortedByPriority(): Flow<List<TaskEntity>>

    /** Non-deleted tasks of a single category. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_deleted = 0 AND category = :category
        ORDER BY is_completed ASC, created_at DESC
        """
    )
    fun observeTasksByCategory(category: TaskCategory): Flow<List<TaskEntity>>

    /** Non-deleted tasks of a single priority. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_deleted = 0 AND priority = :priority
        ORDER BY is_completed ASC, created_at DESC
        """
    )
    fun observeTasksByPriority(priority: Priority): Flow<List<TaskEntity>>

    /** Non-deleted tasks filtered by completion state. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_deleted = 0 AND is_completed = :completed
        ORDER BY created_at DESC
        """
    )
    fun observeTasksByCompletion(completed: Boolean): Flow<List<TaskEntity>>

    /** Non-deleted recurring tasks (is_recurring != NONE). */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_deleted = 0 AND is_recurring <> 'NONE'
        ORDER BY created_at DESC
        """
    )
    fun observeRecurringTasks(): Flow<List<TaskEntity>>

    /** Tasks that currently carry a reminder, for re-scheduling after boot. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_deleted = 0
          AND is_completed = 0
          AND reminder_time IS NOT NULL
        ORDER BY reminder_time ASC
        """
    )
    suspend fun getTasksWithReminders(): List<TaskEntity>

    /** Soft-deleted tasks, for a "recently deleted" / restore screen. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_deleted = 1
        ORDER BY created_at DESC
        """
    )
    fun observeDeletedTasks(): Flow<List<TaskEntity>>

    /** Live single task (including deleted ones). */
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun observeTaskById(id: Long): Flow<TaskEntity?>

    /** One-shot fetch of a single task. */
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): TaskEntity?

    /** Number of active (non-deleted, unfinished) tasks. */
    @Query("SELECT COUNT(*) FROM tasks WHERE is_deleted = 0 AND is_completed = 0")
    fun observeActiveTaskCount(): Flow<Int>

    // =====================================================================
    // Writes
    // =====================================================================

    /** Inserts a task. Returns the generated row id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    /** Inserts many tasks. Returns the generated row ids. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    /** Replaces an existing task by primary key. */
    @Update
    suspend fun update(task: TaskEntity)

    /** Updates several tasks in one transaction. */
    @Update
    suspend fun updateAll(tasks: List<TaskEntity>)

    /** Convenience toggle for the completion checkbox. */
    @Query("UPDATE tasks SET is_completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    // =====================================================================
    // Soft delete / restore
    // =====================================================================

    /** Marks one task as deleted without removing the row. */
    @Query("UPDATE tasks SET is_deleted = 1 WHERE id = :id")
    suspend fun softDeleteById(id: Long)

    /** Marks many tasks as deleted. */
    @Query("UPDATE tasks SET is_deleted = 1 WHERE id IN (:ids)")
    suspend fun softDeleteByIds(ids: List<Long>)

    /** Restores a soft-deleted task. */
    @Query("UPDATE tasks SET is_deleted = 0 WHERE id = :id")
    suspend fun restoreById(id: Long)

    /** Permanently removes every soft-deleted task. */
    @Query("DELETE FROM tasks WHERE is_deleted = 1")
    suspend fun purgeSoftDeleted()

    // =====================================================================
    // Permanent delete
    // =====================================================================

    /** Permanently removes one task by id. */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Permanently removes all completed tasks. */
    @Query("DELETE FROM tasks WHERE is_completed = 1")
    suspend fun deleteCompleted()

    /** Permanently removes every task. */
    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
