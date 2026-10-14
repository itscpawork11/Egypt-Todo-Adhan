package com.offline.app.data.local.entities

/**
 * Destination:
 * app/src/main/java/com/offline/app/data/local/entities/Priority.kt
 *
 * Priority level for a task.
 * Persisted by Room as its [name] via [com.offline.app.data.local.Converters].
 */
enum class Priority {
    HIGH,
    MEDIUM,
    LOW;

    /** Optional helper used by UI sorting (higher value = more urgent). */
    val weight: Int
        get() = when (this) {
            HIGH -> 2
            MEDIUM -> 1
            LOW -> 0
        }
}
