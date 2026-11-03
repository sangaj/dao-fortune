package com.laki.daofortune.data

import android.content.Context
import androidx.core.content.edit
import com.laki.daofortune.core.Natal
import com.laki.daofortune.core.PickActivity
import com.laki.daofortune.core.YearSign
import java.time.LocalDate

/**
 * The querent's year branch (nian ming), kept in its own preferences file.
 *
 * **Deliberately not in the backup allow-list.** `backup_rules.xml` and
 * `data_extraction_rules.xml` include only `dao_fortune_access.xml`; this file
 * is left out on purpose, so the year sign never leaves the device — not even
 * into Android's cloud backup. It is a single branch out of twelve, it can be
 * cleared from inside the app, and it costs the reader nothing to set again.
 *
 * The free-reading flag and the unlock live in a different file
 * (`dao_fortune_access.xml`) precisely so that adding personal information
 * later can never quietly end up inside the backed-up one.
 */
class ProfileState(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The reader's year sign, or null when they have not chosen one. */
    val yearSign: YearSign?
        get() {
            val ordinal = prefs.getInt(KEY_YEAR_SIGN, -1)
            return if (ordinal < 0) null else YearSign.ofOrdinal(ordinal)
        }

    /** Sets or clears the year sign. */
    fun setYearSign(sign: YearSign?) {
        prefs.edit {
            if (sign == null) remove(KEY_YEAR_SIGN) else putInt(KEY_YEAR_SIGN, sign.ordinal)
        }
    }

    /** True when the reader has turned the daily reading notification on. */
    val dailyReadingEnabled: Boolean get() = prefs.getBoolean(KEY_DAILY_READING, false)

    /** Turns the daily reading notification on or off. */
    fun setDailyReadingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DAILY_READING, enabled) }
    }

    /** True when the first-run introduction has been seen. */
    val onboardingDone: Boolean get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    /** Marks the first-run introduction as seen. */
    fun setOnboardingDone() {
        prefs.edit { putBoolean(KEY_ONBOARDING_DONE, true) }
    }

    /**
     * The activity the forward scan was last ranked for.
     *
     * Remembered so the reader does not pick it again every morning: it is
     * the question they are asking, and asking it once a day is enough. It is
     * a category, not a preference about a person, and it lives in the same
     * backup-excluded file as the year sign.
     *
     * Stored **by name**, not by position in the enum: a new activity added
     * in the middle of the enum would otherwise shift every stored choice
     * one place, and a reader who asked for a wedding date would quietly get
     * their house move ranked instead.
     */
    val lastActivity: PickActivity
        get() {
            val name = prefs.getString(KEY_ACTIVITY, null) ?: return PickActivity.FINANCIAL_MOVE
            return PickActivity.entries.firstOrNull { it.name == name } ?: PickActivity.FINANCIAL_MOVE
        }

    /** Remembers the activity the forward scan is ranked for. */
    fun setActivity(activity: PickActivity) {
        prefs.edit { putString(KEY_ACTIVITY, activity.name) }
    }

    /**
     * The reader's birth day, or null when they have not given one.
     *
     * Optional and strictly local: it lives in this same preferences file,
     * which is excluded from backup, so it never leaves the device. When set,
     * readings anchor on the day pillar of the birth day instead of the year
     * sign — the classical day-master anchor, which is more personal.
     *
     * A date in the future is never returned: a birth day that has not
     * happened yet would anchor the app, the widget and the morning
     * notification on a day that does not exist.
     */
    val birthDate: LocalDate?
        get() {
            val stored = prefs.getString(KEY_BIRTH_DATE, null)?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            } ?: return null
            return Natal.birthDayOrNull(stored, LocalDate.now())
        }

    /**
     * Sets or clears the birth day.
     *
     * A date in the future is refused: nothing is written, and the value that
     * was there before is left alone.
     */
    fun setBirthDate(date: LocalDate?) {
        if (date != null && Natal.birthDayOrNull(date, LocalDate.now()) == null) return
        prefs.edit {
            if (date == null) remove(KEY_BIRTH_DATE) else putString(KEY_BIRTH_DATE, date.toString())
        }
    }

    /**
     * The favourable days the reader asked to be reminded of, as ISO dates.
     *
     * Just dates — which days looked promising is not personal information,
     * and the file is excluded from backup like everything else here.
     */
    val remindedDays: Set<LocalDate>
        get() = prefs.getStringSet(KEY_REMINDED_DAYS, emptySet())
            ?.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }?.toSet()
            ?: emptySet()

    /** Marks or unmarks a day for a reminder. */
    fun setDayReminded(date: LocalDate, reminded: Boolean) {
        val current = remindedDays.map { it.toString() }.toMutableSet()
        if (reminded) current.add(date.toString()) else current.remove(date.toString())
        prefs.edit { putStringSet(KEY_REMINDED_DAYS, current) }
    }

    /**
     * On how many distinct days the app has been opened.
     *
     * It exists for one purpose only: the Play review ask waits until the
     * reader has actually lived with the app for a while.
     */
    val daysOpened: Int get() = prefs.getInt(KEY_DAYS_OPENED, 0)

    /**
     * Counts the app being opened today.
     *
     * One day counts once, no matter how often the app is opened within it:
     * the counter measures how long the reader has had the app, not how
     * busy a day they had.
     */
    fun noteAppOpened(today: LocalDate) {
        val last = prefs.getString(KEY_LAST_OPEN_DAY, null)
        if (last == today.toString()) return
        prefs.edit {
            putInt(KEY_DAYS_OPENED, daysOpened + 1)
            putString(KEY_LAST_OPEN_DAY, today.toString())
        }
    }

    /**
     * The day the app last asked for a Play review, or null when it never has.
     *
     * Written the moment the app asks — whether Google then shows the card or
     * not — so the quota-gated API is never retried in a loop.
     */
    val lastReviewPromptDay: LocalDate?
        get() = prefs.getString(KEY_LAST_REVIEW_PROMPT, null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Records that the app asked for a Play review on [day]. */
    fun setLastReviewPromptDay(day: LocalDate) {
        prefs.edit { putString(KEY_LAST_REVIEW_PROMPT, day.toString()) }
    }

    companion object {
        private const val PREFS_NAME = "dao_fortune_profile"
        private const val KEY_YEAR_SIGN = "year_sign_branch"
        private const val KEY_DAILY_READING = "daily_reading_enabled"
        private const val KEY_BIRTH_DATE = "birth_date"
        private const val KEY_REMINDED_DAYS = "reminded_days"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_ACTIVITY = "last_activity"
        private const val KEY_DAYS_OPENED = "days_opened"
        private const val KEY_LAST_OPEN_DAY = "last_open_day"
        private const val KEY_LAST_REVIEW_PROMPT = "last_review_prompt_day"
    }
}
