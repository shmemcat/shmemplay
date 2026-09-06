package io.github.shmemcat.shmemplay.domain

import org.junit.Assert.*
import org.junit.Test

class NestedPlaylistRulesTest {
    private val rule = PlaylistRuleNode.Group(children = listOf(
        PlaylistRuleNode.Group(RecipeMatch.ANY, listOf(PlaylistRuleNode.Membership("trip"), PlaylistRuleNode.Membership("favorites"))),
        PlaylistRuleNode.Membership("christmas", false)))
    @Test fun nestedUnionAndExclusion() {
        val result = NestedPlaylistRules.evaluate(setOf("A","B","C","D"), mapOf("trip" to setOf("A","B"), "favorites" to setOf("C"), "christmas" to setOf("B")), rule)
        assertEquals(RecipeEvaluation.Success(setOf("A","C")), result)
    }
    @Test fun unreadableNegativeSourceIsUnknownNotEmpty() {
        assertTrue(NestedPlaylistRules.evaluate(setOf("A"), mapOf("trip" to setOf("A"), "favorites" to emptySet()),rule) is RecipeEvaluation.UnknownSources)
    }
    @Test fun sourceReplacementRepairsNestedReferences() {
        assertEquals(setOf("renamed","favorites","christmas"), NestedPlaylistRules.sources(NestedPlaylistRules.replaceSource(rule,"trip","renamed")))
    }
    @Test(expected = IllegalArgumentException::class) fun emptyGroupsAreRejected() {
        NestedPlaylistRules.evaluate(emptySet(), emptyMap(), PlaylistRuleNode.Group())
    }
    @Test fun changedSourcesDoNotMutatePriorResults() {
        val before = NestedPlaylistRules.evaluate(setOf("A","B"),mapOf("trip" to setOf("A"),"favorites" to emptySet(),"christmas" to emptySet()),rule)
        val after = NestedPlaylistRules.evaluate(setOf("A","B"),mapOf("trip" to setOf("B"),"favorites" to emptySet(),"christmas" to emptySet()),rule)
        assertEquals(RecipeEvaluation.Success(setOf("A")),before)
        assertEquals(RecipeEvaluation.Success(setOf("B")),after)
    }
}
