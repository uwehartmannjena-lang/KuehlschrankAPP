package com.example.myapplication

import com.example.myapplication.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class StatisticsHelperTest {

    @Test
    fun `test freshness overview calculation`() {
        val now = System.currentTimeMillis()
        val items = listOf(
            FridgeItem(name = "Frische Milch", expiryDate = now + TimeUnit.DAYS.toMillis(10)),
            FridgeItem(name = "Käse bald fällig", expiryDate = now + TimeUnit.DAYS.toMillis(2)),
            FridgeItem(name = "Abgelaufener Joghurt", expiryDate = now - TimeUnit.DAYS.toMillis(1)),
            FridgeItem(name = "Dose ohne Datum", expiryDate = null)
        )

        val overview = StatisticsHelper.calculateFreshnessOverview(items, now)
        assertEquals(2, overview.freshCount) // Milch + Dose
        assertEquals(1, overview.expiringSoonCount) // Käse
        assertEquals(1, overview.expiredCount) // Joghurt
        // (2*1.0 + 1*0.5) / 4 = 2.5 / 4 = 62.5 -> 62 %
        assertEquals(62, overview.freshnessScore)
    }

    @Test
    fun `test sustainability stats calculation`() {
        val consumed = listOf(
            ConsumedItem(name = "Apfel", quantity = 3, unit = "Stk.", category = "Obst"),
            ConsumedItem(name = "Milch", quantity = 1, unit = "l", category = "Milch")
        )
        val wasted = listOf(
            WastedItem(name = "Banane", quantity = 1, unit = "Stk.", category = "Obst", price = 0.50, carbonFootprint = 0.5)
        )

        val stats = StatisticsHelper.calculateSustainabilityStats(consumed, wasted)
        assertEquals(4, stats.consumedCount)
        assertEquals(1, stats.wastedCount)
        // 4 / 5 = 80.0 %
        assertEquals(80.0, stats.rescueRate, 0.01)
        assertEquals(0.50, stats.wastedValue, 0.01)
        assertEquals(0.5, stats.wastedCo2, 0.01)
    }

    @Test
    fun `test location and category breakdown`() {
        val items = listOf(
            FridgeItem(name = "Butter", quantity = 2, price = 1.50, category = "Milchprodukte", storageLocation = "Kühlschrank"),
            FridgeItem(name = "TK Pizza", quantity = 1, price = 2.50, category = "Brot & Backwaren", storageLocation = "Gefrierfach"),
            FridgeItem(name = "Nudeln", quantity = 3, price = 0.99, category = "Vorrat", storageLocation = "Vorratskammer")
        )

        val locationStats = StatisticsHelper.calculateLocationStats(items)
        val fridge = locationStats.find { it.location == "Kühlschrank" }!!
        val freezer = locationStats.find { it.location == "Gefrierfach" }!!
        val pantry = locationStats.find { it.location == "Vorratskammer" }!!

        assertEquals(2, fridge.count)
        assertEquals(3.00, fridge.totalValue, 0.01)
        assertEquals(1, freezer.count)
        assertEquals(2.50, freezer.totalValue, 0.01)
        assertEquals(3, pantry.count)
        assertEquals(2.97, pantry.totalValue, 0.01)

        val categoryStats = StatisticsHelper.calculateCategoryStats(items)
        assertEquals(3, categoryStats.size)
        assertEquals("Milchprodukte", categoryStats[0].category)
        assertEquals(3.00, categoryStats[0].totalValue, 0.01)
    }

    @Test
    fun `test top wasted items`() {
        val wasted = listOf(
            WastedItem(name = "Banane", quantity = 2, unit = "Stk.", category = "Obst", price = 0.40),
            WastedItem(name = "banane", quantity = 1, unit = "Stk.", category = "Obst", price = 0.40),
            WastedItem(name = "Joghurt", quantity = 1, unit = "Becher", category = "Milch", price = 0.69)
        )

        val top = StatisticsHelper.calculateTopWasted(wasted)
        assertEquals(2, top.size)
        assertEquals("Banane", top[0].name)
        assertEquals(3, top[0].count)
        assertEquals(1.20, top[0].totalLoss, 0.01)
    }

    @Test
    fun `test share report generation`() {
        val report = StatisticsHelper.generateShareReport(
            timeFrameTitle = "Dieser Monat",
            inventoryCount = 15,
            inventoryValue = 35.50,
            consumedCount = 20,
            wastedCount = 2,
            wastedValue = 2.40,
            wastedCo2 = 1.2,
            freshnessScore = 90
        )

        assertTrue(report.contains("Kühlschrank Profi"))
        assertTrue(report.contains("Dieser Monat"))
        assertTrue(report.contains("35,50 €"))
        assertTrue(report.contains("90 %"))
    }
}
