@file:OptIn(ExperimentalMaterial3Api::class)

package com.offline.app.ui.screens.prayer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batoulapps.adhan.Prayer
import com.offline.app.ui.viewmodels.MinuteOffsets
import com.offline.app.ui.viewmodels.PrayerCountdown
import com.offline.app.ui.viewmodels.PrayerUiState
import com.offline.app.ui.viewmodels.PrayerViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/screens/prayer/PrayerScreen.kt
 *
 * Prayer-times dashboard.
 *
 * Structure (top to bottom):
 *  - a Material 3 top app bar with the title and two actions: "settings" (opens
 *    [PrayerSettingsBottomSheet]) and "resync" (re-arms the exact alarms),
 *  - a hero card for the upcoming prayer with a live, ticking countdown clock
 *    (the digits are driven by the ViewModel's one-second ticker; the two
 *    separators pulse so the clock reads as "alive"),
 *  - the full day's timetable: the five obligatory prayers plus Sunrise, in
 *    chronological order, each showing its exact time, with the current prayer
 *    highlighted in the primary container color and the next one in the
 *    secondary container color.
 *
 * Everything is offline: the coordinates, the calculation standard (Egyptian
 * General Authority of Survey) and the Shafi Asr madhab are fixed in
 * [com.offline.app.core.prayer.PrayerCalculationEngine]; the UI only renders
 * [PrayerUiState] and forwards user intents. The whole subtree is right-to-left
 * because the app is Arabic-first.
 *
 * @param viewModel the prayer ViewModel, created by the caller with
 *                  `viewModel(factory = PrayerViewModel.factory(repository))`.
 * @param modifier  layout modifier applied to the root.
 */
@Composable
fun PrayerScreen(
    viewModel: PrayerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var isSettingsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        val message = errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // Deriving "now" from the ViewModel's own tick keeps the rows perfectly in
    // phase with the countdown, without introducing a second timer.
    val nowMillis = uiState.nextPrayer
        ?.let { info -> info.triggerMillis - info.remainingMillis }
        ?: System.currentTimeMillis()

    // Hoisted into a local val so the compiler can smart-cast it inside the
    // branches below (a delegated property cannot be smart-cast).
    val timings = uiState.timings

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "مواقيت الصلاة",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(onClick = viewModel::syncAlarms) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "إعادة مزامنة التنبيهات"
                            )
                        }
                        IconButton(onClick = { isSettingsVisible = true }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "إعدادات المواقيت"
                            )
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                timings == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "تعذّر حساب مواقيت اليوم",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(onClick = viewModel::syncAlarms) {
                                Text(text = "إعادة المحاولة")
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 32.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "hero") {
                            UpcomingPrayerHero(uiState = uiState)
                        }

                        item(key = "timetable-header") {
                            SectionHeader(
                                title = "مواقيت اليوم",
                                subtitle = prayerFullDate(nowMillis),
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }

                        items(
                            items = timings.inChronologicalOrder(),
                            key = { entry -> entry.first.name }
                        ) { entry ->
                            val prayer = entry.first
                            val timeMillis = entry.second
                            TimetableRow(
                                prayer = prayer,
                                timeMillis = timeMillis,
                                state = prayerRowState(
                                    prayer = prayer,
                                    timeMillis = timeMillis,
                                    nowMillis = nowMillis,
                                    uiState = uiState
                                ),
                                offsetMinutes = offsetFor(prayer, uiState.offsets)
                            )
                        }

                        item(key = "footer") {
                            CalculationFootnote(
                                governorateName = uiState.governorate.displayName,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isSettingsVisible) {
            PrayerSettingsBottomSheet(
                uiState = uiState,
                onDismiss = { isSettingsVisible = false },
                onGovernorateSelected = viewModel::updateGovernorate,
                onOffsetsChanged = viewModel::updateMinuteOffsets,
                onPreAdhanMinutesSelected = viewModel::updatePreAdhanMinutes,
                onAdhanSoundToggled = viewModel::setAdhanSoundEnabled,
                onSyncAlarms = viewModel::syncAlarms,
                onCancelAlarms = viewModel::cancelAlarms
            )
        }
    }
}

