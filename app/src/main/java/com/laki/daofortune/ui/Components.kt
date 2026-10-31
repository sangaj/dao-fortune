package com.laki.daofortune.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laki.daofortune.core.Verdict

/** A small circular gauge that shows the score for a module. */
@Composable
fun ScoreGauge(
    score: Int,
    verdict: Verdict,
    modifier: Modifier = Modifier,
    diameter: Int = 92
) {
    val accent = verdictColor(verdict)
    Box(modifier = modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter.dp)) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = accent.copy(alpha = 0.18f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = accent,
                startAngle = 135f,
                sweepAngle = 270f * (score / 100f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = verdict.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun verdictColor(verdict: Verdict): Color = when (verdict) {
    Verdict.EXCEPTIONAL -> Gold
    Verdict.FAVORABLE -> Jade
    Verdict.MILDLY_GOOD -> Jade
    Verdict.MIXED -> Amber
    Verdict.CAUTIOUS -> Amber
    Verdict.UNFAVORABLE -> Crimson
}

/** A labelled plate used for the pillars, the term and other quick facts. */
@Composable
fun FactChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent
        )
    }
}

/** A section title with a thin rule under it. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/** A bullet line with a small gold marker. */
@Composable
fun InsightRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .background(Gold, RoundedCornerShape(3.dp))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
