package androidx.media3.exoplayer.offline

const val STATE_STOPPED = 0
const val STATE_DOWNLOADING = 2
const val STATE_COMPLETED = 3
const val STATE_FAILED = 4
const val STATE_REMOVING = 5
const val STATE_RESTARTING = 7
const val STATE_QUEUED = 1
const val FAILURE_REASON_NONE = 0
const val STOP_REASON_NONE = 0

data class Download(
    val id: String = "",
    val state: Int = STATE_COMPLETED,
    val progress: Float = 1f,
) {
    companion object {
        const val STATE_STOPPED = 0
        const val STATE_DOWNLOADING = 2
        const val STATE_COMPLETED = 3
        const val STATE_FAILED = 4
        const val STATE_REMOVING = 5
        const val STATE_RESTARTING = 7
        const val STATE_QUEUED = 1
        const val FAILURE_REASON_NONE = 0
        const val STOP_REASON_NONE = 0
    }
}
