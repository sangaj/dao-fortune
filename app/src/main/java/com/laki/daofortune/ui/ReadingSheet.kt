package com.laki.daofortune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.os.Build
import android.widget.Toast
import com.laki.daofortune.core.ModuleReading
import com.laki.daofortune.ui.share.ShareCard
import com.laki.daofortune.ui.share.ShareImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSheet(
    reading: ModuleReading,
    onClose: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreGauge(score = reading.score, verdict = reading.verdict)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            reading.module.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            reading.contextLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                        Text(
                            reading.headline,
                            style = MaterialTheme.typography.titleLarge,
                            color = verdictColor(reading.verdict)
                        )
                    }
                }
            }

            item {
                Text(
                    reading.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item { SectionTitle("What the plate says") }
            items(reading.insights.size) { index ->
                InsightRow(reading.insights[index])
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        "BEST WINDOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    reading.bestHours.forEach { hour ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${hour.range}  ·  ${hour.branch.pinyin} hour",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                hour.officer.en,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hour.officer.auspicious) Jade else Crimson
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    DirectionLine("Turn towards", reading.bestDirection, Jade)
                    Spacer(Modifier.height(4.dp))
                    DirectionLine("Turn away from", reading.avoidDirection, Crimson)
                }
            }

            item { SectionTitle("What to do") }
            items(reading.advice.size) { index ->
                InsightRow(reading.advice[index])
            }

            item {
                Column {
                    SectionTitle("How this was scored")
                    Spacer(Modifier.height(4.dp))
                    reading.factors.forEach { factor ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                factor.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                (if (factor.delta > 0) "+" else "") + factor.delta,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (factor.delta > 0) Jade else Crimson
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "This reading follows the classical luopan rules: the sun's mountain, the day's officer, " +
                        "the twelve double-hours, the five phases and the direction of the day branch. " +
                        "It is a cultural reading, not advice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                val context = LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = {
                        val bitmap = ShareImage.render(context) {
                            ShareCard(
                                moduleTitle = reading.module.title,
                                contextLabel = reading.contextLabel,
                                score = reading.score,
                                verdict = reading.verdict,
                                headline = reading.headline,
                                summary = reading.summary
                            )
                        }
                        ShareImage.share(context, bitmap)
                    }) {
                        Text("Share as image", color = MaterialTheme.colorScheme.primary)
                    }
                    if (Build.VERSION.SDK_INT >= 29) {
                        TextButton(onClick = {
                            val bitmap = ShareImage.render(context) {
                                ShareCard(
                                    moduleTitle = reading.module.title,
                                    contextLabel = reading.contextLabel,
                                    score = reading.score,
                                    verdict = reading.verdict,
                                    headline = reading.headline,
                                    summary = reading.summary
                                )
                            }
                            val saved = ShareImage.saveToGallery(
                                context, bitmap,
                                "DaoFortune_${reading.module.name.lowercase()}"
                            )
                            Toast.makeText(
                                context,
                                if (saved) "Saved to gallery" else "Could not save the image",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Text("Save image", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item {
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DirectionLine(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box2(accent)
        Spacer(Modifier.width(8.dp))
        Text(
            "$label  ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = accent
        )
    }
}

@Composable
private fun Box2(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(7.dp)
            .background(color, RoundedCornerShape(4.dp))
    )
}
