package com.laki.daofortune.data

import android.content.Context
import androidx.core.content.edit
import com.laki.daofortune.core.YearSign

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

    companion object {
        private const val PREFS_NAME = "dao_fortune_profile"
        private const val KEY_YEAR_SIGN = "year_sign_branch"
    }
}
