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
    fun `test smart sanitization enhancements`() {
        assertEquals("Schweppes Tonic Water", ReceiptImportSanitizer.cleanReceiptText("Schw.TonicW Zero1,25"))
        assertEquals("Schweppes Tonic Water", ReceiptImportSanitizer.cleanReceiptText("schw.tonicw"))
        assertEquals("Schweppes Wild Berry", ReceiptImportSanitizer.cleanReceiptText("sw wild berry"))
        assertEquals("Hex vom Dasenstein Weissherbst", ReceiptImportSanitizer.cleanReceiptText("hxm weissherbst"))
        assertEquals("Dinkel Johannisbeer Rührkuchen", ReceiptImportSanitizer.cleanReceiptText("dinkel joh beerrührk"))
        assertEquals("Cola", ReceiptImportSanitizer.cleanReceiptText("Cola Light 0,5l"))
        assertEquals("Fleisch-/Wurstwaren (Theke)", ReceiptImportSanitizer.cleanReceiptText("Metzgerei PLU 1234"))
        assertEquals("Käse (Frischetheke)", ReceiptImportSanitizer.cleanReceiptText("Käse PLU 5678"))
        assertEquals("Herta Finesse Hähnchenbrust", ReceiptImportSanitizer.cleanReceiptText("Fin. Hähnchenbrust M"))
        assertEquals("Hausmacher Handkäse", ReceiptImportSanitizer.cleanReceiptText("Haus Handkäse"))
        assertEquals("Herta Finesse Pfeffer", ReceiptImportSanitizer.cleanReceiptText("Finesse Pfeffer"))
        assertEquals("Expressreis", ReceiptImportSanitizer.cleanReceiptText("Exsreis"))
        assertEquals("Karottenkrüstchen", ReceiptImportSanitizer.cleanReceiptText("Arottenkrüstchen"))
        assertEquals("Gewürzgurken", ReceiptImportSanitizer.cleanReceiptText("Gewürzs"))
        assertEquals("Erdnüsse Geröstet", ReceiptImportSanitizer.correctNameWithFuzzyMatching("Erdn Gerös Ges"))
        assertEquals("Schinken", ReceiptImportSanitizer.cleanReceiptText("Schww Schinken"))
        assertEquals("Hähnchen-Brustfilet", ReceiptImportSanitizer.cleanReceiptText("H Brustfilet"))
        assertEquals("Herzhaft", ReceiptImportSanitizer.cleanReceiptText("Herzhaf"))
        assertEquals("Puten-Lachsschinken", ReceiptImportSanitizer.cleanReceiptText("Puten-lachsschinke n"))
    }
}
