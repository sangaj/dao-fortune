package com.laki.daofortune.ui.share

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.laki.daofortune.ui.DaoFortuneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Renders a composable off-screen to a Bitmap, then hands it to the share
 * sheet or the gallery.
 *
 * Rendering happens at a forced density of 3.0 (densityDpi 480), so a card
 * designed at 360×640 dp always produces a 1080×1920 px bitmap no matter
 * which device it runs on. The view is measured and laid out manually and
 * never attached to a window.
 *
 * Rendering needs a window, so it happens on the main thread; the PNG is
 * compressed and written to disk **off** the main thread, because a 1080×1920
 * image takes long enough to be felt as a stutter.
 */
object ShareImage {

    private const val WIDTH_PX = 1080
    private const val HEIGHT_PX = 1920

    /** The cache folder the share cards live in. */
    private const val SHARE_DIR = "share"

    /** Every card gets its own name, so no two shares share a file. */
    private const val FILE_PREFIX = "dao_fortune_card_"

    /** A card older than this is swept away. */
    private const val MAX_CARD_AGE_MS = 2 * 60 * 60 * 1000L

    /** Makes the file names unique even inside the same millisecond. */
    private val shareCounter = AtomicLong(0)

    /** Walks the wrapper chain to the hosting activity, if there is one. */
    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * Draws [content] into a 1080×1920 bitmap. Must be called on the main
     * thread with an [Activity] context: Compose needs a window to compose
     * against, so the view is briefly added to the window's decor view as a
     * zero-size child, measured, laid out and drawn manually, then removed
     * before the function returns. Nothing of it is ever visible.
     */
    fun render(context: Context, content: @Composable () -> Unit): Bitmap {
        val activity = context.findActivity()
            ?: throw IllegalArgumentException("render needs an Activity context")
        val config = Configuration(context.resources.configuration)
        config.densityDpi = 480
        val renderContext = activity.createConfigurationContext(config)

        val view = ComposeView(renderContext)
        view.setContent {
            DaoFortuneTheme(darkTheme = true) {
                content()
            }
        }
        val decor = activity.window.decorView as ViewGroup
        // Compose needs a lifecycle owner on the window to build its
        // recomposer. A ComponentActivity sets one; set it ourselves when the
        // host did not (plain Activity, some test harnesses).
        if (decor.findViewTreeLifecycleOwner() == null && activity is LifecycleOwner) {
            decor.setViewTreeLifecycleOwner(activity as LifecycleOwner)
        }
        if (decor.findViewTreeViewModelStoreOwner() == null && activity is ViewModelStoreOwner) {
            decor.setViewTreeViewModelStoreOwner(activity as ViewModelStoreOwner)
        }
        if (decor.findViewTreeSavedStateRegistryOwner() == null && activity is SavedStateRegistryOwner) {
            decor.setViewTreeSavedStateRegistryOwner(activity as SavedStateRegistryOwner)
        }
        decor.addView(view, ViewGroup.LayoutParams(0, 0))
        try {
            val w = View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY)
            val h = View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY)
            view.measure(w, h)
            view.layout(0, 0, WIDTH_PX, HEIGHT_PX)

            val bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            return bitmap
        } finally {
            decor.removeView(view)
            view.disposeComposition()
        }
    }

    /** Opens the system share sheet with the card as a PNG. */
    suspend fun share(context: Context, bitmap: Bitmap) {
        val file = withContext(Dispatchers.IO) { writeCard(context, bitmap) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // The share sheet has to be opened on the main thread.
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Writes the card into the cache under a name of its own.
     *
     * Every share gets a **fresh file name**: two shares in a row must not
     * overwrite each other, or the second share would hand out the first
     * picture. The counter makes the name unique even inside the same
     * millisecond.
     */
    private fun writeCard(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, SHARE_DIR)
        dir.mkdirs()
        sweepOldCards(dir)
        val file = File(dir, "$FILE_PREFIX${System.currentTimeMillis()}_${shareCounter.incrementAndGet()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    /**
     * Throws away the cards that are old enough that nobody is still sharing
     * them, so the cache does not grow without end.
     */
    private fun sweepOldCards(dir: File) {
        val cutoff = System.currentTimeMillis() - MAX_CARD_AGE_MS
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    /**
     * Saves the card to the user's gallery. Uses MediaStore, which needs no
     * permission on API 29+; on older versions it returns false and the caller
     * hides the button.
     */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, title: String): Boolean =
        withContext(Dispatchers.IO) { saveToGalleryNow(context, bitmap, title) }

    private fun saveToGalleryNow(context: Context, bitmap: Bitmap, title: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$title.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DaoFortune")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return false
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
