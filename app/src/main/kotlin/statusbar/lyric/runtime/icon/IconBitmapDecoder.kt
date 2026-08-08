package statusbar.lyric.runtime.icon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/** Shared defensive decoder for lyric icons received as Base64 strings. */
object IconBitmapDecoder {
    private const val MAX_BASE64_CHARS = 700_000
    private const val MAX_COMPRESSED_BYTES = 524_288
    private const val MAX_SOURCE_DIMENSION = 2_048
    private const val MAX_DECODED_DIMENSION = 512

    fun decode(base64: String): Bitmap? {
        if (base64.isBlank()) return null

        return runCatching {
            val raw = base64.substringAfter("base64,", base64).trim()
            if (raw.length > MAX_BASE64_CHARS) return@runCatching null

            val bytes = Base64.decode(raw, Base64.DEFAULT)
            if (bytes.size > MAX_COMPRESSED_BYTES) return@runCatching null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            if (bounds.outWidth > MAX_SOURCE_DIMENSION || bounds.outHeight > MAX_SOURCE_DIMENSION) {
                return@runCatching null
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (
            width / sampleSize > MAX_DECODED_DIMENSION ||
            height / sampleSize > MAX_DECODED_DIMENSION
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
