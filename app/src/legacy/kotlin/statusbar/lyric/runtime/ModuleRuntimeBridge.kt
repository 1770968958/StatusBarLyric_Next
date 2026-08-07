package statusbar.lyric.runtime

import statusbar.lyric.tools.ActivityTools

object ModuleRuntimeBridge {
    fun initialize(onActivationChanged: (Boolean) -> Unit) {
        onActivationChanged(ActivityTools.isHook())
    }
}