// =====================================================================
// Hero card
// =====================================================================

@Composable
private fun UpcomingPrayerHero(
    uiState: PrayerUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "الصلاة القادمة",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                text = uiState.nextPrayerLabel.ifEmpty { "غير محددة" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            CountdownTiles(
                countdown = uiState.countdown,
                modifier = Modifier.fillMaxWidth()
            )

            val nextPrayerInfo = uiState.nextPrayer
            Text(
                text = if (nextPrayerInfo != null) {
                    "تبدأ في ${prayerClockTime(nextPrayerInfo.triggerMillis)}"
                } else {
                    "لم يتم تحديد الوقت بعد"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroInfoChip(text = uiState.governorate.arabicName)
                if (uiState.currentPrayerLabel.isNotEmpty()) {
                    HeroInfoChip(text = "الآن: ${uiState.currentPrayerLabel}")
                }
                if (uiState.preAdhanReminderMinutes > 0) {
                    HeroInfoChip(text = "تنبيه قبل ${uiState.preAdhanReminderMinutes} د")
                }
                if (!uiState.isAdhanSoundEnabled) {
                    HeroInfoChip(text = "الصوت مكتوم")
                }
            }
        }
    }
}

@Composable
private fun CountdownTiles(
    countdown: PrayerCountdown,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "countdownSeparator")
    val separatorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "countdownSeparatorAlpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CountdownUnit(value = countdown.hours, label = "ساعة")
        CountdownSeparator(alpha = separatorAlpha)
        CountdownUnit(value = countdown.minutes, label = "دقيقة")
        CountdownSeparator(alpha = separatorAlpha)
        CountdownUnit(value = countdown.seconds, label = "ثانية")
    }
}

@Composable
private fun CountdownUnit(
    value: Long,
    label: String
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "%02d".format(value),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFeatureSettings = "tnum"
                ),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun CountdownSeparator(alpha: Float) {
    Text(
        text = ":",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.alpha(alpha)
    )
}

