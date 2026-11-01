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
import com.laki.daofortune.core.FortuneEngine
import com.laki.daofortune.core.FortuneModule
import com.laki.daofortune.core.LuopanEngine
import com.laki.daofortune.data.ProfileState
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Computes today's plate and posts one notification with the verdict of the
 * day. Purely on-device: the engine is local, so this worker needs no network
 * and none is declared for it.
 */
class DailyReadingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            postReading()
            Result.success()
        } catch (error: Throwable) {
            // A transient failure (e.g. process limits) is worth one retry;
            // a persistent one should not loop forever.
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun postReading() {
        val zone = ZoneId.systemDefault()
        val snapshot = LuopanEngine.snapshot(LocalDateTime.now(), zone)
        val natal = ProfileState(applicationContext).yearSign?.branch
        val reading = FortuneEngine.reading(snapshot, FortuneModule.OVERALL, natal)

        val tap = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bestWindow = reading.bestHours.firstOrNull()?.let { " Best window: ${it.range}." } ?: ""
        val text = "${reading.score}/100 — ${reading.headline}$bestWindow"

        NotificationScheduler.ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Your day on the plate")
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
        private const val NOTIFICATION_ID = 41001
    }
}
