package io.github.shmemcat.shmemplay.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Mp3TagsTest {
    @Test fun multilingualTextEditsPreserveAudioAndUnrelatedFrames() {
        val directory=Files.createTempDirectory("tag-fixture").toFile()
        try {
            val original=File(directory,"original.mp3");val tagged=File(directory,"tagged.mp3");val updated=File(directory,"updated.mp3")
            val audio=byteArrayOf(255.toByte(),251.toByte())+ByteArray(10000){(it%251).toByte()}
            original.writeBytes(audio)
            Mp3Tags.rewrite(original,tagged,mapOf("TIT2" to "노래 中文 ไทย","TPE1" to "Artist"))
            Mp3Tags.rewrite(tagged,updated,mapOf("TIT2" to "New title"))
            val tag=updated.inputStream().use(Mp3Tags::read)
            assertEquals("New title",tag.text("TIT2"));assertEquals("Artist",tag.text("TPE1"))
            assertArrayEquals(audio,updated.readBytes().copyOfRange(tag.audioOffset.toInt(),updated.length().toInt()))
            assertArrayEquals(tagged.inputStream().use(Mp3Tags::read).frames.first{it.id=="TPE1"}.bytes,tag.frames.first{it.id=="TPE1"}.bytes)
        } finally {directory.deleteRecursively()}
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsUnsynchronisedTagsBeforeWriting() {
        Mp3Tags.read(byteArrayOf(73,68,51,4,0,128.toByte(),0,0,0,0).inputStream())
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsNonAudioDisguisedAsMp3() {Mp3Tags.read(ByteArray(12).inputStream())}
}
