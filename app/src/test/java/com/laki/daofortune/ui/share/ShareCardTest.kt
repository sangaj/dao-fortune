package com.laki.daofortune.ui.share

import androidx.activity.ComponentActivity
import com.laki.daofortune.core.FortuneEngine
import com.laki.daofortune.core.FortuneModule
import com.laki.daofortune.core.LuopanEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The share card is rendered off-screen, so a broken renderer would fail
 * silently on the device. This test draws the card once and checks the
 * bitmap is the right size and not a blank page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardTest {

    @Test
    fun cardRendersAt1080x1920AndIsNotBlank() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val snapshot = LuopanEngine.snapshot(
            LocalDateTime.of(2026, 3, 15, 10, 0), ZoneId.of("Asia/Shanghai")
        )
        val reading = FortuneEngine.reading(snapshot, FortuneModule.OVERALL)
        val bitmap = ShareImage.render(activity) {
            ShareCard(
                moduleTitle = reading.module.title,
                contextLabel = reading.contextLabel,
                score = reading.score,
                verdict = reading.verdict,
                headline = reading.headline,
                summary = reading.summary
            )
        }
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)

        // A blank render would be one solid colour; sample a grid and
        // require at least two distinct colours across the card.
        val colours = mutableSetOf<Int>()
        for (x in 0 until bitmap.width step 90) {
            for (y in 0 until bitmap.height step 120) {
                colours.add(bitmap.getPixel(x, y))
            }
        }
        assertTrue("the card should not be a single flat colour", colours.size > 1)
    }
}
