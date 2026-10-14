@file:OptIn(ExperimentalMaterial3Api::class)

package com.offline.app.ui.screens.todo

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offline.app.data.local.entities.Priority
import com.offline.app.data.local.entities.RecurringInterval
import com.offline.app.data.local.entities.TaskCategory
import com.offline.app.data.local.entities.TaskEntity
import com.offline.app.ui.theme.GoldAccent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Destination:
 * app/src/main/java/com/offline/app/ui/screens/todo/TaskItemCard.kt
 *
 * A single task row plus the presentation helpers shared by the whole tasks
 * feature (Arabic labels and date/time formatting).
 *
 * The card is intentionally self-contained:
 *  - an animated checkbox drawn with a Canvas (no icon swap, so the fill and the
 *    tick animate smoothly and there is no layout jump),
 *  - a color-coded priority bar painted on the leading edge with `drawBehind`, so
 *    it always spans the full card height without intrinsic measurement,
 *  - a full-width metadata strip with compact chips (priority, category, due
 *    date, reminder) and a gold recurring badge,
 *  - a quick-snooze menu and an overflow menu for edit/delete.
 *
 * It performs no business logic and no I/O; every interaction is delegated to the
 * callbacks, which the screen wires to `TodoViewModel`.
 */

/** Snooze durations offered on the card, in minutes. */
val DefaultSnoozeOptionsMinutes: List<Int> = listOf(5, 10, 15, 30, 60)

// =====================================================================
// Shared Arabic presentation helpers
// =====================================================================

private val ARABIC_LOCALE: Locale = Locale.forLanguageTag("ar-EG")

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", ARABIC_LOCALE)

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", ARABIC_LOCALE)

/** Arabic display label for a task category. */
fun TaskCategory.arabicLabel(): String = when (this) {
    TaskCategory.WORK -> "عمل"
    TaskCategory.PERSONAL -> "شخصي"
    TaskCategory.WORSHIP -> "عبادة"
    TaskCategory.ROUTINE -> "روتين"
}

/** Arabic display label for a task priority. */
fun Priority.arabicLabel(): String = when (this) {
    Priority.HIGH -> "عالية"
    Priority.MEDIUM -> "متوسطة"
    Priority.LOW -> "منخفضة"
}

/** Arabic display label for a recurrence rule. */
fun RecurringInterval.arabicLabel(): String = when (this) {
    RecurringInterval.NONE -> "لا يتكرر"
    RecurringInterval.DAILY -> "يومي"
    RecurringInterval.WEEKLY -> "أسبوعي"
    RecurringInterval.MONTHLY -> "شهري"
}

/** Formats an epoch-millis value as an Arabic day/month/year date. */
fun formatTaskDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DATE_FORMATTER)

/** Formats an epoch-millis value as an Arabic clock time (12-hour). */
fun formatTaskTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalTime()
    .format(TIME_FORMATTER)

/** Formats an epoch-millis value as "date • time". */
fun formatTaskDateTime(epochMillis: Long): String =
    "${formatTaskDate(epochMillis)} • ${formatTaskTime(epochMillis)}"

// =====================================================================
// Card
// =====================================================================

/**
 * Renders one task.
 *
 * @param task                 the task to display.
 * @param onToggleComplete     invoked when the checkbox is tapped.
 * @param onEdit               invoked when the card body or the edit menu item is tapped.
 * @param onDelete             invoked when the delete menu item is tapped.
 * @param onSnooze             invoked with a minute count when a snooze option is chosen.
 * @param modifier             layout modifier applied to the card surface.
 * @param snoozeOptionsMinutes the snooze durations offered in the quick menu.
 */
