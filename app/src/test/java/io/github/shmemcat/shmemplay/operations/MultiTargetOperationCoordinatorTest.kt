package io.github.shmemcat.shmemplay.operations

import android.content.Context
import io.github.shmemcat.shmemplay.domain.BatchAction
import io.github.shmemcat.shmemplay.storage.ExactByteBackupRepository
import io.github.shmemcat.shmemplay.storage.PlaylistDocumentHandle
import io.github.shmemcat.shmemplay.storage.PlaylistDocumentStorage
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
class MultiTargetOperationCoordinatorTest {
    private lateinit var context: Context
    private lateinit var storages: LinkedHashMap<String, MemoryStorage>
    private lateinit var journal: MemoryMultiJournal
    private lateinit var writeEvents: MutableList<String>
    private var nextId = 0

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.noBackupFilesDir.resolve("phase6-playlist-backups").deleteRecursively()
        storages = linkedMapOf()
        journal = MemoryMultiJournal()
        writeEvents = mutableListOf()
        nextId = 0
    }

    @Test
    fun `applies ordered batch with target scoped backups and history`() = runBlocking {
        val first = storage("first", "/storage/emulated/0/Music/one.mp3\n")
        val second = storage("second", "")
        val coordinator = coordinator()
        val preview = coordinator.preview(
            BatchAction.ADD_ONE,
            "Music/song.mp3",
            listOf(first, second),
        )

        val result = coordinator.apply(preview, "track", "DIRECT_MEDIASTORE")

        assertTrue(result is OperationOutcome.Changed)
        assertEquals(
            listOf(first.handle.documentIdentity, second.handle.documentIdentity),
            storages.values.flatMap { it.writeLog },
        )
        val row = coordinator.history().single()
        assertEquals(listOf(0, 1), row.targets.map { it.targetOrder })
        assertTrue(row.targets[0].backupName!!.contains("target-0"))
        assertTrue(row.targets[1].backupName!!.contains("target-1"))
        assertEquals(JournalState.SUCCEEDED, row.state)
    }

    @Test
    fun `failure on second target restores both in reverse order`() = runBlocking {
        val originalFirst = "/storage/emulated/0/Music/one.mp3\n".toByteArray()
        val originalSecond = "/storage/emulated/0/Music/two.mp3\n".toByteArray()
        val first = storage("first", originalFirst.decodeToString())
        val second = storage("second", originalSecond.decodeToString())
        var writes = 0
        second.failWrite = {
            writes++
            if (writes == 1) error("second-write-failed")
        }
        val result = coordinator().apply(
            coordinator().preview(
                BatchAction.ADD_ONE,
                "Music/song.mp3",
                listOf(first, second),
            ),
            "track",
            "MANUAL",
        )

        assertTrue(result is OperationOutcome.FailedSafe)
        assertArrayEquals(originalFirst, first.bytes)
        assertArrayEquals(originalSecond, second.bytes)
        assertEquals(
            listOf(
                "write-${first.handle.documentIdentity}",
                "failed-${second.handle.documentIdentity}",
                "restore-${second.handle.documentIdentity}",
                "restore-${first.handle.documentIdentity}",
            ),
            writeEvents,
        )
        assertEquals(JournalState.ROLLED_BACK, journal.rows.values.single().state)
    }

    @Test
    fun `apply refuses stale confirmation without journal or write`() = runBlocking {
        val target = storage("one", "")
        val coordinator = coordinator()
        val preview = coordinator.preview(BatchAction.ADD_ONE, "Music/song.mp3", listOf(target))
        target.bytes = "/storage/emulated/0/Music/external.mp3\n".toByteArray()

        val result = coordinator.apply(preview, "track", "MANUAL")

        assertEquals(OperationOutcome.FailedSafe(null, "confirmation-stale"), result)
        assertTrue(journal.rows.isEmpty())
        assertTrue(target.writeLog.isEmpty())
    }

    @Test
    fun `undo restores exact originals only when all targets still equal result`() = runBlocking {
        val first = storage("first", "/storage/emulated/0/Music/one.mp3\n")
        val second = storage("second", "")
        val coordinator = coordinator()
        val changed = coordinator.apply(
            coordinator.preview(BatchAction.ADD_ONE, "Music/song.mp3", listOf(first, second)),
            "track",
            "MANUAL",
        ) as OperationOutcome.Changed

        val undo = coordinator.undo(changed.operationId)

        assertTrue(undo is OperationOutcome.Changed)
        assertArrayEquals("/storage/emulated/0/Music/one.mp3\n".toByteArray(), first.bytes)
        assertArrayEquals(byteArrayOf(), second.bytes)
        assertEquals(JournalState.UNDONE, journal.rows[changed.operationId]?.state)
    }

    @Test
    fun `undo refuses entire batch when one target changed concurrently`() = runBlocking {
        val first = storage("first", "")
        val second = storage("second", "")
        val coordinator = coordinator()
        val changed = coordinator.apply(
            coordinator.preview(BatchAction.ADD_ONE, "Music/song.mp3", listOf(first, second)),
            "track",
            "MANUAL",
        ) as OperationOutcome.Changed
        second.bytes += "/storage/emulated/0/Music/external.mp3\n".toByteArray()
        val firstAfterApply = first.bytes.copyOf()

        val undo = coordinator.undo(changed.operationId)

        assertEquals(OperationOutcome.UndoRefused("concurrent-change-1"), undo)
        assertArrayEquals(firstAfterApply, first.bytes)
    }

    private fun storage(identity: String, content: String) =
        MemoryStorage(identity, content.toByteArray(), writeEvents).also {
            storages[it.handle.documentIdentity] = it
        }

    private fun coordinator() = MultiTargetOperationCoordinator(
        storageResolver = PlaylistDocumentStorageResolver { checkNotNull(storages[it]) },
        backups = ExactByteBackupRepository(context),
        journal = journal,
        clock = OperationClock { 1234L },
        operationId = { "batch-${++nextId}" },
    )

    private class MemoryStorage(
        identity: String,
        initial: ByteArray,
        private val writeEvents: MutableList<String>,
    ) : PlaylistDocumentStorage {
        override val handle = PlaylistDocumentHandle(
            "tree",
            "document-$identity",
            identity,
            "$identity.m3u",
        )
        var bytes = initial
        var failWrite: (() -> Unit)? = null
        val writeLog = mutableListOf<String>()
        val restoreLog = mutableListOf<String>()
        private var successfulWrites = 0
        private var attempts = 0

        override fun readExact() = bytes.copyOf()

        override fun overwriteExact(bytes: ByteArray) {
            attempts++
            try {
                failWrite?.invoke()
            } catch (failure: Throwable) {
                writeEvents += "failed-${handle.documentIdentity}"
                throw failure
            }
            val restoring = successfulWrites > 0 || attempts > 1
            if (restoring) {
                restoreLog += handle.documentIdentity
                writeEvents += "restore-${handle.documentIdentity}"
            } else {
                writeLog += handle.documentIdentity
                writeEvents += "write-${handle.documentIdentity}"
            }
            successfulWrites++
            this.bytes = bytes.copyOf()
        }
    }

    private class MemoryMultiJournal : MultiTargetOperationJournal {
        val rows = linkedMapOf<String, MultiTargetJournalOperation>()

        override suspend fun create(operation: MultiTargetJournalOperation) {
            check(rows.put(operation.operationId, operation) == null)
        }

        override suspend fun updateOperation(
            operationId: String,
            state: JournalState,
            now: Long,
            errorCode: String?,
        ) {
            val row = checkNotNull(rows[operationId])
            rows[operationId] = row.copy(
                state = state,
                updatedAtEpochMs = now,
                errorCode = errorCode,
            )
        }

        override suspend fun updateTarget(
            operationId: String,
            targetOrder: Int,
            state: JournalState,
            backupName: String?,
            backupSha256: String?,
            errorCode: String?,
        ) {
            val row = checkNotNull(rows[operationId])
            rows[operationId] = row.copy(
                targets = row.targets.map {
                    if (it.targetOrder != targetOrder) it else it.copy(
                        state = state,
                        backupName = backupName ?: it.backupName,
                        backupSha256 = backupSha256 ?: it.backupSha256,
                        errorCode = errorCode,
                    )
                },
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

        override suspend fun history() = rows.values.toList().asReversed()

        override suspend fun hasBlockingOperation() = nonTerminal().isNotEmpty()
    }
}
