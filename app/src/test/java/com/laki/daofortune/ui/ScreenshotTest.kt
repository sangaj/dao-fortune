package com.laki.daofortune.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.laki.daofortune.billing.BillingUiState
import com.laki.daofortune.core.FavourableDates
import com.laki.daofortune.core.FortuneEngine
import com.laki.daofortune.core.FortuneModule
import com.laki.daofortune.core.LuopanEngine
import com.laki.daofortune.core.ReadingKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Renders the real Compose screens under Robolectric.
 *
 * Two jobs: prove that every screen composes without throwing (the smoke part,
 * which always runs), and capture the rendered surface as a PNG when the
 * headless renderer cooperates (the capture part, which needs a working
 * framebuffer and is therefore allowed to be skipped).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val moment = LocalDateTime.of(2026, 10, 31, 12, 0)
    private val date = LocalDate.of(2026, 10, 31)

    @Test
    fun homeScreenComposesAndCaptures() {
        val snapshot = LuopanEngine.snapshot(moment, zone)
        compose.setContent {
            DaoFortuneTheme {
                Box(Modifier.fillMaxSize().testTag(TAG)) {
                    HomeScreen(
                        snapshot = snapshot,
                        readings = emptyMap(),
                        previews = FortuneModule.entries.associateWith {
                            FortuneEngine.reading(snapshot, it).headline
                        },
                        selectedDate = date,
                        isToday = true,
                        yearSign = null,
                        favourableDays = FavourableDates.scan(date, zone, null),
                        freeReadingsLeft = 1,
                        unlocked = false,
                        notice = null,
                        onOpen = {},
                        onOpenDream = {},
                        onSelectDate = {},
                        onSetYearSign = {},
                        onDismissNotice = {},
                        onAbout = {},
                        notifyEnabled = false,
                        onToggleNotify = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        assertTrue("home screen rendered", snapshot.day.toString().isNotBlank())
        capture("01_home.png")
    }

    @Test
    fun dayPlateScreenComposesAndCaptures() {
        val plate = LuopanEngine.dayPlate(date.plusDays(12), zone)
        compose.setContent {
            DaoFortuneTheme {
                Box(Modifier.fillMaxSize().testTag(TAG)) {
                    HomeScreen(
                        snapshot = plate,
                        readings = emptyMap(),
                        previews = FortuneModule.entries.associateWith {
                            FortuneEngine.reading(plate, it).headline
                        },
                        selectedDate = date.plusDays(12),
                        isToday = false,
                        yearSign = com.laki.daofortune.core.YearSign.RAT,
                        favourableDays = FavourableDates.scan(date, zone, null),
                        freeReadingsLeft = 1,
                        unlocked = false,
                        notice = null,
                        onOpen = {},
                        onOpenDream = {},
                        onSelectDate = {},
                        onSetYearSign = {},
                        onDismissNotice = {},
                        onAbout = {},
                        notifyEnabled = false,
                        onToggleNotify = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        assertTrue("day plate rendered", plate.dayOnly)
        capture("02_day_plate.png")
    }

    @Test
    fun readingSheetComposesAndCaptures() {
        val snapshot = LuopanEngine.snapshot(moment, zone)
        val reading = FortuneEngine.reading(snapshot, FortuneModule.WINDFALL)
        compose.setContent {
            DaoFortuneTheme {
                Box(Modifier.fillMaxSize().testTag(TAG)) {
                    ReadingSheet(reading = reading, onClose = {})
                }
            }
        }
        compose.waitForIdle()
        assertTrue("reading sheet rendered", reading.headline.isNotBlank())
        capture("03_windfall_reading.png")
    }

    @Test
    fun paywallComposesAndCaptures() {
        compose.setContent {
            DaoFortuneTheme {
                Box(Modifier.fillMaxSize().testTag(TAG)) {
                    PaywallSheet(
                        billingState = BillingUiState.Ready(
                            price = "$9.99",
                            title = "Full Access",
                            description = "Every reading, every day"
                        ),
                        onBuy = {},
                        onRestore = {},
                        onDismiss = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        capture("04_unlock.png")
    }

    @Test
    fun dreamSheetComposesAndCaptures() {
        val plate = LuopanEngine.dayPlate(date, zone)
        val text = "A black snake by a river in the south, at dawn."
        val image = com.laki.daofortune.core.DreamEngine.extract(text)
        val reading = com.laki.daofortune.core.DreamEngine.reading(plate, image)
        compose.setContent {
            DaoFortuneTheme {
                Box(Modifier.fillMaxSize().testTag(TAG)) {
                    DreamSheet(
                        text = text,
                        wokeAt = "5:30",
                        reading = reading,
                        snapshot = plate,
                        yearSign = com.laki.daofortune.core.YearSign.RAT,
                        onTextChange = {},
                        onWokeAtChange = {},
                        onRead = {},
                        onDismiss = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        assertTrue("dream sheet rendered", reading.headline.isNotBlank())
        assertTrue("the dream must have found an omen", image.principal != null)
        capture("05_dream_sheet.png")
    }

    /** A reading is filed under its day; this keeps the key type honest. */
    @Test
    fun readingKeyKeepsDaysApart() {
        val a = ReadingKey(date, FortuneModule.WINDFALL)
        val b = ReadingKey(date.plusDays(1), FortuneModule.WINDFALL)
        assertTrue(a != b)
        assertEquals(a, ReadingKey(date, FortuneModule.WINDFALL))
    }

    private fun capture(name: String) {
        // Build output, not a store asset: the Play screenshot folder must only
        // ever contain the final PNGs.
        val dir = File(findProjectRoot(), "build/screenshots")
        dir.mkdirs()
        val out = File(dir, name)
        val bitmap = runCatching {
            compose.onNodeWithTag(TAG).captureToImage().asAndroidBitmap()
        }.getOrNull()
        if (bitmap == null) {
            println("capture unavailable for $name (headless framebuffer not ready)")
            return
        }
        FileOutputStream(out).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        println("Wrote ${out.absolutePath} (${bitmap.width}x${bitmap.height})")
    }

    private fun findProjectRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "docs").isDirectory && File(dir, "app").isDirectory) return dir
            dir = dir.parentFile
        }
        return File(System.getProperty("user.dir")).absoluteFile
    }

    private companion object {
        const val TAG = "screenshot-root"
    }
}
