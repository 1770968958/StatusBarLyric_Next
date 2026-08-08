package statusbar.lyric.runtime.input

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/** 通过 AudioManager 分发媒体按键，避免启动 shell 进程。 */
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
