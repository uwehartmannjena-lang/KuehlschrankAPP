package com.example.myapplication

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.FridgeItemDao
import com.example.myapplication.data.PriceRecord
import com.example.myapplication.data.Receipt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ReceiptScannerTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: FridgeItemDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.fridgeItemDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun testReceiptParserWithReweText() {
        val sampleOcrText = """
            REWE Markt GmbH
            01.03.2025
            VOLLMILCH 3.5% 1,19 B
            BIO BUTTER 2,49 B
            APFEL BRAEBURN 1,99 A
            SUMME 5,67 €
        """.trimIndent()

        val parsedProducts = KassenzettelParser.parseReceiptText(sampleOcrText)

        assertFalse("Es sollten Produkte erkannt werden", parsedProducts.isEmpty())
        val names = parsedProducts.map { it.name }
        assertTrue("Sollte Vollmilch enthalten", names.any { it.contains("VOLLMILCH", ignoreCase = true) })
        assertTrue("Sollte Butter enthalten", names.any { it.contains("BUTTER", ignoreCase = true) })

        val total = parsedProducts.sumOf { it.price * it.quantity }
        assertTrue("Gesamtsumme sollte ca. 5.67 sein", total > 5.0 && total < 6.0)
    }

    @Test
    fun testReceiptParserWithAldiText() {
        val sampleOcrText = """
            ALDI SÜD
            15.02.2025
            2 x 0,89 € ASTRA URTYP DOSE 1,78 A
            BANANEN 1,49 A
            SUMME 3,27
        """.trimIndent()

        val parsedProducts = KassenzettelParser.parseReceiptText(sampleOcrText)
        assertFalse(parsedProducts.isEmpty())
    }

    @Test
    fun testReceiptImportSanitizerCleansNoiseLines() {
        val rawName = "TH.WQ. Mineralwasser 1,5l 0,45 B"
        val cleaned = ReceiptImportSanitizer.cleanSearchTerm(rawName)

        assertFalse(cleaned.contains("0,45"))
        assertFalse(cleaned.contains("1,5l"))
        assertTrue(cleaned.contains("Thüringer Waldquell") || cleaned.contains("Mineralwasser"))
    }

    @Test
    fun testSavingReceiptAndPriceHistoryInDatabase() = runBlocking {
        val receipt = Receipt(
            uri = "content://receipts/123",
            supermarket = "REWE",
            total = 18.50,
            date = System.currentTimeMillis()
        )
        dao.insertReceipt(receipt)

        val receipts = dao.getAllReceipts().first()
        assertEquals(1, receipts.size)
        assertEquals("REWE", receipts[0].supermarket)

        // Save PriceRecord for historical lookup
        val priceRecord = PriceRecord(
            itemId = "item_123",
            itemName = "Bio Butter",
            price = 2.49,
            quantity = 1,
            unit = "Stk.",
            date = System.currentTimeMillis()
        )
        dao.insertPriceRecord(priceRecord)

        val history = dao.getHistoryByName("Bio Butter")
        assertEquals(1, history.size)
        assertEquals(2.49, history[0].price, 0.01)
    }
}
