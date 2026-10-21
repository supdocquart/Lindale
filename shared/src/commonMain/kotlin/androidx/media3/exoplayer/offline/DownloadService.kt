package androidx.media3.exoplayer.offline

import com.metrolist.music.ui.component.DownloadCenter

object DownloadService {
    fun sendRemoveDownload(context: Any?, clazz: Any?, id: String, b: Boolean) = DownloadCenter.remove(id)
    fun sendAddDownload(context: Any?, clazz: Any?, mediaItem: Any?, b: Boolean) {}
}
