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
import com.laki.daofortune.data.ProfileState
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The reminder for one favourable day the reader marked.
 *
 * Fires at eight in the morning of that day, reads the day's plate (a day
 * plate — the day has barely started), and posts one notification. The
 * marked date is cleared afterwards, so the bell in the list goes quiet
 * again on its own.
 */
class DayReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val date = inputData.getString(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return Result.failure()
        return try {
            postReminder(date)
            ProfileState(applicationContext).setDayReminded(date, false)
            Result.success()
        } catch (error: Throwable) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun postReminder(date: LocalDate) {
        val zone = ZoneId.systemDefault()
        val snapshot = LuopanEngine.dayPlate(date, zone)
        val profile = ProfileState(applicationContext)
        val natal = profile.birthDate?.let { CalendarEngine.dayGanZhi(it.atTime(12, 0)).branch }
            ?: profile.yearSign?.branch
        val reading = FortuneEngine.reading(snapshot, FortuneModule.OVERALL, natal)

        val tap = PendingIntent.getActivity(
            applicationContext,
            date.hashCode(),
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dayLabel = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH))
        val text = "${reading.score}/100 — ${reading.headline}"

        NotificationScheduler.ensureReminderChannel(applicationContext)
        val notification =
            NotificationCompat.Builder(applicationContext, NotificationScheduler.REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("A day you marked: $dayLabel")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()

        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_BASE + date.dayOfYear, notification)
        } catch (missing: SecurityException) {
            // The reader revoked the notification permission since marking the day.
        }
    }

    companion object {
        const val KEY_DATE = "date"
        private const val NOTIFICATION_BASE = 42000
    }
}
