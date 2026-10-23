package com.example.myapplication

import com.example.myapplication.data.ChefkochHelper
import com.example.myapplication.data.Product
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptImportSanitizerTest {

    @Test
    fun `cleanSearchTerm entfernt Kassenbon-Kuerzel und Mengen`() {
        assertEquals("Blattspinat", ReceiptImportSanitizer.cleanSearchTerm("K.Blattspinat 250g"))
        assertEquals("SENF MS", ReceiptImportSanitizer.cleanSearchTerm("BAUTZ.SENF MS 200 ml"))
        assertEquals("La Brique", ReceiptImportSanitizer.cleanSearchTerm("PRES. La Brique"))
        assertEquals("Thüringer Waldquell Medium", ReceiptImportSanitizer.cleanSearchTerm("TH.WQ. Medium 1,5l"))
    }

    @Test
    fun `fasst gleiche gueltige Scanartikel zusammen und verwirft unbrauchbare Treffer`() {
        val result = ReceiptImportSanitizer.prepareForImport(
            listOf(
                Product(name = "  Milch ", price = 1.09, quantity = 1),
                Product(name = "milch", price = 1.09, quantity = 2),
                Product(name = "___IGNORE___", price = 1.49),
                Product(name = "X", price = 1.29),
                Product(name = "Käse", price = 0.0)
            )
        )

        assertEquals(1, result.size)
        assertEquals("Milch", result.single().name)
        assertEquals(3, result.single().quantity)
        assertEquals(1.09, result.single().price, 0.001)
    }

    @Test
    fun `test striktes Entfernen von Marken und Suffixen`() {
        assertEquals("eier", ReceiptImportSanitizer.cleanReceiptText("K.eier"))
        assertEquals("Hackfleisch", ReceiptImportSanitizer.cleanReceiptText("Hackfleisch Gem"))
        assertEquals("Fleischsalat", ReceiptImportSanitizer.cleanReceiptText("K-classic Fleischsalat"))
    }

    @Test
    fun `test pre sanitization and title case beauty fallback`() {
        assertEquals("apfelmus", ReceiptImportSanitizer.cleanSearchTerm("Spreewh.apfelmus"))
        assertEquals("der Himmlische", ReceiptImportSanitizer.cleanSearchTerm("Möv.der Himmlische"))
        assertEquals("saure Sahne", ReceiptImportSanitizer.cleanSearchTerm("K.saure Sahne"))
        assertEquals("goldkrüstchen", ReceiptImportSanitizer.cleanSearchTerm("Kfav.goldkrüstchen"))
        assertEquals("Bay hel", ReceiptImportSanitizer.cleanSearchTerm("Allg.büble Bay.hel"))

        assertEquals("Spreewh Apfelmus", ReceiptImportSanitizer.toTitleCase("spreewh apfelmus"))
        assertEquals("Der Himmlische", ReceiptImportSanitizer.toTitleCase("der Himmlische"))
        assertEquals("Saure Sahne", ReceiptImportSanitizer.correctNameWithFuzzyMatching("K.saure Sahne"))
    }

    @Test
    fun `test learned correction feedback loop`() {
        ReceiptImportSanitizer.addLearnedCorrection("Allg.büble Bay.hel", "Bier")
        ReceiptImportSanitizer.addLearnedCorrection("Spreewh.apfelmus", "Apfelmus")

        assertEquals("Bier", ReceiptImportSanitizer.getLearnedCorrection("Allg.büble Bay.hel"))
        assertEquals("Bier", ReceiptImportSanitizer.cleanReceiptText("Allg.büble Bay.hel"))
        assertEquals("Apfelmus", ReceiptImportSanitizer.cleanReceiptText("Spreewh.apfelmus"))
    }

    @Test
    fun `test ChefkochZutatenReinigung`() {
        val cleaned = ChefkochHelper.cleanIngredientForChefkoch("Fenchel 1 Kg, K-classic Naturjoghurt")
        assertEquals("Fenchel Naturjoghurt", cleaned)
    }

    @Test
    fun `test reassembly and abbreviation resolution`() {
        assertEquals("Hähnchen-Salami", ReceiptImportSanitizer.cleanReceiptText("Hähnchen-sala mi"))
        assertEquals("Puten-Lachsschinken", ReceiptImportSanitizer.cleanReceiptText("Puten-lachssc hinken"))
        assertEquals("Geflügel-Fleischwurst", ReceiptImportSanitizer.cleanReceiptText("Geflügel-fleisc hwurst"))
        assertEquals("Karottenkrüstchen", ReceiptImportSanitizer.cleanReceiptText("Karottenkrüstc hen"))
        assertEquals("Chia-Skyr-Brötchen", ReceiptImportSanitizer.cleanReceiptText("Chia-skyr-bröt chen"))
        assertEquals("Hähnchenbrust", ReceiptImportSanitizer.cleanReceiptText("Hähnchenbrus t"))
        assertEquals("Schwarzwälder Schinken", ReceiptImportSanitizer.cleanReceiptText("Schww Schinken"))
        assertEquals("Knorr Fix Chili con Carne", ReceiptImportSanitizer.cleanReceiptText("Knorr Fix Chili C Car"))
        assertEquals("Cashew-Cranberry-Mix", ReceiptImportSanitizer.cleanReceiptText("Cash Cranb Mix"))
        assertEquals("Erdnüsse geröstet gesalzen", ReceiptImportSanitizer.cleanReceiptText("Erdn Gerös Ges"))
        assertEquals("Hähnchen-Brustfilet", ReceiptImportSanitizer.cleanReceiptText("H Brustfilet"))
        assertEquals("Wagner Steinofen Pizza", ReceiptImportSanitizer.cleanReceiptText("Wagner St Pizza"))
        assertEquals("Quäse", ReceiptImportSanitizer.cleanReceiptText("Ouäse"))
        assertEquals("Vollkorn-Kumpel", ReceiptImportSanitizer.cleanReceiptText("VOLLKORN KUMPEL"))
        assertEquals("Rosenbrötchen Mohn", ReceiptImportSanitizer.cleanReceiptText("ROSENBROE.MOHN"))
        assertEquals("Karottenkrüstchen", ReceiptImportSanitizer.cleanReceiptText("KAROTTENKRUEST."))
        assertEquals("Roggen-Ecke", ReceiptImportSanitizer.cleanReceiptText("ROGGEN X"))
        assertEquals("Vollkorn Brot", ReceiptImportSanitizer.cleanReceiptText("Vollkorn Brot Brot"))
    }

    @Test
    fun `prepareForImport filtert Summe MwSt Kartenzahlung Datum Pfand und Non-Food zuverlaessig heraus`() {
        val candidates = listOf(
            Product(name = "Frischmilch", price = 1.09, quantity = 1),
            Product(name = "Summe", price = 14.50, quantity = 1),
            Product(name = "MwSt 7%", price = 0.85, quantity = 1),
            Product(name = "Kartenzahlung", price = 14.50, quantity = 1),
            Product(name = "Datum: 04.10.2026", price = 1.00, quantity = 1),
            Product(name = "Pfand Flasche", price = 0.25, quantity = 1),
            Product(name = "Papiertragetasche", price = 0.35, quantity = 1),
            Product(name = "Zahnpasta", price = 1.45, quantity = 1),
            Product(name = "Weichspüler", price = 2.19, quantity = 1),
            Product(name = "Gouda Käse", price = 2.49, quantity = 2)
        )

        val result = ReceiptImportSanitizer.prepareForImport(candidates)

        assertEquals(2, result.size)
        val names = result.map { it.name }
        org.junit.Assert.assertTrue(names.contains("Frischmilch"))
        org.junit.Assert.assertTrue(names.contains("Gouda Käse"))
        org.junit.Assert.assertFalse(names.any { it.contains("Summe", ignoreCase = true) })
        org.junit.Assert.assertFalse(names.any { it.contains("MwSt", ignoreCase = true) })
        org.junit.Assert.assertFalse(names.any { it.contains("Kartenzahlung", ignoreCase = true) })
        org.junit.Assert.assertFalse(names.any { it.contains("Datum", ignoreCase = true) })
        org.junit.Assert.assertFalse(names.any { it.contains("Pfand", ignoreCase = true) })
        org.junit.Assert.assertFalse(names.any { it.contains("Tasche", ignoreCase = true) })
        org.junit.Assert.assertFalse(names.any { it.contains("Zahnpasta", ignoreCase = true) })
        org.junit.Assert.assertFalse(names.any { it.contains("Weichspüler", ignoreCase = true) })
    }
}
