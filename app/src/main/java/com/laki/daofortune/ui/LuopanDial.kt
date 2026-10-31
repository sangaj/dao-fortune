package com.laki.daofortune.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laki.daofortune.core.FortuneEngine
import com.laki.daofortune.core.LuopanSnapshot
import com.laki.daofortune.core.Palace
import com.laki.daofortune.core.YearSign
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The twenty-four mountain plate. It shows where the sun stands today, which
 * sector the wealth element rules, the direction the day rubs against, and —
 * when the reader has set one — where their own year branch sits.
 */
@Composable
fun LuopanDial(
    snapshot: LuopanSnapshot,
    modifier: Modifier = Modifier,
    yearSign: YearSign? = null
) {
    val measurer = rememberTextMeasurer()
    Box(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            drawPlate(snapshot, yearSign, measurer)
        }
    }
}

private fun DrawScope.drawPlate(
    snapshot: LuopanSnapshot,
    yearSign: YearSign?,
    measurer: TextMeasurer
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f - 18.dp.toPx()
    val labelRadius = radius - 16.dp.toPx()
    val gold = Gold
    val muted = Muted

    // Rings.
    drawCircle(color = gold.copy(alpha = 0.22f), radius = radius, center = center, style = Stroke(1.2.dp.toPx()))
    drawCircle(color = gold.copy(alpha = 0.14f), radius = radius * 0.62f, center = center, style = Stroke(1.dp.toPx()))
    drawCircle(color = gold.copy(alpha = 0.10f), radius = radius * 0.22f, center = center, style = Stroke(1.dp.toPx()))

    // Wealth sector: the four mountains ruled by the wealth element.
    //
    // These are four mountains, not always one continuous stretch: water sits
    // on the north side (Hai, Ren, Zi, Gui), but earth takes the four corners
    // (Chou, Chen, Wei, Xu). Each mountain therefore gets its own fifteen
    // degrees, and the band reads as continuous where it truly is.
    //
    // (Taking the smallest and the largest bearing and sweeping between them
    // is wrong: for a water day the set straddles north and the sweep closes
    // into a full circle; for an earth day it would cover most of the dial.)
    snapshot.wealthPalaces.forEach { palace ->
        drawArc(
            color = Jade.copy(alpha = 0.22f),
            startAngle = (palace.bearing - 7.5 - 90).toFloat(),
            sweepAngle = 15f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
        )
    }

    // Avoid direction: the mountain opposite the day branch.
    val clashAngle = Math.toRadians((snapshot.clashPalace.bearing - 90).toDouble())
    val clashPoint = Offset(
        center.x + (radius - 6.dp.toPx()) * cos(clashAngle).toFloat(),
        center.y + (radius - 6.dp.toPx()) * sin(clashAngle).toFloat()
    )
    drawCircle(color = Crimson.copy(alpha = 0.85f), radius = 4.dp.toPx(), center = clashPoint)

    // Ticks and mountain names.
    Palace.entries.forEach { palace ->
        val angle = Math.toRadians((palace.bearing - 90).toDouble())
        val isSun = palace == snapshot.sunPalace
        val isWealth = palace in snapshot.wealthPalaces
        val tickInner = radius - 7.dp.toPx()
        val tickOuter = radius - (if (isSun || isWealth) 15.dp.toPx() else 11.dp.toPx())
        val start = Offset(
            center.x + tickInner * cos(angle).toFloat(),
            center.y + tickInner * sin(angle).toFloat()
        )
        val end = Offset(
            center.x + tickOuter * cos(angle).toFloat(),
            center.y + tickOuter * sin(angle).toFloat()
        )
        drawLine(
            color = when {
                isSun -> gold
                isWealth -> Jade
                else -> gold.copy(alpha = 0.35f)
            },
            start = start,
            end = end,
            strokeWidth = if (isSun) 2.4.dp.toPx() else 1.dp.toPx()
        )

        val label = palace.pinyin
        val style = TextStyle(
            fontSize = if (isSun) 12.sp else 10.sp,
            color = when {
                isSun -> gold
                isWealth -> Jade
                else -> muted.copy(alpha = 0.75f)
            },
            fontWeight = if (isSun) FontWeight.Bold else FontWeight.Normal
        )
        val measured = measurer.measure(label, style)
        val px = center.x + labelRadius * cos(angle).toFloat() - measured.size.width / 2f
        val py = center.y + labelRadius * sin(angle).toFloat() - measured.size.height / 2f
        drawText(measured, topLeft = Offset(px, py))
    }

    // Sun marker: a small diamond at the sun's mountain.
    val sunAngle = Math.toRadians((snapshot.sunPalace.bearing - 90).toDouble())
    val sunPoint = Offset(
        center.x + (radius + 12.dp.toPx()) * cos(sunAngle).toFloat(),
        center.y + (radius + 12.dp.toPx()) * sin(sunAngle).toFloat()
    )
    val diamond = PathDiamond(sunPoint, 5.dp.toPx())
    drawPath(diamond, color = gold)

    // The reader's own year branch: a hollow ring, so it never reads as the sun.
    if (yearSign != null) {
        val natalAngle = Math.toRadians((yearSign.palace.bearing - 90).toDouble())
        val natalPoint = Offset(
            center.x + (radius + 12.dp.toPx()) * cos(natalAngle).toFloat(),
            center.y + (radius + 12.dp.toPx()) * sin(natalAngle).toFloat()
        )
        drawCircle(
            color = Parchment,
            radius = 6.dp.toPx(),
            center = natalPoint,
            style = Stroke(width = 2.dp.toPx())
        )
    }

    // Centre: the day pillar and the wealth element.
    val dayStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Parchment)
    val day = measurer.measure(snapshot.day.toString(), dayStyle)
    drawText(day, topLeft = Offset(center.x - day.size.width / 2f, center.y - day.size.height / 2f - 6.dp.toPx()))

    val subStyle = TextStyle(fontSize = 10.sp, color = muted)
    val sub = measurer.measure("${snapshot.wealthElement.en} · ${FortuneEngine.primaryBearing(snapshot.wealthElement)}°", subStyle)
    drawText(sub, topLeft = Offset(center.x - sub.size.width / 2f, center.y + 6.dp.toPx()))
}

private fun PathDiamond(center: Offset, size: Float) =
    androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x, center.y - size)
        lineTo(center.x + size * 0.72f, center.y)
        lineTo(center.x, center.y + size)
        lineTo(center.x - size * 0.72f, center.y)
        close()
    }

@Suppress("unused")
private fun unusedColorRef(): Color = Color.Transparent
