package com.laki.daofortune.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.laki.daofortune.core.Verdict
import com.laki.daofortune.ui.Gold
import com.laki.daofortune.ui.ScoreGauge
import com.laki.daofortune.ui.verdictColor

/**
 * The vertical card that is rendered off-screen and shared or saved as an
 * image. It is laid out at exactly 360×640 dp; the renderer draws it at
 * density 3.0, so the bitmap always comes out 1080×1920 px regardless of the
 * device it runs on.
 *
 * The card is deliberately a summary — score, verdict, headline and a short
 * summary — not a screenshot of the sheet: a shared image should read well
 * standing alone in a chat thread.
 */
@Composable
fun ShareCard(
    moduleTitle: String,
    contextLabel: String,
    score: Int,
    verdict: Verdict,
    headline: String,
    summary: String
) {
    Box(
        modifier = Modifier
            .size(width = 360.dp, height = 640.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "DAO FORTUNE",
                style = MaterialTheme.typography.labelSmall,
                color = Gold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                moduleTitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                contextLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(26.dp))
            ScoreGauge(score = score, verdict = verdict, diameter = 150)
            Spacer(Modifier.height(22.dp))

            Text(
                headline,
                style = MaterialTheme.typography.titleLarge,
                color = verdictColor(verdict),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(Gold, RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "A cultural reading, not advice",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
