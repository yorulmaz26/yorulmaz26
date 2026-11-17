package com.kuaforumcepte.app.feature.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.kuaforumcepte.domain.qr.QrCodes

/** ZXing piksellerinden Bitmap ve yazdırılabilir A4 afiş (1240×1754 px ≈ 150 dpi). */
object QrPoster {
    private const val NAVY = 0xFF14243A.toInt()
    private const val GOLD = 0xFFC59A64.toInt()
    private const val INK_MUTED = 0xFF5E6672.toInt()

    fun bitmap(image: QrCodes.QrImage): Bitmap =
        Bitmap.createBitmap(image.pixels, image.size, image.size, Bitmap.Config.ARGB_8888)

    fun poster(qr: Bitmap, salonName: String, link: String, code: String): Bitmap {
        val w = 1240
        val h = 1754
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFFFFFFFF.toInt())
        val band = Paint().apply { color = NAVY }
        c.drawRect(0f, 0f, w.toFloat(), 260f, band)

        fun text(value: String, size: Float, color: Int, y: Float, bold: Boolean = false, serif: Boolean = false): Float {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, p, w - 160).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
            c.save(); c.translate(80f, y); layout.draw(c); c.restore()
            return y + layout.height
        }

        text("Kuaförüm Cepte", 64f, 0xFFFFFFFF.toInt(), 70f, serif = true)
        text("Güzelliğe Bir Dokunuş, Randevuya Tek Tık.", 34f, GOLD, 165f)
        var y = text(salonName, 72f, NAVY, 330f, bold = true, serif = true)
        y = text("Randevu almak için QR kodu telefonunuzun kamerasıyla okutun", 36f, INK_MUTED, y + 24f)
        val qrSize = 820
        val left = (w - qrSize) / 2
        val top = (y + 50f).toInt()
        c.drawBitmap(qr, null, Rect(left, top, left + qrSize, top + qrSize), Paint(Paint.FILTER_BITMAP_FLAG))
        y = text(link, 34f, NAVY, top + qrSize + 40f)
        text("Salon kodu: $code", 32f, INK_MUTED, y + 16f)
        return out
    }
}
