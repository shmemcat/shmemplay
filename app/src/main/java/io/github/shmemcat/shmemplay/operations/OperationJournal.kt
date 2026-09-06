package io.github.shmemcat.shmemplay.operations

import androidx.room.withTransaction
import io.github.shmemcat.shmemplay.persistence.AppDatabase
import io.github.shmemcat.shmemplay.persistence.PlaylistOperationEntity
import io.github.shmemcat.shmemplay.persistence.PlaylistOperationTargetEntity

enum class JournalState {
    PENDING,
    BACKED_UP,
    WRITE_INTENT,
    WRITTEN,
    VERIFYING,
    SUCCEEDED,
    SKIPPED,
    ROLLBACK_INTENT,
    ROLLED_BACK,
    RECOVERY_REQUIRED,
    FAILED_SAFE,
    UNDONE,
}

data class JournalOperation(
    val operationId: String,
    val action: String,
    val state: JournalState,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val trackIdentityRedacted: String,
    val approvalSource: String,
    val target: JournalTarget,
    val errorCode: String? = null,
    val undoOfOperationId: String? = null,
)

data class JournalTarget(
    val targetId: Long = 0,
    val documentIdentity: String,
    val state: JournalState,
    val originalByteSha256: String,
    val originalSemanticSha256: String,
    val expectedByteSha256: String,
    val expectedSemanticSha256: String,
    val backupName: String?,
    val backupSha256: String?,
    val generatedPath: String,
    val originalOccurrenceIndexes: List<Int>,
    val originalOccurrenceCount: Int,
    val expectedOccurrenceCount: Int,
    val errorCode: String? = null,
)

interface OperationJournal {
    suspend fun create(operation: JournalOperation)
    suspend fun update(
        operationId: String,
        state: JournalState,
        now: Long,
        backupName: String? = null,
        backupSha256: String? = null,
        errorCode: String? = null,
    )
    suspend fun get(operationId: String): JournalOperation?
    suspend fun nonTerminal(): List<JournalOperation>
    suspend fun hasBlockingOperation(): Boolean
}

class RoomOperationJournal(private val database: AppDatabase) : OperationJournal {
    private val dao = database.playlistOperationDao()

    override suspend fun create(operation: JournalOperation) = database.withTransaction {
        dao.insert(
            operation.toEntity(),
            operation.target.toEntity(operation.operationId),
        )
    }

    override suspend fun update(
        operationId: String,
        state: JournalState,
        now: Long,
        backupName: String?,
        backupSha256: String?,
        errorCode: String?,
    ) = database.withTransaction {
        val current = dao.get(operationId) ?: error("journal-operation-missing")
        check(current.targets.size == 1) { "phase6-requires-one-target" }
        dao.updateOperation(
            current.operation.copy(
                state = state.name,
                updatedAtEpochMs = now,
                errorCode = errorCode,
            ),
        )
        dao.updateTarget(
            current.targets.single().copy(
                state = state.name,
                backupName = backupName ?: current.targets.single().backupName,
                backupSha256 = backupSha256 ?: current.targets.single().backupSha256,
                errorCode = errorCode,
            ),
        )
    }

    override suspend fun get(operationId: String): JournalOperation? =
        dao.get(operationId)?.let { row ->
            check(row.targets.size == 1) { "phase6-requires-one-target" }
            row.operation.toModel(row.targets.single())
        }

    override suspend fun nonTerminal(): List<JournalOperation> = dao.nonTerminal().map { row ->
        check(row.targets.size == 1) { "phase6-requires-one-target" }
        row.operation.toModel(row.targets.single())
    }

    override suspend fun hasBlockingOperation(): Boolean = dao.blockingCount() != 0

    private fun JournalOperation.toEntity() = PlaylistOperationEntity(
        operationId = operationId,
        action = action,
        state = state.name,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        trackIdentityRedacted = trackIdentityRedacted,
        approvalSource = approvalSource,
        errorCode = errorCode,
        undoOfOperationId = undoOfOperationId,
    )

    private fun JournalTarget.toEntity(operationId: String) = PlaylistOperationTargetEntity(
        operationId = operationId,
        documentIdentity = documentIdentity,
        displayName = "Shmemplaylist Companion Test.m3u",
        state = state.name,
        originalByteSha256 = originalByteSha256,
        originalSemanticSha256 = originalSemanticSha256,
        expectedByteSha256 = expectedByteSha256,
        expectedSemanticSha256 = expectedSemanticSha256,
        backupName = backupName,
        backupSha256 = backupSha256,
        generatedPath = generatedPath,
        originalOccurrenceIndexes = originalOccurrenceIndexes.joinToString(","),
        originalOccurrenceCount = originalOccurrenceCount,
        expectedOccurrenceCount = expectedOccurrenceCount,
        errorCode = errorCode,
    )

    private fun PlaylistOperationEntity.toModel(target: PlaylistOperationTargetEntity) =
        JournalOperation(
            operationId = operationId,
            action = action,
            state = JournalState.valueOf(state),
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            trackIdentityRedacted = trackIdentityRedacted,
            approvalSource = approvalSource,
            errorCode = errorCode,
            undoOfOperationId = undoOfOperationId,
            target = JournalTarget(
                targetId = target.targetId,
                documentIdentity = target.documentIdentity,
                state = JournalState.valueOf(target.state),
                originalByteSha256 = target.originalByteSha256,
                originalSemanticSha256 = target.originalSemanticSha256,
                expectedByteSha256 = target.expectedByteSha256,
                expectedSemanticSha256 = target.expectedSemanticSha256,
                backupName = target.backupName,
                backupSha256 = target.backupSha256,
                generatedPath = target.generatedPath,
                originalOccurrenceIndexes = target.originalOccurrenceIndexes
                    .split(',')
                    .filter(String::isNotEmpty)
                    .map(String::toInt),
                originalOccurrenceCount = target.originalOccurrenceCount,
                expectedOccurrenceCount = target.expectedOccurrenceCount,
                errorCode = target.errorCode,
            ),
        )
}
