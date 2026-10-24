package com.metrolist.music.platform

// DESKTOP: features that are unfinished or Android-only are hidden from the UI, not deleted.
object DesktopFeatures {
    const val LISTEN_TOGETHER = false   // ListenTogetherManager is incomplete on desktop
    const val AI_LYRICS_TRANSLATION = false // LyricsTranslationHelper is a stub on desktop
    const val CAST = false              // no Google Cast on desktop
}
