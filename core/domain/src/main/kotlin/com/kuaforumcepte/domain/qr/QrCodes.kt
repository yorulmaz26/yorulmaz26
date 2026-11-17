package com.kuaforumcepte.domain.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Platformdan bağımsız QR üretimi ve taranabilirlik kontrolü (ZXing core).
 * Android tarafı pikselleri Bitmap'e, PDF'e veya yazıcıya aktarır.
 */
object QrCodes {

    /** size×size ARGB piksel dizisi (siyah modüller, beyaz zemin, sessiz bölge dahil). */
    class QrImage(val size: Int, val pixels: IntArray)

    fun encode(content: String, size: Int = 1024, dark: Int = 0xFF14243A.toInt(), light: Int = 0xFFFFFFFF.toInt()): QrImage {
        require(content.isNotBlank()) { "QR içeriği boş olamaz" }
        val hints = mapOf(
            // Yüksek hata düzeltme: basılı/yıpranmış QR'lar ve kısmi kapanma için.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 4,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) dark else light }
        return QrImage(size, pixels)
    }

    /** Üretilen görüntüyü geri okur; içerik birebir eşleşirse true (taranabilirlik kontrolü). */
    fun verify(image: QrImage, expected: String): Boolean = decode(image) == expected

    fun decode(image: QrImage): String? = runCatching {
        val source = RGBLuminanceSource(image.size, image.size, image.pixels)
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.PURE_BARCODE to true)).text
    }.getOrNull()
}
