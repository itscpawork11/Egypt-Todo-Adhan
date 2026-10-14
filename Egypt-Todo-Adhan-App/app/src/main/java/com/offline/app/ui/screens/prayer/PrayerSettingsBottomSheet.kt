@file:OptIn(ExperimentalMaterial3Api::class)

package com.offline.app.ui.screens.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batoulapps.adhan.Prayer
import com.offline.app.core.prayer.EgyptGovernorate
import com.offline.app.data.local.entities.PrayerConfigEntity
import com.offline.app.ui.viewmodels.MinuteOffsets
import com.offline.app.ui.viewmodels.PrayerUiState
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/screens/prayer/PrayerSettingsBottomSheet.kt
 *
 * Modal bottom sheet with the prayer-time settings.
 *
 * Sections:
 *  - Governorate selector: a dropdown over all 27 Egyptian governorates, each
 *    labelled with its Arabic and English name. Selection is applied immediately
 *    so the timetable behind the sheet recalculates at once.
 *  - Manual per-prayer offsets: one slider per obligatory prayer, from -10 to
 *    +10 minutes in one-minute steps. Values are applied when the user releases
 *    the slider, so dragging does not hammer the database or the alarm scheduler.
 *  - Pre-Adhan reminder lead time: Off, 5, 10 or 15 minutes (validated against
 *    [PrayerConfigEntity.ALLOWED_REMINDER_MINUTES]).
 *  - Adhan sound toggle, plus explicit "resync" and "cancel all" alarm actions.
 *
 * The composable is stateless with respect to the data layer: it renders
 * [uiState] and reports every change through a callback. No I/O happens here.
 *
 * @param uiState                  the current prayer state to edit.
 * @param onDismiss                invoked when the sheet is dismissed.
 * @param onGovernorateSelected    invoked with the newly chosen governorate.
 * @param onOffsetsChanged         invoked with all five offsets when a slider is released.
 * @param onPreAdhanMinutesSelected invoked with 0, 5, 10 or 15.
 * @param onAdhanSoundToggled      invoked with the new sound-enabled value.
 * @param onSyncAlarms             invoked when the user asks to re-arm the alarms.
 * @param onCancelAlarms           invoked when the user asks to cancel every alarm.
 */
