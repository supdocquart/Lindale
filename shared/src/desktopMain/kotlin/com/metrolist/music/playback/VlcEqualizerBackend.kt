package com.metrolist.music.playback

import com.metrolist.music.eq.EqualizerBackend
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer

/** Maps EQ profiles onto libVLC's 10-band graphic equaliser. */
class VlcEqualizerBackend(
    private val factory: MediaPlayerFactory,
    private val player: MediaPlayer,
) : EqualizerBackend {
    override val bandFrequencies: List<Float> = factory.equalizer().bands()
    override val maxGainDb: Float = 20f

    override fun apply(preampDb: Float, gainsDb: List<Float>) {
        val eq = factory.equalizer().newEqualizer()
        eq.setPreamp(preampDb)
        gainsDb.forEachIndexed { index, gain -> if (index < bandFrequencies.size) eq.setAmp(index, gain) }
        player.audio().setEqualizer(eq)
    }

    override fun disable() {
        player.audio().setEqualizer(null)
    }
}
