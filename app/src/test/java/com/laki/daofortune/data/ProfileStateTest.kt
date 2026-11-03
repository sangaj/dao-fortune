package com.laki.daofortune.data

import android.content.Context
import com.laki.daofortune.core.PickActivity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Locks the remembered activity.
 *
 * The reader picks what they are planning, and the forward scan is ranked for
 * it — so the choice has to survive until tomorrow. It is stored **by name**:
 * a new activity added in the middle of the enum would otherwise shift every
 * stored choice one place along, and a reader who asked about a wedding would
 * quietly get their house move ranked instead.
 */
@RunWith(RobolectricTestRunner::class)
// SDK 35, like the other Robolectric test here: SDK 36 wants Java 21 and
// this machine runs the portable JDK 17.
@Config(sdk = [35])
class ProfileStateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // A fresh file for every test.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `nothing chosen yet means the windfall scan`() {
        assertEquals(PickActivity.FINANCIAL_MOVE, ProfileState(context).lastActivity)
    }

    @Test
    fun `the choice is remembered`() {
        ProfileState(context).setActivity(PickActivity.LOVE_WEDDING)
        assertEquals(PickActivity.LOVE_WEDDING, ProfileState(context).lastActivity)
    }

    @Test
    fun `the choice is stored by name and not by position`() {
        ProfileState(context).setActivity(PickActivity.SIGNING)
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVITY, null)
        assertEquals("SIGNING", raw)
    }

    @Test
    fun `a stored name that no longer exists falls back to the default`() {
        // As if the enum had been edited under an old install.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVITY, "SOMETHING_ELSE").commit()
        assertEquals(PickActivity.FINANCIAL_MOVE, ProfileState(context).lastActivity)
    }

    private companion object {
        const val PREFS = "dao_fortune_profile"
        const val KEY_ACTIVITY = "last_activity"
    }
}
