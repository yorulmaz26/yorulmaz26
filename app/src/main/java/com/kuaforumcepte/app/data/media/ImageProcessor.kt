package com.kuaforumcepte.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.di.DefaultDispatcher
import com.kuaforumcepte.app.data.registration.DraftImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/** Logo kare (1:1), mekan fotoğrafı 4:3 kaydedilir. */
enum class ImageKind(val aspect: Float, val maxLongSidePx: Int) { LOGO(1f, 1024), PHOTO(4f / 3f, 1600) }

/** CROP: hedef orana ortadan kırp; FIT: tamamını sığdır, boşlukları açık zeminle doldur (en-boy oranı korunur). */
enum class FitMode { CROP, FIT }

/**
 * Seçilen görseli doğrular (tür, boyut), EXIF yönünü düzeltir, oranı koruyarak kırpar/sığdırır,
 * küçültür ve JPEG olarak sıkıştırır. Çıktı < 2 MB hedeflenir (sunucu sınırı 5 MB).
 */
@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val allowedTypes = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

    suspend fun process(source: Uri, kind: ImageKind, mode: FitMode, outDir: File): DraftImage = withContext(dispatcher) {
        val resolver = context.contentResolver
        val type = resolver.getType(source)
        if (type == null || type !in allowedTypes) throw AppException("IMAGE_TYPE_NOT_SUPPORTED")
        val size = resolver.query(source, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
        if (size != null && size > MAX_INPUT_BYTES) throw AppException("IMAGE_TOO_LARGE")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw AppException("IMAGE_DECODE_FAILED")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= kind.maxLongSidePx) sample *= 2
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw AppException("IMAGE_DECODE_FAILED")

        val orientation = resolver.openInputStream(source)?.use {
            runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrNull()
        } ?: ExifInterface.ORIENTATION_NORMAL
        val upright = rotate(decoded, orientation)

        val framed = frame(upright, kind, mode)
        if (framed !== upright) upright.recycle()

        val out = File(outDir, "${kind.name.lowercase()}_${UUID.randomUUID()}.jpg")
        var quality = 85
        do {
            FileOutputStream(out).use { framed.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            quality -= 10
        } while (out.length() > TARGET_OUTPUT_BYTES && quality >= 55)
        val result = DraftImage(out.absolutePath, null, framed.width, framed.height, out.length())
        framed.recycle()
        result
    }

    private fun rotate(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it !== src) src.recycle() }
    }

    private fun frame(src: Bitmap, kind: ImageKind, mode: FitMode): Bitmap {
        val targetW: Int
        val targetH: Int
        val longSide = min(kind.maxLongSidePx, max(src.width, src.height))
        if (kind.aspect >= 1f) { targetW = longSide; targetH = (longSide / kind.aspect).toInt() } else {
            targetH = longSide; targetW = (longSide * kind.aspect).toInt()
        }
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val srcAspect = src.width.toFloat() / src.height
        when (mode) {
            FitMode.CROP -> {
                val crop = if (srcAspect > kind.aspect) {
                    val w = (src.height * kind.aspect).toInt()
                    Rect((src.width - w) / 2, 0, (src.width - w) / 2 + w, src.height)
                } else {
                    val h = (src.width / kind.aspect).toInt()
                    Rect(0, (src.height - h) / 2, src.width, (src.height - h) / 2 + h)
                }
                canvas.drawBitmap(src, crop, RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()), paint)
            }
            FitMode.FIT -> {
                canvas.drawColor(BACKGROUND)
                val scale = min(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
                val w = src.width * scale
                val h = src.height * scale
                val left = (targetW - w) / 2
                val top = (targetH - h) / 2
                canvas.drawBitmap(src, null, RectF(left, top, left + w, top + h), paint)
            }
        }
        return out
    }

    companion object {
        const val MAX_INPUT_BYTES = 20L * 1024 * 1024
        const val TARGET_OUTPUT_BYTES = 2L * 1024 * 1024
        private const val BACKGROUND = 0xFFF7F5F1.toInt()
    }
}
