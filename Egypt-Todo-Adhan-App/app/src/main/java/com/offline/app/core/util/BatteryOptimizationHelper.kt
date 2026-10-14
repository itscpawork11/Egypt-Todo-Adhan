package com.offline.app.core.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.offline.app.core.alarm.AlarmScheduler
import java.util.Locale

/**
 * Destination:
 * app/src/main/java/com/offline/app/core/util/BatteryOptimizationHelper.kt
 *
 * Battery-optimization and OEM background-restriction helper for the 100% offline
 * prayer-times app.
 *
 * Why this exists
 * ---------------
 * The Adhan is delivered by an exact [android.app.AlarmManager] alarm. On many
 * Android devices the vendor's power management ("battery saver", "adaptive
 * battery", "MIUI/HyperOS optimizer", "ColorOS", One UI, EMUI ...) will either
 *   - defer or drop exact alarms,
 *   - freeze the app in the background, or
 *   - kill the app after it is swiped away from Recents,
 * which makes an otherwise perfectly scheduled Adhan silently not fire.
 *
 * This file provides three things:
 *  1. [BatteryOptimizationHelper] - a stateless object that *queries* the current
 *     state and *builds/launches* the correct Settings intents, including
 *     manufacturer-specific "autostart" screens where they exist.
 *  2. [BatteryOptimizationStatus] + [rememberBatteryOptimizationStatus] - a small
 *     Compose-friendly snapshot that re-reads itself every time the host returns
 *     to the foreground (so it updates after the user changes a system setting).
 *  3. [BatteryOptimizationDialog] - a stateless Material 3 [AlertDialog] that
 *     explains, in Arabic, why background permissions are needed for the Adhan
 *     and offers a direct action for each missing capability.
 *
 * The file performs no I/O of its own and declares no dependency on the network:
 * everything is either a local system query or a decision between Intents.
 */

// =====================================================================
// 1. Queries, intent builders and launch helpers
// =====================================================================

/**
 * Stateless helper around the Android battery-optimization, exact-alarm and
 * vendor-autostart settings screens.
 */
object BatteryOptimizationHelper {

    // -----------------------------------------------------------------
    // Capability queries
    // -----------------------------------------------------------------

    /**
     * Whether the app is currently exempt from battery optimization.
     *
     * `PowerManager.isIgnoringBatteryOptimizations` has existed since API 23,
     * which is below this app's `minSdk`, so no version gate is required.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Whether the app may schedule exact alarms. Delegates to
     * [AlarmScheduler.canScheduleExactAlarms] so the capability is defined in
     * exactly one place in the codebase.
     */
    fun canScheduleExactAlarms(context: Context): Boolean =
        AlarmScheduler.canScheduleExactAlarms(context)

    /**
     * Whether the app is fully configured for reliable background Adhan delivery:
     * exempt from battery optimization AND allowed to schedule exact alarms.
     */
    fun isFullyConfigured(context: Context): Boolean =
        isIgnoringBatteryOptimizations(context) && canScheduleExactAlarms(context)

    /** The lower-cased [Build.MANUFACTURER] string used to pick OEM screens. */
    fun manufacturer(): String = Build.MANUFACTURER.lowercase(Locale.ROOT)

    /**
     * Whether this device exposes a manufacturer-specific "autostart" (
     * background-launch) screen we know how to open.
     */
    fun hasAutoStartSettings(context: Context): Boolean =
        resolveAutoStartIntent(context) != null

    // -----------------------------------------------------------------
    // Intent builders
    // -----------------------------------------------------------------

    /**
     * Builds the intent that opens the system dialog asking the user to exempt
     * this app from battery optimization.
     *
     * Requires `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in the
     * manifest; without it the system raises a [SecurityException].
     */
    @SuppressLint("BatteryLife")
    fun buildIgnoreBatteryOptimizationsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Builds the intent that opens the *list* of battery-optimized apps. Used as
     * a fallback when the direct request dialog is unavailable or was disabled by
     * the user.
     */
    fun buildBatteryOptimizationListIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Builds the intent that opens the Android 12+ exact-alarm permission screen.
     * Delegates to [AlarmScheduler.exactAlarmPermissionSettingsIntent], which
     * returns `null` below API 31.
     */
    fun buildExactAlarmPermissionIntent(context: Context): Intent? =
        AlarmScheduler.exactAlarmPermissionSettingsIntent(context)

    /**
     * Builds the intent that opens this app's system "App info" screen, the
     * universal fallback for every permission and restriction.
     */
    fun buildAppDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Builds the manufacturer-specific autostart intent, or `null` when this
     * device has no known autostart screen.
     */
    fun buildAutoStartIntent(context: Context): Intent? = resolveAutoStartIntent(context)

    // -----------------------------------------------------------------
    // Launch helpers
    // -----------------------------------------------------------------

    /** Opens the direct "ignore battery optimizations" request dialog. */
    fun openIgnoreBatteryOptimizations(context: Context): Boolean =
        startActivitySafely(context, buildIgnoreBatteryOptimizationsIntent(context))

