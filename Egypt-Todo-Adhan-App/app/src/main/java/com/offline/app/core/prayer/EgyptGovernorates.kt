package com.offline.app.core.prayer

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Destination:
 * app/src/main/java/com/offline/app/core/prayer/EgyptGovernorates.kt
 *
 * Complete, fully-offline reference of the 27 Egyptian governorates.
 *
 * Each entry carries:
 *  - [id]                         stable numeric identifier (1..27).
 *  - [arabicName]                 Arabic governorate name.
 *  - [englishName]                Latin governorate name.
 *  - [latitude] / [longitude]     geographic centre of the governorate capital,
 *                                 in decimal degrees (WGS-84).
 *  - [defaultQiblaAngleDegrees]   pre-computed Qibla direction: the initial
 *                                 (great-circle) bearing from the governorate to
 *                                 the Kaaba, measured clockwise from TRUE NORTH.
 *  - [distanceToKaabaKm]          great-circle distance to the Kaaba, in km.
 *
 * Because both the coordinates and the default Qibla angles are embedded as
 * constants, the app can compute accurate prayer times and show the Qibla
 * direction with NO network access and WITHOUT requiring GPS.
 *
 * The Qibla angles were produced with the standard spherical initial-bearing
 * formula (see [calculateQiblaAngleDegrees]) and rounded to two decimals. Use
 * that function to recompute a value for an arbitrary coordinate.
 *
 * Note: the canonical set has 27 governorates; البحيرة (Beheira) is included
 * here so the list is complete.
 */
