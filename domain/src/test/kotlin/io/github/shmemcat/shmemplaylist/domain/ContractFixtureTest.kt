package io.github.shmemcat.shmemplaylist.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractFixtureTest {
    private val loader = FixtureLoader()

    @Test
    fun `fixture index names every Phase 2 contract`() {
        val ids = loader.json("manifest.json")["contracts"]!!.jsonArray.map {
            it.jsonObject.string("id")
        }
        assertEquals(
            listOf(
                "m3u-parser-v1",
                "phone-path-v1",
                "semantic-checksum-v1",
                "m3u-writer-v1",
                "canonical-gonemad-profile-v1",
                "playlist-operations-v1",
            ),
            ids,
        )
    }

    @Test
    fun `parser fixtures match exact desktop behavior`() {
        val cases = loader.json("parser/m3u-parser-v1.json")["cases"]!!.jsonArray
        cases.forEach { element ->
            val case = element.jsonObject
            val result = M3uParserV1.parse(
                loader.bytes(case.string("input")),
                "fixture.m3u",
                preserveExtendedInfo = case.boolOrDefault("preserveExtendedInfo", true),
            )
            if ("errorCode" in case) {
                val failure = requireType<ParseResult.Failure>(result)
                assertEquals(case.string("errorCode"), failure.error.code)
                if ("errorLine" in case) {
                    assertEquals(
                        case.int("errorLine"),
                        requireType<ParseError.InvalidNormalizedPath>(failure.error).lineNumber,
                    )
                }
            } else {
                val records = requireType<ParseResult.Success>(result).records
                val expected = case["records"]!!.jsonArray
                assertEquals(expected.size, records.size)
                expected.zip(records).forEach { (expectedElement, actual) ->
                    val item = expectedElement.jsonObject
                    assertEquals(item.int("line"), actual.lineNumber)
                    assertEquals(item.string("source"), actual.source)
                    assertEquals(item.string("normalized"), actual.normalizedPath)
                    if ("duration" in item) {
                        val duration = item["duration"]!!.jsonPrimitive
                            .takeUnless { it.content == "null" }?.int
                        assertEquals(duration, actual.extInf?.durationSeconds)
                        assertEquals(item.nullableString("title"), actual.extInf?.title)
                    }
                }
            }
        }
    }

    @Test
    fun `normalization and comparison fixtures match`() {
        val root = loader.json("normalization/phone-path-v1.json")
        root["cases"]!!.jsonArray.forEach {
            val case = it.jsonObject
            assertEquals(
                case.nullableString("expected"),
                PhonePathV1.normalize(case.nullableString("input")),
            )
        }
        root["comparison"]!!.jsonArray.forEach {
            val case = it.jsonObject
            val left = VolumePath(VolumeId(case.string("leftVolume")), case.string("left"))
            val right = VolumePath(VolumeId(case.string("rightVolume")), case.string("right"))
            assertEquals(
                case.bool("matches"),
                VolumeAwareComparison.matches(
                    left,
                    right,
                    PathCasePolicy.valueOf(case.string("policy")),
                ),
            )
        }
    }

    @Test
    fun `checksum fixtures match`() {
        loader.json("checksums/semantic-checksum-v1.json")["cases"]!!.jsonArray.forEach {
            val case = it.jsonObject
            val paths = case.stringList("paths")
            val expected = case.string("sha256")
            assertEquals(expected, SemanticChecksumV1.ofNormalizedPaths(paths))
        }
    }

    @Test
    fun `writer fixtures match exact bytes`() {
        loader.json("writer/m3u-writer-v1.json")["cases"]!!.jsonArray.forEach {
            val case = it.jsonObject
            assertArrayEquals(
                loader.bytes(case.string("output")),
                M3uWriterV1.write(
                    case.stringList("paths"),
                    case.nullableString("prefix"),
                    case.boolOrDefault("includeTrailingNewline", true),
                ),
            )
        }
    }

    @Test
    fun `canonical GoneMAD eligibility fixtures match typed reasons`() {
        loader.json("gonemad/canonical-gonemad-profile-v1.json")["cases"]!!.jsonArray.forEach {
            val case = it.jsonObject
            val result = CanonicalGoneMadProfileV1.validate(loader.bytes(case.string("input")))
            assertEquals(case.bool("writable"), result.writable)
            assertEquals(case.stringList("reasonCodes"), result.reasons.map { reason -> reason.code })
        }
    }

    @Test
    fun `operation fixtures preserve order and duplicate semantics`() {
        val root = loader.json("operations/playlist-operations-v1.json")
        root["cases"]!!.jsonArray.forEach {
            val case = it.jsonObject
            val volume = VolumeId(case.string("volume"))
            val policy = PathCasePolicy.valueOf(case.string("policy"))
            val paths = case.stringList("paths").map { path -> VolumePath(volume, path) }
            val target = VolumePath(VolumeId(case.string("targetVolume")), case.string("target"))
            assertEquals(
                case["indexes"]!!.jsonArray.map { index -> index.jsonPrimitive.int },
                PlaylistOperationsV1.occurrenceIndexes(paths, target, policy),
            )
            assertEquals(
                case.stringList("add"),
                PlaylistOperationsV1.addOneIfAbsent(paths, target, policy).map(VolumePath::normalizedPath),
            )
            assertEquals(
                case.stringList("remove"),
                PlaylistOperationsV1.removeAll(paths, target, policy).map(VolumePath::normalizedPath),
            )
            if ("addVolumes" in case) {
                assertEquals(
                    case.stringList("addVolumes"),
                    PlaylistOperationsV1.addOneIfAbsent(paths, target, policy).map { it.volume.value },
                )
            }
        }
    }

    @Test
    fun `canonical output plan produces eligible absolute primary path`() {
        val path = CanonicalAbsolutePrimaryPathPlan.generate("Music/Artist/Song.mp3")
        val output = M3uWriterV1.write(listOf(path))
        assertEquals("/storage/emulated/0/Music/Artist/Song.mp3", path)
        assertTrue(CanonicalGoneMadProfileV1.validate(output).writable)
    }

    @Test
    fun `parser enforces limits with typed errors`() {
        val lineFailure = M3uParserV1.parse(
            "a\nb\n".toByteArray(),
            "x.m3u",
            ParserLimits(maxLines = 1),
        )
        assertEquals(
            ParseError.LimitExceeded(ParseLimit.LINES, 1),
            requireType<ParseResult.Failure>(lineFailure).error,
        )
        requireType<ParseError.MissingDisplayName>(
            requireType<ParseResult.Failure>(
                M3uParserV1.parse(byteArrayOf(), " "),
            ).error,
        )
    }

    @Test
    fun `parser leaves caller stream open`() {
        val stream = TrackingInputStream("Music/A.mp3\n".toByteArray())
        requireType<ParseResult.Success>(M3uParserV1.parse(stream, "x.m3u"))
        assertFalse(stream.closed)
        assertEquals(-1, stream.read())
    }

    @Test
    fun `preserve extended info false consumes but omits metadata`() {
        val result = requireType<ParseResult.Success>(
            M3uParserV1.parse(
                loader.bytes("parser/bytes/extinf-cases.m3u"),
                "x.m3u",
                preserveExtendedInfo = false,
            ),
        )
        assertTrue(result.records.all { it.extInf == null })
        assertEquals(6, result.records.size)
    }

    @Test
    fun `writer rejects invalid records and supports trailing newline option`() {
        loader.json("writer/m3u-writer-v1.json")["rejections"]!!.jsonArray.forEach {
            val value = it.jsonObject.string("path")
            var thrown = false
            try {
                M3uWriterV1.write(listOf(value))
            } catch (_: IllegalArgumentException) {
                thrown = true
            }
            assertTrue(thrown)
        }
        assertArrayEquals(
            "Music/A.mp3".toByteArray(),
            M3uWriterV1.write(listOf("Music/A.mp3"), includeTrailingNewline = false),
        )
    }

    @Test
    fun `case-sensitive policy differs while volumes always isolate`() {
        val upper = VolumePath(VolumeId("primary"), "Music/A.mp3")
        val lower = VolumePath(VolumeId("primary"), "music/a.mp3")
        val removable = lower.copy(volume = VolumeId("ABCD-1234"))
        assertFalse(VolumeAwareComparison.matches(upper, lower, PathCasePolicy.SENSITIVE))
        assertTrue(VolumeAwareComparison.matches(upper, lower, PathCasePolicy.INSENSITIVE))
        assertFalse(VolumeAwareComparison.matches(upper, removable, PathCasePolicy.INSENSITIVE))
    }
}

private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    var closed = false

    override fun close() {
        closed = true
        super.close()
    }
}

private class FixtureLoader {
    private val classLoader = requireNotNull(javaClass.classLoader)

    fun bytes(path: String): ByteArray =
        requireNotNull(classLoader.getResourceAsStream(path)) { "Missing fixture: $path" }
            .use { it.readBytes() }

    fun json(path: String): JsonObject =
        Json.parseToJsonElement(bytes(path).toString(Charsets.UTF_8)).jsonObject
}

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
private fun JsonObject.nullableString(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int
private fun JsonObject.bool(key: String): Boolean = getValue(key).jsonPrimitive.boolean
private fun JsonObject.boolOrDefault(key: String, default: Boolean): Boolean =
    get(key)?.jsonPrimitive?.boolean ?: default
private fun JsonObject.stringList(key: String): List<String> =
    getValue(key).jsonArray.map { it.jsonPrimitive.content }

private inline fun <reified T> requireType(value: Any?): T {
    assertTrue("Expected ${T::class.simpleName}, got ${value?.let { it::class.simpleName }}", value is T)
    return value as T
}
