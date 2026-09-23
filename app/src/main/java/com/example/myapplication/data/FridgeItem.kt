package com.example.myapplication.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "fridge_items",
    indices = [Index(value = ["importHash"], unique = true)]
)
data class FridgeItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "item_name")
    var name: String,
    var quantity: Int = 1,
    var unit: String = "Stk.",
    var category: String = "Sonstiges",
    @ColumnInfo(name = "expiry_date")
    var expiryDate: Long? = null,
    @ColumnInfo(name = "min_stock")
    var minStock: Int = 0,
    @ColumnInfo(name = "storage_location")
    var storageLocation: String = "Kühlschrank",
    var price: Double = 0.0,
    var brand: String? = null,
    var kcal: Int = 0,
    var nutriScore: String = "",
    var allergens: String = "",
    var isOrganic: Boolean = false,
    var isFavorite: Boolean = false,
    var notes: String = "",
    var imageUrl: String? = null,
    var barcode: String? = null,
    var hasOffer: Boolean = false,
    var purchaseDate: Long = System.currentTimeMillis(),
    var ecoScore: String = "",
    var isFrozen: Boolean = false,
    var defrostTimestamp: Long? = null,
    var lastPrice: Double = 0.0,
    var carbonFootprint: Double = 0.0,
    var boundingBox: String? = null,
    
    // Multi-Haushalt
    var householdId: String = "default",
    
    // Nährwerte pro 100g/ml
    var sugar: Double = 0.0,
    var salt: Double = 0.0,
    var protein: Double = 0.0,
    var fat: Double = 0.0,
    
    // Ernährungsprofile
    var isVegan: Boolean = false,
    var isVegetarian: Boolean = false,
    var isGlutenFree: Boolean = false,
    var isLactoseFree: Boolean = false,
    var isLowCarb: Boolean = false,

    // NEU: Advanced Management
    var freezerDrawer: Int = 0, // 0 = kein Gefrierfach, 1-N = Fachnummer
    var openedDate: Long? = null,
    var consumptionAfterOpeningDays: Int? = null, // Empfohlene Haltbarkeit nach Öffnen
    var isRecallChecked: Boolean = false,
    var lastRecallStatus: String = "OK", // "OK", "WARN", "UNKNOWN"
    
    // NEU: Donation & Sharing
    var isDonationCandidate: Boolean = false,
    var sharedWithUserId: String? = null,

    // Hash zur Vermeidung von Doppel-Imports (Dateiname + Artikel)
    var importHash: String? = null,

    // Verknüpfung zum Beleg-Archiv
    var receiptId: String? = null,
    
    // NFC-Tag ID für Gefrierdosen
    var nfcTagId: String? = null,

    // Individuelle Favoriten-Farbe
    var favoriteColor: Int? = null
) {
    @Ignore
    var isDuplicate: Boolean = false
}

@Entity(tableName = "cached_products")
data class CachedProduct(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String?,
    val imageUrl: String?,
    val imageSmallUrl: String?,
    val kcal: Int,
    val nutriScore: String,
    val allergens: String,
    val ecoScore: String,
    val categories: String,
    val sugar: Double,
    val salt: Double,
    val protein: Double,
    val fat: Double,
    val timestamp: Long = System.currentTimeMillis()
)
