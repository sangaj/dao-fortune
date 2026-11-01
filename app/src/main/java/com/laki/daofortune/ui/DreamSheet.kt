package com.laki.daofortune.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laki.daofortune.core.DreamImage
import com.laki.daofortune.core.DreamReading
import com.laki.daofortune.core.FortuneEngine
import com.laki.daofortune.core.LuopanSnapshot
import com.laki.daofortune.core.Palace
import com.laki.daofortune.core.YearSign
import com.laki.daofortune.ui.share.ShareCard
import com.laki.daofortune.ui.share.ShareImage
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The dream sheet: write what you remember, and the plate reads it.
 *
 * The dream text never leaves the phone — it is not stored, not logged and not
 * sent anywhere. It is read on the device, drawn on the plate and then gone
 * when the sheet closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DreamSheet(
    text: String,
    wokeAt: String,
    reading: DreamReading?,
    snapshot: LuopanSnapshot? = null,
    yearSign: YearSign? = null,
    onTextChange: (String) -> Unit,
    onWokeAtChange: (String) -> Unit,
    onRead: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "DREAM & OMENS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Write what you remember, and the plate will read it against the day.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "The tradition calls this 梦占: a dream is not read on its own, it is read against " +
                        "the day's own pillar. Your words are read on this phone and never leave it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("The dream") },
                    placeholder = { Text("A black snake by a river, and a house with a red door…") },
                    minLines = 4,
                    shape = RoundedCornerShape(14.dp)
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = wokeAt,
                        onValueChange = onWokeAtChange,
                        modifier = Modifier.width(140.dp),
                        label = { Text("You woke at") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "The double-hour is part of the reading.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Button(
                    onClick = onRead,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = text.isNotBlank()
                ) {
                    Text(if (reading == null) "Read the dream" else "Read it again")
                }
            }

            if (reading != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ScoreGauge(score = reading.score, verdict = reading.verdict)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    reading.contextLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    reading.headline,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = verdictColor(reading.verdict)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            reading.summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                item {
                    DreamPlate(
                        image = reading.image,
                        snapshot = snapshot,
                        yearSign = yearSign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item { SectionTitle("What the plate says") }
                items(reading.insights.size) { index ->
                    InsightRow(reading.insights[index])
                }

                item { SectionTitle("What to do") }
                items(reading.advice.size) { index ->
                    InsightRow(reading.advice[index])
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
                                    moduleTitle = "Dream & Omens",
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
                                        moduleTitle = "Dream & Omens",
                                        contextLabel = reading.contextLabel,
                                        score = reading.score,
                                        verdict = reading.verdict,
                                        headline = reading.headline,
                                        summary = reading.summary
                                    )
                                }
                                val saved = ShareImage.saveToGallery(context, bitmap, "DaoFortune_dream")
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
                    Column {
                        SectionTitle("How this was scored")
                        Spacer(Modifier.height(4.dp))
                        reading.factors.forEach { factor ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
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
            }

            item {
                Text(
                    "A cultural reading of a dream, in the classical luopan tradition. It is not " +
                        "psychology, and it does not predict events.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

/**
 * The dream drawn on the plate.
 *
 * The picture is generated on the device from the dream itself: the images the
 * vocabulary found are laid on the mountains they belong to, the sun of the day
 * is marked, and the ink wash is seeded by the dream's own text — so the same
 * dream always draws the same plate, and no image ever leaves the phone.
 */
@Composable
fun DreamPlate(
    image: DreamImage,
    modifier: Modifier = Modifier,
    snapshot: LuopanSnapshot? = null,
    yearSign: YearSign? = null
) {
    val measurer = rememberTextMeasurer()
    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            drawDreamPlate(image, snapshot, yearSign, measurer)
        }
    }
}

private fun DrawScope.drawDreamPlate(
    image: DreamImage,
    snapshot: LuopanSnapshot?,
    yearSign: YearSign?,
    measurer: TextMeasurer
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f - 26.dp.toPx()
    val random = InkRandom(image.seed)

    // The ink wash: a soft field whose density comes from the dream itself.
    // Each wash is a gradient that fades out, so the plate reads as ink on
    // paper rather than as a set of discs.
    repeat(7) {
        val angle = random.next() * 6.2831853f
        val distance = radius * (0.18f + random.next() * 0.62f)
        val blob = Offset(
            center.x + distance * cos(angle),
            center.y + distance * sin(angle)
        )
        val alpha = 0.018f + random.next() * 0.03f
        val washRadius = radius * (0.22f + random.next() * 0.38f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Parchment.copy(alpha = alpha), Parchment.copy(alpha = 0f)),
                center = blob,
                radius = washRadius
            ),
            radius = washRadius,
            center = blob
        )
    }

    // The ring of mountains.
    drawCircle(color = Gold.copy(alpha = 0.20f), radius = radius, center = center, style = Stroke(1.2.dp.toPx()))
    drawCircle(color = Gold.copy(alpha = 0.12f), radius = radius * 0.60f, center = center, style = Stroke(1.dp.toPx()))

    // The wealth sector of the day.
    //
    // Four mountains, and not always one continuous stretch: water sits on the
    // north side, earth takes the four corners. Each mountain gets its own
    // fifteen degrees, the same way the main dial draws it.
    snapshot?.let { plate ->
        plate.wealthPalaces.forEach { palace ->
            drawArc(
                color = Jade.copy(alpha = 0.18f),
                startAngle = (palace.bearing - 7.5 - 90).toFloat(),
                sweepAngle = 15f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }

    // Ticks and mountain names.
    Palace.entries.forEach { palace ->
        val angle = Math.toRadians((palace.bearing - 90).toDouble())
        val start = Offset(
            center.x + (radius - 6.dp.toPx()) * cos(angle).toFloat(),
            center.y + (radius - 6.dp.toPx()) * sin(angle).toFloat()
        )
        val end = Offset(
            center.x + (radius - 13.dp.toPx()) * cos(angle).toFloat(),
            center.y + (radius - 13.dp.toPx()) * sin(angle).toFloat()
        )
        drawLine(color = Gold.copy(alpha = 0.30f), start = start, end = end, strokeWidth = 1.dp.toPx())

        val style = TextStyle(fontSize = 9.sp, color = Muted.copy(alpha = 0.7f))
        val measured = measurer.measure(palace.pinyin, style)
        val labelRadius = radius - 22.dp.toPx()
        drawText(
            measured,
            topLeft = Offset(
                center.x + labelRadius * cos(angle).toFloat() - measured.size.width / 2f,
                center.y + labelRadius * sin(angle).toFloat() - measured.size.height / 2f
            )
        )
    }

    // The images of the dream, laid on the mountains they belong to.
    val byPalace = image.tokens
        .mapNotNull { token -> token.palace?.let { palace -> palace to token } }
        .groupBy({ it.first }, { it.second })
    byPalace.forEach { (palace, tokens) ->
        val angle = Math.toRadians((palace.bearing - 90).toDouble())
        tokens.forEachIndexed { index, token ->
            val isPrincipal = token == image.principal
            val spread = 12.dp.toPx()
            val step = (index - (tokens.size - 1) / 2f) * spread
            val px = center.x + (radius + 18.dp.toPx()) * cos(angle).toFloat() - step * sin(angle).toFloat()
            val py = center.y + (radius + 18.dp.toPx()) * sin(angle).toFloat() + step * cos(angle).toFloat()

            // A brush mark, and the original term.
            val mark = Path().apply {
                moveTo(px - 7.dp.toPx(), py + 6.dp.toPx())
                cubicTo(
                    px - 2.dp.toPx(), py - 6.dp.toPx(),
                    px + 3.dp.toPx(), py + 4.dp.toPx(),
                    px + 8.dp.toPx(), py - 5.dp.toPx()
                )
            }
            drawPath(
                mark,
                color = if (isPrincipal) Gold else Parchment.copy(alpha = 0.7f),
                style = Stroke(width = if (isPrincipal) 2.6.dp.toPx() else 1.6.dp.toPx())
            )
            if (token.chinese.isNotEmpty() && token.chinese.any { it.code > 0x2E80 }) {
                val style = TextStyle(
                    fontSize = if (isPrincipal) 15.sp else 12.sp,
                    color = if (isPrincipal) Gold else Parchment.copy(alpha = 0.75f),
                    fontWeight = if (isPrincipal) FontWeight.Bold else FontWeight.Normal
                )
                val measured = measurer.measure(token.chinese, style)
                val textLeft = px - measured.size.width / 2f
                val textTop = py - measured.size.height / 2f - 12.dp.toPx()
                drawText(
                    measured,
                    topLeft = Offset(textLeft, textTop)
                )
            }
        }
    }

    // The sun of the day.
    snapshot?.let { plate ->
        val sunAngle = Math.toRadians((plate.sunPalace.bearing - 90).toDouble())
        val sunPoint = Offset(
            center.x + (radius + 34.dp.toPx()) * cos(sunAngle).toFloat(),
            center.y + (radius + 34.dp.toPx()) * sin(sunAngle).toFloat()
        )
        val diamond = Path().apply {
            moveTo(sunPoint.x, sunPoint.y - 5.dp.toPx())
            lineTo(sunPoint.x + 4.dp.toPx(), sunPoint.y)
            lineTo(sunPoint.x, sunPoint.y + 5.dp.toPx())
            lineTo(sunPoint.x - 4.dp.toPx(), sunPoint.y)
            close()
        }
        drawPath(diamond, color = Gold)
    }

    // The reader's own year branch.
    yearSign?.let { sign ->
        val natalAngle = Math.toRadians((sign.palace.bearing - 90).toDouble())
        val natalPoint = Offset(
            center.x + (radius + 34.dp.toPx()) * cos(natalAngle).toFloat(),
            center.y + (radius + 34.dp.toPx()) * sin(natalAngle).toFloat()
        )
        drawCircle(color = Parchment, radius = 6.dp.toPx(), center = natalPoint, style = Stroke(width = 2.dp.toPx()))
    }

    // The centre: the day's own pillar.
    snapshot?.let { plate ->
        val style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Parchment)
        val measured = measurer.measure(plate.day.toString(), style)
        drawText(
            measured,
            topLeft = Offset(
                center.x - measured.size.width / 2f,
                center.y - measured.size.height / 2f - 6.dp.toPx()
            )
        )
        val sub = TextStyle(fontSize = 10.sp, color = Muted)
        val wealth = measurer.measure(
            "${plate.wealthElement.en} · ${FortuneEngine.primaryBearing(plate.wealthElement)}°",
            sub
        )
        drawText(wealth, topLeft = Offset(center.x - wealth.size.width / 2f, center.y + 6.dp.toPx()))
    }
}

/**
 * A tiny deterministic generator, so the same dream always draws the same ink.
 */
private class InkRandom(seed: Int) {
    private var state: Long = (seed.toLong() xor 0x5DEECE66DL) and ((1L shl 48) - 1)

    fun next(): Float {
        state = (state * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        return ((state shr 16).toInt() and 0x7FFFFFFF).toFloat() / Int.MAX_VALUE.toFloat()
    }
}
