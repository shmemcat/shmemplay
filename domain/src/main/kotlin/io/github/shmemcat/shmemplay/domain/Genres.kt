package io.github.shmemcat.shmemplay.domain

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/** Separators used by MusicBee, ID3v2.4 and Mp3tag. A slash/comma can be part of a genre. */
object Genres {
    fun split(value: String): List<String> = value.replace("\\\\", ";").split(';', '\u0000')
        .map { it.trim { ch -> ch.isWhitespace() || ch == '\ufeff' } }
        .filter { it.isNotEmpty() && it != "<unknown>" && it != "Unknown genre" }
        .distinctBy { it.lowercase(Locale.ROOT) }

    fun join(values: Iterable<String>): String = split(values.joinToString(";")).joinToString("; ")
}

/** Read bounded embedded genre metadata without scanning audio payloads.
 * Android's MediaStore can omit all but the first value. Unsupported/bad tags fall back to it.
 */
object AudioGenreTags {
    private const val MAX_TAG = 16 * 1024 * 1024

    fun read(input: InputStream): List<String> {
        val data = DataInputStream(input)
        val signature = ByteArray(4).also(data::readFully)
        return when {
            String(signature, 0, 3, Charsets.US_ASCII) == "ID3" -> id3(data, signature[3].toInt())
            String(signature, Charsets.US_ASCII) == "fLaC" -> flac(data)
            String(signature, Charsets.US_ASCII) == "OggS" -> ogg(data)
            else -> emptyList()
        }
    }

    private fun id3(data: DataInputStream, version: Int): List<String> {
        require(version in 2..4)
        val revision = data.readUnsignedByte()
        val flags = data.readUnsignedByte()
        require(revision == 0 && (version != 2 || flags and 0x40 == 0))
        val size = sync(ByteArray(4).also(data::readFully), 0)
        require(size <= MAX_TAG)
        // Only old tag-wide unsynchronisation needs a decoded copy. For ordinary tags,
        // seek past artwork instead of reading megabytes of it for every library song.
        if (version < 4 && flags and 0x80 != 0) {
            val body = unsync(ByteArray(size).also(data::readFully))
            return id3Frames(DataInputStream(body.inputStream()), version, flags, body.size)
        }
        return id3Frames(data, version, flags, size)
    }

    private fun id3Frames(data: DataInputStream, version: Int, flags: Int, size: Int): List<String> {
        var remaining = size
        if (version >= 3 && flags and 0x40 != 0) {
            require(remaining >= 4)
            val encoded = ByteArray(4).also(data::readFully)
            val extended = if (version == 4) sync(encoded, 0) - 4 else number(encoded, 0, 4)
            require(extended in 0..(remaining - 4))
            skipFully(data, extended)
            remaining -= 4 + extended
        }
        val values = mutableListOf<String>()
        val header = ByteArray(if (version == 2) 6 else 10)
        while (remaining >= header.size) {
            data.readFully(header)
            remaining -= header.size
            if (header[0] == 0.toByte()) break
            val id = String(header, 0, if (version == 2) 3 else 4, Charsets.US_ASCII)
            if (version == 4 && id.startsWith("3DI")) break // Optional tag footer.
            require(id.matches(Regex("[A-Z0-9]{3,4}")))
            val length = when (version) {
                2 -> number(header, 3, 3)
                3 -> number(header, 4, 4)
                else -> sync(header, 4)
            }
            require(length > 0 && length <= remaining)
            if (id == "TCON" || id == "TCO") {
                val format = if (version == 2) 0 else header[9].toInt() and 255
                val unsupported = if (version == 3) format and 0xc0 else format and 0x0c
                if (unsupported == 0) {
                    var payload = ByteArray(length).also(data::readFully)
                    if (version == 4 && (flags and 0x80 != 0 || format and 2 != 0)) payload = unsync(payload)
                    var start = if ((version == 3 && format and 0x20 != 0) || (version == 4 && format and 0x40 != 0)) 1 else 0
                    if (version == 4 && format and 1 != 0) start += 4
                    if (start < payload.size) {
                        val encoding = when (payload[start].toInt()) {
                            0 -> Charsets.ISO_8859_1
                            1 -> Charsets.UTF_16
                            2 -> Charsets.UTF_16BE
                            3 -> Charsets.UTF_8
                            else -> error("Unknown text encoding")
                        }
                        val text = String(payload, start + 1, payload.size - start - 1, encoding)
                        Genres.split(text).forEach { value -> values += id3Genres(value) }
                    }
                } else skipFully(data, length)
            } else skipFully(data, length)
            remaining -= length
        }
        return Genres.split(values.joinToString(";"))
    }

    private fun flac(data: DataInputStream): List<String> {
        var consumed = 0
        while (consumed < MAX_TAG) {
            val type = data.readUnsignedByte()
            val size = (data.readUnsignedByte() shl 16) or (data.readUnsignedByte() shl 8) or data.readUnsignedByte()
            consumed += size + 4
            require(consumed <= MAX_TAG)
            if (type and 0x7f == 4) return comments(ByteArray(size).also(data::readFully))
            skipFully(data, size)
            if (type and 0x80 != 0) break
        }
        return emptyList()
    }