@Composable
fun TaskItemCard(
    task: TaskEntity,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSnooze: (Int) -> Unit,
    modifier: Modifier = Modifier,
    snoozeOptionsMinutes: List<Int> = DefaultSnoozeOptionsMinutes
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var snoozeMenuExpanded by remember { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(task.id) {
        appeared = true
    }

    val appearance by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "taskCardAppearance"
    )

    val containerColor = if (task.isCompleted) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val priorityBarColor = priorityColor(task.priority)
    val titleColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (task.isCompleted) 0.6f else 1f
    )

    Surface(
        onClick = onEdit,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appearance
                val scale = 0.96f + (0.04f * appearance)
                scaleX = scale
                scaleY = scale
            },
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        color = priorityBarColor,
                        topLeft = Offset.Zero,
                        size = Size(width = 4.dp.toPx(), height = size.height)
                    )
                }
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                AnimatedCheckbox(
                    checked = task.isCompleted,
                    onClick = onToggleComplete,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                        textDecoration = if (task.isCompleted) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!task.notes.isNullOrBlank()) {
                        Text(
                            text = task.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box {
                    IconButton(onClick = { snoozeMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "تأجيل التنبيه",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = snoozeMenuExpanded,
                        onDismissRequest = { snoozeMenuExpanded = false }
                    ) {
                        snoozeOptionsMinutes.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text("تأجيل $minutes دقيقة") },
                                onClick = {
                                    snoozeMenuExpanded = false
                                    onSnooze(minutes)
                                }
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { moreMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "خيارات المهمة",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { moreMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("تعديل") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                moreMenuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                moreMenuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            TaskMetaRow(
                task = task,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 38.dp)
            )
        }
    }
}

// =====================================================================
// Private building blocks
// =====================================================================

/**
 * Custom animated checkbox.
 *
 * A single `animateFloatAsState` progress value drives both the growing fill
 * circle and the fading tick, producing a continuous, jump-free animation.
 */
@Composable
private fun AnimatedCheckbox(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "checkboxProgress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val tickColor = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            val radius = size.minDimension / 2f

            drawCircle(
                color = outlineColor,
                radius = radius,
                style = Stroke(width = 2.dp.toPx())
            )

            if (progress > 0f) {
                drawCircle(
                    color = primaryColor,
                    radius = radius * progress
                )
            }

            if (progress > 0.35f) {
                val tickAlpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                val width = size.width
                val height = size.height
                val tickPath = Path().apply {
                    moveTo(width * 0.26f, height * 0.52f)
                    lineTo(width * 0.44f, height * 0.70f)
                    lineTo(width * 0.76f, height * 0.32f)
                }
                drawPath(
                    path = tickPath,
                    color = tickColor.copy(alpha = tickAlpha),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/** Full-width strip of compact metadata chips for a task. */
@Composable
private fun TaskMetaRow(
    task: TaskEntity,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetaChip(
            icon = null,
            text = task.priority.arabicLabel(),
            containerColor = priorityColor(task.priority).copy(alpha = 0.16f),
            contentColor = priorityColor(task.priority)
        )

        MetaChip(
            icon = null,
            text = task.category.arabicLabel(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )

        val dueDate = task.dueDate
        if (dueDate != null) {
            val isOverdue = dueDate < now && !task.isCompleted
            MetaChip(
                icon = Icons.Filled.DateRange,
                text = formatTaskDate(dueDate),
                containerColor = if (isOverdue) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (isOverdue) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        val reminderTime = task.reminderTime
        if (reminderTime != null) {
            MetaChip(
                icon = Icons.Filled.Notifications,
                text = formatTaskTime(reminderTime),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (task.isRecurring != RecurringInterval.NONE) {
            MetaChip(
                icon = Icons.Filled.Repeat,
                text = task.isRecurring.arabicLabel(),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/** Small rounded chip with an optional icon. */
@Composable
private fun MetaChip(
    icon: ImageVector?,
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Color associated with a task priority. */
@Composable
private fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.HIGH -> MaterialTheme.colorScheme.error
    Priority.MEDIUM -> GoldAccent
    Priority.LOW -> MaterialTheme.colorScheme.outline
}
