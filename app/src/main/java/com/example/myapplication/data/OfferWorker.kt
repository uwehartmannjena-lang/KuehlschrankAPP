package com.example.myapplication.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.OfferRepository
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import android.util.Log

class OfferWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("OfferWorker", "Starte Hintergrund-Angebotsprüfung...")
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.fridgeItemDao()
        
        val httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val repository = OfferRepository(httpClient)

        // 1. Sammle alle relevanten Artikelnamen
        val favorites = dao.getAllFridgeItems().first().filter { it.isFavorite }.map { it.name }
        val shoppingList = dao.getAllShoppingItems().first().map { it.name }
        val allSearchItems = (favorites + shoppingList).distinct()

        if (allSearchItems.isEmpty()) return Result.success()

        // 2. Prüfe Angebote bei allen Händlern
        val offers = repository.checkLocalOffers(allSearchItems)

        // 3. Wenn Angebote gefunden wurden, markiere die Artikel oder löse (später) Benachrichtigung aus
        for (offer in offers) {
            Log.d("OfferWorker", "Angebot gefunden: ${offer.title} bei ${offer.retailer} für ${offer.price}€")
            // Hier könnten wir ein Flag in der DB setzen oder eine lokale Notification schicken
        }

        return Result.success()
    }
}
