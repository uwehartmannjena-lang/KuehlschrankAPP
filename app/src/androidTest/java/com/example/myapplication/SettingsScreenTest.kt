package com.example.myapplication

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.ui.screens.SettingsScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testBottomIconsDisplayedAndClickable() {
        var helpClicked = false
        var tourClicked = false
        var backupClicked = false
        var webClicked = false
        var clearClicked = false

        composeTestRule.setContent {
            SettingsScreen(
                onOpenHelp = { helpClicked = true },
                onStartTour = { tourClicked = true },
                onExportBackup = { backupClicked = true },
                onOpenWebCompanion = { webClicked = true },
                onClearInventory = { clearClicked = true }
            )
        }

        val tags = listOf(
            "SettingsIconBottom_0",
            "SettingsIconBottom_1",
            "SettingsIconBottom_2",
            "SettingsIconBottom_3",
            "SettingsIconBottom_4"
        )

        tags.forEach { tag ->
            composeTestRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHasClickAction()
        }

        composeTestRule.onNodeWithTag("SettingsIconBottom_0").performClick()
        assertTrue(helpClicked)

        composeTestRule.onNodeWithTag("SettingsIconBottom_1").performClick()
        assertTrue(tourClicked)

        composeTestRule.onNodeWithTag("SettingsIconBottom_2").performClick()
        assertTrue(backupClicked)

        composeTestRule.onNodeWithTag("SettingsIconBottom_3").performClick()
        assertTrue(webClicked)

        composeTestRule.onNodeWithTag("SettingsIconBottom_4").performClick()
        assertTrue(clearClicked)
    }
}
