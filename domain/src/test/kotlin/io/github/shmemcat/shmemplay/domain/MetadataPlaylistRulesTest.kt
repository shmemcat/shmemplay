package io.github.shmemcat.shmemplay.domain

import org.junit.Assert.*
import org.junit.Test

class MetadataPlaylistRulesTest {
    @Test fun artistRulesMatchEachNameAndNeverSplitCommasOrSongTitles() {
        val data = mapOf("duet" to RuleTrackMetadata("Title feat. Guest", "Tyler, the Creator; Guest", "Pop"),
            "solo" to RuleTrackMetadata("Other", "Guest", "Pop"), "unknown" to RuleTrackMetadata("Missing", "", ""))
        fun matches(field: RuleField, operator: RuleOperator, vararg values: String): Set<String> =
            (NestedPlaylistRules.evaluate(data.keys, emptyMap(), PlaylistRuleNode.Metadata(field, operator, values.toList()), data) as RecipeEvaluation.Success).trackIdentities
        assertEquals(setOf("duet", "solo"), matches(RuleField.ARTIST, RuleOperator.IS, "Guest"))
        assertEquals(setOf("duet"), matches(RuleField.ARTIST, RuleOperator.IS, "Tyler, the Creator"))
        assertTrue(matches(RuleField.ARTIST, RuleOperator.IS, "the Creator").isEmpty())
        assertEquals(setOf("unknown"), matches(RuleField.ARTIST, RuleOperator.IS_NOT, "Guest"))
        assertEquals(setOf("duet"), matches(RuleField.ARTIST, RuleOperator.STARTS_WITH, "Tyler"))
        assertEquals(setOf("duet"), matches(RuleField.TITLE, RuleOperator.IS, "Title feat. Guest"))
        assertEquals(setOf("unknown"), matches(RuleField.ARTIST, RuleOperator.HAS_NO_VALUE))
    }
    @Test fun everyGenreMatchesIndividuallyAndExclusionRequiresNoMatch() {
        val data = mapOf("multi" to RuleTrackMetadata("Song", "Artist", "Rock; Dream pop\u0000Shoegaze"),
            "pop" to RuleTrackMetadata("Other", "Artist", "Pop"), "missing" to RuleTrackMetadata("Missing", "Artist", "; ;"))
        fun matches(operator: RuleOperator, vararg values: String): Set<String> =
            (NestedPlaylistRules.evaluate(data.keys, emptyMap(), PlaylistRuleNode.Metadata(RuleField.GENRE, operator, values.toList()), data) as RecipeEvaluation.Success).trackIdentities
        assertEquals(setOf("multi"), matches(RuleOperator.IS, "Dream pop"))
        assertEquals(setOf("multi"), matches(RuleOperator.IS, "shoegaze"))
        assertEquals(setOf("multi", "pop"), matches(RuleOperator.IS_ANY_OF, "Shoegaze", "Pop"))
        assertEquals(setOf("pop", "missing"), matches(RuleOperator.IS_NOT, "Rock"))
        assertEquals(setOf("multi"), matches(RuleOperator.STARTS_WITH, "Dream"))
        assertEquals(setOf("multi"), matches(RuleOperator.CONTAINS, "gaze"))
        assertEquals(emptySet<String>(), matches(RuleOperator.CONTAINS, "pop shoe"))
        assertEquals(setOf("missing"), matches(RuleOperator.HAS_NO_VALUE))
    }
    private val metadata = linkedMapOf(
        "a" to RuleTrackMetadata("Bloom", "Mira Vale", "Dream pop"),
        "b" to RuleTrackMetadata("Bloom", "Mira Vale", "Dream pop"),
        "c" to RuleTrackMetadata("Summer", "hellogoodbye", "Indie pop"),
        "d" to RuleTrackMetadata("Joke", "RM", "K-pop"),
    )
    private fun evaluate(rule: PlaylistRuleNode, dedupe: Boolean = false) = NestedPlaylistRules.evaluate(metadata.keys, mapOf("favorites" to setOf("a", "b", "c")), rule, metadata, dedupe)

