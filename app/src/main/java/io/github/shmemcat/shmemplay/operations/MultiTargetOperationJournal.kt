package io.github.shmemcat.shmemplay.operations

import androidx.room.withTransaction
import io.github.shmemcat.shmemplay.persistence.AppDatabase
import io.github.shmemcat.shmemplay.persistence.PlaylistOperationEntity
import io.github.shmemcat.shmemplay.persistence.PlaylistOperationTargetEntity

data class MultiTargetJournalOperation(
    val operationId: String,
    val action: String,
    val state: JournalState,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val trackIdentityRedacted: String,
    val approvalSource: String,
    val targets: List<MultiTargetJournalTarget>,
    val errorCode: String? = null,
    val undoOfOperationId: String? = null,
)

data class MultiTargetJournalTarget(
    val targetId: Long = 0,
    val targetOrder: Int,
    val documentIdentity: String,
    val displayName: String,
    val state: JournalState,
    val originalExisted: Boolean = true,
    val originalByteSha256: String,
    val originalSemanticSha256: String,
    val expectedByteSha256: String,
    val expectedSemanticSha256: String,
    val backupName: String? = null,
    val backupSha256: String? = null,
    val generatedPath: String,
    val originalOccurrenceIndexes: List<Int>,
    val originalOccurrenceCount: Int,
    val expectedOccurrenceCount: Int,
    val errorCode: String? = null,
)

interface MultiTargetOperationJournal {
    suspend fun create(operation: MultiTargetJournalOperation)
    suspend fun updateOperation(
        operationId: String,
        state: JournalState,
        now: Long,
        errorCode: String? = null,
    )
    suspend fun updateTarget(
        operationId: String,
        targetOrder: Int,
        state: JournalState,
        backupName: String? = null,
        backupSha256: String? = null,
        errorCode: String? = null,
    )
    suspend fun get(operationId: String): MultiTargetJournalOperation?
    suspend fun nonTerminal(): List<MultiTargetJournalOperation>
    suspend fun history(): List<MultiTargetJournalOperation>
    suspend fun hasBlockingOperation(): Boolean
}

class RoomMultiTargetOperationJournal(private val database: AppDatabase) :
    MultiTargetOperationJournal {
    private val dao = database.playlistOperationDao()

    override suspend fun create(operation: MultiTargetJournalOperation) =
        database.withTransaction {
            require(operation.targets.isNotEmpty()) { "operation-requires-targets" }
            require(operation.targets.map { it.targetOrder } == operation.targets.indices.toList()) {
                "targets-must-be-contiguous-and-ordered"
            }
            dao.insertOperation(operation.toEntity())
            operation.targets.forEach { dao.insertTarget(it.toEntity(operation.operationId)) }
        }

    override suspend fun updateOperation(
        operationId: String,
        state: JournalState,
        now: Long,
        errorCode: String?,
    ) = database.withTransaction {
        val row = dao.get(operationId) ?: error("journal-operation-missing")
        dao.updateOperation(
            row.operation.copy(state = state.name, updatedAtEpochMs = now, errorCode = errorCode),
        )
    }

    override suspend fun updateTarget(
        operationId: String,
        targetOrder: Int,
        state: JournalState,
        backupName: String?,
        backupSha256: String?,
        errorCode: String?,
    ) = database.withTransaction {
        val target = dao.targets(operationId).singleOrNull { it.targetOrder == targetOrder }
            ?: error("journal-target-missing")
        dao.updateTarget(
            target.copy(
                state = state.name,
                backupName = backupName ?: target.backupName,
                backupSha256 = backupSha256 ?: target.backupSha256,
                errorCode = errorCode,
            ),
        )
    }

    override suspend fun get(operationId: String): MultiTargetJournalOperation? =
        dao.get(operationId)?.let { row ->
            row.operation.toModel(dao.targets(operationId))
        }

    override suspend fun nonTerminal(): List<MultiTargetJournalOperation> =
        dao.nonTerminal().map { row -> row.operation.toModel(dao.targets(row.operation.operationId)) }

    override suspend fun history(): List<MultiTargetJournalOperation> =
        dao.historyOperations().map { operation ->
            operation.toModel(dao.targets(operation.operationId))
        }

    override suspend fun hasBlockingOperation(): Boolean = dao.blockingCount() != 0

    private fun MultiTargetJournalOperation.toEntity() = PlaylistOperationEntity(
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

    private fun MultiTargetJournalTarget.toEntity(operationId: String) =
        PlaylistOperationTargetEntity(
            targetId = targetId,
            operationId = operationId,
            targetOrder = targetOrder,
            documentIdentity = documentIdentity,
            displayName = displayName,
            state = state.name,
            originalExisted = originalExisted,
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

    private fun PlaylistOperationEntity.toModel(
        targets: List<PlaylistOperationTargetEntity>,
    ) = MultiTargetJournalOperation(
        operationId = operationId,
        action = action,
        state = JournalState.valueOf(state),
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        trackIdentityRedacted = trackIdentityRedacted,
        approvalSource = approvalSource,
        errorCode = errorCode,
        undoOfOperationId = undoOfOperationId,
        targets = targets.sortedBy { it.targetOrder }.map { it.toModel() },
    )

    private fun PlaylistOperationTargetEntity.toModel() = MultiTargetJournalTarget(
        targetId = targetId,
        targetOrder = targetOrder,
        documentIdentity = documentIdentity,
        displayName = displayName,
        state = JournalState.valueOf(state),
        originalExisted = originalExisted,
        originalByteSha256 = originalByteSha256,
        originalSemanticSha256 = originalSemanticSha256,
        expectedByteSha256 = expectedByteSha256,
        expectedSemanticSha256 = expectedSemanticSha256,
        backupName = backupName,
        backupSha256 = backupSha256,
        generatedPath = generatedPath,
        originalOccurrenceIndexes = originalOccurrenceIndexes.split(',')
            .filter(String::isNotEmpty)
            .map(String::toInt),
        originalOccurrenceCount = originalOccurrenceCount,
        expectedOccurrenceCount = expectedOccurrenceCount,
        errorCode = errorCode,
    )
}
