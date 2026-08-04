package io.github.shmemcat.shmemplaylist.storage

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class PublishedBackup(
    val name: String,
    val sha256: String,
    val size: Long,
)

class ExactByteBackupRepository(context: Context) {
    private val directory = File(context.noBackupFilesDir, "phase6-playlist-backups")

    fun publish(operationId: String, bytes: ByteArray): PublishedBackup =
        publish(operationId, null, bytes)

    fun publish(operationId: String, targetOrder: Int?, bytes: ByteArray): PublishedBackup {
        require(operationId.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "invalid-operation-id" }
        require(targetOrder == null || targetOrder >= 0) { "invalid-target-order" }
        check(directory.exists() || directory.mkdirs()) { "backup-directory-unavailable" }
        val name = if (targetOrder == null) {
            "$operationId.original"
        } else {
            "$operationId.target-$targetOrder.original"
        }
        val destination = File(directory, name)
        check(!destination.exists()) { "backup-already-exists" }
        val temporary = File(directory, ".$name.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use {
                it.write(bytes)
                it.fd.sync()
            }
            check(temporary.renameTo(destination)) { "backup-publication-failed" }
            val digest = sha256(bytes)
            check(readVerified(name, digest).contentEquals(bytes)) { "backup-publication-mismatch" }
            return PublishedBackup(name, digest, bytes.size.toLong())
        } finally {
            temporary.delete()
        }
    }

    fun readVerified(name: String, expectedSha256: String): ByteArray {
        require(name.matches(Regex("[A-Za-z0-9-]{1,64}(\\.target-[0-9]+)?\\.original"))) {
            "invalid-backup-name"
        }
        val bytes = File(directory, name).readBytes()
        check(sha256(bytes) == expectedSha256) { "backup-integrity-failed" }
        return bytes
    }

    companion object {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
