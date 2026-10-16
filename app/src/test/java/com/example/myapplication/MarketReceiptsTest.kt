package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MarketReceiptsTest {

    @Test
    fun `test Kaufland Beleg mit Inline-Multiplikator und Rabatten`() {
        val text = """
            Belegkopie
            Kaufland - Humboldtstraße 90
            99425 Weimar
            Preis EUR
            Harzbube Edelschi.
            1,99 B
            Rinderhackfleisch
            4,99 B
            Lübzer Pils     2 * 1,09
            2,18 A
            Pfandartikel     2 * 0,08
            0,16 A
            Bens Expressreis
               2 * 1,69
            3,38 B
            Mengenrabatt
            -0,71
            Summe
            11,83
            Kartenzahlung 11,83
            Datum 20.07.26 08:16 Uhr
        """.trimIndent()

        val result = KassenzettelParser.parseReceiptDetailed(text)

        assertEquals("Kaufland", result.supermarket)
        val dateFormatted = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(result.purchaseDate))
        assertEquals("20.07.2026", dateFormatted)

        val products = result.products
        assertTrue("Artikel sollten erkannt werden", products.size >= 4)

        val luebzer = products.find { it.name.contains("Lübzer Pils", ignoreCase = true) }
        assertNotNull("Lübzer Pils sollte existieren", luebzer)
        assertEquals(2, luebzer!!.quantity)
        assertEquals(2.18, luebzer.price, 0.01)

        val reis = products.find { it.name.contains("Expressreis", ignoreCase = true) }
        assertNotNull("Bens Expressreis sollte existieren", reis)
        assertEquals(2, reis!!.quantity)
        // 3.38 - 0.71 = 2.67
        assertEquals(2.67, reis.price, 0.01)

        assertEquals(0.16, result.sumDeposit, 0.01)
    }

    @Test
    fun `test Rewe und Nahkauf Beleg mit nachfolgendem Multiplikator`() {
        val text = """
            **** Nahkauf Heyer ****
            Leonhard Frank Str. 08
            99425 Weimar
            EUR
            KOCHSCHINKEN O.                  2,48 B
            MIREE DATTEL-CUR                 1,19 B
            MAGGI FIX CARBO                  2,18 B
                         2 Stk x    1,09
            MOENCH.LANDBIER                  1,39 A
            PFAND 0,15 EUR                   0,15 A *
            --------------------------------------
            SUMME                   EUR      7,39
            18.09.2026     14:30     Bon-Nr.:4235
        """.trimIndent()

        val result = KassenzettelParser.parseReceiptDetailed(text)

        assertEquals("Rewe", result.supermarket)
        val dateFormatted = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(result.purchaseDate))
        assertEquals("18.09.2026", dateFormatted)

        val products = result.products
        assertEquals(4, products.size)

        val maggi = products.find { it.name.contains("Maggi Fix Carbonara", ignoreCase = true) }
        assertNotNull("Maggi Fix Carbonara sollte existieren", maggi)
        assertEquals(2, maggi!!.quantity)
        assertEquals(2.18, maggi.price, 0.01)

        val bier = products.find { it.name.contains("Mönchshof Landbier", ignoreCase = true) }
        assertNotNull("Mönchshof Landbier sollte existieren", bier)
        assertEquals(1, bier!!.quantity)
        assertEquals(1.39, bier.price, 0.01)

        assertEquals(0.15, result.sumDeposit, 0.01)
    }

    @Test
    fun `test Netto Beleg mit Mengen nach und vor Artikel und Preisaenderung`() {
        val text = """
            Dein Einkauf vom 16.08.2025 09:34
            Filiale 0008696 / Pos 001 / Bon-Nr. 0412
            EUR
            BO Schnittbroetchen 1ST 2,28
              12 x 0,19 EUR 
            TWQ Medium MW12x0,75L 4,44
            MW-Pfand 3,30 EUR 3,30*
            GL Creme Fraiche Natur 200g 0,89
            0.10€ Rabatt -0,10
            Kiwi Gold ST 1,38
              2 x 0,69 EUR 
            Bio Bananen Lose 0,71
              0,40 g x 1,79 EUR/g 
            Summe 12,62
            Netto App 12,62
        """.trimIndent()

        val result = KassenzettelParser.parseReceiptDetailed(text)

        assertEquals("Netto", result.supermarket)
        val dateFormatted = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(result.purchaseDate))
        assertEquals("16.08.2025", dateFormatted)

        val products = result.products
        assertTrue(products.size >= 5)

        val broetchen = products.find { it.name.contains("Schnittbrötchen", ignoreCase = true) }
        assertNotNull(broetchen)
        assertEquals(12, broetchen!!.quantity)
        assertEquals(2.28, broetchen.price, 0.01)

        val kiwi = products.find { it.name.contains("Kiwi", ignoreCase = true) }
        assertNotNull(kiwi)
        assertEquals(2, kiwi!!.quantity)
        assertEquals(1.38, kiwi.price, 0.01)

        val creme = products.find { it.name.contains("Crème Fraîche", ignoreCase = true) }
        assertNotNull(creme)
        assertEquals(0.79, creme!!.price, 0.01) // 0.89 - 0.10

        val bananen = products.find { it.name.contains("Bananen", ignoreCase = true) }
        assertNotNull(bananen)
        assertEquals("kg", bananen!!.unit)

        assertEquals(3.30, result.sumDeposit, 0.01)
    }

    @Test
    fun `test Netto eBon mit Multiplikator vor Artikel`() {
        val text = """
            *** eBon ***
            NETTO
            Filiale 1778
            Erfurter Str. 84
            99427 Weimar
            EUR
            2 x 0,49
            BO-Bio Weizenbroetchen 1ST 0,98 B
            Preisaenderung -0,50
            BO-Bio Dinkelrusti 1ST 0,55 B
            Preisaenderung -0,28
            Satori Jasminreis 1kg 1,99 B
            SUMME 2,74€
            02.10.26 18:20
        """.trimIndent()

        val result = KassenzettelParser.parseReceiptDetailed(text)

        assertEquals("Netto", result.supermarket)
        val dateFormatted = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(result.purchaseDate))
        assertEquals("02.10.2026", dateFormatted)

        val products = result.products
        assertEquals(3, products.size)

        val weizen = products[0]
        assertEquals("Bio Weizenbrötchen", weizen.name)
        assertEquals(2, weizen.quantity)
        assertEquals(0.48, weizen.price, 0.01) // 0.98 - 0.50

        val dinkel = products[1]
        assertEquals("Bio Dinkelrusti Brötchen", dinkel.name)
        assertEquals(1, dinkel.quantity)
        assertEquals(0.27, dinkel.price, 0.01) // 0.55 - 0.28
    }

    @Test
    fun `test Globus Bon mit schmalem Leerzeichen EAN Barcodes und Multiplikatorzeile`() {
        // Enthält Unicode \u202F
        val text = """
                  GLOBUS Handelshof GmbH & Co.KG
                    Markthalle Jena-Isserstedt
                 Weimarische Str. 3 - 07751 Jena
            EUR
            #2070000602755
            Kart.vfk 2,5kg                  2,49 2
            #4000582188093
            Fin.Sweet Chili 100g            1,39 2
            #4009268333093
            KLOSSTEIG 750 G
                     3    x    1,99         5,97 2
            Paprika gelb lose
                 0,224 kg x  4,99 €/kg      1,12 2
            ANZ. ARTIKEL   6
            Summe           10,97
            Datum 14.04.26                   18:10 Uhr
        """.trimIndent()

        val result = KassenzettelParser.parseReceiptDetailed(text)

        assertEquals("Globus", result.supermarket)
        val dateFormatted = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(result.purchaseDate))
        assertEquals("14.04.2026", dateFormatted)

        val products = result.products
        assertEquals(4, products.size)

        assertEquals("Kartoffeln", products[0].name)
        assertEquals(2.49, products[0].price, 0.01)
        assertEquals("2070000602755", products[0].barcode)

        assertEquals("Finesse Sweet Chili", products[1].name)
        assertEquals(1.39, products[1].price, 0.01)
        assertEquals("4000582188093", products[1].barcode)

        val klossteig = products[2]
        assertEquals("Kloßteig", klossteig.name)
        assertEquals(3, klossteig.quantity)
        assertEquals(5.97, klossteig.price, 0.01)
        assertEquals("4009268333093", klossteig.barcode)

        val paprika = products[3]
        assertEquals("Paprika Gelb Lose", paprika.name)
        assertEquals(1.12, paprika.price, 0.01)
        assertEquals("kg", paprika.unit)
    }
}
