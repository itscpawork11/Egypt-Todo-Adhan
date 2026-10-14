package com.offline.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Destination:
 * app/src/main/java/com/offline/app/data/local/entities/PrayerConfigEntity.kt
 *
 * Table: "prayer_config"
 *
 * Single-row configuration table. There is always exactly one row with
 * [id] == [SINGLETON_ID]. Use `INSERT OR REPLACE` (Room `@Insert(onConflict =
 * OnConflictStrategy.REPLACE)`) to update it.
 *
 * All offsets are manual adjustments in minutes applied on top of the computed
 * Adhan times, allowing the user to fine-tune per prayer without any network.
 *
 * @param id Fixed primary key; always [SINGLETON_ID].
 * @param selectedGovernorate Governorate/city whose coordinates drive the Adhan
 *        calculation (resolved offline from a bundled list).
 * @param fajrOffset Manual Fajr offset in minutes (may be negative).
 * @param dhuhrOffset Manual Dhuhr offset in minutes (may be negative).
 * @param asrOffset Manual Asr offset in minutes (may be negative).
 * @param maghribOffset Manual Maghrib offset in minutes (may be negative).
 * @param ishaOffset Manual Isha offset in minutes (may be negative).
 * @param preAdhanReminderMinutes Minutes before Adhan to notify; 0 disables it.
 *        Expected values: 0, 5, 10, 15.
 * @param isAdhanSoundEnabled Whether to play the Adhan sound at prayer time.
 * @param selectedAdhanSoundUri Optional content URI of the user-selected Adhan
 *        audio file. Null means "use the bundled default sound".
 */
@Entity(tableName = "prayer_config")
data class PrayerConfigEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long = SINGLETON_ID,

    @ColumnInfo(name = "selected_governorate")
    val selectedGovernorate: String = DEFAULT_GOVERNORATE,

    @ColumnInfo(name = "fajr_offset")
    val fajrOffset: Int = 0,

    @ColumnInfo(name = "dhuhr_offset")
    val dhuhrOffset: Int = 0,

    @ColumnInfo(name = "asr_offset")
    val asrOffset: Int = 0,

    @ColumnInfo(name = "maghrib_offset")
    val maghribOffset: Int = 0,

    @ColumnInfo(name = "isha_offset")
    val ishaOffset: Int = 0,

    @ColumnInfo(name = "pre_adhan_reminder_minutes")
    val preAdhanReminderMinutes: Int = 0,

    @ColumnInfo(name = "is_adhan_sound_enabled")
    val isAdhanSoundEnabled: Boolean = true,

    @ColumnInfo(name = "selected_adhan_sound_uri")
    val selectedAdhanSoundUri: String? = null
) {
    companion object {
        /** The one and only primary key value for this configuration table. */
        const val SINGLETON_ID: Long = 1L

        /** Fallback governorate used before the user makes a selection. */
        const val DEFAULT_GOVERNORATE: String = "Riyadh"

        /** Allowed reminder lead times, in minutes (0 = disabled). */
        val ALLOWED_REMINDER_MINUTES: Set<Int> = setOf(0, 5, 10, 15)
    }
}