    private fun ogg(data: DataInputStream): List<String> {
        val packet = ByteArrayOutputStream()
        var consumed = 0
        var serial: Int? = null
        // Only header packets: Vorbis comment is packet 2, OpusTags is packet 2.
        repeat(64) {
            val header = ByteArray(23).also(data::readFully) // OggS already consumed
            require(header[0] == 0.toByte())
            val pageSerial = number(header, 10, 4)
            if (serial == null) serial = pageSerial
            require(serial == pageSerial)
            val lengths = ByteArray(header[22].toInt() and 255).also(data::readFully)
            consumed += 27 + lengths.size
            lengths.forEach { byte ->
                val size = byte.toInt() and 255
                consumed += size
                require(consumed <= MAX_TAG)
                packet.write(ByteArray(size).also(data::readFully))
                if (size < 255) {
                    val bytes = packet.toByteArray()
                    if (bytes.size >= 7 && bytes[0] == 3.toByte() && String(bytes, 1, 6, Charsets.US_ASCII) == "vorbis") return comments(bytes.copyOfRange(7, bytes.size))
                    if (bytes.size >= 8 && String(bytes, 0, 8, Charsets.US_ASCII) == "OpusTags") return comments(bytes.copyOfRange(8, bytes.size))
                    val identification = (bytes.size >= 7 && bytes[0] == 1.toByte() && String(bytes, 1, 6, Charsets.US_ASCII) == "vorbis") ||
                        (bytes.size >= 8 && String(bytes, 0, 8, Charsets.US_ASCII) == "OpusHead")
                    if (!identification) return emptyList()
                    packet.reset()
                }
            }
            require(String(ByteArray(4).also(data::readFully), Charsets.US_ASCII) == "OggS")
        }
        return emptyList()
    }

    private fun comments(bytes: ByteArray): List<String> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun string(): String {
            val size = buffer.int
            require(size in 0..buffer.remaining())
            return String(ByteArray(size).also(buffer::get), Charsets.UTF_8)
        }
        string() // vendor
        val count = buffer.int
        require(count in 0..(buffer.remaining() / 4))
        val values = mutableListOf<String>()
        repeat(count) {
            val comment = string()
            if (comment.substringBefore('=').equals("GENRE", true) && '=' in comment) values += comment.substringAfter('=')
        }
        return Genres.split(values.joinToString(";"))
    }

    private fun unsync(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size)
        var i = 0
        while (i < bytes.size) {
            val value = bytes[i++]
            out.write(value.toInt())
            if (value == 0xff.toByte() && i < bytes.size && bytes[i] == 0.toByte()) i++
        }
        return out.toByteArray()
    }

    private fun sync(bytes: ByteArray, offset: Int): Int {
        var n = 0
        repeat(4) { val b = bytes[offset + it].toInt() and 255; require(b < 128); n = (n shl 7) or b }
        return n
    }

    private fun number(bytes: ByteArray, offset: Int, count: Int): Int {
        var n = 0
        repeat(count) { n = (n shl 8) or (bytes[offset + it].toInt() and 255) }
        return n
    }

    private fun skipFully(data: DataInputStream, count: Int) {
        var left = count
        while (left > 0) { val skipped = data.skipBytes(left); require(skipped > 0); left -= skipped }
    }

    private fun id3Genres(value: String): List<String> {
        if (value.toIntOrNull() != null) return listOf(numericGenre(value.toInt()) ?: value)
        var text = value
        val values = mutableListOf<String>()
        while (text.startsWith('(') && !text.startsWith("((")) {
            val end = text.indexOf(')')
            if (end < 0) break
            val token = text.substring(1, end)
            val genre = when (token) { "RX" -> "Remix"; "CR" -> "Cover"; else -> token.toIntOrNull()?.let(::numericGenre) } ?: break
            values += genre
            text = text.substring(end + 1)
        }
        if (text.isNotBlank()) values += if (text.startsWith("((")) text.drop(1) else text
        return values
    }

    private fun numericGenre(id: Int): String? = id3Names.getOrNull(id)
    private val id3Names = ("Blues|Classic Rock|Country|Dance|Disco|Funk|Grunge|Hip-Hop|Jazz|Metal|New Age|Oldies|Other|Pop|R&B|Rap|Reggae|Rock|Techno|Industrial|Alternative|Ska|Death Metal|Pranks|Soundtrack|Euro-Techno|Ambient|Trip-Hop|Vocal|Jazz+Funk|Fusion|Trance|Classical|Instrumental|Acid|House|Game|Sound Clip|Gospel|Noise|AlternRock|Bass|Soul|Punk|Space|Meditative|Instrumental Pop|Instrumental Rock|Ethnic|Gothic|Darkwave|Techno-Industrial|Electronic|Pop-Folk|Eurodance|Dream|Southern Rock|Comedy|Cult|Gangsta|Top 40|Christian Rap|Pop/Funk|Jungle|Native American|Cabaret|New Wave|Psychadelic|Rave|Showtunes|Trailer|Lo-Fi|Tribal|Acid Punk|Acid Jazz|Polka|Retro|Musical|Rock & Roll|Hard Rock|Folk|Folk-Rock|National Folk|Swing|Fast Fusion|Bebob|Latin|Revival|Celtic|Bluegrass|Avantgarde|Gothic Rock|Progressive Rock|Psychedelic Rock|Symphonic Rock|Slow Rock|Big Band|Chorus|Easy Listening|Acoustic|Humour|Speech|Chanson|Opera|Chamber Music|Sonata|Symphony|Booty Bass|Primus|Porn Groove|Satire|Slow Jam|Club|Tango|Samba|Folklore|Ballad|Power Ballad|Rhythmic Soul|Freestyle|Duet|Punk Rock|Drum Solo|A capella|Euro-House|Dance Hall|Goa|Drum & Bass|Club-House|Hardcore|Terror|Indie|BritPop|Negerpunk|Polsk Punk|Beat|Christian Gangsta Rap|Heavy Metal|Black Metal|Crossover|Contemporary Christian|Christian Rock|Merengue|Salsa|Thrash Metal|Anime|JPop|SynthPop").split('|')
}
