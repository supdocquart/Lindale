package com.metrolist.music.platform

// DESKTOP: features that are unfinished or Android-only are hidden from the UI, not deleted.
object DesktopFeatures {
    const val LISTEN_TOGETHER = false   // ListenTogetherManager is incomplete on desktop
    const val AI_LYRICS_TRANSLATION = false // LyricsTranslationHelper is a stub on desktop
    const val CAST = false              // no Google Cast on desktop
    const val CROSSFADE = false          // VLC engine has no crossfade yet
    const val SKIP_SILENCE = false       // VLC engine has no silence skipping yet
    const val ANDROID_AUDIO_OUTPUT = false // audio offload / AudioTrack params are Android-only
    const val BLUETOOTH_RESUME = false   // Android-only
    const val TASK_CLEAR_STOP = false    // Android-only (no "recent apps" on desktop)
}
