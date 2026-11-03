package com.laki.daofortune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.laki.daofortune.core.YearSign

/**
 * The first-run introduction: three short pages, seen once.
 *
 * The luopan is not a familiar object to every reader, so the first open
 * explains the three things the whole app hangs on — the plate it reads
 * every day, the anchor that makes a reading yours, and the morning bell —
 * and then gets out of the way. Nothing here is required: every page can be
 * skipped, and the flag that it has been seen lives in the profile file, so
 * a reinstall shows it again but a day-to-day open never does.
 */
@Composable
fun OnboardingSheet(
    yearSign: YearSign?,
    notifyEnabled: Boolean,
    onSetYearSign: (YearSign?) -> Unit,
    onEnableNotify: () -> Unit,
    onDone: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(28.dp))

                when (page) {
                    0 -> PagePlate()
                    1 -> PageAnchor(yearSign = yearSign, onSetYearSign = onSetYearSign)
                    else -> PageBell(notifyEnabled = notifyEnabled, onEnableNotify = onEnableNotify)
                }

                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(24.dp))

                // The page dots.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        val on = index == page
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(if (on) 9.dp else 7.dp)
                                .background(
                                    if (on) Gold else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(5.dp)
                                )
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))

                if (page < 2) {
                    Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) {
                        Text("Next")
                    }
                    TextButton(onClick = onDone) {
                        Text(
                            "Skip the introduction",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Read today's plate")
                    }
                }
            }
        }
    }
}

/** Page one: what the app reads, and what is always free. */
@Composable
private fun PagePlate() {
    Text(
        "A plate for every day",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(14.dp))
    Text(
        "Dao Fortune turns the classical Chinese luopan for the day you are living in: " +
            "the pillars of the year, month, day and hour, the sun's mountain, and the " +
            "twelve double-hours, all computed on this phone.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Today's Fortune is free every day, in full. Windfall, love, career and body " +
            "go deeper, and the dream you had last night can be read against it too.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** Page two: set the anchor that makes a reading personal. */
@Composable
private fun PageAnchor(yearSign: YearSign?, onSetYearSign: (YearSign?) -> Unit) {
    Text(
        "Make it yours",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(14.dp))
    Text(
        "Choose your year sign and every reading is measured against your own position " +
            "on the plate — and the Year Officer card reads the year itself against you. " +
            "It stays on this device and is never sent anywhere.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(20.dp))

    YearSign.entries.chunked(4).forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { sign ->
                val selected = sign == yearSign
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
                        .clickable { onSetYearSign(if (selected) null else sign) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        sign.animal,
                        style = MaterialTheme.typography.titleSmall,
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
}

/** Page three: the morning bell, off by default. */
@Composable
private fun PageBell(notifyEnabled: Boolean, onEnableNotify: () -> Unit) {
    Text(
        "One line, every morning",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(14.dp))
    Text(
        "At eight each morning the app can post one silent notification — the day's " +
            "score and its headline, computed on the device the night says nothing to " +
            "anyone. No account, no server, and the bell turns it off again whenever " +
            "you like.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(22.dp))
    if (notifyEnabled) {
        Text(
            "The morning bell is on.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        OutlinedButton(onClick = onEnableNotify, modifier = Modifier.fillMaxWidth()) {
            Text("Turn on the morning bell", color = Gold)
        }
    }
}
