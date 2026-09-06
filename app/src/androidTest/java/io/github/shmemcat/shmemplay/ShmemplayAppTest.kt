package io.github.shmemcat.shmemplay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ShmemplayAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherDisplaysAppName() {
        composeRule.onNodeWithTag("app-title").assertIsDisplayed()
    }

    @Test
    fun settingsContainsLibraryAndPlaylistRefreshControls() {
        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNodeWithText("Music folders").assertIsDisplayed()
        composeRule.onNodeWithText("Refresh music library").assertIsDisplayed()
        composeRule.onNodeWithText("Playlist folder").assertIsDisplayed()
        composeRule.onNodeWithText("Rescan playlists").assertIsDisplayed()
    }
}
