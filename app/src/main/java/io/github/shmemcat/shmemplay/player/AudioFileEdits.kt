package io.github.shmemcat.shmemplay.player

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import io.github.shmemcat.shmemplay.domain.Mp3Tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Persist exact-byte backups and a journal so an interrupted tag save remains recoverable. */
class AudioFileEdits(private val context: Context) {
    private val directory=File(context.filesDir,"audio-tag-recovery")
    private val journal=AtomicFile(File(directory,"pending.json"))
    data class Pending(val uri: Uri,val filename:String,val backup:File,val before:String,val after:String)
    fun pending(): Pending? {
        if(!journal.baseFile.exists() && !File(journal.baseFile.path+".bak").exists())return null
        val json=JSONObject(journal.openRead().bufferedReader().use{it.readText()})
        val backup=File(directory,json.getString("backup"))
        require(backup.canonicalFile.parentFile==directory.canonicalFile)
        return Pending(Uri.parse(json.getString("uri")),json.getString("filename"),backup,json.getString("before"),json.getString("after"))
    }
    suspend fun save(uri:Uri,filename:String,changes:Map<String,String>) = withContext(NonCancellable+Dispatchers.IO) { lock.withLock {
        check(pending()==null){"An interrupted tag edit needs recovery in Settings first."}
        directory.mkdirs()
        val backup=File(directory,"original.mp3")
        val updated=File(directory,"updated.mp3")
        context.contentResolver.openInputStream(uri)?.use{input->backup.outputStream().use{out->input.copyTo(out);out.fd.sync()}} ?: error("Audio file is unavailable")
        try {
            Mp3Tags.rewrite(backup,updated,changes)
            val before=backup.inputStream().use(::digest);val after=updated.inputStream().use(::digest)
            check(readDigest(uri)==before){"The audio file changed while the tags were being prepared."}
            val json=JSONObject().put("uri",uri.toString()).put("filename",filename).put("backup",backup.name).put("before",before).put("after",after)
            val stream=journal.startWrite()
            try{stream.write(json.toString().toByteArray());journal.finishWrite(stream)}catch(failure:Throwable){journal.failWrite(stream);throw failure}
            try {
                overwrite(uri,updated)
                check(readDigest(uri)==after){"Tag write verification failed"}
                journal.delete();backup.delete();updated.delete()
            } catch(failure:Throwable) {
                val restored=runCatching{overwrite(uri,backup);check(readDigest(uri)==before)}.isSuccess
                if(restored){journal.delete();backup.delete();updated.delete();error("Tag save failed; the original file was restored.")}
                error("Tag save was interrupted. The exact backup is kept; use Restore interrupted tag edit in Settings.")
            }
        } finally {if(pending()==null){backup.delete();updated.delete()}}
    } }
    suspend fun restore() = withContext(NonCancellable+Dispatchers.IO) { lock.withLock {
        val p=pending() ?: return@withLock
        check(p.backup.inputStream().use(::digest)==p.before){"Recovery backup could not be verified."}
        val current=runCatching{readDigest(p.uri)}.getOrNull()
        if(current!=p.before && current!=p.after) {overwrite(p.uri,p.backup);check(readDigest(p.uri)==p.before)}
        journal.delete();p.backup.delete();File(directory,"updated.mp3").delete()
    } }
    private fun overwrite(uri:Uri,source:File) {
        context.contentResolver.openFileDescriptor(uri,"rwt")?.use{pfd->
            java.io.FileOutputStream(pfd.fileDescriptor).use{output->source.inputStream().use{it.copyTo(output)};output.flush();output.fd.sync()}
        } ?: error("Audio write access unavailable")
    }
    private fun readDigest(uri:Uri)=context.contentResolver.openInputStream(uri)?.use(::digest) ?: error("Audio file unavailable")
    companion object {
        private val lock=Mutex()
        private fun digest(input:java.io.InputStream):String {val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)};return digest.digest().joinToString(""){"%02x".format(it)}}
    }
}
