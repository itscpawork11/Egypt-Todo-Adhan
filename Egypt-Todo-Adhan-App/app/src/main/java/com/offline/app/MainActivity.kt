package com.offline.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.offline.app.core.notification.NotificationHelper
import com.offline.app.core.util.BatteryOptimizationDialog
import com.offline.app.core.util.BatteryOptimizationHelper
import com.offline.app.core.util.rememberBatteryOptimizationStatus
import com.offline.app.data.local.AppDatabase
import com.offline.app.data.repository.DefaultPrayerRepository
import com.offline.app.data.repository.DefaultTaskRepository
import com.offline.app.data.repository.PrayerRepository
import com.offline.app.data.repository.TaskRepository
import com.offline.app.ui.navigation.AppNavHost
import com.offline.app.ui.theme.OfflineAppTheme

/**
 * Destination:
 * app/src/main/java/com/offline/app/MainActivity.kt
 *
 * The single Activity of the 100% offline prayer-times app, and the assembly
 * point that connects every earlier piece.
 *
 * Responsibilities
 * ----------------
 *  1. Host Compose with [setContent], wrapped in the Material 3
 *     [OfflineAppTheme] and in a forced right-to-left layout direction so the
 *     Arabic UI (bottom navigation bar, dialogs, sheets) reads correctly on any
 *     system locale.
 *  2. Instantiate the process-wide singletons: the Room database
 *     ([AppDatabase.getInstance]), the notification channels
 *     ([NotificationHelper.ensureChannels]) and the two repositories
 *     ([DefaultTaskRepository] / [DefaultPrayerRepository]). The repositories are
 *     created once here and injected into [AppNavHost], so every destination
 *     shares the same data layer instances.
 *  3. Check and request the Android 13+ (`POST_NOTIFICATIONS`) runtime
 *     permission.
 *  4. Render the root layout hosting [AppNavHost] and, on first launch, the
 *     [BatteryOptimizationDialog] when the device is not yet configured for
 *     reliable background Adhan delivery.
 *
 * Everything is local: no network permission is declared anywhere in the
 * manifest, and this Activity performs no remote calls.
 */
class MainActivity : ComponentActivity() {

    /**
     * Task repository built from the process-wide database. Created lazily so it
     * is only constructed after [onCreate] has run and `applicationContext` is
     * fully available.
     */
    private val taskRepository: TaskRepository by lazy {
        DefaultTaskRepository.create(applicationContext)
    }

    /** Prayer-configuration repository built from the process-wide database. */
    private val prayerRepository: PrayerRepository by lazy {
        DefaultPrayerRepository.create(applicationContext)
    }

    /** Whether the user currently allows notifications for this app. */
    private var notificationsGranted by mutableStateOf(false)

    /**
     * Whether the notification-permission flow has finished (granted, denied, or
     * not applicable on API < 33). Used to sequence the onboarding dialogs so the
     * battery dialog is never stacked on top of the system permission dialog.
     */
    private var notificationFlowFinished by mutableStateOf(false)

    /**
     * Registers the `POST_NOTIFICATIONS` request. Registered as a field so it is
     * ready before the Activity starts, which is when the system requires
     * `registerForActivityResult` to be called.
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        notificationFlowFinished = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Process-wide singletons. Building the database object does not open the
        // file: schema creation and the seeding callback run lazily on first use.
        AppDatabase.getInstance(applicationContext)

        // Notification channels must exist before any reminder is posted.
        NotificationHelper.ensureChannels(applicationContext)

        notificationsGranted = NotificationHelper.areNotificationsEnabled(applicationContext)
        notificationFlowFinished =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationsGranted

        setContent {
            OfflineAppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppRootContent(
                        taskRepository = taskRepository,
                        prayerRepository = prayerRepository,
                        notificationsGranted = notificationsGranted,
                        notificationFlowFinished = notificationFlowFinished,
                        onRequestNotificationPermission = { requestNotificationPermission() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read after the user may have changed the permission in Settings.
        notificationsGranted = NotificationHelper.areNotificationsEnabled(applicationContext)
    }

    /**
     * Requests `POST_NOTIFICATIONS` when needed.
     *
     * On API < 33 there is nothing to request. On API 33+ the request is only
     * launched when the permission is still missing, so an already-granted app
     * never shows the system dialog.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted = true
            notificationFlowFinished = true
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        notificationsGranted = granted

        if (granted) {
            notificationFlowFinished = true
        } else {
            notificationFlowFinished = false
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Root composable: renders [AppNavHost] and the one-shot background-reliability
 * onboarding dialog.
 *
 * The two onboarding steps are sequenced deliberately:
 *  - the notification-permission request runs first (only on API 33+),
 *  - the battery/exact-alarm dialog is shown only once that flow has finished,
 *    so the user never has to dismiss two dialogs at the same time.
 *
 * @param taskRepository the shared task repository injected into the graph.
 * @param prayerRepository the shared prayer repository injected into the graph.
 * @param notificationsGranted current notification-permission state.
 * @param notificationFlowFinished whether the notification-permission flow ended.
 * @param onRequestNotificationPermission launches the runtime-permission request.
 */
@Composable
private fun AppRootContent(
    taskRepository: TaskRepository,
    prayerRepository: PrayerRepository,
    notificationsGranted: Boolean,
    notificationFlowFinished: Boolean,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val batteryStatus = rememberBatteryOptimizationStatus()

    var showBatteryDialog by rememberSaveable { mutableStateOf(false) }
    var hasAutoShownBatteryDialog by rememberSaveable { mutableStateOf(false) }
    var notificationPromptAttempted by rememberSaveable { mutableStateOf(false) }

    // Ask for POST_NOTIFICATIONS exactly once, on API 33+, when still missing.
    LaunchedEffect(Unit) {
        if (!notificationPromptAttempted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationsGranted
        ) {
            notificationPromptAttempted = true
            onRequestNotificationPermission()
        }
    }

    // Show the background-reliability dialog once, after the notification flow,
    // and auto-dismiss it as soon as the device becomes fully configured.
    LaunchedEffect(batteryStatus.isFullyConfigured, notificationFlowFinished) {
        if (!notificationFlowFinished) {
            return@LaunchedEffect
        }
        if (batteryStatus.isFullyConfigured) {
            showBatteryDialog = false
        } else if (!hasAutoShownBatteryDialog) {
            hasAutoShownBatteryDialog = true
            showBatteryDialog = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AppNavHost(
            taskRepository = taskRepository,
            prayerRepository = prayerRepository
        )
    }

    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            status = batteryStatus,
            notificationsEnabled = notificationsGranted,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestIgnoreBatteryOptimizations = {
                BatteryOptimizationHelper.openIgnoreBatteryOptimizations(context)
            },
            onRequestExactAlarmPermission = {
                BatteryOptimizationHelper.openExactAlarmSettings(context)
            },
            onOpenAutoStartSettings = {
                BatteryOptimizationHelper.openAutoStartSettings(context)
            },
            onOpenAppSettings = {
                BatteryOptimizationHelper.openAppDetails(context)
            },
            onDismiss = { showBatteryDialog = false }
        )
    }
}
