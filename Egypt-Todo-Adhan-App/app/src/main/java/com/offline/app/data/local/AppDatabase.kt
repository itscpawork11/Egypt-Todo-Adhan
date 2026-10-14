package com.offline.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.offline.app.data.local.converters.Converters
import com.offline.app.data.local.dao.PrayerConfigDao
import com.offline.app.data.local.dao.TaskDao
import com.offline.app.data.local.entities.PrayerConfigEntity
import com.offline.app.data.local.entities.TaskEntity

/**
 * Destination:
 * app/src/main/java/com/offline/app/data/local/AppDatabase.kt
 *
 * Singleton Room database for the 100% offline prayer-times app.
 *
 *  - Tables: "tasks" and "prayer_config".
 *  - Type converters are registered once here, so DAO query parameters such as
 *    `category = :category` are converted automatically.
 *  - A [RoomDatabase.Callback] seeds the default Egypt (Cairo) prayer
 *    configuration the first time the database file is created.
 *
 * Obtain the instance with [AppDatabase.getInstance]; it is created lazily and
 * cached in a thread-safe volatile field.
 */
@Database(
    entities = [
        TaskEntity::class,
        PrayerConfigEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /** Reactive DAO for tasks. */
    abstract fun taskDao(): TaskDao

    /** DAO for the single-row prayer configuration. */
    abstract fun prayerConfigDao(): PrayerConfigDao

    companion object {

        /** On-disk database file name. */
        const val DATABASE_NAME: String = "offline_prayer_times.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the process-wide database instance, building it on first use.
         * Double-checked locking keeps this safe under concurrent access.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(DefaultPrayerConfigCallback())
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Closes and releases the singleton. Intended for teardown/testing; the
         * next [getInstance] call rebuilds it.
         */
        @Synchronized
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }

    /**
     * Seeds the default prayer configuration on first launch.
     *
     * The insert is executed directly against the freshly created schema with
     * [SupportSQLiteDatabase.execSQL], which avoids any re-entrancy on
     * [AppDatabase.getInstance] while the database is still being opened.
     */
    private class DefaultPrayerConfigCallback : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            db.execSQL(DEFAULT_PRAYER_CONFIG_INSERT_SQL)
        }

        private companion object {
            /**
             * Egypt / Cairo defaults:
             *  - governorate = Cairo
             *  - all manual offsets = 0 minutes
             *  - pre-Adhan reminder disabled (0 minutes)
             *  - Adhan sound enabled, using the bundled default (NULL uri)
             */
            const val DEFAULT_PRAYER_CONFIG_INSERT_SQL: String =
                "INSERT INTO prayer_config (" +
                    "id, " +
                    "selected_governorate, " +
                    "fajr_offset, " +
                    "dhuhr_offset, " +
                    "asr_offset, " +
                    "maghrib_offset, " +
                    "isha_offset, " +
                    "pre_adhan_reminder_minutes, " +
                    "is_adhan_sound_enabled, " +
                    "selected_adhan_sound_uri" +
                    ") VALUES (" +
                    "1, " +
                    "'Cairo', " +
                    "0, " +
                    "0, " +
                    "0, " +
                    "0, " +
                    "0, " +
                    "0, " +
                    "1, " +
                    "NULL" +
                    ")"
        }
    }
}
