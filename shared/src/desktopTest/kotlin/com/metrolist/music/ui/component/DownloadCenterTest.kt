package com.metrolist.music.ui.component

import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.Download
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DownloadCenterTest {
    companion object {
        private lateinit var music: File

        @BeforeClass
        @JvmStatic
        fun home() {
            // DownloadCenter keeps files in ~/Music/Lindale; point the home folder somewhere temporary
            // before the object is first touched.
            val home = Files.createTempDirectory("lindale-test").toFile()
            System.setProperty("user.home", home.absolutePath)
            music = File(home, "Music/Lindale").apply { mkdirs() }
        }
    }

    private val id = "abcdefghijk"
    private val meta = MediaMetadata(id = id, title = "Song", artists = emptyList(), duration = 100)

    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(20)
    }

    @Test
    fun removingAndDownloadingAgainKeepsTheNewDownload() {
        val existing = File(music, "Artist - Song [$id].m4a").apply { writeText("audio") }
        DownloadCenter.engine = object : DownloadCenter.Engine {
            override suspend fun resolve(meta: MediaMetadata): DownloadCenter.StreamSource = awaitCancellation()
            override suspend fun markDownloaded(songId: String, downloaded: Boolean) = Unit
        }

        // Before: remove() cleared the state in the background and could wipe the new download's state.
        DownloadCenter.remove(id)
        DownloadCenter.enqueue(meta)
        waitFor { DownloadCenter.downloads.value[id]?.state == Download.STATE_DOWNLOADING }

        assertEquals(Download.STATE_DOWNLOADING, DownloadCenter.downloads.value[id]?.state)
        assertFalse(existing.exists())

        DownloadCenter.remove(id)
        waitFor { DownloadCenter.downloads.value[id] == null }
        assertNull(DownloadCenter.downloads.value[id])
    }
}
