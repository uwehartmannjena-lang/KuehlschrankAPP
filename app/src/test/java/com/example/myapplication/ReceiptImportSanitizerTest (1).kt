package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptImportSanitizerTest {

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
