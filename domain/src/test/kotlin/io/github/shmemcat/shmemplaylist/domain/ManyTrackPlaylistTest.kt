package io.github.shmemcat.shmemplaylist.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManyTrackPlaylistTest {
    @Test
    fun `add appends only missing tracks and creates no duplicates`() {
        val plan = ManyTrackBatchOperationPlannerV2.plan(
            BatchAction.ADD_ONE,
            listOf("Music/one.mp3", "Music/two.mp3", "Music/ONE.mp3"),
            listOf(target("/storage/emulated/0/Music/one.mp3\n/storage/emulated/0/Music/keep.mp3\n")),
        )

        val change = plan.changes.single()
        assertArrayEquals(
            (
                "/storage/emulated/0/Music/one.mp3\n" +
                    "/storage/emulated/0/Music/keep.mp3\n" +
                    "/storage/emulated/0/Music/two.mp3\n"
                ).toByteArray(),
            change.expectedBytes,
        )
        assertEquals(2, change.expectedOccurrenceCount)
    }

    @Test
    fun `add skips playlist that contains every selected track`() {
        val plan = ManyTrackBatchOperationPlannerV2.plan(
            BatchAction.ADD_ONE,
            listOf("Music/one.mp3", "Music/two.mp3"),
            listOf(target("/storage/emulated/0/Music/two.mp3\n/storage/emulated/0/Music/one.mp3\n")),
        )

        assertEquals("all-already-present", (plan.targets.single() as BatchTargetDecision.Skip).reason)
    }

    @Test
    fun `remove deletes every occurrence of every selected track`() {
        val plan = ManyTrackBatchOperationPlannerV2.plan(
            BatchAction.REMOVE_ALL,
            listOf("Music/one.mp3", "Music/two.mp3"),
            listOf(
                target(
                    "/storage/emulated/0/Music/one.mp3\n" +
                        "/storage/emulated/0/Music/keep.mp3\n" +
                        "/storage/emulated/0/music/TWO.mp3\n" +
                        "/storage/emulated/0/Music/one.mp3\n",
                ),
            ),
        )

        assertArrayEquals(
            "/storage/emulated/0/Music/keep.mp3\n".toByteArray(),
            plan.changes.single().expectedBytes,
        )
    }

    @Test
    fun `recipe evaluator supports intersection difference neither and union`() {
        val all = linkedSetOf("a", "b", "c", "d")
        val memberships = mapOf("p1" to setOf("a", "b"), "p2" to setOf("b", "c"))

        fun evaluate(match: RecipeMatch, vararg rules: PlaylistRule): Set<String> =
            (PlaylistRecipeEvaluatorV1.evaluate(all, memberships, PlaylistRecipe("x", match, rules.toList()))
                as RecipeEvaluation.Success).trackIdentities

        assertEquals(setOf("b"), evaluate(RecipeMatch.ALL, PlaylistRule("p1", true), PlaylistRule("p2", true)))
        assertEquals(setOf("a"), evaluate(RecipeMatch.ALL, PlaylistRule("p1", true), PlaylistRule("p2", false)))
        assertEquals(setOf("d"), evaluate(RecipeMatch.ALL, PlaylistRule("p1", false), PlaylistRule("p2", false)))
        assertEquals(setOf("a", "b", "c"), evaluate(RecipeMatch.ANY, PlaylistRule("p1", true), PlaylistRule("p2", true)))
    }

    @Test
    fun `unknown recipe source blocks evaluation`() {
        val result = PlaylistRecipeEvaluatorV1.evaluate(
            setOf("a"),
            emptyMap(),
            PlaylistRecipe("x", RecipeMatch.ALL, listOf(PlaylistRule("missing", false))),
        )
        assertTrue(result is RecipeEvaluation.UnknownSources)
    }

    private fun target(content: String) = BatchTargetInput("playlist", "playlist.m3u", content.toByteArray())
}
