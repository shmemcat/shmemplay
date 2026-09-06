package io.github.shmemcat.shmemplay.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.shmemcat.shmemplay.playlists.*
import io.github.shmemcat.shmemplay.tracks.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Runs in an empty Compose host, with in-memory actions; never edits the user's files or queues. */
class PlayerPolishTest {
    @get:Rule val compose = createComposeRule()
    private val track = LibraryTrack(MediaStoreIdentity("external_primary", 99999999),
        Uri.parse("content://shmemplay-fixture/audio"), "Fixture.mp3", "Music/",
        "Fixture song", "Fixture artist", "Fixture album", "Test", 60000, null)

    @Test fun shortMenuLabelIsClickableAtFarRightEdge() {
        var clicks = 0
        compose.setContent { MaterialTheme { MenuAction("Info") { clicks++ } } }
        compose.onNodeWithText("Info").assertHasClickAction().performTouchInput {
            click(Offset(width - 2f, height / 2f))
        }
        compose.runOnIdle { assertEquals(1, clicks) }
    }


    @Test fun draggingMovesTheRowBeforeReleaseAndCommitsItsDropPosition() {
        val drag = QueueDragState()
        val order = mutableStateOf(listOf("A", "B", "C", "D"))
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxWidth()) {
                    order.value.forEach { id ->
                        Row(draggedRow(id, order.value, drag, 56.dp).fillMaxWidth().height(56.dp).testTag(id)) {
                            QueueDragHandle(id, order.value, drag, 56.dp) { order.value = it }
                            Text(id)
                        }
                    }
                }
            }
        }
        val originalTop = compose.onNodeWithTag("A").fetchSemanticsNode().boundsInRoot.top
        compose.onAllNodesWithContentDescription("Drag to reorder")[0].performTouchInput {
            down(center)
            moveBy(Offset(0f, 30f))
            moveBy(Offset(0f, 180f))
        }
        val heldTop = compose.onNodeWithTag("A").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Dragged row must visibly follow the finger before release", heldTop > originalTop + 100f)
        compose.onAllNodesWithContentDescription("Drag to reorder")[0].performTouchInput { up() }
        compose.runOnIdle {
            assertTrue(order.value.indexOf("A") > 0)
            assertEquals(setOf("A", "B", "C", "D"), order.value.toSet())
        }
    }

    @Test fun plusMenuCreatesAnEmptyPlaylistAfterNamePrompt() {
        var createdName: String? = null
        var createdTracks: List<LibraryTrack>? = null
        compose.setContent {
            MaterialTheme { LibraryBrowserApp(LibraryBrowserState(), LibraryBrowserActions(
                createPlaylist = { name, tracks -> createdName = name; createdTracks = tracks }
            )) }
        }
        compose.onNodeWithContentDescription("Playlists").performClick()
        compose.onNodeWithContentDescription("Create playlist").performClick()
        compose.onNodeWithText("New playlist").performClick()
        compose.onNodeWithText("Playlist name").performTextInput("Blank fixture")
        compose.onNodeWithText("Create").performClick()
        compose.runOnIdle {
            assertEquals("Blank fixture", createdName)
            assertEquals(emptyList<LibraryTrack>(), createdTracks)
        }
    }

    @Test fun plusMenuRetainsTheRulesCreationFlow() {
        compose.setContent { MaterialTheme { LibraryBrowserApp(LibraryBrowserState(), LibraryBrowserActions()) } }
        compose.onNodeWithContentDescription("Playlists").performClick()
        compose.onNodeWithContentDescription("Create playlist").performClick()
        compose.onNodeWithText("New playlist from rules").performClick()
        compose.onNodeWithText("Create from rules").assertIsDisplayed()
        compose.onNodeWithText("Match all").assertIsDisplayed()
        compose.onNodeWithText("Match any").assertIsDisplayed()
    }

    private fun beginMembershipWrite(deferClear: Boolean = false): MutableState<LibraryBrowserState> {
        val state = mutableStateOf(LibraryBrowserState(
            allTracks = listOf(track), includedFolderRoots = setOf("Music"),
            playlistScan = PlaylistLibraryScan(listOf(PlaylistSnapshot(
                PlaylistDocument(Uri.parse("content://shmemplay-fixture/playlist"), "Fixture.m3u", "audio/x-mpegurl"),
                emptyList())), emptyList())))
        compose.setContent {
            MaterialTheme {
                LibraryBrowserApp(state.value, LibraryBrowserActions(
                    applyMembership = { _, _, _ -> state.value = state.value.copy(mutation = BrowserMutationState.Working("Writing fixture")) },
                    clearMutationMessage = { if (!deferClear) state.value = state.value.copy(mutation = BrowserMutationState.Idle) },
                ))
            }
        }
        compose.onNodeWithContentDescription("All Songs").performClick()
        compose.onNodeWithContentDescription("Options for Fixture song").performClick()
        compose.onNodeWithText("Add/remove from playlists").performClick()
        compose.onNodeWithText("Fixture.m3u").performTouchInput { click() }
        compose.onNodeWithText("Add to 1").performClick()
        compose.onNodeWithText("Add to 1").assertDoesNotExist()
        compose.onAllNodes(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertCountEquals(1)
        return state
    }

    @Test fun verifiedWriteClosesMembershipWithoutConfirmation() {
        val state = beginMembershipWrite(deferClear = true)
        compose.runOnIdle { state.value = state.value.copy(mutation = BrowserMutationState.Result("Verified fixture")) }
        compose.onNodeWithText("Search playlists").assertDoesNotExist()
        compose.onNodeWithText("Verified fixture").assertDoesNotExist()
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(mutation = BrowserMutationState.Idle) }
        compose.onNodeWithText("Verified fixture").assertDoesNotExist()
    }

    @Test fun failedWriteKeepsMembershipAndDisplaysError() {
        val state = beginMembershipWrite()
        compose.runOnIdle { state.value = state.value.copy(mutation = BrowserMutationState.Error("Fixture write failed")) }
        compose.onNodeWithText("Fixture write failed").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Search playlists").assertIsDisplayed()
        compose.onNodeWithText("Add to 1").assertIsEnabled()
    }
}