    /** Opens the battery-optimization app list screen. */
    fun openBatteryOptimizationList(context: Context): Boolean =
        startActivitySafely(context, buildBatteryOptimizationListIntent())

    /**
     * Opens the exact-alarm permission screen. Returns false on devices below
     * Android 12, where the permission does not exist.
     */
    fun openExactAlarmSettings(context: Context): Boolean {
        val intent = buildExactAlarmPermissionIntent(context) ?: return false
        return startActivitySafely(context, intent)
    }

    /**
     * Opens the manufacturer-specific autostart screen when one exists, otherwise
     * falls back to this app's system "App info" screen so the user always has a
     * next step.
     */
    fun openAutoStartSettings(context: Context): Boolean {
        val autoStartIntent = resolveAutoStartIntent(context)
        if (autoStartIntent != null && startActivitySafely(context, autoStartIntent)) {
            return true
        }
        return openAppDetails(context)
    }

    /** Opens this app's system "App info" screen. */
    fun openAppDetails(context: Context): Boolean =
        startActivitySafely(context, buildAppDetailsIntent(context))

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    /**
     * Resolves the first existing autostart activity for this manufacturer.
     *
     * Resolution needs package visibility on Android 11+ (API 30+), which is why
     * the manifest declares a `<queries>` block listing the OEM packages.
     */
    private fun resolveAutoStartIntent(context: Context): Intent? {
        val packageManager = context.packageManager
        for (componentName in autoStartComponentCandidates()) {
            val intent = Intent().apply {
                component = componentName
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(packageManager) != null) {
                return intent
            }
        }
        return null
    }

    /**
     * Launches [intent], converting the two expected failures into `false`
     * instead of a crash: the component may be absent
     * ([ActivityNotFoundException]) or blocked by the platform
     * ([SecurityException]).
     */
    private fun startActivitySafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (activityNotFound: ActivityNotFoundException) {
            false
        } catch (security: SecurityException) {
            false
        }
    }

    /**
     * The known autostart activities for the current manufacturer, most reliable
     * first. Empty for stock Android / Pixel and for any vendor we do not have a
     * dedicated screen for.
     */
    private fun autoStartComponentCandidates(): List<ComponentName> {
        val manufacturer = manufacturer()
        return when {
            manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("poco") -> listOf(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ),
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings"
                )
            )

            manufacturer.contains("samsung") -> listOf(
                ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                ),
                ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            )

            manufacturer.contains("oppo") ||
                manufacturer.contains("realme") ||
                manufacturer.contains("oneplus") -> listOf(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ),
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                ),
                ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                ),
                ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            )

            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ),
                ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            )

            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            )

            manufacturer.contains("asus") -> listOf(
                ComponentName(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.powersaver.PowerSaverSettings"
                )
            )

            manufacturer.contains("letv") || manufacturer.contains("leeco") -> listOf(
                ComponentName(
                    "com.letv.android.letvsafe",
                    "com.letv.android.letvsafe.AutobootManageActivity"
                )
            )

            manufacturer.contains("tecno") ||
                manufacturer.contains("infinix") ||
                manufacturer.contains("itel") -> listOf(
                ComponentName(
                    "com.transsion.phonemanager",
                    "com.transsion.phonemanager.activity.AutoStartManagementActivity"
                ),
                ComponentName(
                    "com.transsion.phonemaster",
                    "com.transsion.phonemaster.activity.AutoStartManagementActivity"
                )
            )

            else -> emptyList()
        }
    }
}

// =====================================================================
// 2. Compose-friendly state snapshot
// =====================================================================

/**
 * Immutable snapshot of the background-reliability capabilities of the device.
 *
 * @param ignoringBatteryOptimizations whether the app is exempt from battery
 *        optimization.
 * @param exactAlarmsAllowed whether the app may schedule exact alarms.
 * @param autoStartSettingsAvailable whether a manufacturer-specific autostart
 *        screen can be opened on this device.
 */
data class BatteryOptimizationStatus(
    val ignoringBatteryOptimizations: Boolean,
    val exactAlarmsAllowed: Boolean,
    val autoStartSettingsAvailable: Boolean
) {

    /**
     * Whether the device-side capabilities needed for a reliably scheduled Adhan
     * are all satisfied. Notification permission is tracked separately by the
     * caller because it lives in the runtime-permission system, not in Settings.
     */
    val isFullyConfigured: Boolean
        get() = ignoringBatteryOptimizations && exactAlarmsAllowed
}

/**
 * Remembers a [BatteryOptimizationStatus] that re-reads itself every time the
 * host lifecycle owner reaches [Lifecycle.Event.ON_RESUME].
 *
 * Re-reading on resume is what makes the dialog self-correcting: the user taps a
 * button, leaves to a Settings screen, comes back, and the rows update without
 * any manual refresh.
 */
