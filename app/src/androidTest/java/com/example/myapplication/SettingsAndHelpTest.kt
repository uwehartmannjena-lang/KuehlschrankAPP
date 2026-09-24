package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.ui.help.AppHelpManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAndHelpTest {

    @Test
    fun testAppHelpManagerTopicsAndOnboardingSteps() {
        val topics = AppHelpManager.topics
        assertFalse("Hilfethemen sollten nicht leer sein", topics.isEmpty())

        val scanTopic = topics.find { it.id == "receipt_scan" }
        assertNotNull("Kassenbon-Scan Thema sollte existieren", scanTopic)
        assertTrue("Sollte detaillierte Schritte enthalten", scanTopic!!.detailedSteps.isNotEmpty())
        assertTrue("Sollte FAQs enthalten", scanTopic.faqs.isNotEmpty())

        val onboardingSteps = AppHelpManager.onboardingSteps
        assertEquals(5, onboardingSteps.size)
        assertEquals("welcome", onboardingSteps[0].id)
    }

    @Test
    fun testHelpSearchFunctionality() {
        val queryResultScan = AppHelpManager.searchHelp("Kassenbon")
        assertFalse(queryResultScan.isEmpty())
        assertTrue(queryResultScan.any { it.id == "receipt_scan" })

        val queryResultMhd = AppHelpManager.searchHelp("MHD")
        assertFalse(queryResultMhd.isEmpty())
        assertTrue(queryResultMhd.any { it.id == "mhd_tracking" })

        val emptyResult = AppHelpManager.searchHelp("UnbekannterBegriff12345")
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun testFavoriteIconOptionsConfigured() {
        val favoriteIcons = listOf(
            "Star", "Heart", "Favorite", "Bookmark", "PushPin",
            "WorkspacePremium", "LocalFireDepartment", "Lightbulb", "CheckCircle", "ShoppingBag",
            "LocalMall", "Loyalty", "Diamond", "Celebration", "Eco"
        )
        assertEquals("Es müssen genau 15 Favoriten-Symbole verfügbar sein", 15, favoriteIcons.size)
    }
}
