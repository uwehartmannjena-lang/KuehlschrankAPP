package com.example.myapplication

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.CategoryDetector
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.FridgeItemDao
import com.example.myapplication.data.ShoppingItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ShoppingListTest {

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
    fun testParseShoppingInputItemSingleAndMultiple() {
        val parsed1 = parseShoppingInputItem("6 Eier")
        assertEquals("Eier", parsed1.name)
        assertEquals(6, parsed1.quantity)
        assertEquals("Stk.", parsed1.unit)

        val parsed2 = parseShoppingInputItem("500g Hackfleisch")
        assertEquals("Hackfleisch", parsed2.name)
        assertEquals(500, parsed2.quantity)
        assertEquals("g", parsed2.unit)

        val parsed3 = parseShoppingInputItem("2x Saft")
        assertEquals("Saft", parsed3.name)
        assertEquals(2, parsed3.quantity)
        assertEquals("Stk.", parsed3.unit)

        val parsed4 = parseShoppingInputItem("Butter")
        assertEquals("Butter", parsed4.name)
        assertEquals(1, parsed4.quantity)
    }

    @Test
    fun testShoppingListDatabaseOperations() = runBlocking {
        val item1 = ShoppingItem(
            name = "Milch",
            quantity = 2,
            unit = "L",
            priceEstimate = 1.29,
            note = "nur Bio",
            urgency = "URGENT"
        )
        val item2 = ShoppingItem(
            name = "Butter",
            quantity = 1,
            unit = "Stk.",
            priceEstimate = 2.19,
            urgency = "NORMAL"
        )

        dao.insertShoppingItem(item1)
        dao.insertShoppingItem(item2)

        var list = dao.getAllShoppingItems().first()
        assertEquals(2, list.size)

        // Toggle checked state
        val checkedItem = list.find { it.name == "Milch" }!!.copy(isChecked = true)
        dao.insertShoppingItem(checkedItem)

        list = dao.getAllShoppingItems().first()
        assertTrue(list.find { it.name == "Milch" }!!.isChecked)

        // Delete item
        dao.deleteShoppingItem(checkedItem)
        list = dao.getAllShoppingItems().first()
        assertEquals(1, list.size)
        assertEquals("Butter", list[0].name)
    }

    @Test
    fun testTransferShoppingItemToInventory() = runBlocking {
        val shoppingItem = ShoppingItem(
            name = "Brot",
            quantity = 1,
            unit = "Stk.",
            priceEstimate = 2.49
        )
        dao.insertShoppingItem(shoppingItem)

        // Transfer into fridge/inventory
        val cat = CategoryDetector.detectCategory(shoppingItem.name)
        val expiryMs = System.currentTimeMillis() + (cat.expiryDays * 24 * 60 * 60 * 1000L)

        val fridgeItem = FridgeItem(
            name = shoppingItem.name,
            quantity = shoppingItem.quantity,
            unit = shoppingItem.unit,
            category = cat.displayName,
            storageLocation = "Vorratskammer",
            expiryDate = expiryMs,
            price = shoppingItem.priceEstimate
        )
        dao.insertItem(fridgeItem)
        dao.deleteShoppingItem(shoppingItem)

        val fridgeItems = dao.getAllFridgeItems().first()
        val remainingShoppingItems = dao.getAllShoppingItems().first()

        assertEquals(1, fridgeItems.size)
        assertEquals("Brot", fridgeItems[0].name)
        assertEquals("Vorratskammer", fridgeItems[0].storageLocation)
        assertTrue(remainingShoppingItems.isEmpty())
    }
}
