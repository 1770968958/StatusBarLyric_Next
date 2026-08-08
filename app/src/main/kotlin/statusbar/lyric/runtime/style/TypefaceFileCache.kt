package statusbar.lyric.runtime.style

import android.graphics.Typeface
import java.io.File

/** Caches a custom font until its file identity actually changes. */
class TypefaceFileCache {
    private data class FileKey(
        val path: String,
        val lastModified: Long,
        val length: Long
    )

    private var cachedKey: FileKey? = null
    private var cachedTypeface: Typeface? = null

    fun resolve(file: File, fallback: Typeface): Typeface {
        val key = file.takeIf { it.exists() && it.canRead() }?.let {
            FileKey(it.absolutePath, it.lastModified(), it.length())
        }
        if (key != cachedKey) {
            cachedKey = key
            cachedTypeface = key?.let { runCatching { Typeface.createFromFile(file) }.getOrNull() }
        }
        return cachedTypeface ?: fallback
    }

    fun invalidate() {
        cachedKey = null
        cachedTypeface = null
    }
}
