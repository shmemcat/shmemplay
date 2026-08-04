package io.github.shmemcat.shmemplaylist.tracks

import io.github.shmemcat.shmemplaylist.persistence.ApprovedAliasDao
import io.github.shmemcat.shmemplaylist.persistence.ApprovedAliasEntity
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

data class ApprovedAlias(
    val aliasId: Long,
    val fingerprint: ByteArray,
    val target: MediaStoreIdentity,
    val displayName: String,
    val sizeBytes: Long?,
    val durationMs: Long?,
)

class ApprovedAliasRepository(
    private val dao: ApprovedAliasDao,
) {
    suspend fun find(evidence: SharedTrackEvidence): ApprovedAlias? =
        dao.find(evidence.fingerprint())?.toDomain()

    suspend fun remember(
        evidence: SharedTrackEvidence,
        candidate: TrackCandidate,
    ) {
        val now = System.currentTimeMillis()
        dao.save(
            ApprovedAliasEntity(
                evidenceFingerprint = evidence.fingerprint(),
                targetVolumeName = candidate.identity.volumeName,
                targetMediaId = candidate.identity.mediaId,
                targetDisplayName = candidate.displayName,
                targetSizeBytes = candidate.sizeBytes,
                targetDurationMs = candidate.durationMs,
                createdAtEpochMs = now,
                lastValidatedAtEpochMs = now,
            ),
        )
    }

    suspend fun touch(aliasId: Long) = dao.touch(aliasId, System.currentTimeMillis())

    suspend fun forget(aliasId: Long) = dao.forget(aliasId)

    suspend fun list(): List<ApprovedAlias> = dao.all().map(ApprovedAliasEntity::toDomain)
}

fun SharedTrackEvidence.fingerprint(): ByteArray {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { output ->
        output.writeUTF("evidence-fingerprint-v1")
        output.writeNullable(sourceAuthority)
        output.writeNullable(displayName?.normalizedEvidence())
        output.writeNullable(sizeBytes?.toString())
        output.writeNullable(durationMs?.toString())
        output.writeNullable(title?.normalizedEvidence())
        output.writeNullable(artist?.normalizedEvidence())
        output.writeNullable(album?.normalizedEvidence())
    }
    return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
}

private fun DataOutputStream.writeNullable(value: String?) {
    writeBoolean(value != null)
    if (value != null) writeUTF(value)
}

private fun ApprovedAliasEntity.toDomain() = ApprovedAlias(
    aliasId = aliasId,
    fingerprint = evidenceFingerprint,
    target = MediaStoreIdentity(targetVolumeName, targetMediaId),
    displayName = targetDisplayName,
    sizeBytes = targetSizeBytes,
    durationMs = targetDurationMs,
)
