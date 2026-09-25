package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class KassenzettelParserTest {

    @Test
    fun `test expansion of BRAND_DATABASE`() {
        val text = "G&G APFELSAFT 1,49 €"
        val products = KassenzettelParser.parseReceiptText(text)
        
        println("Result 1: ${products.getOrNull(0)?.name}")
        assertEquals(1, products.size)
        assertEquals("Gut & Günstig Apfelsaft", products[0].name)
        assertEquals(1.49, products[0].price, 0.001)
    }

    @Test
    fun `test expansion of private labels`() {
        val text = "JA! MILCH 1,5% 0,99 €"
        val products = KassenzettelParser.parseReceiptText(text)
        
        println("Result 2: ${products.getOrNull(0)?.name}")
        assertEquals(1, products.size)
        assertEquals("Ja Milch", products[0].name)
    }

    @Test
    fun `test OCR repair for Stk`() {
        val text = "2 5tk BANANEN 1,99 €"
        val products = KassenzettelParser.parseReceiptText(text)
        
        println("Result 3: ${products.getOrNull(0)?.name}")
        assertEquals(1, products.size)
        assertEquals("Bananen", products[0].name)
        assertEquals(2, products[0].quantity)
    }

    @Test
    fun `test learning mechanism`() {
        val text = "UNBEKANNTER ARTIKEL 2,99 €"
        val corrections = mapOf("UNBEKANNTER ARTIKEL" to "Lieblingsmüsli")
        
        val products = KassenzettelParser.parseReceiptText(text, corrections)
        
        assertEquals(1, products.size)
        assertEquals("Lieblingsmüsli", products[0].name)
    }

    @Test
    fun `test warehouse abbreviations`() {
        val text = "MOPRO JOGHURT 0,49 €"
        val products = KassenzettelParser.parseReceiptText(text)
        
        assertEquals(1, products.size)
        assertEquals("Molkerei Joghurt", products[0].name)
    }

    @Test
    fun `mehrzeilige Bonposition ignoriert Marktadresse und stoppt bei Summe`() {
        val text = """
            REWE Markt
            Musterstraße 12
            FRISCHMILCH
            1,09 A
            BANANEN
            2 x 0,79 A
            SUMME 2,67
            Kartenzahlung
        """.trimIndent()

        val products = KassenzettelParser.parseReceiptText(text)

        assertEquals(2, products.size)
        assertEquals("Frischmilch", products[0].name)
        assertEquals(1.09, products[0].price, 0.001)
        assertEquals("Bananen", products[1].name)
        assertEquals(2, products[1].quantity)
        assertEquals(0.79, products[1].price, 0.001)
    }

    @Test
    fun `rabatt wird dem vorherigen Artikel zugerechnet`() {
        val products = KassenzettelParser.parseReceiptText(
            """
                GOUDA 2,99 A
                RABATT -0,50
                ZU ZAHLEN 2,49
            """.trimIndent()
        )

        assertEquals(1, products.size)
        assertEquals("Gouda", products.single().name)
        assertEquals(2.49, products.single().price, 0.001)
    }

    @Test
    fun `test lild receipt style without tax letters`() {
        val text = """
            LIDL
            BIO BANANEN 1.99
            H-MILCH 1,5% 0.88
            SUMME 2.87
        """.trimIndent()
        
        val products = KassenzettelParser.parseReceiptText(text)
        
        assertEquals(2, products.size)
        assertEquals("Bio Bananen", products[0].name)
        assertEquals(1.99, products[0].price, 0.001)
        assertEquals("H-Milch", products[1].name) // 1,5% wird weggefiltert
    }

    @Test
    fun `test globus receipt style`() {
        val text = """
            GLOBUS Markthalle
            Bananen lose 0.99
            1.234 kg x 0,80
            H-Milch 1,5%
            * 0,88 A
            SUMME 1.87
        """.trimIndent()
        
        val products = KassenzettelParser.parseReceiptText(text)
        
        assertEquals(2, products.size)
        assertEquals("Bananen Lose", products[0].name)
        assertEquals(0.99, products[0].price, 0.001)
        assertEquals("H-Milch", products[1].name)
        assertEquals(0.88, products[1].price, 0.001)
    }

    @Test
    fun `test app export style with separate lines`() {
        val text = """
            Lidl Plus Beleg
            Bio Gurke
            1 x 0.99
            0.99
            Käseaufschnitt
            2 x 1.49
            2.98
            GESAMT 3.97
        """.trimIndent()
        
        val products = KassenzettelParser.parseReceiptText(text)
        
        assertEquals(2, products.size)
        assertEquals("Bio Gurke", products[0].name)
        assertEquals(0.99, products[0].price, 0.001)
        assertEquals(1, products[0].quantity)
        assertEquals("Käseaufschnitt", products[1].name)
        assertEquals(2.98, products[1].price, 0.001)
        assertEquals(2, products[1].quantity)
    }

    @Test
    fun `test filter market address with PLZ and legal forms and stop at cash payment`() {
        val text = """
            REWE Markt GmbH & Co. KG
            Musterstraße 12
            07743 Jena
            Tel. 03641/123456
            St.-Nr. 123/456/7890
            FRISCHMILCH
            1,09 A
            BANANEN
            2 x 0,79 A
            BAR 10,00
            RÜCKGELD 7,33
        """.trimIndent()

        val products = KassenzettelParser.parseReceiptText(text)

        assertEquals(2, products.size)
        assertEquals("Frischmilch", products[0].name)
        assertEquals(1.09, products[0].price, 0.001)
        assertEquals("Bananen", products[1].name)
        assertEquals(2, products[1].quantity)
        assertEquals(0.79, products[1].price, 0.001)
    }
}
