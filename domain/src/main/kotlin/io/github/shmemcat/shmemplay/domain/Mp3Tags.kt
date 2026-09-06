package io.github.shmemcat.shmemplay.domain

import java.io.*
import java.nio.charset.Charset

/** Conservative ID3v2.3/2.4 text editing. Unsupported flags are rejected before any output is written. */
object Mp3Tags {
    data class Frame(val id: String, val bytes: ByteArray)
    data class Tag(val version: Int, val frames: List<Frame>, val audioOffset: Long, val size: Int) {
        fun text(id: String): String? = frames.firstOrNull { it.id == id }?.bytes?.let { raw ->
            val bytes=raw.copyOfRange(10,raw.size)
            if(bytes.isEmpty()) null else decode(bytes[0].toInt(),bytes.copyOfRange(1,bytes.size)).trimEnd('\u0000')
        }
        fun lyrics(): String? = frames.firstOrNull { it.id == "USLT" }?.bytes?.let { raw ->
            val data=raw.copyOfRange(10,raw.size)
            if(data.size<5) null else {
                val encoding=data[0].toInt();var start=4
                if(encoding==1||encoding==2) {while(start+1<data.size && !(data[start]==0.toByte() && data[start+1]==0.toByte()))start+=2;start+=2}
                else {while(start<data.size && data[start]!=0.toByte())start++;start++}
                if(start>=data.size)null else decode(encoding,data.copyOfRange(start,data.size)).trimEnd('\u0000')
            }
        }
    }
    val editable = linkedMapOf("TIT2" to "Title","TPE1" to "Artist","TALB" to "Album","TPE2" to "Album artist", "TCOM" to "Composer","TCON" to "Genre","TEXT" to "Lyricist","TRCK" to "Track number","TPOS" to "Disc number")
    fun read(input: InputStream): Tag {
        val data=DataInputStream(input)
        val header=ByteArray(10); data.readFully(header)
        if(String(header,0,3,Charsets.ISO_8859_1)!="ID3") {
            require(header[0].toInt() and 255 == 255 && header[1].toInt() and 224 == 224) { "This MP3 tag format is not supported for editing." }
            return Tag(4,emptyList(),0,0)
        }
        val version=header[3].toInt()
        require(version in 3..4 && header[4]==0.toByte() && header[5]==0.toByte()) { "Editing supports ID3v2.3/2.4 without extended headers, unsynchronisation or footers." }
        val size=syncInt(header,6)
        require(size<=16*1024*1024) { "The embedded tag is too large to edit safely." }
        val body=ByteArray(size);data.readFully(body)
        val frames=mutableListOf<Frame>();var offset=0
        while(offset<body.size && body[offset]!=0.toByte()) {
            require(offset+10<=body.size)
            val id=String(body,offset,4,Charsets.ISO_8859_1)
            require(id.matches(Regex("[A-Z0-9]{4}"))) { "Invalid ID3 frame" }
            val length=if(version==4)syncInt(body,offset+4) else int32(body,offset+4)
            require(length>0 && length<=body.size-offset-10)
            require(body[offset+8]==0.toByte() && body[offset+9]==0.toByte()) { "This file uses protected or encoded ID3 frames; editing is unavailable." }
            frames.add(Frame(id,body.copyOfRange(offset,offset+10+length)));offset+=10+length
        }
        require(body.drop(offset).all {it==0.toByte()}) {"Invalid ID3 padding"}
        return Tag(version,frames,size.toLong()+10,size)
    }
    fun rewrite(original: File, destination: File, changes: Map<String,String>) {
        val tag=original.inputStream().use(::read)
        val allowed=editable.keys+setOf("TYER","TDRC")
        require(changes.keys.all {it in allowed})
        val updates=changes.mapValues { (_,v)->v.trim().also {require(it.length<=10000 && '\u0000' !in it)} }
        val body=ByteArrayOutputStream()
        tag.frames.filterNot {it.id in updates}.forEach {body.write(it.bytes)}
        updates.forEach { (id,value)->
            if(value.isNotEmpty()) {
                val encoded=if(tag.version==4) byteArrayOf(3)+value.toByteArray(Charsets.UTF_8) else byteArrayOf(1)+value.toByteArray(Charsets.UTF_16)
                body.write(id.toByteArray(Charsets.US_ASCII));body.write(if(tag.version==4)syncBytes(encoded.size)else intBytes(encoded.size));body.write(byteArrayOf(0,0));body.write(encoded)
            }
        }
        val size=if(body.size()<=tag.size)tag.size else body.size()+1024
        require(size<=16*1024*1024)
        require(size==tag.size || tag.frames.none {it.id in setOf("CHAP","CTOC","SEEK","ASPI")}) {"This MP3 has absolute seek offsets; its tag cannot be expanded."}
        destination.outputStream().use {output->
            output.write(byteArrayOf(73,68,51,tag.version.toByte(),0,0));output.write(syncBytes(size));body.writeTo(output)
            output.write(ByteArray(size-body.size()))
            original.inputStream().use {input->var left=tag.audioOffset;while(left>0){val skipped=input.skip(left);require(skipped>0);left-=skipped};input.copyTo(output)}
            output.fd.sync()
        }
        val verified=destination.inputStream().use(::read)
        updates.forEach {(id,value)->require((verified.text(id)?:"")==value){"Tag verification failed"}}
    }
    private fun decode(encoding:Int,bytes:ByteArray):String = String(bytes,when(encoding){0->Charsets.ISO_8859_1;1->Charsets.UTF_16;2->Charset.forName("UTF-16BE");3->Charsets.UTF_8;else->error("Unknown ID3 encoding")})
    private fun syncInt(bytes:ByteArray,start:Int):Int {var n=0;repeat(4){val b=bytes[start+it].toInt() and 255;require(b<128);n=(n shl 7) or b};return n}
    private fun int32(bytes:ByteArray,start:Int):Int {var n=0;repeat(4){n=(n shl 8) or (bytes[start+it].toInt() and 255)};return n}
    private fun syncBytes(n:Int)=ByteArray(4){(n ushr ((3-it)*7) and 127).toByte()}
    private fun intBytes(n:Int)=ByteArray(4){(n ushr ((3-it)*8) and 255).toByte()}
}
