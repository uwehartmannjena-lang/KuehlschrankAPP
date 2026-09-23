package com.example.myapplication

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.FridgeItemDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DuplicateImportTest {
    private lateinit var dao: FridgeItemDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.fridgeItemDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeItemAndCheckDuplicate() = runBlocking {
        val item1 = FridgeItem(
            name = "Testmilch",
            importHash = "hash123"
        )
        val item2 = FridgeItem(
            name = "Testmilch",
            importHash = "hash123" // Gleicher Hash wie item1
        )

        dao.insertItem(item1)
        dao.insertItem(item2) // Sollte ignoriert werden wegen OnConflictStrategy.IGNORE

        val allItems = dao.getAllFridgeItems().first()
        assertEquals("Nur ein Item sollte in der DB sein", 1, allItems.size)
    }

    @Test
    @Throws(Exception::class)
    fun writeDifferentItemsAndCheck() = runBlocking {
        val item1 = FridgeItem(
            name = "Testmilch",
            importHash = "hash1"
        )
        val item2 = FridgeItem(
            name = "Testbrot",
            importHash = "hash2"
        )

        dao.insertItem(item1)
        dao.insertItem(item2)

        val allItems = dao.getAllFridgeItems().first()
        assertEquals("Zwei verschiedene Items sollten in der DB sein", 2, allItems.size)
    }
}
