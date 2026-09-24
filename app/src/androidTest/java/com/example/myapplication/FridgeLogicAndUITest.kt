package com.example.myapplication

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.CategoryDetector
import com.example.myapplication.data.FoodCategory
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.FridgeItemDao
import com.example.myapplication.ui.ExpiryLabel
import com.example.myapplication.ui.calculateDaysRemaining
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class FridgeLogicAndUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun testMhdCalculationAndExpiryRemainingDays() {
        val now = System.currentTimeMillis()
        val in3Days = now + (3L * 24 * 60 * 60 * 1000L)
        val in10Days = now + (10L * 24 * 60 * 60 * 1000L)
        val expired2DaysAgo = now - (2L * 24 * 60 * 60 * 1000L)

        val daysRemaining3 = calculateDaysRemaining(in3Days)
        val daysRemaining10 = calculateDaysRemaining(in10Days)
        val daysRemainingExpired = calculateDaysRemaining(expired2DaysAgo)

        assertTrue("Verbleibende Tage sollten ca. 3 sein", daysRemaining3 in 2..3)
        assertTrue("Verbleibende Tage sollten ca. 10 sein", daysRemaining10 in 9..10)
        assertTrue("Abgelaufene Tage sollten negativ sein", daysRemainingExpired < 0)
    }

    @Test
    fun testFridgeItemCrudOperationsInDatabase() = runBlocking {
        val item = FridgeItem(
            name = "Bio Milch",
            quantity = 2,
            unit = "L",
            category = "Kühlung & Milch",
            storageLocation = "Kühlschrank",
            price = 1.49,
            isOrganic = true
        )

        // Insert
        dao.insertItem(item)

        var allItems = dao.getAllFridgeItems().first()
        assertEquals(1, allItems.size)
        assertEquals("Bio Milch", allItems[0].name)
        assertEquals("Kühlschrank", allItems[0].storageLocation)

        // Update quantity
        val updatedItem = allItems[0].copy(quantity = 5)
        dao.updateItem(updatedItem)

        allItems = dao.getAllFridgeItems().first()
        assertEquals(5, allItems[0].quantity)

        // Delete
        dao.deleteItem(allItems[0])
        allItems = dao.getAllFridgeItems().first()
        assertTrue(allItems.isEmpty())
    }

    @Test
    fun testCategoryDetectionAndAisleOrder() {
        val appleCategory = CategoryDetector.detectCategory("Apfel")
        val milkCategory = CategoryDetector.detectCategory("Vollmilch")
        val breadCategory = CategoryDetector.detectCategory("Baguette")
        val meatCategory = CategoryDetector.detectCategory("Rinderhackfleisch")

        assertEquals(FoodCategory.OBST_GEMUESE, appleCategory)
        assertEquals(FoodCategory.MILCHPRODUKTE, milkCategory)
        assertEquals(FoodCategory.BROT_BACKWAREN, breadCategory)
        assertEquals(FoodCategory.FLEISCH_FISCH, meatCategory)

        // Verify supermarket route order (aisle order)
        assertTrue("Obst & Gemüse vor Brot", appleCategory.aisleOrder < breadCategory.aisleOrder)
        assertTrue("Brot vor Kühlung", breadCategory.aisleOrder < milkCategory.aisleOrder)
        assertTrue("Kühlung vor Fleisch", milkCategory.aisleOrder < meatCategory.aisleOrder)
    }

    @Test
    fun testExpiryLabelComposableRendering() {
        val futureMhd = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000L)

        composeTestRule.setContent {
            ExpiryLabel(expiryDateMs = futureMhd, householdSize = 1)
        }

        composeTestRule.waitForIdle()
    }
}
