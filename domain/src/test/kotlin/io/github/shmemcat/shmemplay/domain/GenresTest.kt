package io.github.shmemcat.shmemplay.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GenresTest {
    @Test fun largeArtworkIsSkippedWhileGenresAfterItAreStillRead() {
        val picture = ByteArray(2 * 1024 * 1024) { 1 }
        for (version in listOf(3, 4)) {
            val art = "APIC".toByteArray() + (if (version == 4) sync(picture.size) else int(picture.size)) + byteArrayOf(0, 0) + picture
            var bytesRead = 0
            var bytesSkipped = 0L
            val input = object : java.io.ByteArrayInputStream(tag(version, art + frame(version, "Rock\u0000Dream pop"))) {
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bytesRead += it }
                override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }
                override fun skip(n: Long): Long = super.skip(n).also { bytesSkipped += it }
            }
            assertEquals(listOf("Rock", "Dream pop"), AudioGenreTags.read(input))
            assertEquals(picture.size.toLong(), bytesSkipped)
            assertTrue("Artwork must not be read", bytesRead < 100)
        }
    }
    @Test fun separatorsDeduplicateWithoutSplittingGenrePunctuation() {
        assertEquals(listOf("Rock", "Dream pop", "Pop/Funk", "Rock, experimental"),
            Genres.split(" Rock ; Dream pop\u0000\ufeffrock\\\\Pop/Funk; ;Rock, experimental;Unknown genre"))
    }

    @Test fun musicBeeRepeatedFramesAndId3NullValuesAndLiteralSemicolons() {
        for (version in listOf(3, 4)) {
            val bytes = tag(version, frame(version, "Rock\u0000Dream pop") + frame(version, "Shoegaze; Rock"))
            assertEquals(listOf("Rock", "Dream pop", "Shoegaze"), AudioGenreTags.read(bytes.inputStream()))
        }
    }

    @Test fun utf16AndUtf16BigEndianPreserveAllUnicodeValues() {
        listOf(1 to Charsets.UTF_16, 2 to Charsets.UTF_16BE).forEach { (encoding, charset) ->
            val bytes = tag(3, frame(3, "韓國; ไทย\u0000\ufeffDream pop", encoding, charset))
            assertEquals(listOf("韓國", "ไทย", "Dream pop"), AudioGenreTags.read(bytes.inputStream()))
        }
    }

    @Test fun readsExtendedHeadersAndUnsynchronisedText() {
        val extended3 = int(6) + ByteArray(6)
        assertEquals(listOf("Rock", "Pop"), AudioGenreTags.read(tag(3, extended3 + frame(3, "Rock;Pop"), 0x40).inputStream()))
        val extended4 = sync(6) + byteArrayOf(1, 0)
        assertEquals(listOf("Rock", "Pop"), AudioGenreTags.read(tag(4, extended4 + frame(4, "Rock;Pop"), 0x40).inputStream()))
        // UTF-16 BOM FF FE triggers tag-level ID3v2.3 unsynchronisation.
        val plain = frame(3, "Rock\u0000Pop", 1, Charsets.UTF_16)
        val encoded = ByteArrayOutputStream().apply {
            plain.forEach { write(it.toInt()); if (it == 0xff.toByte()) write(0) }
        }.toByteArray()
        assertEquals(listOf("Rock", "Pop"), AudioGenreTags.read(tag(3, encoded, 0x80).inputStream()))
    }

    @Test fun legacyNumericGenresAndRefinementsAreNamed() {
        assertEquals(listOf("Rock", "Pop", "Dream pop", "Remix"),
            AudioGenreTags.read(tag(3, frame(3, "(17)(13)Dream pop; (RX)")).inputStream()))
        assertEquals(listOf("Rock", "Pop"), AudioGenreTags.read(tag(4, frame(4, "17\u000013")).inputStream()))
    }

    @Test fun flacRepeatedGenreCommentsAreReadWithoutReadingAudio() {
        val comments = comments("GENRE=Rock", "GENRE=Dream pop;Shoegaze", "genre=Rock", "ARTIST=Artist")
        val bytes = "fLaC".toByteArray() + byteArrayOf(0x84.toByte(), 0, (comments.size shr 8).toByte(), comments.size.toByte()) + comments
        assertEquals(listOf("Rock", "Dream pop", "Shoegaze"), AudioGenreTags.read(bytes.inputStream()))
    }

    @Test fun vorbisAndOpusRepeatedCommentsAreReadFromHeaderPackets() {
        for ((head, comment) in listOf(
            byteArrayOf(1) + "vorbis".toByteArray() to byteArrayOf(3) + "vorbis".toByteArray(),
            "OpusHead".toByteArray() to "OpusTags".toByteArray(),
        )) {
            val bytes = page(head) + page(comment + comments("GENRE=Rock", "GENRE=Dream pop"))
            assertEquals(listOf("Rock", "Dream pop"), AudioGenreTags.read(bytes.inputStream()))
        }
    }

    @Test fun boundedAndMalformedTagsFailBeforeReadingUnboundedPayloads() {
        assertTrue(AudioGenreTags.read("other format".byteInputStream()).isEmpty())
        for (bytes in listOf("ID3".toByteArray(), tag(4, byteArrayOf(84, 67, 79, 78, 127, 127, 127, 127, 0, 0)),
            "ID3".toByteArray() + byteArrayOf(4, 0, 0) + sync(32 * 1024 * 1024))) {
            assertTrue(runCatching { AudioGenreTags.read(bytes.inputStream()) }.isFailure)
        }
    }

    private fun frame(version: Int, value: String, encoding: Int = 3, charset: java.nio.charset.Charset = Charsets.UTF_8): ByteArray {
        val payload = byteArrayOf(encoding.toByte()) + value.toByteArray(charset)
        return "TCON".toByteArray() + (if (version == 4) sync(payload.size) else int(payload.size)) + byteArrayOf(0, 0) + payload
    }
    private fun tag(version: Int, body: ByteArray, flags: Int = 0) = "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, flags.toByte()) + sync(body.size) + body
    private fun int(value: Int) = ByteBuffer.allocate(4).putInt(value).array()
    private fun sync(value: Int) = ByteArray(4) { (value ushr ((3 - it) * 7) and 127).toByte() }
    private fun comments(vararg values: String): ByteArray {
        fun le(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        return le(0) + le(values.size) + values.fold(byteArrayOf()) { all, value -> all + le(value.toByteArray().size) + value.toByteArray() }
    }
    private fun page(packet: ByteArray): ByteArray {
        require(packet.size < 255)
        return "OggS".toByteArray() + ByteArray(22) + byteArrayOf(1, packet.size.toByte()) + packet
    }
}
