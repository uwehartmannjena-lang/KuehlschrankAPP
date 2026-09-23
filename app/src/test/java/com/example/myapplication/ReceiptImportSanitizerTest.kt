package com.example.myapplication

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
}