@Composable
fun rememberBatteryOptimizationStatus(): BatteryOptimizationStatus {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var status by remember(context) { mutableStateOf(readBatteryOptimizationStatus(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_RESUME) {
                    status = readBatteryOptimizationStatus(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return status
}

/** Reads the three capabilities in one shot. */
private fun readBatteryOptimizationStatus(context: Context): BatteryOptimizationStatus =
    BatteryOptimizationStatus(
        ignoringBatteryOptimizations =
            BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context),
        exactAlarmsAllowed = BatteryOptimizationHelper.canScheduleExactAlarms(context),
        autoStartSettingsAvailable = BatteryOptimizationHelper.hasAutoStartSettings(context)
    )

// =====================================================================
// 3. Compose dialog
// =====================================================================

/**
 * Arabic-first explanation dialog for the background permissions the Adhan needs.
 *
 * The composable is completely stateless: it renders the supplied [status] and
 * [notificationsEnabled] and reports every user choice through a callback, so the
 * caller owns the "show once" policy and the lifecycle of the permission request.
 *
 * @param status the current battery/exact-alarm capability snapshot.
 * @param notificationsEnabled whether the user currently allows notifications.
 * @param onRequestNotificationPermission asks the host to launch the
 *        `POST_NOTIFICATIONS` runtime-permission request.
 * @param onRequestIgnoreBatteryOptimizations opens the battery-optimization
 *        exemption dialog.
 * @param onRequestExactAlarmPermission opens the Android 12+ exact-alarm screen.
 * @param onOpenAutoStartSettings opens the manufacturer autostart screen.
 * @param onOpenAppSettings opens this app's system "App info" screen.
 * @param onDismiss closes the dialog without further action.
 */
@Composable
fun BatteryOptimizationDialog(
    status: BatteryOptimizationStatus,
    notificationsEnabled: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onOpenAutoStartSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val primaryLabel: String
    val primaryAction: () -> Unit
    if (!notificationsEnabled) {
        primaryLabel = "السماح بالإشعارات"
        primaryAction = onRequestNotificationPermission
    } else if (!status.ignoringBatteryOptimizations) {
        primaryLabel = "السماح بالعمل في الخلفية"
        primaryAction = onRequestIgnoreBatteryOptimizations
    } else if (!status.exactAlarmsAllowed) {
        primaryLabel = "السماح بالتنبيهات الدقيقة"
        primaryAction = onRequestExactAlarmPermission
    } else {
        primaryLabel = "حسنًا"
        primaryAction = onDismiss
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Filled.BatteryAlert,
                    contentDescription = null
                )
            },
            title = {
                Text(text = "رفع الأذان في وقته")
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "ليُرفع الأذان في وقته تمامًا حتى عندما يكون الهاتف في وضع السكون، " +
                            "يحتاج التطبيق إلى الصلاحيات التالية. يعمل التطبيق بالكامل دون إنترنت، " +
                            "وكل ما يلي يخص استقرار التنبيه على جهازك فقط.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    BatteryRequirementRow(
                        satisfied = notificationsEnabled,
                        title = "إشعارات الأذان",
                        description = "تسمح بإظهار تنبيه الأذان وتنبيهات المهام في الوقت المحدد.",
                        actionLabel = "السماح",
                        onAction = onRequestNotificationPermission
                    )

                    BatteryRequirementRow(
                        satisfied = status.ignoringBatteryOptimizations,
                        title = "استثناء من توفير البطارية",
                        description = "يمنع النظام من تجميد التطبيق وتأخير الأذان أو إسقاطه.",
                        actionLabel = "السماح",
                        onAction = onRequestIgnoreBatteryOptimizations
                    )

                    BatteryRequirementRow(
                        satisfied = status.exactAlarmsAllowed,
                        title = "التنبيهات الدقيقة",
                        description = "تضمن ضبط المنبه على وقت الصلاة بالثانية حتى في وضع Doze.",
                        actionLabel = "السماح",
                        onAction = onRequestExactAlarmPermission
                    )

                    if (status.autoStartSettingsAvailable) {
                        BatteryRequirementRow(
                            satisfied = null,
                            title = "التشغيل التلقائي",
                            description = "مطلوب على أجهزة مثل Xiaomi وOppo وSamsung " +
                                "ليستمر التنبيه بعد إعادة تشغيل الجهاز.",
                            actionLabel = "فتح",
                            onAction = onOpenAutoStartSettings
                        )
                    }

                    BatteryRequirementRow(
                        satisfied = null,
                        title = "إعدادات التطبيق",
                        description = "يمكنك مراجعة جميع الصلاحيات من هنا إن لم تُجدِ الخطوات السابقة.",
                        actionLabel = "فتح",
                        onAction = onOpenAppSettings
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = primaryAction) {
                    Text(text = primaryLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = "لاحقًا")
                }
            }
        )
    }
}

/**
 * One requirement row inside [BatteryOptimizationDialog].
 *
 * @param satisfied `true` renders a satisfied check with no action, `false`
 *        renders a warning with an action button, and `null` renders a neutral
 *        settings row with an action button (for screens whose state cannot be
 *        queried, such as the vendor autostart screen).
 */
@Composable
private fun BatteryRequirementRow(
    satisfied: Boolean?,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = when (satisfied) {
                true -> Icons.Filled.CheckCircle
                false -> Icons.Filled.Warning
                null -> Icons.Filled.Tune
            },
            contentDescription = null,
            tint = when (satisfied) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (satisfied != true) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}
