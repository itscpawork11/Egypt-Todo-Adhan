package com.offline.app.data.local.entities

/**
 * Destination:
 * app/src/main/java/com/offline/app/data/local/entities/RecurringInterval.kt
 *
 * Recurrence rule for a task.
 * Persisted by Room as its [name] via [com.offline.app.data.local.Converters].
 */
enum class RecurringInterval {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}
