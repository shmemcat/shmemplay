package io.github.shmemcat.shmemplay.playlists

import android.net.Uri
import io.github.shmemcat.shmemplay.domain.*
import io.github.shmemcat.shmemplay.tracks.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28])
class RuleLibraryIndexTest {
    private fun track(id: Long, title: String = "Song $id", artist: String = "Artist", genre: String = "Pop") =
        LibraryTrack(MediaStoreIdentity("external_primary", id), Uri.parse("content://fixture/$id"), "$id.mp3", "Music/", title, artist, "Album", genre, 60000, null)

    @Test fun distinctValuesCountSongsAndSearchUsesCachedNormalizedValues() {
        val index = RuleLibraryIndex(listOf(track(1, artist = "Beyoncé"), track(2, artist = "BEYONCÉ"), track(3, artist = "Other")))
        val values = index.choices(RuleField.ARTIST)
        assertEquals(2, values.size)
        assertEquals(2, RuleLibraryIndex.filter(values, "BEYONCE").single().songCount)
        assertSame(values, index.choices(RuleField.ARTIST))
    }
    @Test fun unknownGenreMatchesMissingValueAndIsAbsentFromPicker() {
        val index = RuleLibraryIndex(listOf(track(1, genre = "Unknown genre"), track(2)))
        assertEquals(listOf("Pop"), index.choices(RuleField.GENRE).map { it.label })
        assertEquals(RecipeEvaluation.Success(setOf("external_primary:1")), index.evaluate(PlaylistRuleNode.Metadata(RuleField.GENRE, RuleOperator.HAS_NO_VALUE), emptyList(), true))
    }
    @Test fun metadataRuleCodecRoundTripsNestedMultiValuesAndSourceReferences() {
        val node = PlaylistRuleNode.Group(children = listOf(PlaylistRuleNode.Membership("content://fixture/list", false),
            PlaylistRuleNode.Metadata(RuleField.TITLE, RuleOperator.IS_ANY_OF, listOf("Hello, Goodbye", "冬天"))))
        assertEquals(node, LocalPlaylistRecipes.decodeRule(LocalPlaylistRecipes.encodeRule(node)))
    }
    @Test fun oldSavedDefinitionsLoadWithoutChangingDuplicateBehavior() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nested-playlist-recipes-v1", 0).edit().putString("definitions",
            """[{"id":"legacy","name":"Old mix","live":true,"rule":{"match":"ALL","children":[{"source":"content://fixture/list","present":true}]}}]""").commit()
        val store = LocalPlaylistRecipes(context)
        val old = store.load().single()
        assertFalse(old.excludeDuplicates)
        store.save(old.copy(excludeDuplicates = true))
        assertTrue(store.load().single().excludeDuplicates)
    }
    @Test fun livePlaylistReevaluatesMetadataAndDuplicatePreference() {
        val songs = listOf(track(1, "Bloom"), track(2, "Bloom"))
        val recipe = LocalPlaylistRecipe("live", "Pop", PlaylistRuleNode.Metadata(RuleField.GENRE, RuleOperator.IS, listOf("Pop")), excludeDuplicates = true)
        val scan = PlaylistLibraryScan(emptyList(), emptyList())
        assertEquals(1, LocalPlaylistRecipes.evaluate(listOf(recipe), scan, songs).single().entries.size)
        assertEquals(2, LocalPlaylistRecipes.evaluate(listOf(recipe.copy(excludeDuplicates = false)), scan, songs).single().entries.size)
        assertEquals(1, LocalPlaylistRecipes.evaluate(listOf(recipe), scan, listOf(songs[0].copy(genre = "Rock"), songs[1])).single().entries.size)
    }
    @Test fun largeTitleIndexIsDistinctSortedAndSearchable() {
        val index = RuleLibraryIndex((1L..23000L).map { track(it) })
        val choices = index.choices(RuleField.TITLE)
        assertEquals(23000, choices.size)
        assertEquals("Song 23000", RuleLibraryIndex.filter(choices, "Song 23000").single().label)
    }
}
