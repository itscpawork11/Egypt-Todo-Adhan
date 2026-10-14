package com.offline.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.offline.app.data.local.entities.PrayerConfigEntity
import kotlinx.coroutines.flow.Flow

/**
 * Destination:
 * app/src/main/java/com/offline/app/data/local/dao/PrayerConfigDao.kt
 *
 * DAO for the single-row "prayer_config" table (id is always 1).
 *
 * [observeConfig] returns Flow so any settings change re-emits immediately.
 * [upsertConfig] uses INSERT OR REPLACE, making it safe to call whether or not
 * the default row already exists.
 */
@Dao
interface PrayerConfigDao {

    /** Reactive read of the configuration row; null only before first insert. */
    @Query("SELECT * FROM prayer_config WHERE id = 1 LIMIT 1")
    fun observeConfig(): Flow<PrayerConfigEntity?>

    /** One-shot read of the configuration row. */
    @Query("SELECT * FROM prayer_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): PrayerConfigEntity?

    /** Inserts or replaces the configuration row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: PrayerConfigEntity)

    /** Number of configuration rows (expected to be 0 or 1). */
    @Query("SELECT COUNT(*) FROM prayer_config")
    suspend fun count(): Int

    // =====================================================================
    // Targeted updates (avoid rewriting the whole row)
    // =====================================================================

    @Query("UPDATE prayer_config SET selected_governorate = :governorate WHERE id = 1")
    suspend fun updateGovernorate(governorate: String)

    @Query("UPDATE prayer_config SET fajr_offset = :minutes WHERE id = 1")
    suspend fun updateFajrOffset(minutes: Int)

    @Query("UPDATE prayer_config SET dhuhr_offset = :minutes WHERE id = 1")
    suspend fun updateDhuhrOffset(minutes: Int)

    @Query("UPDATE prayer_config SET asr_offset = :minutes WHERE id = 1")
    suspend fun updateAsrOffset(minutes: Int)

    @Query("UPDATE prayer_config SET maghrib_offset = :minutes WHERE id = 1")
    suspend fun updateMaghribOffset(minutes: Int)

    @Query("UPDATE prayer_config SET isha_offset = :minutes WHERE id = 1")
    suspend fun updateIshaOffset(minutes: Int)

    @Query("UPDATE prayer_config SET pre_adhan_reminder_minutes = :minutes WHERE id = 1")
    suspend fun updatePreAdhanReminderMinutes(minutes: Int)

    @Query("UPDATE prayer_config SET is_adhan_sound_enabled = :enabled WHERE id = 1")
    suspend fun updateAdhanSoundEnabled(enabled: Boolean)

    @Query("UPDATE prayer_config SET selected_adhan_sound_uri = :uri WHERE id = 1")
    suspend fun updateAdhanSoundUri(uri: String?)

    /** Restores factory defaults in a single statement. */
    @Query(
        """
        UPDATE prayer_config SET
            selected_governorate = 'Cairo',
            fajr_offset = 0,
            dhuhr_offset = 0,
            asr_offset = 0,
            maghrib_offset = 0,
            isha_offset = 0,
            pre_adhan_reminder_minutes = 0,
            is_adhan_sound_enabled = 1,
            selected_adhan_sound_uri = NULL
        WHERE id = 1
        """
    )
    suspend fun resetToDefaults()

    /** Removes the configuration row entirely (used by destructive reset flows). */
    @Query("DELETE FROM prayer_config")
    suspend fun deleteConfig()
}