    @Test fun mixedMembershipAndNestedMetadataRules() {
        val rule = PlaylistRuleNode.Group(children = listOf(PlaylistRuleNode.Membership("favorites"),
            PlaylistRuleNode.Group(RecipeMatch.ANY, listOf(
                PlaylistRuleNode.Metadata(RuleField.GENRE, RuleOperator.IS, listOf("Dream pop")),
                PlaylistRuleNode.Metadata(RuleField.ARTIST, RuleOperator.STARTS_WITH, listOf("hello")),
            ))))
        assertEquals(RecipeEvaluation.Success(setOf("a", "c")), evaluate(rule, true))
        assertEquals(RecipeEvaluation.Success(setOf("a", "b", "c")), evaluate(rule))
    }
    @Test fun multipleValuesPreserveCommasInSongTitles() {
        val data = mapOf("a" to RuleTrackMetadata("Hello, Goodbye", "Artist", "Pop"), "b" to RuleTrackMetadata("Hello", "Artist", "Pop"))
        val rule = PlaylistRuleNode.Metadata(RuleField.TITLE, RuleOperator.IS_ANY_OF, listOf("Hello, Goodbye", "Elsewhere"))
        assertEquals(RecipeEvaluation.Success(setOf("a")), NestedPlaylistRules.evaluate(data.keys, emptyMap(), rule, data))
    }
    @Test fun accentAndCaseInsensitiveContainsAndExclusion() {
        assertEquals(RecipeEvaluation.Success(setOf("a", "b")), evaluate(PlaylistRuleNode.Metadata(RuleField.ARTIST, RuleOperator.CONTAINS, listOf("MÍRA"))))
        assertEquals(RecipeEvaluation.Success(setOf("c", "d")), evaluate(PlaylistRuleNode.Metadata(RuleField.ARTIST, RuleOperator.IS_NOT, listOf("Mira Vale"))))
    }
    @Test fun missingMetadataValuesDoNotCollapseUnrelatedSongs() {
        val data = mapOf("a" to RuleTrackMetadata("", "", ""), "b" to RuleTrackMetadata("", "", ""))
        assertEquals(RecipeEvaluation.Success(data.keys), NestedPlaylistRules.evaluate(data.keys, emptyMap(), PlaylistRuleNode.Metadata(RuleField.GENRE, RuleOperator.HAS_NO_VALUE), data, true))
    }
    @Test fun missingSourceIsStillUnknownEvenWhenAnotherAnyRuleMatches() {
        val rule = PlaylistRuleNode.Group(RecipeMatch.ANY, listOf(PlaylistRuleNode.Membership("missing", false), PlaylistRuleNode.Metadata(RuleField.GENRE, RuleOperator.IS, listOf("Dream pop"))))
        assertEquals(RecipeEvaluation.UnknownSources(setOf("missing")), evaluate(rule))
    }
    @Test(expected = IllegalArgumentException::class) fun emptyMultiValueRuleIsRejected() { evaluate(PlaylistRuleNode.Metadata(RuleField.ARTIST, RuleOperator.IS_ANY_OF)) }
    @Test(expected = IllegalArgumentException::class) fun textRulesRequireAvailableMetadata() {
        NestedPlaylistRules.evaluate(setOf("a"), emptyMap(), PlaylistRuleNode.Metadata(RuleField.ARTIST, RuleOperator.IS_NOT, listOf("X")))
    }
    @Test fun largeLibraryUsesValueMembershipAndKeepsInputOrder() {
        val data = (0 until 23000).associate { "$it" to RuleTrackMetadata("Song $it", "Artist ${it % 100}", "Pop") }
        val rule = PlaylistRuleNode.Metadata(RuleField.ARTIST, RuleOperator.IS_ANY_OF, listOf("Artist 4", "Artist 17"))
        val result = NestedPlaylistRules.evaluate(data.keys, emptyMap(), rule, data) as RecipeEvaluation.Success
        assertEquals(460, result.trackIdentities.size)
        assertEquals(listOf("4", "17", "104", "117"), result.trackIdentities.take(4))
    }
}
