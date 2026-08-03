package io.github.shmemcat.shmemplaylist

import org.junit.Assert.assertFalse
import org.junit.Test

class PhaseOneSmokeTest {
    @Test
    fun playlistMutationIsDisabled() {
        assertFalse(PhaseOneSafety.PLAYLIST_MUTATION_ENABLED)
    }
}
