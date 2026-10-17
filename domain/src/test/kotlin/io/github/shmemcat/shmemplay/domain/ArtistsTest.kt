package io.github.shmemcat.shmemplay.domain

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class ArtistsTest {
    @Test fun commaNamesAndBandPunctuationStayIntactByDefault() {
        for (name in listOf("Tyler, the Creator", "Earth, Wind & Fire", "AC/DC", "Simon & Garfunkel", "20,000 Leagues Under The Sea")) {
            assertEquals(listOf(name), Artists.credits(listOf(name)))
        }
    }

    @Test fun featuredCreditsSplitOnlyAtExplicitArtistFieldMarkers() {
        for (credit in listOf("Main feat. Guest", "Main ft Guest", "Main FT. Guest", "Main featuring Guest", "Main (feat. Guest)", "Main [ft. Guest]")) {
            assertEquals(credit, listOf("Main", "Guest"), Artists.credits(listOf(credit)))
        }
        assertEquals(listOf("Tyler, the Creator", "Guest"), Artists.credits(listOf("Tyler, the Creator feat. Guest")))
        assertEquals(listOf("Main", "Guest", "Other"), Artists.credits(listOf("Main feat. Guest ft. Other")))
        assertEquals(listOf("Leftfield"), Artists.credits(listOf("Leftfield")))
    }

    @Test fun explicitlySavedNamesPreserveCommas() {
        assertEquals(listOf("Tyler, the Creator", "Guest"), Artists.credits(listOf("Tyler, the Creator", "Guest")))
        assertEquals(listOf("Tyler, the Creator", "Guest"), Artists.credits(listOf("Tyler, the Creator;Guest")))
        assertEquals(listOf("Main, Guest"), Artists.credits(listOf("Main, Guest")))
        assertEquals(listOf("Main", "Guest"), Artists.split("Main\u0000Guest; MAIN"))
    }

    @Test fun spacedSlashesSeparateArtistsWithoutBreakingAcDc() {
        assertEquals(listOf("AC/DC", "Guest", "Tyler, the Creator"), Artists.credits(listOf("AC/DC / Guest / Tyler, the Creator")))
        assertEquals(listOf("Main", "Guest", "Other"), Artists.credits(listOf("Main / Guest feat. Other")))
        assertEquals(listOf("Main", "Guest", "Other"), Artists.credits(listOf("Main;Guest ft. Other")))
    }

    @Test fun embeddedRepeatedArtistFramesAndNullListsPreserveCommasAndGenres() {
        for (version in listOf(3, 4)) {
            fun size(value: Int) = if (version == 3) ByteBuffer.allocate(4).putInt(value).array() else sync(value)
            fun frame(id: String, text: String): ByteArray {
                val payload = byteArrayOf(3) + text.toByteArray()
                return id.toByteArray() + size(payload.size) + byteArrayOf(0, 0) + payload
            }
            val body = frame("TPE1", "Tyler, the Creator\u0000Guest") + frame("TPE1", "Other; Guest") + frame("TCON", "Rock;Pop")
            val tag = "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, 0) + sync(body.size) + body
            assertEquals(AudioTextTags.Values(listOf("Tyler, the Creator", "Guest", "Other"), listOf("Rock", "Pop")), AudioTextTags.read(tag.inputStream()))
        }
    }

    private fun sync(value: Int) = ByteArray(4) { (value ushr ((3 - it) * 7) and 127).toByte() }
}