@Composable
fun PrayerSettingsBottomSheet(
    uiState: PrayerUiState,
    onDismiss: () -> Unit,
    onGovernorateSelected: (EgyptGovernorate) -> Unit,
    onOffsetsChanged: (MinuteOffsets) -> Unit,
    onPreAdhanMinutesSelected: (Int) -> Unit,
    onAdhanSoundToggled: (Boolean) -> Unit,
    onSyncAlarms: () -> Unit,
    onCancelAlarms: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isGovernorateMenuExpanded by remember { mutableStateOf(false) }

    // Local draft of the offsets so a drag does not write on every pixel; it is
    // initialised once per sheet opening from the persisted configuration.
    var draftOffsets by remember { mutableStateOf(uiState.offsets) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "إعدادات المواقيت",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ---------------------------------------------------------------
            // Governorate
            // ---------------------------------------------------------------
            FieldLabel(text = "المحافظة")

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { isGovernorateMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.governorate.displayName,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null
                    )
                }

                DropdownMenu(
                    expanded = isGovernorateMenuExpanded,
                    onDismissRequest = { isGovernorateMenuExpanded = false }
                ) {
                    EgyptGovernorate.entries.forEach { governorate ->
                        DropdownMenuItem(
                            text = { Text(text = governorate.displayName) },
                            trailingIcon = {
                                if (governorate == uiState.governorate) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = {
                                isGovernorateMenuExpanded = false
                                if (governorate != uiState.governorate) {
                                    onGovernorateSelected(governorate)
                                }
                            }
                        )
                    }
                }
            }

            CoordinatesLine(governorate = uiState.governorate)

            HorizontalDivider()

            // ---------------------------------------------------------------
            // Manual offsets
            // ---------------------------------------------------------------
            FieldLabel(text = "تعديل المواقيت يدويًا (بالدقائق)")

            OffsetSlider(
                label = prayerArabicLabel(Prayer.FAJR),
                valueMinutes = draftOffsets.fajr,
                onValueChange = { value -> draftOffsets = draftOffsets.copy(fajr = value) },
                onValueChangeFinished = { onOffsetsChanged(draftOffsets) }
            )
            OffsetSlider(
                label = prayerArabicLabel(Prayer.DHUHR),
                valueMinutes = draftOffsets.dhuhr,
                onValueChange = { value -> draftOffsets = draftOffsets.copy(dhuhr = value) },
                onValueChangeFinished = { onOffsetsChanged(draftOffsets) }
            )
            OffsetSlider(
                label = prayerArabicLabel(Prayer.ASR),
                valueMinutes = draftOffsets.asr,
                onValueChange = { value -> draftOffsets = draftOffsets.copy(asr = value) },
                onValueChangeFinished = { onOffsetsChanged(draftOffsets) }
            )
            OffsetSlider(
                label = prayerArabicLabel(Prayer.MAGHRIB),
                valueMinutes = draftOffsets.maghrib,
                onValueChange = { value -> draftOffsets = draftOffsets.copy(maghrib = value) },
                onValueChangeFinished = { onOffsetsChanged(draftOffsets) }
            )
            OffsetSlider(
                label = prayerArabicLabel(Prayer.ISHA),
                valueMinutes = draftOffsets.isha,
                onValueChange = { value -> draftOffsets = draftOffsets.copy(isha = value) },
                onValueChangeFinished = { onOffsetsChanged(draftOffsets) }
            )

            OutlinedButton(
                onClick = {
                    draftOffsets = MinuteOffsets.ZERO
                    onOffsetsChanged(MinuteOffsets.ZERO)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "تصفير جميع الإزاحات")
            }

            HorizontalDivider()

            // ---------------------------------------------------------------
            // Pre-Adhan reminder
            // ---------------------------------------------------------------
            FieldLabel(text = "التنبيه قبل الأذان")

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = PrayerConfigEntity.ALLOWED_REMINDER_MINUTES.sorted(),
                    key = { minutes -> minutes.toString() }
                ) { minutes ->
                    FilterChip(
                        selected = uiState.preAdhanReminderMinutes == minutes,
                        onClick = { onPreAdhanMinutesSelected(minutes) },
                        label = { Text(text = reminderLabel(minutes)) }
                    )
                }
            }

            HorizontalDivider()

            // ---------------------------------------------------------------
            // Adhan sound
            // ---------------------------------------------------------------
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "تشغيل صوت الأذان",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (uiState.isAdhanSoundEnabled) {
                                "سيُشغَّل الصوت عند دخول وقت الصلاة"
                            } else {
                                "لن يُشغَّل الصوت، وسيظهر إشعار فقط"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = uiState.isAdhanSoundEnabled,
                        onCheckedChange = onAdhanSoundToggled
                    )
                }
            }

            HorizontalDivider()

            // ---------------------------------------------------------------
            // Alarm maintenance
            // ---------------------------------------------------------------
            FieldLabel(text = "التنبيهات")

            OutlinedButton(
                onClick = onSyncAlarms,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "إعادة مزامنة جميع التنبيهات")
            }

            OutlinedButton(
                onClick = onCancelAlarms,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "إلغاء جميع التنبيهات")
            }

            HorizontalDivider()

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(text = "تم")
            }
        }
    }
}

// =====================================================================
// Private building blocks
// =====================================================================

@Composable
private fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CoordinatesLine(
    governorate: EgyptGovernorate,
    modifier: Modifier = Modifier
) {
    val latitude = "%.4f".format(Locale.US, governorate.latitude)
    val longitude = "%.4f".format(Locale.US, governorate.longitude)
    val qibla = "%.1f".format(Locale.US, governorate.qiblaFromNorthDegrees)

    Text(
        modifier = modifier.fillMaxWidth(),
        text = "الإحداثيات: $latitude, $longitude • اتجاه القبلة: $qibla° • المسافة للكعبة: " +
            "${governorate.distanceToKaabaKm} كم",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OffsetSlider(
    label: String,
    valueMinutes: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatOffsetMinutes(valueMinutes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = valueMinutes.toFloat(),
            onValueChange = { rawValue -> onValueChange(rawValue.roundToInt()) },
            valueRange = OFFSET_RANGE,
            steps = OFFSET_STEPS,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

/** Arabic label for a pre-Adhan reminder lead time in minutes. */
private fun reminderLabel(minutes: Int): String = when (minutes) {
    0 -> "متوقف"
    5 -> "5 دقائق"
    10 -> "10 دقائق"
    15 -> "15 دقيقة"
    else -> "$minutes دقيقة"
}

private val OFFSET_RANGE: ClosedFloatingPointRange<Float> = -10f..10f

/** 21 integer stops (from -10 to +10) need 19 in-between steps. */
private const val OFFSET_STEPS: Int = 19
