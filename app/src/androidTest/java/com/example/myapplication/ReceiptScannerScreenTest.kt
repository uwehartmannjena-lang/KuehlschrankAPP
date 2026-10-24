package com.example.myapplication

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.ui.screens.ReceiptScannerScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptScannerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testScannerButtonsDisplayedAndClickable() {
        composeTestRule.setContent {
            ReceiptScannerScreen()
        }

        val buttonTags = listOf(
            "ReceiptScannerButton_Camera",
            "ReceiptScannerButton_Gallery",
            "ReceiptScannerButton_Pdf"
        )

        buttonTags.forEach { tag ->
            composeTestRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHasClickAction()
        }

        composeTestRule.onNodeWithTag("ReceiptScannerButton_Camera").performClick()
    }
}
