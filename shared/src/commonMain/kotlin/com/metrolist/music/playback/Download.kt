package com.metrolist.music.playback

/** Snapshot of one song's offline download. */
data class Download(
    val id: String = "",
    val state: Int = STATE_COMPLETED,
    val progress: Float = 1f,
) {
    companion object {
        const val STATE_QUEUED = 1
        const val STATE_STOPPED = 0
        const val STATE_DOWNLOADING = 2
        const val STATE_COMPLETED = 3
        const val STATE_FAILED = 4
        const val STATE_REMOVING = 5
        const val STATE_RESTARTING = 7
    }
}
