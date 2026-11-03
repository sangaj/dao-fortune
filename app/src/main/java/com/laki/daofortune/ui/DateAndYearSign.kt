package com.laki.daofortune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SelectableDates
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

/**
 * The day picker.
 *
 * [maxDate] and [minDate] bound what can be chosen. The reading-date picker
 * leaves both open — a reader may look at yesterday or at next month — while
 * the birth day is bounded, because a birth day in the future is not a birth
 * day and would anchor every reading to a day that has not happened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    maxDate: LocalDate? = null,
    minDate: LocalDate? = null
) {
    val selectableDates = remember(maxDate, minDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                if (minDate != null && date.isBefore(minDate)) return false
                if (maxDate != null && date.isAfter(maxDate)) return false
                return true
            }

            override fun isSelectableYear(year: Int): Boolean {
                if (minDate != null && year < minDate.year) return false
                if (maxDate != null && year > maxDate.year) return false
                return true
            }
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectableDates
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
        val today = LocalDate.now()
        DayPickerDialog(
            // A birth day already stored in the future would be a broken
            // anchor, so the picker opens on a day that can actually be picked.
            initial = birthDate?.takeIf { !it.isAfter(today) } ?: today.minusYears(30),
            onPick = {
                onPickBirthDate(it)
                showBirthPicker = false
            },
            onDismiss = { showBirthPicker = false },
            // A birth day is in the past, and not in the deep past either.
            maxDate = today,
            minDate = today.minusYears(120)
        )
    }
}

/**
 * The year officer (tai sui) card: how the reader's sign stands against the
 * year itself.
 *
 * Free and always on the home screen. With no sign set it is the invitation
 * to set one — tapping it opens the same picker the anchor row uses.
 */
@Composable
fun YearOfficerCard(
    yearSign: YearSign?,
    yearBranch: com.laki.daofortune.core.Branch,
    yearNumber: Int,
    onSetYearSign: (YearSign?) -> Unit,
    onSetBirthDate: (LocalDate?) -> Unit,
    birthDate: LocalDate?,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val reading = yearSign?.let { com.laki.daofortune.core.YearReading.of(it, yearBranch) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(enabled = reading == null) { showPicker = true }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "THE YEAR OFFICER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // The year is named, not just the animal: the card follows the
                // plate on screen, so "the year of the Goat" must say which
                // one it means.
                "Year of the ${yearBranch.animal} · $yearNumber",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(4.dp))
        if (reading != null) {
            Text(
                reading.headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                reading.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                com.laki.daofortune.core.YearReading.teaser(yearBranch),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "SET YOUR SIGN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
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
    remindedDays: Set<LocalDate>,
    activity: com.laki.daofortune.core.PickActivity,
    onSelectActivity: (com.laki.daofortune.core.PickActivity) -> Unit,
    onPick: (LocalDate) -> Unit,
    onToggleReminder: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle("Favourable days ahead")
        Spacer(Modifier.height(6.dp))
        Text(
            if (activity == com.laki.daofortune.core.PickActivity.FINANCIAL_MOVE)
                "The next sixty days, ranked by the windfall reading. Tap a day to read its plate, " +
                    "the bell to be reminded on its morning."
            else
                "The next sixty days, ranked for ${activity.label}. Tap a day to read its plate, " +
                    "the bell to be reminded on its morning.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        // What the reader is planning: the ranking follows the activity.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.laki.daofortune.core.PickActivity.entries.forEach { option ->
                val selected = option == activity
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectActivity(option) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        days.forEach { day ->
            val selected = day.date == selectedDate
            val reminded = day.date in remindedDays
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
                if (!day.isToday) {
                    IconButton(
                        onClick = { onToggleReminder(day.date) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            if (reminded) Icons.Filled.Notifications
                            else Icons.Outlined.Notifications,
                            contentDescription = if (reminded) "Cancel the reminder" else "Remind me",
                            tint = if (reminded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                // The score belongs to the module that speaks for the
                // activity, and the module says so under the number: the
                // reader sees the same score when they open the day.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${day.score}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = verdictColor(day.verdict),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        day.module.short,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