enum class EgyptGovernorate(
    val id: Int,
    val arabicName: String,
    val englishName: String,
    val latitude: Double,
    val longitude: Double,
    val defaultQiblaAngleDegrees: Double,
    val distanceToKaabaKm: Int
) {

    /** 1 — القاهرة */
    CAIRO(1, "القاهرة", "Cairo", 30.0444, 31.2357, 136.14, 1287),

    /** 2 — الإسكندرية */
    ALEXANDRIA(2, "الإسكندرية", "Alexandria", 31.2001, 29.9187, 135.44, 1467),

    /** 3 — الجيزة */
    GIZA(3, "الجيزة", "Giza", 30.0131, 31.2089, 135.94, 1287),

    /** 4 — القليوبية (بنها) */
    QALYUBIA(4, "القليوبية", "Qalyubia", 30.4667, 31.1833, 137.34, 1325),

    /** 5 — الدقهلية (المنصورة) */
    DAKAHALIA(5, "الدقهلية", "Dakahlia", 31.0409, 31.3785, 139.81, 1360),

    /** 6 — الشرقية (الزقازيق) */
    SHARQIA(6, "الشرقية", "Sharqia", 30.5877, 31.5020, 138.86, 1314),

    /** 7 — المنوفية (شبين الكوم) */
    MONUFIA(7, "المنوفية", "Monufia", 30.5540, 31.0078, 137.01, 1343),

    /** 8 — الغربية (طنطا) */
    GHARBIA(8, "الغربية", "Gharbia", 30.7865, 31.0004, 137.72, 1363),

    /** 9 — دمياط */
    DAMIETTA(9, "دمياط", "Damietta", 31.4165, 31.8133, 142.45, 1367),

    /** 10 — كفر الشيخ */
    KAFR_EL_SHEIKH(10, "كفر الشيخ", "Kafr El Sheikh", 31.1107, 30.9388, 138.49, 1394),

    /** 11 — بورسعيد */
    PORT_SAID(11, "بورسعيد", "Port Said", 31.2653, 32.3019, 143.84, 1326),

    /** 12 — الإسماعيلية */
    ISMAILIA(12, "الإسماعيلية", "Ismailia", 30.5965, 32.2715, 141.76, 1268),

    /** 13 — السويس */
    SUEZ(13, "السويس", "Suez", 29.9668, 32.5498, 140.85, 1197),

    /** 14 — شمال سيناء (العريش) */
    NORTH_SINAI(14, "شمال سيناء", "North Sinai", 31.1310, 33.7984, 149.50, 1235),

    /** 15 — جنوب سيناء (الطور) */
    SOUTH_SINAI(15, "جنوب سيناء", "South Sinai", 28.2416, 33.6222, 139.10, 983),

    /** 16 — بني سويف */
    BENI_SUEF(16, "بني سويف", "Beni Suef", 29.0661, 31.0994, 132.13, 1221),

    /** 17 — المنيا */
    MINYA(17, "المنيا", "Minya", 28.1099, 30.7503, 127.08, 1179),

    /** 18 — أسيوط */
    ASYUT(18, "أسيوط", "Asyut", 27.1809, 31.1837, 124.32, 1084),

    /** 19 — سوهاج */
    SOHAG(19, "سوهاج", "Sohag", 26.5591, 31.6957, 122.95, 1004),

    /** 20 — قنا */
    QENA(20, "قنا", "Qena", 26.1551, 32.7160, 124.55, 894),

    /** 21 — الأقصر */
    LUXOR(21, "الأقصر", "Luxor", 25.6872, 32.6396, 121.44, 872),

    /** 22 — أسوان */
    ASWAN(22, "أسوان", "Aswan", 24.0889, 32.8998, 111.29, 769),

    /** 23 — البحر الأحمر (الغردقة) */
    RED_SEA(23, "البحر الأحمر", "Red Sea", 27.2579, 33.8116, 135.52, 890),

    /** 24 — الوادي الجديد (الخارجة) */
    NEW_VALLEY(24, "الوادي الجديد", "New Valley", 25.4514, 30.5464, 113.42, 1047),

    /** 25 — مطروح (مرسى مطروح) */
    MATROUH(25, "مطروح", "Matrouh", 31.3543, 27.2373, 128.40, 1669),

    /** 26 — الفيوم */
    FAYOUM(26, "الفيوم", "Fayoum", 29.3084, 30.8428, 132.16, 1257),

    /** 27 — البحيرة (دمنهور) */
    BEHEIRA(27, "البحيرة", "Beheira", 31.0341, 30.4682, 136.69, 1418);

    /** Human readable label combining both scripts. */
    val displayName: String
        get() = "$arabicName — $englishName"

    /** Qibla direction expressed as a clockwise angle from true north. */
    val qiblaFromNorthDegrees: Double
        get() = defaultQiblaAngleDegrees

    companion object {

        /** Kaaba latitude in decimal degrees. */
        const val KAABA_LATITUDE: Double = 21.4225

        /** Kaaba longitude in decimal degrees. */
        const val KAABA_LONGITUDE: Double = 39.8262

        /** The default governorate used when nothing has been chosen yet. */
        val DEFAULT: EgyptGovernorate = CAIRO

        /** Total number of governorates held by this enum. */
        val count: Int
            get() = entries.size

        /**
         * Returns the governorate with the given [id], or null if none matches.
         */
        fun fromId(id: Int): EgyptGovernorate? =
            entries.firstOrNull { it.id == id }

        /**
         * Returns the governorate whose Arabic name equals [name], or null.
         */
        fun fromArabicName(name: String): EgyptGovernorate? =
            entries.firstOrNull { it.arabicName == name }

        /**
         * Returns the governorate whose English name equals [name]
         * (case-insensitive), or null.
         */
        fun fromEnglishName(name: String): EgyptGovernorate? =
            entries.firstOrNull { it.englishName.equals(name, ignoreCase = true) }

        /**
         * Resolves a stored governorate name (Arabic or English) to an enum value,
         * falling back to [DEFAULT] when the name is null or unknown. This keeps
         * the app functional even if historical data holds a stale name.
         */
        fun fromNameOrDefault(name: String?): EgyptGovernorate {
            if (name.isNullOrBlank()) return DEFAULT
            return fromArabicName(name)
                ?: fromEnglishName(name)
                ?: DEFAULT
        }

        /**
         * Computes the Qibla direction for an arbitrary coordinate.
         *
         * Returns the initial great-circle bearing from
         * ([latitude], [longitude]) to the Kaaba, in degrees measured clockwise
         * from true north, normalised to the range [0, 360).
         *
         * This is the same method used to build [defaultQiblaAngleDegrees].
         */
        fun calculateQiblaAngleDegrees(latitude: Double, longitude: Double): Double {
            val phi1 = Math.toRadians(latitude)
            val phi2 = Math.toRadians(KAABA_LATITUDE)
            val deltaLambda = Math.toRadians(KAABA_LONGITUDE - longitude)

            val y = sin(deltaLambda) * cos(phi2)
            val x = cos(phi1) * sin(phi2) -
                sin(phi1) * cos(phi2) * cos(deltaLambda)

            val bearing = Math.toDegrees(atan2(y, x))
            return (bearing + 360.0) % 360.0
        }
    }
}
