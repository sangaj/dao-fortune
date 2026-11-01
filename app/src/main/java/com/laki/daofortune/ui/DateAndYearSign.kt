package com.laki.daofortune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.laki.daofortune.core.FavourableDay
import com.laki.daofortune.core.YearSign
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val longDateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
private val shortDateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** Days from today to [date], phrased for a reader. */
private fun relativeLabel(date: LocalDate, today: LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days == -1L -> "Yesterday"
        days > 0 -> "in $days days"
        else -> "${-days} days ago"
    }
}

/**
 * The day the plate is about.
 *
 * A reader can step day by day or open the calendar. Any day other than today
 * is read as a day plate: no hour, because the day has not happened yet.
 */
@Composable
fun DateSelector(
    selectedDate: LocalDate,
    isToday: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onSelectDate(selectedDate.minusDays(1)) },
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = "Previous day",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .clickable { showPicker = true }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                if (isToday) "Today" else relativeLabel(selectedDate, today),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                selectedDate.format(longDateFormat),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = { onSelectDate(selectedDate.plusDays(1)) },
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Next day",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPicker) {
        DayPickerDialog(
            initial = selectedDate,
            onPick = {
                onSelectDate(it)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                DatePicker(state = state)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        onClick = {
                            state.selectedDateMillis?.let { millis ->
                                onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                            } ?: onDismiss()
                        }
                    ) {
                        Text("Select", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * The reader's year branch (nian ming).
 *
 * One tap on the animal. It is the lightest classical anchor that makes a
 * day's reading personal, and it never leaves the device.
 */
@Composable
fun YearSignRow(
    yearSign: YearSign?,
    birthDate: LocalDate?,
    onSetYearSign: (YearSign?) -> Unit,
    onSetBirthDate: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable { showPicker = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "YOUR ANCHOR",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                when {
                    birthDate != null -> "Birth day · ${birthDate.format(shortDateFormat)}"
                    yearSign != null -> "${yearSign.animal} · ${yearSign.branch.pinyin}"
                    else -> "Set yours to personalise the plate"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (yearSign == null && birthDate == null)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary
            )
            if (birthDate == null) {
                Text(
                    "Add your birth day for a deeper reading",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (yearSign != null || birthDate != null) {
            Text(
                "CHANGE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPicker) {
        YearSignDialog(
            current = yearSign,
            birthDate = birthDate,
            onPick = {
                onSetYearSign(it)
                showPicker = false
            },
            onPickBirthDate = onSetBirthDate,
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun YearSignDialog(
    current: YearSign?,
    birthDate: LocalDate?,
    onPick: (YearSign?) -> Unit,
    onPickBirthDate: (LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    var showBirthPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    "Your year sign",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "The classical anchor for a day's reading. It stays on this device and is never sent anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                YearSign.entries.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { sign ->
                            val selected = sign == current && birthDate == null
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onPick(sign) }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    sign.animal,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    sign.branch.pinyin,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text(
                    "BIRTH DAY (OPTIONAL)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "More personal: the reading anchors on the pillar of the day you were born. " +
                        "Stored on this phone only, and excluded from backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showBirthPicker = true }) {
                        Text(
                            if (birthDate == null) "Add birth day"
                            else birthDate.format(longDateFormat),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (birthDate != null) {
                        TextButton(onClick = { onPickBirthDate(null) }) {
                            Text("Remove", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { onPick(null) }) {
                        Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showBirthPicker) {
        DayPickerDialog(
            initial = birthDate ?: LocalDate.now().minusYears(30),
            onPick = {
                onPickBirthDate(it)
                showBirthPicker = false
            },
            onDismiss = { showBirthPicker = false }
        )
    }
}

/**
 * The forward scan: the days ahead the plate favours, best first.
 *
 * The list itself is free — it is the answer to "when should I move?" — while
 * the full reading of any day stays behind the unlock.
 */
@Composable
fun FavourableDaysSection(
    days: List<FavourableDay>,
    selectedDate: LocalDate,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle("Favourable days ahead")
        Spacer(Modifier.height(6.dp))
        Text(
            "The next sixty days, ranked by the windfall reading. Tap a day to read its plate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        days.forEach { day ->
            val selected = day.date == selectedDate
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onPick(day.date) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            day.date.format(shortDateFormat),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (day.isToday) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "TODAY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        "${day.dayPillar} · ${day.term} · ${day.headline}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${day.score}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = verdictColor(day.verdict),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** A small coloured dot, used in legends. */
@Composable
fun MarkerDot(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(7.dp)
            .background(color, RoundedCornerShape(4.dp))
    )
}
