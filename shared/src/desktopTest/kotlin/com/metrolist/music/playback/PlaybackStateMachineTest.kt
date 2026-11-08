package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateMachineTest {
    @Test
    fun phasesSetConsistentFlags() {
        assertEquals(PlaybackFlags(Player.STATE_READY, true, true, null), PlaybackPhase.Playing.flags())
        assertEquals(PlaybackFlags(Player.STATE_READY, false, false, null), PlaybackPhase.Paused.flags())
        assertEquals(PlaybackFlags(Player.STATE_ENDED, false, false, null), PlaybackPhase.Ended.flags())
        // Loading the next song keeps the play button and the play intent as they are.
        val buffering = PlaybackPhase.Buffering.flags()
        assertNull(buffering.isPlaying)
        assertNull(buffering.playWhenReady)
        // The engine's stop between songs keeps an error and the play intent.
        val stopped = PlaybackPhase.Stopped.flags()
        assertTrue(stopped.keepsError)
        assertNull(stopped.state)
        val error = PlaybackException(PlaybackException.ERROR_CODE_REMOTE_ERROR, "x")
        assertEquals(error, PlaybackPhase.Failed(error).flags().error)
        assertEquals(Player.STATE_IDLE, PlaybackPhase.Failed(error).flags().state)
    }

    @Test
    fun aStreamResolvedAfterTheUserMovedOnIsDropped() {
        val g = PlayGenerations()
        val first = g.next()
        val second = g.next() // user skipped before the first stream resolved
        assertFalse(g.load(first))
        assertTrue(g.load(second))
        assertTrue(g.loadedIsCurrent())
    }

    @Test
    fun theEndOfAReplacedSongIsNotCurrent() {
        val g = PlayGenerations()
        assertTrue(g.load(g.next()))
        g.next() // user pressed next; the old media may still report "ended"
        assertFalse(g.loadedIsCurrent())
    }

    @Test
    fun unmuteResumesOnlyWhatMutePaused() {
        val m = MutePause()
        assertTrue(m.onMute(enabled = true, playing = true))
        assertTrue(m.onUnmute())
        assertFalse(m.onUnmute())
    }

    @Test
    fun aUserPauseWhileMutedIsNotUndoneByUnmuting() {
        val m = MutePause()
        assertTrue(m.onMute(enabled = true, playing = true))
        m.onUserAction() // played by hand while muted
        m.onUserAction() // then paused by hand
        assertFalse(m.onUnmute())
    }

    @Test
    fun muteDoesNothingWhenOffOrNotPlaying() {
        val m = MutePause()
        assertFalse(m.onMute(enabled = false, playing = true))
        assertFalse(m.onMute(enabled = true, playing = false))
        assertFalse(m.onUnmute())
    }
}
