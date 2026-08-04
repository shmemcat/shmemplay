package io.github.shmemcat.shmemplaylist.operations

import io.github.shmemcat.shmemplaylist.domain.CanonicalAbsolutePrimaryPathPlan
import io.github.shmemcat.shmemplaylist.domain.CanonicalGoneMadProfileV1
import io.github.shmemcat.shmemplaylist.domain.M3uParserV1
import io.github.shmemcat.shmemplaylist.domain.M3uWriterV1
import io.github.shmemcat.shmemplaylist.domain.ParseResult
import io.github.shmemcat.shmemplaylist.domain.PhonePathV1
import io.github.shmemcat.shmemplaylist.domain.SemanticChecksumV1
import io.github.shmemcat.shmemplaylist.storage.CompanionTestPlaylistStorage
import io.github.shmemcat.shmemplaylist.storage.ExactByteBackupRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

fun interface OperationClock {
    fun nowEpochMillis(): Long
}

enum class FailurePoint {
    BEFORE_JOURNAL,
    AFTER_JOURNAL,
    BEFORE_BACKUP,
    AFTER_BACKUP,
    BEFORE_WRITE_INTENT,
    AFTER_WRITE_INTENT,
    BEFORE_WRITE,
    AFTER_WRITE,
    BEFORE_REREAD,
    AFTER_REREAD,
    BEFORE_VERIFY,
    AFTER_VERIFY,
    BEFORE_ROLLBACK,
    AFTER_ROLLBACK_INTENT,
    BEFORE_RESTORE,
    AFTER_RESTORE,
    BEFORE_RESTORE_VERIFY,
    AFTER_RESTORE_VERIFY,
}

fun interface FailureController {
    fun hit(point: FailurePoint)

    companion object {
        val NONE = FailureController { }
    }
}

class DeterministicFailureController(
    private val failAt: FailurePoint?,
    private val failure: () -> Throwable = { InjectedOperationFailure(checkNotNull(failAt)) },
) : FailureController {
    override fun hit(point: FailurePoint) {
        if (point == failAt) throw failure()
    }
}

class InjectedOperationFailure(val point: FailurePoint) :
    RuntimeException("injected-${point.name.lowercase()}")

enum class CompanionAction { ADD_ONE, REMOVE_ALL }

sealed interface OperationOutcome {
    data class Changed(val operationId: String, val occurrencesChanged: Int) : OperationOutcome
    data class Skipped(val reason: String) : OperationOutcome
    data class FailedSafe(val operationId: String?, val reason: String) : OperationOutcome
    data class RecoveryRequired(val operationId: String, val reason: String) : OperationOutcome
    data class UndoRefused(val reason: String) : OperationOutcome
}

enum class RecoveryClassification { ORIGINAL, EXPECTED, MISSING, UNKNOWN }

data class RecoveryOutcome(
    val operationId: String,
    val classification: RecoveryClassification,
    val state: JournalState,
)

/**
 * Serial Phase 6 coordinator. The constructor accepts only the identity-gated test storage;
 * there is intentionally no URI, PlaylistDocument, file name, or arbitrary target parameter.
 */
