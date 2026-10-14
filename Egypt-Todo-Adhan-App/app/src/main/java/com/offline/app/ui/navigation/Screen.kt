package com.offline.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/navigation/Screen.kt
 *
 * The three top-level destinations of the offline prayer-and-tasks app.
 *
 * Each destination carries everything the navigation layer needs:
 *  - [route]       the unique string used by the Navigation Compose graph,
 *  - [arabicLabel] the Arabic label shown in the bottom navigation bar
 *                  (the app is Arabic-first, so this is the primary label),
 *  - [icon]        the Material icon shown above the label.
 *
 * Declaring the destinations as a sealed hierarchy with [data object] members
 * makes `when` over a destination exhaustive and gives each one a stable
 * identity for equality checks against the current back-stack route.
 *
 * The class holds no state and performs no navigation itself; it is a pure
 * description consumed by `AppNavHost`. Nothing here touches the network, the
 * database or the alarm scheduler.
 */
sealed class Screen(
    val route: String,
    val arabicLabel: String,
    val icon: ImageVector
) {

    /**
     * Unified dashboard: the compact next-prayer banner with its live countdown
     * followed by today's pending priority tasks.
     */
    data object Dashboard : Screen(
        route = "dashboard",
        arabicLabel = "الرئيسية",
        icon = Icons.Filled.Home
    )

    /**
     * Full task manager: search, category filters, sorting, swipe-to-delete and
     * the create/edit sheet.
     */
    data object Tasks : Screen(
        route = "tasks",
        arabicLabel = "المهام",
        icon = Icons.Filled.Checklist
    )

    /**
     * Prayer schedule: the hero countdown card, the full six-row timetable and
     * the Egyptian governorate/offset/sound settings sheet.
     */
    data object PrayerSchedule : Screen(
        route = "prayer_schedule",
        arabicLabel = "المواقيت",
        icon = Icons.Filled.AccessTime
    )

    companion object {

        /**
         * The destinations shown, in order, in the bottom navigation bar. The
         * order here is the visual order from the right (first) to the left
         * (last) inside the right-to-left layout.
         */
        val bottomBarDestinations: List<Screen> = listOf(
            Dashboard,
            Tasks,
            PrayerSchedule
        )

        /** Every destination declared by this sealed hierarchy. */
        val allDestinations: List<Screen> = listOf(
            Dashboard,
            Tasks,
            PrayerSchedule
        )
    }
}
