package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartLogicTest {

    @Test
    fun `Sahnebecher wird als Becher erkannt`() {
        val unit = KassenzettelParser.determineSmartUnit("Sahnebecher", 0.99, 1)
        assertEquals("Becher", unit)
    }

    @Test
    fun `Moenchshof Bier wird bei Einzelpreis als Flasche erkannt`() {
        assertEquals("Flasche", KassenzettelParser.determineSmartUnit("Mönchshof Zwickel", 1.39, 1))
        assertEquals("Flasche", KassenzettelParser.determineSmartUnit("Mönchshof", 1.39, 1))
        assertEquals("Flasche", KassenzettelParser.determineSmartUnit("Mönchof", 1.39, 1))
    }

    @Test
    fun `Kuschelweich und Weichspueler werden als Flasche erkannt`() {
        assertEquals("Flasche", KassenzettelParser.determineSmartUnit("Kuschelweich Weichspüler", 2.22, 1))
        assertEquals("Flasche", KassenzettelParser.determineSmartUnit("Scheibenklar Apfel", 3.99, 1))
        assertEquals("Flasche", KassenzettelParser.determineSmartUnit("Pril Spülmittel", 1.99, 1))
    }

    @Test
    fun `Kassenbon Muell wie Steuer-IDs werden gefiltert`() {
        val text = """
            KAUFLAND
            DE145804122 4,99
            THÜRINGER WALDQUELL MEDIUM 9,98
            SUMME 9,98
        """.trimIndent()
        
        val products = KassenzettelParser.parseReceiptText(text)
        assertEquals(1, products.size)
        assertEquals("Thüringer Waldquell Medium", products[0].name)
        assertEquals(2, products[0].quantity)
        assertEquals("Kiste", products[0].unit)
        assertEquals(4.99, products[0].price, 0.01)
    }

    @Test
    fun `Intelligente Einheitenordnung erkennt Flasche Glas Dose Packung Becher Kiste`() {
        assertEquals("Becher", KassenzettelParser.determineSmartUnit("Schlagsahne 200g", 0.89, 1))
        assertEquals("Becher", KassenzettelParser.determineSmartUnit("Schmand", 0.79, 1))
        assertEquals("Becher", KassenzettelParser.determineSmartUnit("Naturjoghurt", 0.69, 1))
        assertEquals("Glas", KassenzettelParser.determineSmartUnit("Bautz'ner Senf", 1.19, 1))
        assertEquals("Glas", KassenzettelParser.determineSmartUnit("Marmelade Erdbeere", 2.29, 1))
        assertEquals("Dose", KassenzettelParser.determineSmartUnit("Red Bull Energy", 1.49, 1))
        assertEquals("Dose", KassenzettelParser.determineSmartUnit("Thunfisch in Öl", 1.89, 1))
        assertEquals("Flasche", KassenzettelParser.determineSmartUnit("Rotwein Weißburgunder", 4.99, 1))
        assertEquals("Kiste", KassenzettelParser.determineSmartUnit("Kasten Wasser", 4.49, 1))
        assertEquals("Packung", KassenzettelParser.determineSmartUnit("Käseaufschnitt", 1.99, 1))
        assertEquals("Tüte", KassenzettelParser.determineSmartUnit("Paprika Chips", 1.79, 1))
    }
}
