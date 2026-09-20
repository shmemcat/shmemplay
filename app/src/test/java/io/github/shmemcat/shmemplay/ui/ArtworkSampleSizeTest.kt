package io.github.shmemcat.shmemplay.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkSampleSizeTest {
    @Test
    fun preservesNativeArtworkWhenTheViewNeedsIt() {
        assertEquals(1, artworkSampleSize(600, 600, 600, 600))
        assertEquals(1, artworkSampleSize(600, 600, 900, 900))
    }

    @Test
    fun downsamplesLargeArtworkWithoutDroppingBelowTheRenderedSize() {
        assertEquals(4, artworkSampleSize(2400, 2400, 600, 600))
        assertEquals(8, artworkSampleSize(4096, 4096, 400, 400))
    }

    @Test
    fun invalidBoundsFallBackToAFullDecodeAttempt() {
        assertEquals(1, artworkSampleSize(0, 0, 600, 600))
        assertEquals(1, artworkSampleSize(600, 600, 0, 0))
    }
}
