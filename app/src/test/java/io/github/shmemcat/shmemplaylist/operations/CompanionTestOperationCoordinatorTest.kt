package io.github.shmemcat.shmemplaylist.operations

import android.content.Context
import io.github.shmemcat.shmemplaylist.storage.CompanionTestPlaylistStorage
import io.github.shmemcat.shmemplaylist.storage.ExactByteBackupRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompanionTestOperationCoordinatorTest {
    private lateinit var context: Context
    private lateinit var storage: MemoryStorage
    private lateinit var journal: MemoryJournal
    private var nextId = 0

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.noBackupFilesDir.resolve("phase6-playlist-backups").deleteRecursively()
        storage = MemoryStorage(byteArrayOf())
        journal = MemoryJournal()
        nextId = 0
    }

    @Test
    fun addWritesCanonicalBytesAndSkipsDuplicate() = runBlocking {
        val coordinator = coordinator()

        val changed = coordinator.apply(
            CompanionAction.ADD_ONE,
            "Music/Artist/song.mp3",
            "track-42",
            "DIRECT_MEDIASTORE",
        )
        val skipped = coordinator.apply(
            CompanionAction.ADD_ONE,
            "Music/Artist/song.mp3",
            "track-42",
            "DIRECT_MEDIASTORE",
        )

        assertTrue(changed is OperationOutcome.Changed)
        assertEquals(OperationOutcome.Skipped("already-present"), skipped)
        assertArrayEquals(
            "/storage/emulated/0/Music/Artist/song.mp3\n".toByteArray(),
            storage.bytes,
        )
        assertEquals(JournalState.SUCCEEDED, journal.rows.values.single().state)
    }

    @Test
    fun removeDeletesAllOccurrencesAndPreservesOthers() = runBlocking {
        storage.bytes = (
            "/storage/emulated/0/Music/song.mp3\n" +
                "/storage/emulated/0/Music/other.mp3\n" +
                "/storage/emulated/0/Music/song.mp3\n"
            ).toByteArray()

        val result = coordinator().apply(
            CompanionAction.REMOVE_ALL,
            "Music/song.mp3",
            "track-42",
            "MANUAL",
        )

        assertEquals(2, (result as OperationOutcome.Changed).occurrencesChanged)
        assertArrayEquals(
            "/storage/emulated/0/Music/other.mp3\n".toByteArray(),
            storage.bytes,
        )
    }

    @Test
    fun noncanonicalInputIsRefusedBeforeJournalAndWrite() = runBlocking {
        storage.bytes = "#EXTM3U\n/storage/emulated/0/Music/song.mp3\n".toByteArray()

        val result = coordinator().apply(
            CompanionAction.REMOVE_ALL,
            "Music/song.mp3",
            "track-42",
            "MANUAL",
        )

        assertEquals(
            OperationOutcome.FailedSafe(null, "noncanonical-test-playlist"),
            result,
        )
        assertTrue(journal.rows.isEmpty())
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun failureAfterWriteRestoresExactOriginalBytes() = runBlocking {
        val original = "/storage/emulated/0/Music/other.mp3\n".toByteArray()
        storage.bytes = original.copyOf()
        val coordinator = coordinator(
            DeterministicFailureController(FailurePoint.AFTER_WRITE),
        )

        val result = coordinator.apply(
            CompanionAction.ADD_ONE,
            "Music/song.mp3",
            "track-42",
            "DIRECT_MEDIASTORE",
        )

        assertTrue(result is OperationOutcome.FailedSafe)
        assertArrayEquals(original, storage.bytes)
        assertEquals(JournalState.ROLLED_BACK, journal.rows.values.single().state)
    }

    @Test
    fun undoRefusesConcurrentExactByteChange() = runBlocking {
        val coordinator = coordinator()
        val changed = coordinator.apply(
            CompanionAction.ADD_ONE,
            "Music/song.mp3",
            "track-42",
            "DIRECT_MEDIASTORE",
        ) as OperationOutcome.Changed
        storage.bytes += "/storage/emulated/0/Music/external.mp3\n".toByteArray()

        val undo = coordinator.undo(changed.operationId)

        assertEquals(OperationOutcome.UndoRefused("concurrent-change"), undo)
    }

    @Test
    fun recoveryRestoresUnknownBytesFromVerifiedBackup() = runBlocking {
        val original = "/storage/emulated/0/Music/original.mp3\n".toByteArray()
        storage.bytes = original.copyOf()
        val interrupted = coordinator(
            FailureController { point ->
                when (point) {
                    FailurePoint.AFTER_WRITE -> throw InjectedOperationFailure(point)
                    FailurePoint.BEFORE_RESTORE -> throw SimulatedProcessDeath()
                    else -> Unit
                }
            },
        )
        interrupted.apply(
            CompanionAction.ADD_ONE,
            "Music/song.mp3",
            "track-42",
            "DIRECT_MEDIASTORE",
        )
        storage.bytes = "/storage/emulated/0/Music/unknown.mp3\n".toByteArray()

        val result = coordinator().recover().single()

        assertEquals(RecoveryClassification.UNKNOWN, result.classification)
        assertEquals(JournalState.ROLLED_BACK, result.state)
        assertArrayEquals(original, storage.bytes)
    }

    private fun coordinator(failures: FailureController = FailureController.NONE) =
        CompanionTestOperationCoordinator(
            storage = storage,
            backups = ExactByteBackupRepository(context),
            journal = journal,
            clock = OperationClock { 1234 },
            failures = failures,
            operationId = { "operation-${++nextId}" },
        )

    private class MemoryStorage(initial: ByteArray) : CompanionTestPlaylistStorage {
        override val redactedDocumentIdentity = "document-hash"
        var bytes = initial
        var writeCount = 0

        override fun readExact() = bytes.copyOf()

        override fun overwriteExact(bytes: ByteArray) {
            writeCount++
            this.bytes = bytes.copyOf()
        }
    }

    private class MemoryJournal : OperationJournal {
        val rows = linkedMapOf<String, JournalOperation>()

        override suspend fun create(operation: JournalOperation) {
            check(rows.put(operation.operationId, operation) == null)
        }

        override suspend fun update(
            operationId: String,
            state: JournalState,
            now: Long,
            backupName: String?,
            backupSha256: String?,
            errorCode: String?,
        ) {
            val row = checkNotNull(rows[operationId])
            rows[operationId] = row.copy(
                state = state,
                updatedAtEpochMs = now,
                errorCode = errorCode,
                target = row.target.copy(
                    state = state,
                    backupName = backupName ?: row.target.backupName,
                    backupSha256 = backupSha256 ?: row.target.backupSha256,
                    errorCode = errorCode,
                ),
            )
        }

        override suspend fun get(operationId: String) = rows[operationId]

        override suspend fun nonTerminal() = rows.values.filter {
            it.state !in setOf(
                JournalState.SUCCEEDED,
                JournalState.SKIPPED,
                JournalState.ROLLED_BACK,
                JournalState.UNDONE,
                JournalState.FAILED_SAFE,
            )
        }

        override suspend fun hasBlockingOperation() = nonTerminal().isNotEmpty()
    }

    private class SimulatedProcessDeath : RuntimeException("simulated-process-death")
}
