package com.example.myapplication.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.TimeUnit

class DealWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getDatabase(context).fridgeItemDao()
            val favorites = dao.getAllFridgeItems().first().filter { it.isFavorite }
            val method = inputData.getInt("METHOD", 0)

            if (favorites.isEmpty() || method == 0) return@withContext Result.success()

            for (item in favorites) {
                var dealFound = false
                var source = ""
                var retailer = ""

                if (method == 1) {
                    try {
                        val encodedName = Uri.encode(item.name)
                        val text = URL("https://www.google.de/search?q=angebote+$encodedName").readText()
                        if (text.contains("€") || text.contains("Angebot")) {
                            dealFound = true
                            source = "Online Suche"
                            retailer = "Händler"
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                } else if (method == 2) {
                    try {
                        val rssText = URL("https://www.mydealz.de/rss/search?q=${Uri.encode(item.name)}").readText()
                        if (rssText.contains("<item>")) {
                            dealFound = true
                            source = "MyDealz"
                            retailer = "MyDealz"
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                if (dealFound) {
                    sendNotification(item.name, source, retailer)
                    break 
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendNotification(itemName: String, source: String, retailer: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_OFFER_RETAILER", retailer)
            putExtra("OPEN_OFFER_PRODUCT", itemName)
        }
        
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "DEAL_ALERTS")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Angebot entdeckt! 🛒")
            .setContentText("Dein Favorit '$itemName' ist bei $source im Angebot.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
        }
    }
}

class ExpiryWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getDatabase(context).fridgeItemDao()
            val warningDays = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("expiry_warning", 3)
            val now = System.currentTimeMillis()
            val tomorrow = now + 86400000L // +1 Tag
            
            val items = dao.getAllFridgeItems().first()
            val expiringItems = items.filter { 
                it.expiryDate != null && (it.expiryDate!! - now) < TimeUnit.DAYS.toMillis(warningDays.toLong()) 
            }
            
            val criticalItems = items.filter {
                it.expiryDate != null && it.expiryDate!! <= tomorrow
            }
            
            val freezerBurnItems = items.filter {
                it.isFrozen && (now - it.purchaseDate) > TimeUnit.DAYS.toMillis(180)
            }
            
            if (expiringItems.isNotEmpty() || freezerBurnItems.isNotEmpty()) {
                val msg = if (expiringItems.isNotEmpty()) {
                    "${expiringItems.size} Artikel laufen bald ab!"
                } else {
                    "${freezerBurnItems.size} Artikel zu lange eingefroren (Gefrierbrand-Gefahr)!"
                }
                sendNotification(msg, criticalItems.size)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendNotification(message: String, badgeCount: Int) {
        val builder = NotificationCompat.Builder(context, "EXPIRY_ALERTS")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("FrischeRadar")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (badgeCount > 0) {
            builder.setNumber(badgeCount)
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(202, builder.build())
        }
    }
}

class EnrichmentWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.fridgeItemDao()
            val api = OpenFoodFactsApi.create()

            // Hole alle Items, die kein Bild oder 0 kcal haben
            val itemsToEnrich = dao.getAllFridgeItems().first().filter { it.imageUrl == null || it.kcal == 0 }.take(10) // Max 10 pro Durchlauf um API zu schonen

            var updatedCount = 0
            for (item in itemsToEnrich) {
                var updated = false
                var newImg = item.imageUrl
                var newKcal = item.kcal
                var newNutri = item.nutriScore
                var newBrand = item.brand

                val bcode = item.barcode
                if (bcode != null) {
                    val res = api.getProduktByBarcode(bcode)
                    if (res.status == 1 && res.product != null) {
                        newImg = res.product.imageUrl ?: newImg
                        newKcal = res.product.nutriments?.kcal100g?.toInt() ?: newKcal
                        newNutri = res.product.nutriScore ?: newNutri
                        newBrand = res.product.marke ?: newBrand
                        updated = true
                    }
                }

                if (!updated) {
                    val searchRes = api.searchProduktByName(item.name)
                    if (searchRes.products?.isNotEmpty() == true) {
                        val p = searchRes.products.first()
                        newImg = p.imageUrl ?: newImg
                        newKcal = p.nutriments?.kcal100g?.toInt() ?: newKcal
                        newNutri = p.nutriScore ?: newNutri
                        newBrand = p.marke ?: newBrand
                        updated = true
                    }
                }

                if (updated && (newImg != item.imageUrl || newKcal != item.kcal)) {
                    dao.updateItem(item.copy(imageUrl = newImg, kcal = newKcal, nutriScore = newNutri, brand = newBrand))
                    updatedCount++
                }
            }
            
            Log.d("EnrichmentWorker", "Hintergrund-Suche abgeschlossen: $updatedCount Artikel aktualisiert")
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
