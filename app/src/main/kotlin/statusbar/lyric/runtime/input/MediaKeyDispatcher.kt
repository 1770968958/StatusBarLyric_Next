package statusbar.lyric.runtime.input

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/** Dispatches media key events through AudioManager without spawning shell processes. */
class MediaKeyDispatcher(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun next(): Boolean = dispatch(KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous(): Boolean = dispatch(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun playPause(): Boolean = dispatch(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    fun dispatch(keyCode: Int): Boolean {
        val manager = audioManager ?: return false
        manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return true
    }
}
