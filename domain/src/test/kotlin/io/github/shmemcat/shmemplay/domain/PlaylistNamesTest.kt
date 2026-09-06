package io.github.shmemcat.shmemplay.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaylistNamesTest {
    @Test fun `adds m3u extension once`() {
        assertEquals("Road trip.m3u", PlaylistNames.fileName(" Road trip "))
        assertEquals("Road trip.m3u8", PlaylistNames.fileName("Road trip.m3u8"))
    }

    @Test fun `rejects separators and reserved names`() {
        assertThrows(IllegalArgumentException::class.java) { PlaylistNames.fileName("a/b") }
        assertThrows(IllegalArgumentException::class.java) { PlaylistNames.fileName("CON") }
    }
}
