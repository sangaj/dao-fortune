package com.laki.daofortune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.laki.daofortune.BuildInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Dao Fortune",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Daily Luopan Wisdom · version ${BuildInfo.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Text("WHAT THIS IS", style = MaterialTheme.typography.labelSmall, color = Gold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Dao Fortune reads your day through the twenty-four mountains of the Chinese luopan. " +
                    "It places the sun on the plate, finds the day's officer and hour officers, weighs the " +
                    "five phases against your day element, and points the direction of the day. From that " +
                    "plate it answers one practical question: is today a day to expect an unexpected gain, " +
                    "to push a deal, to open a conversation, or to hold still.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))
            Text("HOW THE READING IS BUILT", style = MaterialTheme.typography.labelSmall, color = Gold)
            Spacer(Modifier.height(6.dp))
            listOf(
                "The sun's apparent longitude puts the day on one of the twenty-four mountains.",
                "The four pillars — year, month, day and hour — are derived from the sexagenary cycle, with the year turning at Li Chun and the day turning at 23:00.",
                "The twelve day-officers (jian, chu, man, ping, ding, zhi, po, wei, cheng, shou, kai, bi) set the day's basic tone.",
                "The twelve double-hours are scored individually, so you can see the good windows rather than a vague verdict.",
                "Your wealth element is the phase you control; the plate shows whether the day feeds it or drains it.",
                "The mountain opposite your day branch is marked as the direction to avoid."
            ).forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .background(Gold, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("HOW THE PLATE KNOWS WHO IS READING", style = MaterialTheme.typography.labelSmall, color = Gold)
            Spacer(Modifier.height(6.dp))
            Text(
                "In this tradition the day itself stands for the reader: the day's own stem is the " +
                    "\"day master\" the plate is read against, and the day's own branch is the reader's " +
                    "root. That is why a plate can be read without knowing anything about you. " +
                    "If you set your year sign, it is measured against the day as well — the way the " +
                    "da liu ren tradition identifies a querent by their year branch. That is the whole of " +
                    "it: a year branch, not a birth chart.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))
            Text("LOOKING AHEAD", style = MaterialTheme.typography.labelSmall, color = Gold)
            Spacer(Modifier.height(6.dp))
            Text(
                "You can pick any day and read its plate, and the app scans the next sixty days and " +
                    "ranks them by the windfall reading. A day that has not happened yet has no hour, " +
                    "so those readings are day plates: the day's own constitution, its officer, its season " +
                    "and the sun's mountain, with the best double-hours of that day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))
            Text("DREAM & OMENS (梦占)", style = MaterialTheme.typography.labelSmall, color = Gold)
            Spacer(Modifier.height(6.dp))
            Text(
                "A dream is not read on its own in this tradition — it is read against the day. Write " +
                    "what you remember and the app looks for the classical images in your own words: " +
                    "the direction you walked, the animal that followed you, the number, the colour, " +
                    "the element, the hour you woke in. Those images are then measured against the " +
                    "plate of that night by the same rules as the other readings: does the omen answer " +
                    "the day's branch or clash with it, does its element feed the day or cut across " +
                    "it, does the direction point at the sun's mountain or away from it. " +
                    "The picture is drawn on this device from the dream itself, so the same dream always " +
                    "draws the same plate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))
            Text("PRIVACY", style = MaterialTheme.typography.labelSmall, color = Gold)
            Spacer(Modifier.height(6.dp))
            Text(
                "The app has no account, no analytics and no network calls of its own. Your reading is " +
                    "computed on your device. Your year sign is stored on this device only — it is " +
                    "excluded from Android's backup and is never transmitted, and you can clear it from " +
                    "the home screen at any time. The dream you write is read on this device, drawn on " +
                    "this device and erased when you close the sheet: it is never stored, never " +
                    "transmitted and never seen by anyone. The only purchase data ever handled is what " +
                    "Google Play Billing processes for the unlock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))
            Text("DISCLAIMER", style = MaterialTheme.typography.labelSmall, color = Gold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Dao Fortune is a cultural and entertainment product built on classical Chinese divination. " +
                    "It is not financial, medical, legal, investment or psychological advice, it does not " +
                    "promise any outcome, and it should not be used to make financial decisions. You remain " +
                    "fully responsible for your own choices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
