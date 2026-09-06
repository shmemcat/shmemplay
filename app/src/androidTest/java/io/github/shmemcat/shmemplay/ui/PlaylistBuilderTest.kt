package io.github.shmemcat.shmemplay.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.shmemcat.shmemplay.domain.*
import io.github.shmemcat.shmemplay.playlists.*
import io.github.shmemcat.shmemplay.tracks.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses only fixture tracks and callbacks in an empty host; never edits device playlists. */
class PlaylistBuilderTest {
    @get:Rule val compose = createComposeRule()
    private fun track(id: Long) = LibraryTrack(MediaStoreIdentity("external_primary", id), Uri.parse("content://fixture/$id"), "$id.mp3", "Music/", "Bloom", "Mira Vale", "Album", "Dream pop", 60000, null)
    private val validRule = PlaylistRuleNode.Group(children = listOf(PlaylistRuleNode.Metadata(RuleField.GENRE, RuleOperator.IS, listOf("Dream pop"))))

    @Test fun builderUsesFullScreenAndKeepsRequestedControlsOnly() {
        compose.setContent {
            var draft by rememberSaveable { mutableStateOf<String?>(null) }
            MaterialTheme { PlaylistBuilderScreen(LibraryBrowserState(), LibraryBrowserActions(), null, draft, { draft = it }, {}, {}) }
        }
        compose.onNodeWithText("Playlist builder").assertIsDisplayed()
        compose.onNodeWithText("Don't add duplicate songs").assertIsOn()
        compose.onNodeWithText("Find songs in").assertDoesNotExist()
        compose.onNodeWithText("Limits & ordering").assertDoesNotExist()
        compose.onNodeWithText("+ Rule").performClick()
        compose.onNodeWithText("Artist").performClick()
        compose.onNodeWithText("Play count").assertDoesNotExist()
        compose.onNodeWithText("Rating").assertDoesNotExist()
        compose.onNodeWithText("More fields").assertDoesNotExist()
    }
    @Test fun saveFailureKeepsDraftAndSuccessClosesBuilder() {
        val state = mutableStateOf(LibraryBrowserState(allTracks = listOf(track(1), track(2)), includedFolderRoots = setOf("Music")))
        var attempts = 0
        var savedDedupe = false
        compose.setContent {
            var draft by rememberSaveable { mutableStateOf<String?>(null) }
            var closed by remember { mutableStateOf(false) }
            MaterialTheme {
                if (closed) Text("Returned to playlists") else PlaylistBuilderScreen(state.value,
                    LibraryBrowserActions(createNestedPlaylist = { name, _, _, _, dedupe ->
                        assertEquals("My mix", name); savedDedupe = dedupe; attempts++
                        state.value = state.value.copy(mutation = if (attempts == 1) BrowserMutationState.Error("Fixture save failed") else BrowserMutationState.Result("Saved"))
                    }, clearMutationMessage = { state.value = state.value.copy(mutation = BrowserMutationState.Idle) }),
                    LocalPlaylistRecipe("fixture", "My mix", validRule, excludeDuplicates = true), draft, { draft = it }, {}, { closed = true })
            }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText("1 songs · 1 min").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save changes").performClick()
        compose.onNodeWithText("Fixture save failed").assertIsDisplayed()
        compose.onNodeWithText("My mix").assertIsDisplayed()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Returned to playlists").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertTrue(savedDedupe); assertEquals(2, attempts) }
    }
    @Test fun pickerVirtualizes23000ValuesAndPreservesSelectionAcrossSearch() {
        var confirmed = emptyList<String>()
        val choices = (0 until 23000).map { RuleValueChoice("$it", "${('A'.code + it % 26).toChar()} artist $it", 1, "artist $it") }.sortedBy { it.label }
        compose.setContent { MaterialTheme { PlaylistRuleValuePicker("Choose artist", choices, emptyList(), true, {}, { confirmed = it }) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("23000 values").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasText("artist", substring = true)).fetchSemanticsNodes().size < 50)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(22990)
        assertTrue(compose.onAllNodes(hasText("artist", substring = true)).fetchSemanticsNodes().size < 50)
        compose.onNodeWithText("Search values").performTextInput("artist 22999")
        compose.waitUntil(10000) { compose.onAllNodesWithText("1 values").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(choices.single { it.value == "22999" }.label).performClick()
        compose.onNodeWithText("Search values").performTextReplacement("no match here")
        compose.waitUntil(10000) { compose.onAllNodesWithText("No matching values. Try a different search.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Use 1 values").performClick()
        compose.runOnIdle { assertEquals(listOf("22999"), confirmed) }
    }
    @Test fun cancelPickerDoesNotCommitPendingSelection() {
        var confirmed = false
        var dismissed = false
        compose.setContent { MaterialTheme { PlaylistRuleValuePicker("Choose genre", listOf(RuleValueChoice("Pop", "Pop", 1, "pop")), emptyList(), false, { dismissed = true }, { confirmed = true }) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Pop").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pop").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(confirmed) }
    }
}