class CompanionTestOperationCoordinator(
    private val storage: CompanionTestPlaylistStorage,
    private val backups: ExactByteBackupRepository,
    private val journal: OperationJournal,
    private val clock: OperationClock = OperationClock(System::currentTimeMillis),
    private val failures: FailureController = FailureController.NONE,
    private val operationId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()

    suspend fun apply(
        action: CompanionAction,
        canonicalNormalizedTrackPath: String,
        trackIdentityRedacted: String,
        approvalSource: String,
    ): OperationOutcome = mutex.withLock {
        if (journal.hasBlockingOperation()) {
            return@withLock OperationOutcome.FailedSafe(null, "recovery-blocks-writes")
        }
        val canonicalPath = try {
            CanonicalAbsolutePrimaryPathPlan.generate(canonicalNormalizedTrackPath)
        } catch (_: IllegalArgumentException) {
            return@withLock OperationOutcome.FailedSafe(null, "unsafe-track-path")
        }
        val original = try {
            storage.readExact()
        } catch (failure: Throwable) {
            return@withLock OperationOutcome.FailedSafe(null, code(failure))
        }
        val plan = plan(action, original, canonicalPath)
            ?: return@withLock OperationOutcome.FailedSafe(null, "noncanonical-test-playlist")
        if (plan.expected.contentEquals(original)) {
            return@withLock OperationOutcome.Skipped(
                if (action == CompanionAction.ADD_ONE) "already-present" else "already-absent",
            )
        }
        execute(
            action = action.name,
            original = original,
            expected = plan.expected,
            generatedPath = canonicalPath,
            originalIndexes = plan.originalIndexes,
            trackIdentityRedacted = trackIdentityRedacted,
            approvalSource = approvalSource,
            undoOf = null,
        )
    }

    suspend fun undo(operationId: String): OperationOutcome = mutex.withLock {
        if (journal.hasBlockingOperation()) {
            return@withLock OperationOutcome.UndoRefused("recovery-blocks-writes")
        }
        val originalOperation = journal.get(operationId)
            ?: return@withLock OperationOutcome.UndoRefused("operation-not-found")
        if (originalOperation.state != JournalState.SUCCEEDED) {
            return@withLock OperationOutcome.UndoRefused("operation-not-undoable")
        }
        val current = try {
            storage.readExact()
        } catch (failure: Throwable) {
            return@withLock OperationOutcome.UndoRefused(code(failure))
        }
        if (ExactByteBackupRepository.sha256(current) !=
            originalOperation.target.expectedByteSha256
        ) {
            return@withLock OperationOutcome.UndoRefused("concurrent-change")
        }
        val backupName = originalOperation.target.backupName
            ?: return@withLock OperationOutcome.UndoRefused("backup-missing")
        val backupSha = originalOperation.target.backupSha256
            ?: return@withLock OperationOutcome.UndoRefused("backup-integrity-missing")
        val desired = try {
            backups.readVerified(backupName, backupSha)
        } catch (failure: Throwable) {
            return@withLock OperationOutcome.UndoRefused(code(failure))
        }
        execute(
            action = "UNDO",
            original = current,
            expected = desired,
            generatedPath = originalOperation.target.generatedPath,
            originalIndexes = occurrenceIndexes(current, originalOperation.target.generatedPath),
            trackIdentityRedacted = originalOperation.trackIdentityRedacted,
            approvalSource = originalOperation.approvalSource,
            undoOf = operationId,
        ).also { outcome ->
            if (outcome is OperationOutcome.Changed) {
                journal.update(operationId, JournalState.UNDONE, clock.nowEpochMillis())
            }
        }
    }

    suspend fun recover(): List<RecoveryOutcome> = mutex.withLock {
        journal.nonTerminal().map { operation ->
            recoverOne(operation)
        }
    }

    private suspend fun execute(
        action: String,
        original: ByteArray,
        expected: ByteArray,
        generatedPath: String,
        originalIndexes: List<Int>,
        trackIdentityRedacted: String,
        approvalSource: String,
        undoOf: String?,
    ): OperationOutcome {
        val id = operationId()
        val now = clock.nowEpochMillis()
        val originalSemantic = semanticSha(original)
            ?: return OperationOutcome.FailedSafe(null, "original-semantic-invalid")
        val expectedSemantic = semanticSha(expected)
            ?: return OperationOutcome.FailedSafe(null, "expected-semantic-invalid")
        val operation = JournalOperation(
            operationId = id,
            action = action,
            state = JournalState.PENDING,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            trackIdentityRedacted = trackIdentityRedacted,
            approvalSource = approvalSource,
            undoOfOperationId = undoOf,
            target = JournalTarget(
                documentIdentity = storage.redactedDocumentIdentity,
                state = JournalState.PENDING,
                originalByteSha256 = ExactByteBackupRepository.sha256(original),
                originalSemanticSha256 = originalSemantic,
                expectedByteSha256 = ExactByteBackupRepository.sha256(expected),
                expectedSemanticSha256 = expectedSemantic,
                backupName = null,
                backupSha256 = null,
                generatedPath = generatedPath,
                originalOccurrenceIndexes = originalIndexes,
                originalOccurrenceCount = originalIndexes.size,
                expectedOccurrenceCount = occurrenceIndexes(expected, generatedPath).size,
            ),
        )
        try {
            failures.hit(FailurePoint.BEFORE_JOURNAL)
            journal.create(operation)
            failures.hit(FailurePoint.AFTER_JOURNAL)
            failures.hit(FailurePoint.BEFORE_BACKUP)
            val backup = backups.publish(id, original)
            journal.update(
                id,
                JournalState.BACKED_UP,
                clock.nowEpochMillis(),
                backup.name,
                backup.sha256,
            )
            failures.hit(FailurePoint.AFTER_BACKUP)
            failures.hit(FailurePoint.BEFORE_WRITE_INTENT)
            journal.update(id, JournalState.WRITE_INTENT, clock.nowEpochMillis())
            failures.hit(FailurePoint.AFTER_WRITE_INTENT)
            failures.hit(FailurePoint.BEFORE_WRITE)
            storage.overwriteExact(expected)
            failures.hit(FailurePoint.AFTER_WRITE)
            journal.update(id, JournalState.WRITTEN, clock.nowEpochMillis())
            failures.hit(FailurePoint.BEFORE_REREAD)
            val reread = storage.readExact()
            failures.hit(FailurePoint.AFTER_REREAD)
            journal.update(id, JournalState.VERIFYING, clock.nowEpochMillis())
            failures.hit(FailurePoint.BEFORE_VERIFY)
            verifyExpected(reread, expected, expectedSemantic)
            failures.hit(FailurePoint.AFTER_VERIFY)
            journal.update(id, JournalState.SUCCEEDED, clock.nowEpochMillis())
            return OperationOutcome.Changed(id, kotlin.math.abs(
                operation.target.expectedOccurrenceCount - operation.target.originalOccurrenceCount,
            ))
        } catch (failure: Throwable) {
            return withContext(NonCancellable) {
                compensate(operation, failure)
            }
        }
    }

    private suspend fun compensate(
        initial: JournalOperation,
        failure: Throwable,
    ): OperationOutcome {
        val current = journal.get(initial.operationId)
        if (current == null) return OperationOutcome.FailedSafe(null, code(failure))
        val target = current.target
        val backupName = target.backupName
        val backupSha = target.backupSha256
        if (backupName == null || backupSha == null) {
            val state = if (current.state == JournalState.PENDING) {
                JournalState.FAILED_SAFE
            } else {
                JournalState.RECOVERY_REQUIRED
            }
            journal.update(current.operationId, state, clock.nowEpochMillis(), errorCode = code(failure))
            return if (state == JournalState.FAILED_SAFE) {
                OperationOutcome.FailedSafe(current.operationId, code(failure))
            } else {
                OperationOutcome.RecoveryRequired(current.operationId, code(failure))
            }
        }
        return try {
            failures.hit(FailurePoint.BEFORE_ROLLBACK)
            journal.update(
                current.operationId,
                JournalState.ROLLBACK_INTENT,
                clock.nowEpochMillis(),
                errorCode = code(failure),
            )
            failures.hit(FailurePoint.AFTER_ROLLBACK_INTENT)
            val original = backups.readVerified(backupName, backupSha)
            failures.hit(FailurePoint.BEFORE_RESTORE)
            storage.overwriteExact(original)
            failures.hit(FailurePoint.AFTER_RESTORE)
            failures.hit(FailurePoint.BEFORE_RESTORE_VERIFY)
            val restored = storage.readExact()
            check(restored.contentEquals(original)) { "restoration-byte-mismatch" }
            check(ExactByteBackupRepository.sha256(restored) == target.originalByteSha256) {
                "restoration-hash-mismatch"
            }
            check(semanticSha(restored) == target.originalSemanticSha256) {
                "restoration-semantic-mismatch"
            }
            failures.hit(FailurePoint.AFTER_RESTORE_VERIFY)
            journal.update(
                current.operationId,
                JournalState.ROLLED_BACK,
                clock.nowEpochMillis(),
                errorCode = code(failure),
            )
            OperationOutcome.FailedSafe(current.operationId, code(failure))
        } catch (rollbackFailure: Throwable) {
            journal.update(
                current.operationId,
                JournalState.RECOVERY_REQUIRED,
                clock.nowEpochMillis(),
                errorCode = "rollback-${code(rollbackFailure)}",
            )
            OperationOutcome.RecoveryRequired(current.operationId, code(rollbackFailure))
        }
    }

    private suspend fun recoverOne(operation: JournalOperation): RecoveryOutcome {
        val bytes = runCatching { storage.readExact() }.getOrNull()
        val classification = when {
            bytes == null -> RecoveryClassification.MISSING
            ExactByteBackupRepository.sha256(bytes) == operation.target.originalByteSha256 ->
                RecoveryClassification.ORIGINAL
            ExactByteBackupRepository.sha256(bytes) == operation.target.expectedByteSha256 ->
                RecoveryClassification.EXPECTED
            else -> RecoveryClassification.UNKNOWN
        }
        val state = try {
            when (classification) {
                RecoveryClassification.ORIGINAL -> JournalState.ROLLED_BACK
                RecoveryClassification.EXPECTED -> {
                    verifyExpected(
                        checkNotNull(bytes),
                        checkNotNull(bytes),
                        operation.target.expectedSemanticSha256,
                    )
                    JournalState.SUCCEEDED
                }
                RecoveryClassification.UNKNOWN -> {
                    val backup = backups.readVerified(
                        checkNotNull(operation.target.backupName),
                        checkNotNull(operation.target.backupSha256),
                    )
                    storage.overwriteExact(backup)
                    check(storage.readExact().contentEquals(backup)) { "recovery-restore-mismatch" }
                    JournalState.ROLLED_BACK
                }
                RecoveryClassification.MISSING -> JournalState.RECOVERY_REQUIRED
            }
        } catch (_: Throwable) {
            JournalState.RECOVERY_REQUIRED
        }
        journal.update(operation.operationId, state, clock.nowEpochMillis())
        return RecoveryOutcome(operation.operationId, classification, state)
    }

    private data class Plan(val expected: ByteArray, val originalIndexes: List<Int>)

    private fun plan(action: CompanionAction, original: ByteArray, target: String): Plan? {
        val profile = CanonicalGoneMadProfileV1.validate(original)
        if (!profile.writable) return null
        val indexes = profile.paths.indices.filter { pathsEqual(profile.paths[it], target) }
        val paths = when (action) {
            CompanionAction.ADD_ONE -> if (indexes.isEmpty()) profile.paths + target else profile.paths
            CompanionAction.REMOVE_ALL -> profile.paths.filterNot { pathsEqual(it, target) }
        }
        return Plan(M3uWriterV1.write(paths), indexes)
    }

    private fun occurrenceIndexes(bytes: ByteArray, target: String): List<Int> {
        val profile = CanonicalGoneMadProfileV1.validate(bytes)
        if (!profile.writable) return emptyList()
        return profile.paths.indices.filter { pathsEqual(profile.paths[it], target) }
    }

    private fun pathsEqual(left: String, right: String): Boolean =
        PhonePathV1.normalize(left).equals(PhonePathV1.normalize(right), ignoreCase = true)

    private fun semanticSha(bytes: ByteArray): String? {
        val parsed = M3uParserV1.parse(bytes, "Shmemplaylist Companion Test.m3u")
        return (parsed as? ParseResult.Success)?.records
            ?.map { it.normalizedPath }
            ?.let(SemanticChecksumV1::ofNormalizedPaths)
    }

    private fun verifyExpected(actual: ByteArray, expected: ByteArray, semantic: String) {
        check(actual.contentEquals(expected)) { "exact-byte-verification-failed" }
        check(CanonicalGoneMadProfileV1.validate(actual).writable) { "profile-verification-failed" }
        check(semanticSha(actual) == semantic) { "semantic-verification-failed" }
    }

    private fun code(failure: Throwable): String =
        failure.message?.take(80) ?: failure.javaClass.simpleName
}
