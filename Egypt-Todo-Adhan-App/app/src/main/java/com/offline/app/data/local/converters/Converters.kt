package com.offline.app.data.local.converters

import androidx.room.TypeConverter
import com.offline.app.data.local.entities.Priority
import com.offline.app.data.local.entities.RecurringInterval
import com.offline.app.data.local.entities.TaskCategory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Date

/**
 * Destination:
 * app/src/main/java/com/offline/app/data/local/converters/Converters.kt
 *
 * Room [TypeConverter]s for enums and date/time types.
 *
 * Design rules:
 *  - Enums are stored by their stable [Enum.name] (TEXT). Using names instead of
 *    ordinals keeps the database readable and makes reordering constants safe.
 *  - All date/time values are stored in UTC so the persisted data is independent
 *    of the device time zone. Local display conversion happens in the UI layer.
 *
 * Register once on the database class with @TypeConverters(Converters::class).
 */
class Converters {

    // =====================================================================
    // Enums
    // =====================================================================

    @TypeConverter
    fun fromPriority(value: Priority): String = value.name

    @TypeConverter
    fun toPriority(value: String): Priority = enumValueOf(value)

    @TypeConverter
    fun fromTaskCategory(value: TaskCategory): String = value.name

    @TypeConverter
    fun toTaskCategory(value: String): TaskCategory = enumValueOf(value)

    @TypeConverter
    fun fromRecurringInterval(value: RecurringInterval): String = value.name

    @TypeConverter
    fun toRecurringInterval(value: String): RecurringInterval = enumValueOf(value)

    // =====================================================================
    // java.time.Instant  <->  epoch millis (Long)
    // =====================================================================

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    // =====================================================================
    // java.time.LocalDate  <->  epoch day (Long)
    // =====================================================================

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun toLocalDate(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }

    // =====================================================================
    // java.time.LocalDateTime  <->  epoch millis at UTC (Long)
    // =====================================================================

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): Long? =
        value?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

    @TypeConverter
    fun toLocalDateTime(value: Long?): LocalDateTime? =
        value?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }

    // =====================================================================
    // java.time.LocalTime  <->  second of day (Long)
    // =====================================================================

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): Long? = value?.toSecondOfDay()?.toLong()

    @TypeConverter
    fun toLocalTime(value: Long?): LocalTime? =
        value?.let { LocalTime.ofSecondOfDay(it.coerceIn(0L, 86_399L)) }

    // =====================================================================
    // java.util.Date  <->  epoch millis (Long)
    // =====================================================================

    @TypeConverter
    fun fromDate(value: Date?): Long? = value?.time

    @TypeConverter
    fun toDate(value: Long?): Date? = value?.let { Date(it) }
}
