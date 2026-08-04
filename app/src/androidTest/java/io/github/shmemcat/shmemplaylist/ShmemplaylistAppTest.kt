package io.github.shmemcat.shmemplaylist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ShmemplaylistAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherDisplaysAppName() {
        composeRule.onNodeWithTag("app-title").assertIsDisplayed()
    }

    @Test
    fun overflowContainsGlobalUtilities() {
        composeRule.onNodeWithTag("overflow-menu").performClick()

        composeRule.onNodeWithText("Choose playlist folder").assertIsDisplayed()
        composeRule.onNodeWithText("Rescan playlists").assertIsDisplayed()
        composeRule.onNodeWithText("Test provider capabilities").assertIsDisplayed()
        composeRule.onNodeWithText("Export diagnostics").assertIsDisplayed()
        composeRule.onNodeWithText("History").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }
}
