package com.laki.daofortune.data

import android.content.Context
import androidx.core.content.edit

/**
 * Tracks the free reading allowance and the one-off unlock.
 *
 * The product rule set by the publisher is simple: the first reading is free,
 * every later reading needs the one-off purchase. The flag lives in shared
 * preferences so it survives a device transfer through Android's auto backup.
 */
class AccessState(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once the one-off purchase has been made. */
    val isUnlocked: Boolean get() = prefs.getBoolean(KEY_UNLOCKED, false)

    /** True once the single free reading has been spent. */
    val freeReadingUsed: Boolean get() = prefs.getBoolean(KEY_FREE_USED, false)

    /** Whether a new reading may be produced without paying. */
    fun canRead(): Boolean = isUnlocked || !freeReadingUsed

    /** How many readings are still available for free. */
    fun freeReadingsLeft(): Int = when {
        isUnlocked -> Int.MAX_VALUE
        freeReadingUsed -> 0
        else -> 1
    }

    /** Spends the free reading. Call only once a reading has actually been shown. */
    fun consumeFreeReading() {
        prefs.edit { putBoolean(KEY_FREE_USED, true) }
    }

    /** Unlocks everything after a successful purchase or restore. */
    fun unlock() {
        prefs.edit { putBoolean(KEY_UNLOCKED, true) }
    }

    companion object {
        private const val PREFS_NAME = "dao_fortune_access"
        private const val KEY_FREE_USED = "free_reading_used"
        private const val KEY_UNLOCKED = "unlocked"
    }
}
