package com.laki.daofortune.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.laki.daofortune.MainActivity
import com.laki.daofortune.core.CalendarEngine
import com.laki.daofortune.core.FortuneEngine
import com.laki.daofortune.core.FortuneModule
import com.laki.daofortune.core.LuopanEngine
import com.laki.daofortune.data.ProfileState
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The home-screen widget: today's overall score, the verdict, the headline
 * and the best double-hour, at a glance.
 *
 * Everything is computed on the device at update time; the widget never
 * touches the network. It refreshes itself through [updateAll], called when
 * the app opens and from the daily-reading worker each morning.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zone = ZoneId.systemDefault()
        val snapshot = LuopanEngine.snapshot(LocalDateTime.now(), zone)
        val profile = ProfileState(context)
        val natal = profile.birthDate?.let { CalendarEngine.dayGanZhi(it.atTime(12, 0)).branch }
            ?: profile.yearSign?.branch
        val reading = FortuneEngine.reading(snapshot, FortuneModule.OVERALL, natal)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(20.dp)
                        .background(INK)
                        .clickable(actionStartActivity<MainActivity>())
                        .padding(14.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = snapshot.day.toString()
                            .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
                            .uppercase(Locale.ENGLISH),
                        style = TextStyle(
                            color = ColorProvider(MUTED),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            text = "${reading.score}",
                            style = TextStyle(
                                color = ColorProvider(GOLD),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(GlanceModifier.width(10.dp))
                        Column {
                            Text(
                                text = reading.verdict.label,
                                style = TextStyle(
                                    color = ColorProvider(JADE),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            reading.bestHours.firstOrNull()?.let { hour ->
                                Text(
                                    text = "Best window ${hour.range}",
                                    style = TextStyle(color = ColorProvider(MUTED), fontSize = 11.sp)
                                )
                            }
                        }
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = reading.headline,
                        maxLines = 2,
                        style = TextStyle(color = ColorProvider(PARCHMENT), fontSize = 13.sp)
                    )
                }
            }
        }
    }

    companion object {
        private val INK = Color(0xFF0B0E13)
        private val GOLD = Color(0xFFD9B36B)
        private val JADE = Color(0xFF6FBFA0)
        private val PARCHMENT = Color(0xFFECE7DC)
        private val MUTED = Color(0xFF9AA3AF)

        /** Recomputes and redraws every placed instance of the widget. */
        suspend fun refresh(context: Context) {
            val widget = TodayWidget()
            GlanceAppWidgetManager(context)
                .getGlanceIds(TodayWidget::class.java)
                .forEach { widget.update(context, it) }
        }
    }
}
