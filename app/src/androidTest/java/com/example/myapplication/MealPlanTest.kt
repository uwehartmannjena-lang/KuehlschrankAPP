package com.example.myapplication

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.ChefkochHelper
import com.example.myapplication.data.FridgeItemDao
import com.example.myapplication.data.MealPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MealPlanTest {

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
    fun testMealPlanDatabaseOperations() = runBlocking {
        val plan1 = MealPlan(
            date = System.currentTimeMillis(),
            recipeTitle = "Spaghetti Bolognese",
            recipeUrl = "https://www.chefkoch.de/rezepte/123",
            mealType = "Abendessen"
        )
        val plan2 = MealPlan(
            date = System.currentTimeMillis() + 86400000L,
            recipeTitle = "Gemüsesuppe",
            mealType = "Mittagessen"
        )

        dao.insertMealPlan(plan1)
        dao.insertMealPlan(plan2)

        var plans = dao.getAllMealPlans().first()
        assertEquals(2, plans.size)
        assertEquals("Spaghetti Bolognese", plans[0].recipeTitle)

        // Delete plan
        dao.deleteMealPlan(plans[0].id)
        plans = dao.getAllMealPlans().first()
        assertEquals(1, plans.size)
        assertEquals("Gemüsesuppe", plans[0].recipeTitle)
    }

    @Test
    fun testChefkochUrlGeneration() {
        val ingredients = listOf("Milch", "Eier", "Mehl")
        val url = ChefkochHelper.buildChefkochSearchUrl(ingredients)

        assertTrue(url.startsWith("https://www.chefkoch.de/rs/s0/"))
        assertTrue(url.contains("Milch"))
        assertTrue(url.contains("Eier"))
        assertTrue(url.contains("Mehl"))
    }
}
