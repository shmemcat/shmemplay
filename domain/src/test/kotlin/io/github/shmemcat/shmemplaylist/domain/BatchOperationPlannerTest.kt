package io.github.shmemcat.shmemplaylist.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BatchOperationPlannerTest {
    @Test
    fun `plans ordered add changes skips and ineligible targets`() {
        val plan = BatchOperationPlannerV1.plan(
            BatchAction.ADD_ONE,
            "Music/Artist/song.mp3",
            listOf(
                target("first", "/storage/emulated/0/Music/other.mp3\n"),
                target("second", "/storage/emulated/0/Music/Artist/song.mp3\n"),
                target("third", "#EXTM3U\n"),
            ),
        )

        assertEquals(listOf("first", "second", "third"), plan.targets.map { it.documentIdentity })
        val change = plan.targets[0] as BatchTargetDecision.Change
        assertArrayEquals(
            (
                "/storage/emulated/0/Music/other.mp3\n" +
                    "/storage/emulated/0/Music/Artist/song.mp3\n"
                ).toByteArray(),
            change.expectedBytes,
        )
        assertEquals("already-present", (plan.targets[1] as BatchTargetDecision.Skip).reason)
        assertEquals(
            listOf("non-path-record"),
            (plan.targets[2] as BatchTargetDecision.Ineligible).reasons,
        )
    }

    @Test
    fun `remove deletes all occurrences while preserving remaining order and multiplicity`() {
        val plan = BatchOperationPlannerV1.plan(
            BatchAction.REMOVE_ALL,
            "Music/song.mp3",
            listOf(
                target(
                    "one",
                    (
                        "/storage/emulated/0/Music/song.mp3\n" +
                            "/storage/emulated/0/Music/keep.mp3\n" +
                            "/storage/emulated/0/music/SONG.mp3\n" +
                            "/storage/emulated/0/Music/keep.mp3\n"
                        ),
                ),
            ),
        )

        val change = plan.changes.single()
        assertEquals(listOf(0, 2), change.originalOccurrenceIndexes)
        assertArrayEquals(
            (
                "/storage/emulated/0/Music/keep.mp3\n" +
                    "/storage/emulated/0/Music/keep.mp3\n"
                ).toByteArray(),
            change.expectedBytes,
        )
    }

    @Test
    fun `duplicate document identities are refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            BatchOperationPlannerV1.plan(
                BatchAction.ADD_ONE,
                "Music/song.mp3",
                listOf(target("same", ""), target("same", "")),
            )
        }
    }

    private fun target(identity: String, content: String) = BatchTargetInput(
        documentIdentity = identity,
        displayName = "$identity.m3u",
        originalBytes = content.toByteArray(),
    )
}
