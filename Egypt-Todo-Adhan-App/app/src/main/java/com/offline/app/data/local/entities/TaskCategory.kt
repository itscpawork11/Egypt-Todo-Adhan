package com.offline.app.data.local.entities

/**
 * Destination:
 * app/src/main/java/com/offline/app/data/local/entities/TaskCategory.kt
 *
 * Functional category a task belongs to.
 * Persisted by Room as its [name] via [com.offline.app.data.local.Converters].
 */
enum class TaskCategory {
    WORK,
    PERSONAL,
    WORSHIP,
    ROUTINE
}
