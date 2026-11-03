package com.laki.daofortune.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the app's two standing notifications: the daily reading every
 * morning, and the look at the week ahead on Sunday evening.
 *
 * Everything here is local: WorkManager wakes the app, the plate is computed
 * on the device, and no network is involved. The reader can turn both off
 * with the same bell they turned them on with, and uninstalling removes the
 * work with the app.
 */
object NotificationScheduler {

    const val CHANNEL_ID = "daily_reading"
    const val REMINDER_CHANNEL_ID = "day_reminder"
    const val WEEK_CHANNEL_ID = "week_ahead"
    private const val WORK_NAME = "daily_reading_work"
    private const val WEEK_WORK_NAME = "week_ahead_work"
    private const val REMINDER_PREFIX = "day_reminder_"

    /** The local time the notification aims for. */
    private val FIRE_AT: LocalTime = LocalTime.of(8, 0)

    /**
     * Schedules (or replaces) the daily work — and the Sunday-evening outlook
     * with it. Both ride on the one bell, so the reader has a single switch
     * for "the app may nudge me"; idempotent.
     */
    fun schedule(context: Context) {
        ensureChannel(context)
        val request = PeriodicWorkRequestBuilder<DailyReadingWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)

        ensureWeekChannel(context)
        val weekRequest = PeriodicWorkRequestBuilder<WeekAheadWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(weekInitialDelay())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WEEK_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, weekRequest)
    }

    /** Cancels the daily work and the weekly outlook. Safe when nothing is scheduled. */
    fun cancel(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.cancelUniqueWork(WORK_NAME)
        manager.cancelUniqueWork(WEEK_WORK_NAME)
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

    /** How long until the next Sunday at 20:00 local time. */
    private fun weekInitialDelay(): Duration {
        val now = LocalDateTime.now()
        return Duration.between(now, com.laki.daofortune.core.WeekAhead.nextFire(now))
    }

    /** Registers the week-ahead channel; harmless to call repeatedly. */
    fun ensureWeekChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(WEEK_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                WEEK_CHANNEL_ID,
                "The week ahead",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Sunday evening: the best day of the coming week."
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules a one-off reminder for eight in the morning of [date].
     *
     * A plain WorkManager delay is used on purpose instead of an exact alarm:
     * the reminder is a nudge, not a medicine dose, and the exact-alarm
     * permission would put the app on a sensitive-permissions list for no
     * real gain.
     */
    fun scheduleDayReminder(context: Context, date: java.time.LocalDate) {
        ensureReminderChannel(context)
        val now = LocalDateTime.now()
        val fireAt = date.atTime(FIRE_AT)
        if (!fireAt.isAfter(now)) return
        val request = OneTimeWorkRequestBuilder<DayReminderWorker>()
            .setInitialDelay(Duration.between(now, fireAt))
            .setInputData(
                androidx.work.Data.Builder().putString(DayReminderWorker.KEY_DATE, date.toString()).build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(REMINDER_PREFIX + date, ExistingWorkPolicy.REPLACE, request)
    }

    /** Cancels the reminder for [date]. Safe to call when none is scheduled. */
    fun cancelDayReminder(context: Context, date: java.time.LocalDate) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(REMINDER_PREFIX + date)
    }

    /** Registers the reminder channel; harmless to call repeatedly. */
    fun ensureReminderChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                "Favourable day reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A note on the morning of a day you marked."
            }
            manager.createNotificationChannel(channel)
        }
    }
}
