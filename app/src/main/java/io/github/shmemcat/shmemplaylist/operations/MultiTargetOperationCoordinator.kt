package io.github.shmemcat.shmemplaylist.operations

import io.github.shmemcat.shmemplaylist.domain.BatchAction
import io.github.shmemcat.shmemplaylist.domain.BatchOperationPlan
import io.github.shmemcat.shmemplaylist.domain.BatchOperationPlannerV1
import io.github.shmemcat.shmemplaylist.domain.BatchTargetDecision
import io.github.shmemcat.shmemplaylist.domain.BatchTargetInput
import io.github.shmemcat.shmemplaylist.domain.CanonicalGoneMadProfileV1
import io.github.shmemcat.shmemplaylist.domain.M3uParserV1
import io.github.shmemcat.shmemplaylist.domain.ManyTrackBatchOperationPlannerV2
import io.github.shmemcat.shmemplaylist.domain.ParseResult
import io.github.shmemcat.shmemplaylist.domain.SemanticChecksumV1
import io.github.shmemcat.shmemplaylist.storage.ExactByteBackupRepository
import io.github.shmemcat.shmemplaylist.storage.PlaylistDocumentStorage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

fun interface PlaylistDocumentStorageResolver {
    fun open(documentIdentity: String): PlaylistDocumentStorage
}

data class BatchPreview(
    val plan: BatchOperationPlan,
    val snapshotByteSha256: Map<String, String>,
    val requestedCanonicalPaths: List<String> = listOf(
        plan.generatedPath.removePrefix("/storage/emulated/0/"),
    ),
)

data class BatchConfirmation(
    val preview: BatchPreview,
    val requiresReconfirmation: Boolean,
)

data class MultiTargetRecoveryOutcome(
    val operationId: String,
    val state: JournalState,
    val classifications: List<RecoveryClassification>,
)

/**
 * Serial production coordinator for ordered real-playlist batches. Every target is backed up
 * before the first write. Writes and verification run in target order; compensation runs in
 * reverse order and recovery-required state blocks all subsequent mutation.
 */