@Composable
private fun HeroInfoChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// =====================================================================
// Timetable
// =====================================================================

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimetableRow(
    prayer: Prayer,
    timeMillis: Long,
    state: PrayerRowState,
    offsetMinutes: Int,
    modifier: Modifier = Modifier
) {
    val containerColor = when (state) {
        PrayerRowState.CURRENT -> MaterialTheme.colorScheme.primaryContainer
        PrayerRowState.NEXT -> MaterialTheme.colorScheme.secondaryContainer
        PrayerRowState.PAST -> MaterialTheme.colorScheme.surfaceContainerLow
        PrayerRowState.UPCOMING -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when (state) {
        PrayerRowState.CURRENT -> MaterialTheme.colorScheme.onPrimaryContainer
        PrayerRowState.NEXT -> MaterialTheme.colorScheme.onSecondaryContainer
        PrayerRowState.PAST -> MaterialTheme.colorScheme.onSurfaceVariant
        PrayerRowState.UPCOMING -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (state == PrayerRowState.CURRENT) 3.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrayerStatusIndicator(state = state)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = prayerArabicLabel(prayer),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (state == PrayerRowState.CURRENT) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        }
                    )
                    if (offsetMinutes != 0) {
                        OffsetTag(minutes = offsetMinutes)
                    }
                }

                val statusText = prayerRowStatusText(state)
                if (statusText.isNotEmpty()) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Text(
                text = prayerClockTime(timeMillis),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFeatureSettings = "tnum"
                ),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PrayerStatusIndicator(
    state: PrayerRowState,
    modifier: Modifier = Modifier
) {
    val indicatorColor = prayerStatusColor(state)

    Canvas(modifier = modifier.size(20.dp)) {
        val radius = size.minDimension / 2f
        when (state) {
            PrayerRowState.CURRENT -> {
                drawCircle(
                    color = indicatorColor.copy(alpha = 0.25f),
                    radius = radius
                )
                drawCircle(
                    color = indicatorColor,
                    radius = radius * 0.55f
                )
            }

            PrayerRowState.NEXT -> {
                drawCircle(
                    color = indicatorColor,
                    radius = radius * 0.60f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            PrayerRowState.UPCOMING -> {
                drawCircle(
                    color = indicatorColor,
                    radius = radius * 0.45f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            PrayerRowState.PAST -> {
                drawCircle(
                    color = indicatorColor,
                    radius = radius * 0.45f
                )
            }
        }
    }
}

@Composable
private fun OffsetTag(
    minutes: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = formatOffsetMinutes(minutes),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CalculationFootnote(
    governorateName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "طريقة الحساب: الهيئة المصرية العامة للمساحة",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "مذهب العصر: الشافعي",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "الموقع: $governorateName",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// =====================================================================
// Row state
// =====================================================================

private enum class PrayerRowState {
    CURRENT,
    NEXT,
    UPCOMING,
    PAST
}

private fun prayerRowState(
    prayer: Prayer,
    timeMillis: Long,
    nowMillis: Long,
    uiState: PrayerUiState
): PrayerRowState {
    val isCurrent = prayer == uiState.currentPrayer
    val isNext = uiState.nextPrayer?.prayer == prayer
    return when {
        isCurrent -> PrayerRowState.CURRENT
        isNext && timeMillis > nowMillis -> PrayerRowState.NEXT
        timeMillis <= nowMillis -> PrayerRowState.PAST
        else -> PrayerRowState.UPCOMING
    }
}

private fun prayerRowStatusText(state: PrayerRowState): String = when (state) {
    PrayerRowState.CURRENT -> "الوقت الحالي"
    PrayerRowState.NEXT -> "الصلاة القادمة"
    PrayerRowState.PAST -> "انتهى وقتها"
    PrayerRowState.UPCOMING -> ""
}

@Composable
private fun prayerStatusColor(state: PrayerRowState): Color = when (state) {
    PrayerRowState.CURRENT -> MaterialTheme.colorScheme.primary
    PrayerRowState.NEXT -> MaterialTheme.colorScheme.secondary
    PrayerRowState.UPCOMING -> MaterialTheme.colorScheme.outline
    PrayerRowState.PAST -> MaterialTheme.colorScheme.outlineVariant
}

// =====================================================================
// Shared presentation helpers (also used by the settings sheet)
// =====================================================================

private val PRAYER_LOCALE: Locale = Locale.forLanguageTag("ar-EG")

private val CLOCK_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", PRAYER_LOCALE)

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", PRAYER_LOCALE)

/** Arabic display label for a prayer, or "" for [Prayer.NONE]. */
fun prayerArabicLabel(prayer: Prayer): String = when (prayer) {
    Prayer.FAJR -> "الفجر"
    Prayer.SUNRISE -> "الشروق"
    Prayer.DHUHR -> "الظهر"
    Prayer.ASR -> "العصر"
    Prayer.MAGHRIB -> "المغرب"
    Prayer.ISHA -> "العشاء"
    Prayer.NONE -> ""
    else -> ""
}

/** Formats an epoch-millis prayer time as an Arabic 12-hour clock time. */
fun prayerClockTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalTime()
    .format(CLOCK_FORMATTER)

/** Formats an epoch-millis value as a full Arabic weekday/date string. */
fun prayerFullDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DATE_FORMATTER)

/** Signed minute offset for [prayer] inside [offsets]. Sunrise is never shifted. */
fun offsetFor(prayer: Prayer, offsets: MinuteOffsets): Int = when (prayer) {
    Prayer.FAJR -> offsets.fajr
    Prayer.DHUHR -> offsets.dhuhr
    Prayer.ASR -> offsets.asr
    Prayer.MAGHRIB -> offsets.maghrib
    Prayer.ISHA -> offsets.isha
    else -> 0
}

/** Formats a signed minute offset, e.g. `+2 د`, `-3 د`, `0 د`. */
fun formatOffsetMinutes(minutes: Int): String = when {
    minutes > 0 -> "+$minutes د"
    minutes < 0 -> "$minutes د"
    else -> "0 د"
}
