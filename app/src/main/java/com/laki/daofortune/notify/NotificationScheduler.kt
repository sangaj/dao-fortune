package com.laki.daofortune.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily reading notification.
 *
 * Everything here is local: WorkManager wakes the app once a day at eight in
 * the morning, the plate is computed on the device, and no network is
 * involved. The reader can turn it off with the same bell they turned it on
 * with, and uninstalling removes the work with the app.
 */
object NotificationScheduler {

    const val CHANNEL_ID = "daily_reading"
    private const val WORK_NAME = "daily_reading_work"

    /** The local time the notification aims for. */
    private val FIRE_AT: LocalTime = LocalTime.of(8, 0)

    /** Schedules (or replaces) the daily work. Idempotent. */
    fun schedule(context: Context) {
        ensureChannel(context)
        val request = PeriodicWorkRequestBuilder<DailyReadingWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Cancels the daily work. Safe to call when nothing is scheduled. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    /** True when the daily work is currently scheduled. */
    fun isScheduled(context: Context): Boolean {
        val infos = WorkManager.getInstance(context.applicationContext)
            .getWorkInfosForUniqueWork(WORK_NAME).get()
        return infos.any { !it.state.isFinished }
    }

    /** Registers the notification channel; harmless to call repeatedly. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily reading",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "One line from the plate, every morning."
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** How long until the next 08:00 local time. */
    private fun initialDelay(): Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(FIRE_AT)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
