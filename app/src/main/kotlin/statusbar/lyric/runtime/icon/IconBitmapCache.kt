package statusbar.lyric.runtime.icon

import android.graphics.Bitmap
import android.util.LruCache

class IconBitmapCache(
    maxBytes: Int = DEFAULT_MAX_BYTES
) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    @Synchronized
    fun getOrDecode(source: String): Bitmap? {
        if (source.isBlank()) return null
        cache.get(source)?.let { return it }
        val bitmap = IconBitmapDecoder.decode(source) ?: return null
        cache.put(source, bitmap)
        return bitmap
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Int = 4 * 1024 * 1024
    }
}

object SharedLyricIconBitmapCache {
    val instance = IconBitmapCache()
}
