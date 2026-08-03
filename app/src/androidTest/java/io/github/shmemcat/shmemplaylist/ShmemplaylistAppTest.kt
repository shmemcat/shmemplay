package io.github.shmemcat.shmemplaylist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class ShmemplaylistAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherDisplaysAppName() {
        composeRule.onNodeWithTag("app-title").assertIsDisplayed()
    }
}