class MultiTargetOperationCoordinator(
    private val storageResolver: PlaylistDocumentStorageResolver,
    private val backups: ExactByteBackupRepository,
    private val journal: MultiTargetOperationJournal,
    private val clock: OperationClock = OperationClock(System::currentTimeMillis),
    private val failures: FailureController = FailureController.NONE,
    private val operationId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()

    suspend fun preview(
        action: BatchAction,
        canonicalNormalizedTrackPath: String,
        targets: List<PlaylistDocumentStorage>,
    ): BatchPreview {
        val inputs = targets.map {
            BatchTargetInput(
                it.handle.documentIdentity,
                it.handle.displayName,
                it.readExact(),
            )
        }
        val plan = BatchOperationPlannerV1.plan(action, canonicalNormalizedTrackPath, inputs)
        return BatchPreview(
            plan,
            inputs.associate { it.documentIdentity to ExactByteBackupRepository.sha256(it.originalBytes) },
            listOf(canonicalNormalizedTrackPath),
        )
    }

    suspend fun previewMany(
        action: BatchAction,
        canonicalNormalizedTrackPaths: List<String>,
        targets: List<PlaylistDocumentStorage>,
    ): BatchPreview {
        val inputs = targets.map {
            BatchTargetInput(
                it.handle.documentIdentity,
                it.handle.displayName,
                it.readExact(),
            )
        }
        val plan = ManyTrackBatchOperationPlannerV2.plan(
            action,
            canonicalNormalizedTrackPaths,
            inputs,
        )
        return BatchPreview(
            plan,
            inputs.associate { it.documentIdentity to ExactByteBackupRepository.sha256(it.originalBytes) },
            canonicalNormalizedTrackPaths,
        )
    }

    suspend fun confirm(preview: BatchPreview): BatchConfirmation {
        val refreshedInputs = preview.plan.targets.map { decision ->
            val storage = storageResolver.open(decision.documentIdentity)
            BatchTargetInput(
                decision.documentIdentity,
                decision.displayName,
                storage.readExact(),
            )
        }
        val refreshed = ManyTrackBatchOperationPlannerV2.plan(
            preview.plan.action,
            preview.requestedCanonicalPaths,
            refreshedInputs,
        )
        val refreshedPreview = BatchPreview(
            refreshed,
            refreshedInputs.associate {
                it.documentIdentity to ExactByteBackupRepository.sha256(it.originalBytes)
            },
            preview.requestedCanonicalPaths,
        )
        return BatchConfirmation(
            refreshedPreview,
            materialSignature(preview.plan) != materialSignature(refreshed),
        )
    }

    suspend fun apply(
        confirmed: BatchPreview,
        trackIdentityRedacted: String,
        approvalSource: String,
    ): OperationOutcome = mutex.withLock {
        if (journal.hasBlockingOperation()) {
            return@withLock OperationOutcome.FailedSafe(null, "recovery-blocks-writes")
        }
        val changes = confirmed.plan.changes
        if (changes.isEmpty()) return@withLock OperationOutcome.Skipped("no-eligible-changes")

        val current = try {
            changes.associate { change ->
                change.documentIdentity to storageResolver.open(change.documentIdentity).readExact()
            }
        } catch (failure: Throwable) {
            return@withLock OperationOutcome.FailedSafe(null, code(failure))
        }
        if (changes.any { change ->
                ExactByteBackupRepository.sha256(checkNotNull(current[change.documentIdentity])) !=
                    confirmed.snapshotByteSha256[change.documentIdentity]
            }
        ) {
            return@withLock OperationOutcome.FailedSafe(null, "confirmation-stale")
        }
        execute(confirmed.plan, trackIdentityRedacted, approvalSource, null)
    }

    suspend fun undo(operationId: String): OperationOutcome = mutex.withLock {
        if (journal.hasBlockingOperation()) {
            return@withLock OperationOutcome.UndoRefused("recovery-blocks-writes")
        }
        val original = journal.get(operationId)
            ?: return@withLock OperationOutcome.UndoRefused("operation-not-found")
        if (original.state != JournalState.SUCCEEDED || original.action == "UNDO") {
            return@withLock OperationOutcome.UndoRefused("operation-not-undoable")
        }
        val desired = mutableListOf<BatchTargetInput>()
        try {
            original.targets.forEach { target ->
                val current = storageResolver.open(target.documentIdentity).readExact()
                if (ExactByteBackupRepository.sha256(current) != target.expectedByteSha256) {
                    return@withLock OperationOutcome.UndoRefused(
                        "concurrent-change-${target.targetOrder}",
                    )
                }
                val backup = backups.readVerified(
                    target.backupName ?: return@withLock OperationOutcome.UndoRefused("backup-missing"),
                    target.backupSha256
                        ?: return@withLock OperationOutcome.UndoRefused("backup-integrity-missing"),
                )
                desired += BatchTargetInput(target.documentIdentity, target.displayName, backup)
            }
        } catch (failure: Throwable) {
            return@withLock OperationOutcome.UndoRefused(code(failure))
        }
        val undoPlan = BatchOperationPlan(
            BatchAction.REMOVE_ALL,
            original.targets.first().generatedPath,
            original.targets.mapIndexed { index, target ->
                val current = storageResolver.open(target.documentIdentity).readExact()
                BatchTargetDecision.Change(
                    target.documentIdentity,
                    target.displayName,
                    current,
                    desired[index].originalBytes,
                    target.generatedPath,
                    emptyList(),
                    target.originalOccurrenceCount,
                )
            },
        )
        execute(undoPlan, original.trackIdentityRedacted, original.approvalSource, operationId)
            .also { outcome ->
                if (outcome is OperationOutcome.Changed) {
                    journal.updateOperation(
                        operationId,
                        JournalState.UNDONE,
                        clock.nowEpochMillis(),
                    )
                }
            }
    }

    suspend fun recover(): List<MultiTargetRecoveryOutcome> = mutex.withLock {
        journal.nonTerminal().map { recoverOne(it) }
    }

    suspend fun history(): List<MultiTargetJournalOperation> = journal.history()

    private suspend fun execute(
        plan: BatchOperationPlan,
        trackIdentityRedacted: String,
        approvalSource: String,
        undoOf: String?,
    ): OperationOutcome {
        val id = operationId()
        val now = clock.nowEpochMillis()
        val changes = plan.changes
        val operation = MultiTargetJournalOperation(
            id,
            if (undoOf == null) plan.action.name else "UNDO",
            JournalState.PENDING,
            now,
            now,
            trackIdentityRedacted,
            approvalSource,
            changes.mapIndexed { order, change ->
                MultiTargetJournalTarget(
                    targetOrder = order,
                    documentIdentity = change.documentIdentity,
                    displayName = change.displayName,
                    state = JournalState.PENDING,
                    originalByteSha256 = ExactByteBackupRepository.sha256(change.originalBytes),
                    originalSemanticSha256 = semanticSha(change.originalBytes, change.displayName)
                        ?: return OperationOutcome.FailedSafe(null, "original-semantic-invalid"),
                    expectedByteSha256 = ExactByteBackupRepository.sha256(change.expectedBytes),
                    expectedSemanticSha256 = semanticSha(change.expectedBytes, change.displayName)
                        ?: return OperationOutcome.FailedSafe(null, "expected-semantic-invalid"),
                    generatedPath = change.generatedPath,
                    originalOccurrenceIndexes = change.originalOccurrenceIndexes,
                    originalOccurrenceCount = change.originalOccurrenceIndexes.size,
                    expectedOccurrenceCount = change.expectedOccurrenceCount,
                )
            },
            undoOfOperationId = undoOf,
        )
        try {
            failures.hit(FailurePoint.BEFORE_JOURNAL)
            journal.create(operation)
            failures.hit(FailurePoint.AFTER_JOURNAL)
            operation.targets.forEach { target ->
                failures.hit(FailurePoint.BEFORE_BACKUP)
                val backup = backups.publish(id, target.targetOrder, changes[target.targetOrder].originalBytes)
                journal.updateTarget(
                    id,
                    target.targetOrder,
                    JournalState.BACKED_UP,
                    backup.name,
                    backup.sha256,
                )
                failures.hit(FailurePoint.AFTER_BACKUP)
            }
            journal.updateOperation(id, JournalState.BACKED_UP, clock.nowEpochMillis())
            changes.forEachIndexed { order, change ->
                failures.hit(FailurePoint.BEFORE_WRITE_INTENT)
                journal.updateTarget(id, order, JournalState.WRITE_INTENT)
                journal.updateOperation(id, JournalState.WRITE_INTENT, clock.nowEpochMillis())
                failures.hit(FailurePoint.AFTER_WRITE_INTENT)
                failures.hit(FailurePoint.BEFORE_WRITE)
                storageResolver.open(change.documentIdentity).overwriteExact(change.expectedBytes)
                failures.hit(FailurePoint.AFTER_WRITE)
                journal.updateTarget(id, order, JournalState.WRITTEN)
                failures.hit(FailurePoint.BEFORE_REREAD)
                val reread = storageResolver.open(change.documentIdentity).readExact()
                failures.hit(FailurePoint.AFTER_REREAD)
                journal.updateTarget(id, order, JournalState.VERIFYING)
                failures.hit(FailurePoint.BEFORE_VERIFY)
                verifyExpected(reread, change.expectedBytes, operation.targets[order])
                failures.hit(FailurePoint.AFTER_VERIFY)
                journal.updateTarget(id, order, JournalState.SUCCEEDED)
            }
            journal.updateOperation(id, JournalState.SUCCEEDED, clock.nowEpochMillis())
            return OperationOutcome.Changed(
                id,
                changes.sumOf {
                    kotlin.math.abs(it.expectedOccurrenceCount - it.originalOccurrenceIndexes.size)
                },
            )
        } catch (failure: Throwable) {
            return withContext(NonCancellable) { compensate(id, failure) }
        }
    }

    private suspend fun compensate(id: String, failure: Throwable): OperationOutcome {
        val current = journal.get(id)
            ?: return OperationOutcome.FailedSafe(null, code(failure))
        val backedUp = current.targets.filter { it.backupName != null && it.backupSha256 != null }
        if (backedUp.size != current.targets.size) {
            val possiblyWritten = current.targets.any {
                it.state in setOf(JournalState.WRITE_INTENT, JournalState.WRITTEN, JournalState.VERIFYING)
            }
            val state = if (possiblyWritten) JournalState.RECOVERY_REQUIRED else JournalState.FAILED_SAFE
            journal.updateOperation(id, state, clock.nowEpochMillis(), code(failure))
            return if (state == JournalState.RECOVERY_REQUIRED) {
                OperationOutcome.RecoveryRequired(id, code(failure))
            } else {
                OperationOutcome.FailedSafe(id, code(failure))
            }
        }
        return try {
            failures.hit(FailurePoint.BEFORE_ROLLBACK)
            journal.updateOperation(id, JournalState.ROLLBACK_INTENT, clock.nowEpochMillis(), code(failure))
            backedUp.asReversed().forEach { target ->
                journal.updateTarget(id, target.targetOrder, JournalState.ROLLBACK_INTENT)
                val original = backups.readVerified(
                    checkNotNull(target.backupName),
                    checkNotNull(target.backupSha256),
                )
                failures.hit(FailurePoint.BEFORE_RESTORE)
                storageResolver.open(target.documentIdentity).overwriteExact(original)
                failures.hit(FailurePoint.AFTER_RESTORE)
                failures.hit(FailurePoint.BEFORE_RESTORE_VERIFY)
                check(storageResolver.open(target.documentIdentity).readExact().contentEquals(original)) {
                    "restoration-byte-mismatch"
                }
                failures.hit(FailurePoint.AFTER_RESTORE_VERIFY)
                journal.updateTarget(id, target.targetOrder, JournalState.ROLLED_BACK)
            }
            journal.updateOperation(id, JournalState.ROLLED_BACK, clock.nowEpochMillis(), code(failure))
            OperationOutcome.FailedSafe(id, code(failure))
        } catch (rollbackFailure: Throwable) {
            journal.updateOperation(
                id,
                JournalState.RECOVERY_REQUIRED,
                clock.nowEpochMillis(),
                "rollback-${code(rollbackFailure)}",
            )
            OperationOutcome.RecoveryRequired(id, code(rollbackFailure))
        }
    }

    private suspend fun recoverOne(
        operation: MultiTargetJournalOperation,
    ): MultiTargetRecoveryOutcome {
        val classifications = operation.targets.map { target ->
            val bytes = runCatching {
                storageResolver.open(target.documentIdentity).readExact()
            }.getOrNull()
            when {
                bytes == null -> RecoveryClassification.MISSING
                ExactByteBackupRepository.sha256(bytes) == target.originalByteSha256 ->
                    RecoveryClassification.ORIGINAL
                ExactByteBackupRepository.sha256(bytes) == target.expectedByteSha256 ->
                    RecoveryClassification.EXPECTED
                else -> RecoveryClassification.UNKNOWN
            }
        }
        val allExpected = classifications.all { it == RecoveryClassification.EXPECTED }
        val allOriginal = classifications.all { it == RecoveryClassification.ORIGINAL }
        val state = try {
            when {
                allExpected -> {
                    operation.targets.forEach { target ->
                        val bytes = storageResolver.open(target.documentIdentity).readExact()
                        check(ExactByteBackupRepository.sha256(bytes) == target.expectedByteSha256) {
                            "recovery-expected-byte-mismatch"
                        }
                        check(CanonicalGoneMadProfileV1.validate(bytes).writable) {
                            "profile-verification-failed"
                        }
                        check(semanticSha(bytes, target.displayName) == target.expectedSemanticSha256) {
                            "semantic-verification-failed"
                        }
                        journal.updateTarget(
                            operation.operationId,
                            target.targetOrder,
                            JournalState.SUCCEEDED,
                        )
                    }
                    JournalState.SUCCEEDED
                }
                allOriginal -> JournalState.ROLLED_BACK
                RecoveryClassification.MISSING in classifications -> JournalState.RECOVERY_REQUIRED
                else -> {
                    operation.targets.asReversed().forEach { target ->
                        val original = backups.readVerified(
                            checkNotNull(target.backupName),
                            checkNotNull(target.backupSha256),
                        )
                        storageResolver.open(target.documentIdentity).overwriteExact(original)
                        check(
                            storageResolver.open(target.documentIdentity)
                                .readExact()
                                .contentEquals(original),
                        )
                        journal.updateTarget(
                            operation.operationId,
                            target.targetOrder,
                            JournalState.ROLLED_BACK,
                        )
                    }
                    JournalState.ROLLED_BACK
                }
            }
        } catch (_: Throwable) {
            JournalState.RECOVERY_REQUIRED
        }
        journal.updateOperation(operation.operationId, state, clock.nowEpochMillis())
        return MultiTargetRecoveryOutcome(operation.operationId, state, classifications)
    }

    private fun materialSignature(plan: BatchOperationPlan): List<String> = plan.targets.map {
        when (it) {
            is BatchTargetDecision.Change ->
                "${it.documentIdentity}:change:${it.originalOccurrenceIndexes}:${it.expectedOccurrenceCount}"
            is BatchTargetDecision.Skip -> "${it.documentIdentity}:skip:${it.reason}"
            is BatchTargetDecision.Ineligible ->
                "${it.documentIdentity}:ineligible:${it.reasons.joinToString(",")}"
        }
    }

    private fun semanticSha(bytes: ByteArray, displayName: String): String? =
        (M3uParserV1.parse(bytes, displayName) as? ParseResult.Success)?.records
            ?.map { it.normalizedPath }
            ?.let(SemanticChecksumV1::ofNormalizedPaths)

    private fun verifyExpected(
        actual: ByteArray,
        expected: ByteArray,
        target: MultiTargetJournalTarget,
    ) {
        check(actual.contentEquals(expected)) { "exact-byte-verification-failed" }
        check(CanonicalGoneMadProfileV1.validate(actual).writable) {
            "profile-verification-failed"
        }
        check(semanticSha(actual, target.displayName) == target.expectedSemanticSha256) {
            "semantic-verification-failed"
        }
    }

    private fun code(failure: Throwable): String =
        failure.message?.take(80) ?: failure.javaClass.simpleName
}
