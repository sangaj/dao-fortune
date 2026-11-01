package com.laki.daofortune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laki.daofortune.core.FavourableDay
import com.laki.daofortune.core.FortuneModule
import com.laki.daofortune.core.LuopanSnapshot
import com.laki.daofortune.core.ModuleReading
import com.laki.daofortune.core.Palace
import com.laki.daofortune.core.ReadingKey
import com.laki.daofortune.core.YearSign
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snapshot: LuopanSnapshot,
    readings: Map<ReadingKey, ModuleReading>,
    previews: Map<FortuneModule, String>,
    selectedDate: LocalDate,
    isToday: Boolean,
    yearSign: YearSign?,
    birthDate: LocalDate?,
    favourableDays: List<FavourableDay>,
    freeReadingsLeft: Int,
    unlocked: Boolean,
    notice: String?,
    notifyEnabled: Boolean,
    onToggleNotify: () -> Unit,
    onOpen: (FortuneModule) -> Unit,
    onOpenDream: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onSetYearSign: (YearSign?) -> Unit,
    onSetBirthDate: (LocalDate?) -> Unit,
    onDismissNotice: () -> Unit,
    onAbout: () -> Unit
) {
    val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Dao Fortune",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Daily Luopan Wisdom",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleNotify) {
                        Icon(
                            if (notifyEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                            contentDescription = if (notifyEnabled)
                                "Daily reading notification on — tap to turn off"
                            else
                                "Turn on the daily reading notification",
                            tint = if (notifyEnabled) Gold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onAbout) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = "About",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        snapshot.moment.format(dateFormat),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        if (snapshot.dayOnly) "Day plate · the hour is not part of it"
                        else "Local time ${snapshot.moment.format(timeFormat)} · ${snapshot.zone.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (notice != null) {
                item {
                    NoticeBanner(
                        text = noticeText(notice),
                        onDismiss = onDismissNotice
                    )
                }
            }

            item {
                DateSelector(
                    selectedDate = selectedDate,
                    isToday = isToday,
                    onSelectDate = onSelectDate
                )
            }

            item {
                YearSignRow(
                    yearSign = yearSign,
                    birthDate = birthDate,
                    onSetYearSign = onSetYearSign,
                    onSetBirthDate = onSetBirthDate
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FactChip("Solar term", snapshot.term.term.en, Modifier.weight(1f))
                    FactChip("Day officer", snapshot.officer.en, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FactChip("Year", snapshot.year.toString(), Modifier.weight(1f))
                    FactChip("Month", snapshot.month.toString(), Modifier.weight(1f))
                    FactChip("Day", snapshot.day.toString(), Modifier.weight(1f))
                    FactChip("Hour", snapshot.hour.toString(), Modifier.weight(1f))
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "THE PLATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Sun in ${snapshot.sunPalace.pinyin} · ${snapshot.sunPalace.element.en}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Gold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LuopanDial(snapshot, yearSign = yearSign)
                    Spacer(Modifier.height(4.dp))
                    LegendRow(snapshot, yearSign)
                }
            }

            item {
                Column {
                    SectionTitle(if (isToday) "Today's readings" else "Readings for this day")
                    Spacer(Modifier.height(6.dp))
                    if (!unlocked) {
                        Text(
                            if (freeReadingsLeft > 0)
                                "Today's Fortune is free every day, in full. Your first reading " +
                                    "of each other module is free too — after that, each one needs " +
                                    "the one-off unlock."
                            else
                                "Today's Fortune is free every day, in full. Your free reading of " +
                                    "the other modules has been used — open one to unlock it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            items(FortuneModule.entries.toList(), key = { it.name }) { module ->
                ModuleCard(
                    module = module,
                    reading = readings[ReadingKey(selectedDate, module)],
                    preview = previews[module],
                    locked = module != FortuneModule.OVERALL && !unlocked && freeReadingsLeft == 0 && readings[ReadingKey(selectedDate, module)] == null,
                    onClick = { onOpen(module) }
                )
            }

            item {
                DreamEntryCard(onClick = onOpenDream)
            }

            item {
                FavourableDaysSection(
                    days = favourableDays,
                    selectedDate = selectedDate,
                    onPick = onSelectDate
                )
            }

            item {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        "DISCLAIMER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Dao Fortune is a cultural tool built on the classical Chinese luopan tradition. " +
                            "It is offered for entertainment and reflection. It is not financial, medical, " +
                            "legal or psychological advice, and it does not predict events. You are always " +
                            "responsible for your own decisions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendRow(snapshot: LuopanSnapshot, yearSign: YearSign?) {    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LegendDot(Gold, "Sun")
        LegendDot(Jade, "Wealth · ${snapshot.wealthElement.en}")
        LegendDot(Crimson, "Avoid")
        if (yearSign != null) LegendDot(Parchment, "Your sign")
        LegendDot(MaterialTheme.colorScheme.onSurfaceVariant, "24 mountains")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModuleCard(
    module: FortuneModule,
    reading: ModuleReading?,
    preview: String?,
    locked: Boolean,
    onClick: () -> Unit
) {    val accent = reading?.let { verdictColor(it.verdict) } ?: MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(
                1.dp,
                if (reading != null) accent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        module.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (reading == null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    reading?.headline ?: preview ?: module.blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (reading != null)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
            if (locked) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            } else if (reading != null) {
                Text(
                    "${reading.score}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Suppress("unused")
private fun palaceHint(palace: Palace): String = palace.pinyin

/** The wording of each notice the app can show. */
private fun noticeText(notice: String): String = when (notice) {
    "FREE_READING_USED" ->
        "That was your free reading. Any reading after this one needs the one-off unlock."
    "NO_PURCHASE_FOUND" ->
        "No previous purchase found on this Google account."
    else -> notice
}

/**
 * A short line that explains what just happened, with a way to take it away.
 */
@Composable
private fun NoticeBanner(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                RoundedCornerShape(14.dp)
            )
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The entry to the dream reading.
 *
 * It is not one of the five modules, because it needs something the others do
 * not: the reader's own words. The same rule applies to it — the first reading
 * is free, later readings need the one-off unlock.
 */
@Composable
private fun DreamEntryCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(
                1.dp,
                Gold.copy(alpha = 0.30f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Dream & Omens",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "梦占",
                style = MaterialTheme.typography.labelSmall,
                color = Gold
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "Write what you remember and the plate will read it against the day. " +
                "Read on this phone; never sent anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
