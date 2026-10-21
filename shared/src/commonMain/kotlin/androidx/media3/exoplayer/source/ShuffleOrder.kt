package androidx.media3.exoplayer.source

interface ShuffleOrder {
    class DefaultShuffleOrder(val length: Int = 0, val seed: Long = 0L) : ShuffleOrder {
        constructor(shuffledIndices: IntArray, randomSeed: Long = 0L) : this(shuffledIndices.size, randomSeed)
    }
}
