package com.laki.daofortune.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.laki.daofortune.MainActivity
import com.laki.daofortune.R
import com.laki.daofortune.core.CalendarEngine
import com.laki.daofortune.core.FortuneEngine
import com.laki.daofortune.core.FortuneModule
import com.laki.daofortune.core.LuopanEngine
import com.laki.daofortune.core.WeekAhead
import com.laki.daofortune.data.ProfileState
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sunday evening: names the best day of the coming week.
 *
 * The seven day plates are computed on the device, exactly as the daily
 * reading is; the worker needs no network and none is declared for it. The
 * outlook rides on the same bell as the daily reading — one switch governs
 * both, and turning it off cancels this work too.
 */
class WeekAheadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            postOutlook()
            Result.success()
        } catch (error: Throwable) {
            // A transient failure (e.g. process limits) is worth one retry;
            // a persistent one should not loop forever.
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun postOutlook() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val profile = ProfileState(applicationContext)
        val natal = profile.birthDate?.let { CalendarEngine.dayGanZhi(it.atTime(12, 0)).branch }
            ?: profile.yearSign?.branch

        // The coming week as day plates: a day that has not happened yet has
        // no hour, so it is read without one.
        val days = (1..WeekAhead.LOOK_AHEAD_DAYS).map { offset ->
            val date = today.plusDays(offset.toLong())
            val plate = LuopanEngine.dayPlate(date, zone)
            date to FortuneEngine.reading(plate, FortuneModule.OVERALL, natal).score
        }
        val (bestDay, bestScore) = WeekAhead.bestDay(days) ?: return

        val tap = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = WeekAhead.line(bestDay, bestScore)
        NotificationScheduler.ensureWeekChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, NotificationScheduler.WEEK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("The week ahead")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setSilent(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (missing: SecurityException) {
            // The reader revoked the notification permission since scheduling.
            // Nothing to do: the bell in the app is the way back.
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 41002
    }
}
