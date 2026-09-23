package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable // NEU: Für Long-Press
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// HINWEIS: Für die vollständige Funktion dieser App müssen die folgenden Berechtigungen
// in der AndroidManifest.xml-Datei deklariert werden:
//
// <uses-permission android:name="android.permission.INTERNET" />
// <uses-permission android:name="android.permission.CAMERA" />
// <!-- Für Android 13 (API 33) und höher -->
// <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

/**
 * Top-Level-Konstante für das Mapping von Kategorienamen zu ImageVector-Icons.
 * Enthält gängige Kategorien und ein Standard-Icon für nicht gemappte Kategorien.
 */
val categoryIcons: Map<String, ImageVector> = mapOf(
    "Obst" to Icons.Default.Grass,
    "Gemüse" to Icons.Default.Grass,
    "Obst & Gemüse" to Icons.Default.Grass, // Für die bestehende Kategorie
    "Milchprodukte" to Icons.Default.Egg, // Ei als generelles Symbol für Milchprodukte (auch Joghurt etc.)
    "Eier" to Icons.Default.Egg,
    "Getränke" to Icons.Default.LocalDrink,
    "Fleisch" to Icons.Default.Restaurant,
    "Fisch" to Icons.Default.Restaurant,
    "Fleisch & Fisch" to Icons.Default.Restaurant, // Für die bestehende Kategorie
    "Backwaren" to Icons.Default.BakeryDining,
    "Tiefkühlkost" to Icons.Default.Fastfood, // Fastfood als generisches Essenssymbol
    "Sonstiges" to Icons.Default.Fastfood // Standard für "Sonstiges" und andere
)

/**
 * Helper-Objekt zum Senden lokaler Benachrichtigungen.
 * Muss als Top-Level-Objekt oder in einem Companion-Objekt definiert sein,
 * um überall zugänglich zu sein.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "fridge_expiry_channel"
    private const val CHANNEL_NAME = "Kühlschrank Abläufe"
    // Geändert von NOTIFICATION_ID zu NOTIFICATION_ID_BASE, um dynamische IDs zu ermöglichen
    private const val NOTIFICATION_ID_BASE = 100 // Basis für eindeutige Benachrichtigungs-IDs

    /**
     * Erstellt einen Benachrichtigungskanal für Android 8.0 (API 26) und höher.
     * Muss aufgerufen werden, bevor Benachrichtigungen gesendet werden.
     */
    fun createNotificationChannel(context: Context) {
        // Der NotificationChannel-Klasse ist neu und nicht in der Support Library verfügbar,
        // daher nur auf API 26+ erstellen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Benachrichtigungen für bald ablaufende oder abgelaufene Artikel."
            }
            // Den Kanal beim System registrieren
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Sendet eine lokale Benachrichtigung.
     *
     * @param context Der Kontext der Anwendung.
     * @param title Der Titel der Benachrichtigung.
     * @param message Der Nachrichtentext der Benachrichtigung.
     * @param notificationIdOffset Ein Offset, der zur NOTIFICATION_ID_BASE addiert wird,
     *                             um eine eindeutige ID für die Benachrichtigung zu erhalten.
     *                             Dies ist wichtig, um mehrere Benachrichtigungen gleichzeitig anzuzeigen.
     *                             Standardmäßig 1, sodass die erste Benachrichtigung die ID 101 erhält.
     */
    fun sendNotification(context: Context, title: String, message: String, notificationIdOffset: Int = 1) {
        val uniqueNotificationId = NOTIFICATION_ID_BASE + notificationIdOffset

        // Erstelle einen expliziten Intent für die Hauptaktivität deiner App
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT // FLAG_IMMUTABLE ist für Android S+ erforderlich
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // System-Drawable als Platzhalter-Icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent) // Setzt den Intent, der ausgelöst wird, wenn der Benutzer auf die Benachrichtigung tippt
            .setAutoCancel(true) // Entfernt die Benachrichtigung, wenn der Benutzer darauf tippt

        with(NotificationManagerCompat.from(context)) {
            // Für Android 13 (API 33) und höher muss die POST_NOTIFICATIONS-Berechtigung überprüft werden
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Berechtigung nicht erteilt, Benachrichtigung kann nicht angezeigt werden.
                // Eine Toast-Nachricht sollte bereits angezeigt worden sein, wenn die Berechtigung verweigert wurde.
                // (oder eine andere Fehlerbehandlung, z.B. Logging)
                return
            }
            // uniqueNotificationId ist eine eindeutige Ganzzahl für jede Benachrichtigung
            notify(uniqueNotificationId, builder.build())
        }
    }
}


/**
 * Überarbeiteter FridgeItem Datenklasse.
 * Hinzugefügt wurde das Feld `isWasted`, um verschwendete Artikel zu kennzeichnen,
 * was für die `wasteItem`-Funktion und den `wastedItems`-Flow des ViewModels erforderlich ist.
 * Ebenfalls wurde eine berechnete Eigenschaft `daysUntilExpiry` hinzugefügt, um die Tage bis zum
 * Ablaufdatum leicht abrufen zu können.
 *
 * Diese Datenklasse wurde hierher verschoben, um die Anforderung "Gib NUR den vollständigen,
 * überarbeiteten Kotlin-Code zurück" zu erfüllen und `Unresolved reference 'data'` Fehler zu vermeiden.
 */
@Entity(tableName = "fridge_items")
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
    @ColumnInfo(name = "is_organic")
    var isOrganic: Boolean = false,
    var notes: String = "",
    @ColumnInfo(name = "image_url")
    var imageUrl: String? = null, // Speichert URI-String oder Base64 Data URI
    var barcode: String? = null,
    @ColumnInfo(name = "is_wasted") // Neues Feld, um verschwendete Artikel zu verfolgen
    var isWasted: Boolean = false,
    @ColumnInfo(name = "is_favorite") // NEU: Feld, um Lieblingsartikel zu kennzeichnen
    var isFavorite: Boolean = false
) {
    /**
     * Berechnet die Anzahl der Tage bis zum Ablaufdatum.
     * Ist das Ablaufdatum null, ist auch dieser Wert null.
     * Ein positiver Wert bedeutet, dass das Produkt noch so viele Tage haltbar ist.
     * Ein negativer Wert bedeutet, dass das Produkt seit so vielen Tagen abgelaufen ist.
     */
    val daysUntilExpiry: Int?
        get() = expiryDate?.let { TimeUnit.MILLISECONDS.toDays(it - System.currentTimeMillis()).toInt() }
}

/**
 * NEU: Datenklasse für Supermarkt-Angebote.
 * Wird verwendet, um simulierte oder zukünftig echte Angebote anzuzeigen.
 */
data class SupermarketOffer(
    val id: String = UUID.randomUUID().toString(),
    val store: String,
    val item: String,
    val originalPrice: Double,
    val offerPrice: Double,
    val endDate: Long, // Enddatum des Angebots als Millisekunden seit der Epoche
    val imageUrl: String? = null
)


/**
 * Room Data Access Object (DAO) für FridgeItem-Entitäten.
 * Definiert die CRUD-Operationen (Create, Read, Update, Delete) für die Kühlschrankartikel.
 */
@Dao
interface FridgeItemDao {
    @Insert
    suspend fun insert(item: FridgeItem)

    @Update
    suspend fun update(item: FridgeItem)

    // NEU: Eine spezifische Methode, um den isFavorite-Status eines Artikels zu aktualisieren.
    // Die bestehende `update(item: FridgeItem)`-Methode würde dies ebenfalls handhaben,
    // aber diese Methode bietet eine gezieltere Aktualisierung des Favoritenstatus.
    @Query("UPDATE fridge_items SET is_favorite = :isFavorite WHERE id = :itemId")
    suspend fun updateFavoriteStatus(itemId: String, isFavorite: Boolean)

    @Delete
    suspend fun delete(item: FridgeItem)

    @Query("SELECT * FROM fridge_items WHERE is_wasted = 0 ORDER BY expiry_date ASC")
    fun getAllItems(): Flow<List<FridgeItem>>

    @Query("SELECT * FROM fridge_items WHERE is_wasted = 1 ORDER BY expiry_date ASC")
    fun getWastedItems(): Flow<List<FridgeItem>>

    @Query("SELECT * FROM fridge_items WHERE id = :itemId")
    fun getItemById(itemId: String): Flow<FridgeItem?>
}

/**
 * Abstrakte Room-Datenbankklasse für die Anwendung.
 * Definiert die Entitäten, die Datenbankversion und stellt die DAOs bereit.
 */
@Database(entities = [FridgeItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fridgeItemDao(): FridgeItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fridge_item_database"
                )
                    .fallbackToDestructiveMigration() // Einfache Strategie für Schemaänderungen
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * ViewModel-Factory, um dem FridgeViewModel das DAO injizieren zu können.
 */
class FridgeViewModelFactory(private val dao: FridgeItemDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FridgeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FridgeViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


/**
 * FridgeViewModel zur Interaktion mit der Datenbank über das DAO.
 * Verwendet Coroutines und Flows für asynchrone Datenbankoperationen und Datenbeobachtung.
 */
class FridgeViewModel(val dao: FridgeItemDao) : ViewModel() { // DAO now public for direct access in some cases for simplicity

    // --- Real Database / Data Store using DAO ---
    // Die Flows werden direkt vom DAO bereitgestellt und vom UI beobachtet
    val allItems: Flow<List<FridgeItem>> = dao.getAllItems()
    val wastedItems: Flow<List<FridgeItem>> = dao.getWastedItems()

    fun addItem(item: FridgeItem) {
        viewModelScope.launch {
            dao.insert(item)
            println("DEBUG: Artikel hinzugefügt: ${item.name} mit ID: ${item.id}")
        }
    }

    fun updateItem(item: FridgeItem) {
        viewModelScope.launch {
            dao.update(item)
            println("DEBUG: Artikel aktualisiert: ${item.name} mit ID: ${item.id}")
        }
    }

    /**
     * Markiert einen Artikel als "verschwendet" und aktualisiert ihn in der Datenbank.
     */
    fun wasteItem(item: FridgeItem) {
        viewModelScope.launch {
            val updatedItem = item.copy(isWasted = true)
            dao.update(updatedItem)
            println("DEBUG: Artikel als verschwendet markiert: ${item.name} mit ID: ${item.id}")
        }
    }

    /**
     * NEU: Aktualisiert den Favoritenstatus eines Artikels in der Datenbank.
     * @param itemId Die ID des zu aktualisierenden Artikels.
     * @param isFavorite Der neue Favoritenstatus.
     */
    fun toggleFavoriteStatus(itemId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            dao.updateFavoriteStatus(itemId, isFavorite)
            println("DEBUG: Artikel $itemId Favoritenstatus auf $isFavorite aktualisiert.")
        }
    }

    /**
     * Löscht einen Artikel aus der Datenbank.
     */
    fun deleteItem(item: FridgeItem) {
        viewModelScope.launch {
            dao.delete(item)
            println("DEBUG: Artikel gelöscht: ${item.name} mit ID: ${item.id}")
        }
    }

    /**
     * Überprüft alle Artikel auf bald ablaufende oder abgelaufene Produkte
     * und sendet entsprechende Benachrichtigungen.
     *
     * @param context Der Anwendungs-Kontext, der für das Senden von Benachrichtigungen benötigt wird.
     * @param daysThreshold Die Anzahl der Tage, innerhalb derer Artikel als "bald ablaufend" gelten.
     *                      Artikel, deren Ablaufdatum <= `daysThreshold` Tage in der Zukunft liegt
     *                      oder bereits abgelaufen sind, werden benachrichtigt.
     */
    fun checkAndNotifyExpiredItems(context: Context, daysThreshold: Int = 3) {
        viewModelScope.launch {
            // Sammle die aktuellen Artikel nur einmal für die Überprüfung
            val currentItems = dao.getAllItems().first() // .first() beendet das Sammeln nach dem ersten Emit

            val nowMillis = System.currentTimeMillis()

            currentItems.filter { it.expiryDate != null && !it.isWasted }.forEach { item ->
                val expiryMillis = item.expiryDate!!
                val daysUntilExpiry = TimeUnit.MILLISECONDS.toDays(expiryMillis - nowMillis).toInt()

                // Prüfe, ob das Produkt abgelaufen ist oder bald abläuft (inkl. heute)
                if (daysUntilExpiry <= daysThreshold) {
                    val title: String
                    val message: String

                    when {
                        daysUntilExpiry < 0 -> {
                            title = "⚠️ Artikel abgelaufen!"
                            message = "${item.name} ist vor ${-daysUntilExpiry} Tagen abgelaufen."
                        }
                        daysUntilExpiry == 0 -> {
                            title = "⏳ Artikel läuft heute ab!"
                            message = "${item.name} läuft heute ab."
                        }
                        else -> { // daysUntilExpiry > 0 && daysUntilExpiry <= daysThreshold
                            title = "⏰ Artikel läuft bald ab!"
                            message = "${item.name} läuft in $daysUntilExpiry Tagen ab."
                        }
                    }

                    // Sende Benachrichtigung. Nutze hashCode() des Item-ID für eine eindeutige Benachrichtigungs-ID.
                    // Der absolute Wert wird verwendet, um negative Hashcodes zu vermeiden.
                    // Eine einfache Umwandlung in Int ist hier ausreichend, da wir nur einen Offset brauchen.
                    NotificationHelper.sendNotification(
                        context = context,
                        title = title,
                        message = message,
                        notificationIdOffset = item.id.hashCode().absoluteValue
                    )
                }
            }
        }
    }
}

/**
 * Definiert die möglichen Bildschirme der Anwendung für die Navigation.
 *
 * MODIFIZIERT: DetailScreen akzeptiert jetzt einen `itemId: String?` anstelle eines `FridgeItem?`.
 */
sealed class AppScreen {
    object OverviewScreen : AppScreen() // Neuer Startbildschirm (führt jetzt zum interaktiven Artikel-Dashboard)
    object ListScreen : AppScreen() // Wird nicht mehr direkt über BottomNav oder Overview erreicht
    data class DetailScreen(val itemId: String?) : AppScreen() // MODIFIZIERT: item ID für Detailansicht
    object OffersScreen : AppScreen()
    object ShoppingListScreen : AppScreen()
    object WasteStatisticsScreen : AppScreen()
}

/**
 * Die Hauptaktivität der Anwendung.
 * Beinhaltet die Jetpack Compose UI für die Navigation und Anzeige der Bildschirme.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Benachrichtigungskanal erstellen, wenn die App startet
        NotificationHelper.createNotificationChannel(this)

        setContent { // BEGIN Composable context
            MyApplicationTheme { // Nutzt das definierte Theme
                val context = LocalContext.current
                // Holen Sie sich die DAO-Instanz über die AppDatabase
                val dao = AppDatabase.getDatabase(context).fridgeItemDao()
                // Erstellen Sie die ViewModelFactory, die das DAO benötigt.
                val factory = FridgeViewModelFactory(dao)
                // Holen Sie sich das ViewModel mithilfe der Factory.
                val fridgeViewModel: FridgeViewModel = viewModel(factory = factory)

                // Launcher für POST_NOTIFICATIONS Berechtigung (für Android 13+)
                // Dieser Launcher wird hier im Wurzel-Composable definiert, um die Berechtigung beim App-Start anzufordern.
                val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (isGranted) {
                        Toast.makeText(context, "Benachrichtigungsberechtigung erteilt.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Benachrichtigungsberechtigung verweigert. Einige Funktionen könnten eingeschränkt sein.", Toast.LENGTH_LONG).show()
                    }
                }

                // Fordere die POST_NOTIFICATIONS Berechtigung beim Start der App an (nur für Android 13+)
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Zustand für die aktuelle Bildschirm-Navigation - Startet jetzt mit der NEUEN Übersicht (Kühlschrank-Tab)
                var currentScreen: AppScreen by remember { mutableStateOf(AppScreen.OverviewScreen) }
                // Zustand für den App-weiten Scanner-Dialog (für Kassenzettel-Scan von Overview)
                var showAppWideScannerDialog by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            val navItems = listOf(
                                Pair("Kühlschrank", Icons.Default.List), // Führt zur neuen OverviewScreen
                                Pair("Angebote", Icons.Default.ShoppingCart),
                                Pair("Einkaufsliste", Icons.Default.AddShoppingCart),
                                Pair("Statistiken", Icons.Default.PieChart)
                            )
                            val navScreens = listOf(
                                AppScreen.OverviewScreen,
                                AppScreen.OffersScreen,
                                AppScreen.ShoppingListScreen,
                                AppScreen.WasteStatisticsScreen
                            )

                            // HIER WIRD DIE FOR-EACH-SCHLEIFE DER NAVIGATION BAR GERENDERT
                            navItems.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    icon = { Icon(item.second, contentDescription = item.first) },
                                    label = { Text(item.first) },
                                    selected = currentScreen == navScreens[index],
                                    onClick = { currentScreen = navScreens[index] }
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Die `when`-Anweisung rendert den entsprechenden Bildschirm basierend auf `currentScreen`.
                        when (currentScreen) {
                            // MODIFIZIERT: AppScreen.OverviewScreen ruft jetzt die NEUE OverviewScreen auf
                            AppScreen.OverviewScreen -> {
                                OverviewScreen( // Aufruf der NEUEN OverviewScreen Funktion
                                    fridgeViewModel = fridgeViewModel,
                                    onNavigateToDetail = { itemId -> // Übergabe der item ID
                                        currentScreen = AppScreen.DetailScreen(itemId)
                                    },
                                    onWasteItem = { item -> fridgeViewModel.wasteItem(item) },
                                    onToggleFavorite = { itemId, isFavorite -> fridgeViewModel.toggleFavoriteStatus(itemId, isFavorite) },
                                    onDeleteItem = { item -> fridgeViewModel.deleteItem(item) },
                                    onShowReceiptScanner = { showAppWideScannerDialog = true } // Öffnet den App-weiten Scanner
                                )
                            }
                            AppScreen.ListScreen -> {
                                // Dieser Bildschirm ist jetzt "verwaist", da er nicht mehr über die Bottom Navigation
                                // oder den FridgeOverviewScreen erreicht wird. Er bleibt hier bestehen,
                                // um die ursprüngliche Struktur beizubehalten, ist aber funktional nicht erreichbar.
                                FridgeItemListScreen(
                                    fridgeViewModel = fridgeViewModel,
                                    // Signatur hier angepasst, um String? zu übergeben
                                    onNavigateToDetail = { itemId ->
                                        currentScreen = AppScreen.DetailScreen(itemId)
                                    },
                                    onShowReceiptScanner = { showAppWideScannerDialog = true } // Öffnet den App-weiten Scanner
                                )
                            }
                            is AppScreen.DetailScreen -> {
                                FridgeItemDetailScreen(
                                    itemId = (currentScreen as AppScreen.DetailScreen).itemId, // MODIFIZIERT: Übergabe der item ID
                                    fridgeViewModel = fridgeViewModel,
                                    onNavigateBack = {
                                        // Beim Abbrechen oder Klick auf den Zurück-Pfeil zur Übersicht zurückkehren
                                        currentScreen = AppScreen.OverviewScreen
                                    },
                                    onItemSaved = {
                                        // Nach erfolgreichem Speichern zur Übersicht zurückkehren
                                        currentScreen = AppScreen.OverviewScreen
                                    }
                                )
                            }
                            // NEUE BILDSCHIRME: Implementierung der Navigationsziele
                            AppScreen.OffersScreen -> OffersScreen()
                            AppScreen.ShoppingListScreen -> ShoppingListScreen()
                            AppScreen.WasteStatisticsScreen -> WasteStatisticsScreen()
                        }
                    }

                    // Der App-weite Scanner-Dialog (für Kassenzettel-Scan von Overview-FAB)
                    if (showAppWideScannerDialog) {
                        DummyScannerDialog(
                            onDismiss = { showAppWideScannerDialog = false },
                            onBarcodeScanned = { barcode, productName ->
                                // Wenn ein Barcode über den globalen FAB gescannt wird, füge ihn als neuen Artikel hinzu.
                                val newItem = FridgeItem(name = productName, barcode = barcode)
                                fridgeViewModel.addItem(newItem)
                                Toast.makeText(context, "Artikel '$productName' (Barcode) hinzugefügt.", Toast.LENGTH_LONG).show()
                                showAppWideScannerDialog = false
                            },
                            onReceiptScanned = { recognizedItems ->
                                if (recognizedItems.isNotEmpty()) {
                                    recognizedItems.forEach { item ->
                                        // Füge jeden erkannten Artikel zur Datenbank hinzu
                                        fridgeViewModel.addItem(item)
                                    }
                                    Toast.makeText(context, "${recognizedItems.size} Artikel vom Kassenzettel hinzugefügt.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Keine Artikel vom Kassenzettel erkannt.", Toast.LENGTH_SHORT).show()
                                }
                                showAppWideScannerDialog = false
                            }
                        )
                    }
                }
            }
        } // END Composable context
    }
}

/**
 * Definiert ein neues `@Composable fun OverviewScreen` als Hauptansicht für Kühlschrankartikel.
 * Diese Ansicht zeigt Artikel in kategorisierten Abschnitten an (abgelaufen, läuft bald ab, Favoriten, andere)
 * und bietet Interaktionen wie das Navigieren zur Detailansicht, das Markieren als verschwendet
 * und das Umschalten des Favoritenstatus.
 *
 * Diese Komposable-Funktion belegt jetzt den "Kühlschrank"-Tab der Bottom Navigation.
 *
 * @param fridgeViewModel Der ViewModel zur Interaktion mit den Artikeldaten.
 * @param onNavigateToDetail Callback, der aufgerufen wird, wenn der Benutzer zur Detailansicht navigieren möchte.
 *                           Erwartet die ID des Artikels (`String?`) oder `null` für einen neuen Artikel.
 * @param onWasteItem Callback, der aufgerufen wird, wenn ein Artikel als verschwendet markiert werden soll.
 * @param onToggleFavorite Callback, der aufgerufen wird, um den Favoritenstatus eines Artikels umzuschalten.
 * @param onDeleteItem Callback, der aufgerufen wird, um einen Artikel zu löschen (für das Kontextmenü der Karte).
 * @param onShowReceiptScanner Callback, der aufgerufen wird, um den app-weiten Kassenzettel-Scanner-Dialog zu öffnen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    fridgeViewModel: FridgeViewModel,
    onNavigateToDetail: (String?) -> Unit, // Wie vom Prompt gefordert
    onWasteItem: (FridgeItem) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onDeleteItem: (FridgeItem) -> Unit, // Hinzugefügt für das Kontextmenü
    onShowReceiptScanner: () -> Unit // Hinzugefügt für die Scanner-Funktionalität
) {
    val context = LocalContext.current
    val allItems by fridgeViewModel.allItems.collectAsState(initial = emptyList())

    // Artikel für die Anzeige gruppieren
    val now = System.currentTimeMillis()
    val sevenDaysInMillis = TimeUnit.DAYS.toMillis(7)

    val expiredItems = remember(allItems) {
        allItems.filter { it.expiryDate != null && it.expiryDate!! < now }
            .sortedBy { it.expiryDate }
    }
    val expiringSoonItems = remember(allItems) {
        allItems.filter { item ->
            item.expiryDate != null && item.expiryDate!! >= now && item.expiryDate!! <= now + sevenDaysInMillis
        }.sortedBy { it.expiryDate }
    }
    val favoriteItems = remember(allItems) {
        allItems.filter { it.isFavorite && !expiredItems.contains(it) && !expiringSoonItems.contains(it) }
            .sortedBy { it.name }
    }
    val otherItems = remember(allItems) {
        allItems.filter { item ->
            !(expiredItems.contains(item) || expiringSoonItems.contains(item) || favoriteItems.contains(item))
        }.sortedBy { it.name }
    }

    // Führe die Benachrichtigungsprüfung beim ersten Komponieren dieses Overviews durch
    LaunchedEffect(Unit) {
        fridgeViewModel.checkAndNotifyExpiredItems(context, daysThreshold = 3)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kühlschrank") }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp) // Abstand zwischen den Buttons
            ) {
                // FAB für den Beleg-Scanner
                ExtendedFloatingActionButton(
                    onClick = onShowReceiptScanner, // Ruft den übergebenen Callback auf
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Beleg scannen") },
                    text = { Text("Beleg scannen") }
                )
                // FAB für "Neuen Artikel hinzufügen"
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToDetail(null) }, // null bedeutet, dass ein neuer Artikel erstellt wird
                    icon = { Icon(Icons.Default.Add, contentDescription = "Neuen Artikel hinzufügen") },
                    text = { Text("Artikel hinzufügen") }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End // Positioniert die FABs am Ende des Bildschirms
    ) { paddingValues ->
        if (allItems.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.List,
                    contentDescription = "Keine Artikel",
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Dein Kühlschrank ist leer! Füge Artikel hinzu.",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Expired Items Section
                if (expiredItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "Abgelaufen",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(expiredItems, key = { fridgeItem -> fridgeItem.id }) { item -> // Fix: Explicit named parameter for key lambda
                        FridgeItemCard(
                            item = item,
                            onClick = { onNavigateToDetail(it) },
                            onWasteItem = onWasteItem,
                            onDelete = onDeleteItem,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }

                // Expiring Soon Items Section
                if (expiringSoonItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "Läuft bald ab",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(expiringSoonItems, key = { fridgeItem -> fridgeItem.id }) { item -> // Fix: Explicit named parameter for key lambda
                        FridgeItemCard(
                            item = item,
                            onClick = { onNavigateToDetail(it) },
                            onWasteItem = onWasteItem,
                            onDelete = onDeleteItem,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }

                // Favorite Items Section
                if (favoriteItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "Deine Favoriten",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(favoriteItems, key = { fridgeItem -> fridgeItem.id }) { item -> // Fix: Explicit named parameter for key lambda
                        FridgeItemCard(
                            item = item,
                            onClick = { onNavigateToDetail(it) },
                            onWasteItem = onWasteItem,
                            onDelete = onDeleteItem,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }

                // Other Items Section
                if (otherItems.isNotEmpty()) {
                    item {
                        Text(
                            text = "Weitere Artikel",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(otherItems, key = { fridgeItem -> fridgeItem.id }) { item -> // Fix: Explicit named parameter for key lambda
                        FridgeItemCard(
                            item = item,
                            onClick = { onNavigateToDetail(it) },
                            onWasteItem = onWasteItem,
                            onDelete = onDeleteItem,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }
            }
        }
    }
}


/**
 * Composable-Funktion, die eine Übersicht über die Kühlschrankartikel anzeigt.
 * Sie bietet Statistiken und Navigationspunkte zu den anderen Bildschirmen.
 *
 * HINWEIS: Diese Funktion ist die ursprüngliche `FridgeOverviewScreen` und ist nun
 * nicht mehr direkt über `AppScreen.OverviewScreen` in der Bottom Navigation erreichbar.
 * Sie bleibt als eigenständige Komponente bestehen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeOverviewScreen(
    fridgeViewModel: FridgeViewModel,
    onNavigateToDetail: (FridgeItem?) -> Unit,
    onShowReceiptScanner: () -> Unit // Callback für den Kassenzettel-Scanner
) {
    val context = LocalContext.current
    // `fridgeViewModel.allItems` wird als `State<List<FridgeItem>>` gesammelt.
    val allItems by fridgeViewModel.allItems.collectAsState(initial = emptyList())
    val wastedItems by fridgeViewModel.wastedItems.collectAsState(initial = emptyList())

    // Führe die Benachrichtigungsprüfung beim ersten Komponieren des Overviews durch
    LaunchedEffect(Unit) {
        // Überprüfe und benachrichtige über bald ablaufende oder abgelaufene Artikel (z.B. innerhalb der nächsten 3 Tage)
        fridgeViewModel.checkAndNotifyExpiredItems(context, daysThreshold = 3)
    }

    // Statistiken berechnen
    val availableItemsCount = remember(allItems) { allItems.size }
    val expiringSoonItemsCount = remember(allItems) {
        allItems.count {
            it.daysUntilExpiry != null && it.daysUntilExpiry!! <= 7 && it.daysUntilExpiry!! >= 0
        }
    }
    val expiredItemsCount = remember(allItems) {
        allItems.count {
            it.daysUntilExpiry != null && it.daysUntilExpiry!! < 0
        }
    }
    val totalWastedItemsCount = remember(wastedItems) { wastedItems.size }

    // Launcher für POST_NOTIFICATIONS Berechtigung, spezifisch für den Test-Benachrichtigungs-Button
    val requestNotificationPermissionLauncherForTestButton = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Modified call: Pass an offset
            NotificationHelper.sendNotification(context, "Test Benachrichtigung", "Dies ist eine Testnachricht vom Kühlschrank.", notificationIdOffset = 999)
        } else {
            Toast.makeText(context, "Berechtigung zum Senden von Benachrichtigungen wurde verweigert.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Kühlschrank Dashboard") }) // Titel angepasst für Klarheit
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp) // Abstand zwischen den Buttons
            ) {
                // FAB für den Beleg-Scanner
                ExtendedFloatingActionButton(
                    onClick = onShowReceiptScanner, // Ruft den übergebenen Callback auf
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Beleg scannen") }, // Icon geändert
                    text = { Text("Beleg scannen") }
                )
                // FAB für "Neuen Artikel hinzufügen"
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToDetail(null) }, // null bedeutet, dass ein neuer Artikel erstellt wird
                    icon = { Icon(Icons.Default.Add, contentDescription = "Neuen Artikel hinzufügen") },
                    text = { Text("Artikel hinzufügen") }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End // Positioniert die FABs am Ende des Bildschirms
    ) { paddingValues ->
        // Die LazyColumn zeigt hier die Übersichtskarten an, nicht die einzelnen Artikel selbst.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Willkommen in deinem Kühlschrank!",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Übersichtskarten für Statistiken
            item {
                StatisticCard(
                    title = "Gesamte Artikel",
                    value = availableItemsCount.toString(),
                    description = "Artikel im Kühlschrank",
                    icon = Icons.Default.ShoppingCart,
                    iconTint = MaterialTheme.colorScheme.primary
                )
            }
            item {
                // HINWEIS: Hier wird Icons.Default.Warning für "Läuft bald ab" verwendet.
                StatisticCard(
                    title = "Läuft bald ab",
                    value = "$expiringSoonItemsCount",
                    description = "Artikel laufen in < 7 Tagen ab",
                    icon = Icons.Default.Warning,
                    iconTint = if (expiringSoonItemsCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                StatisticCard(
                    title = "Abgelaufen",
                    value = "$expiredItemsCount",
                    description = "Artikel sind abgelaufen",
                    icon = Icons.Default.DateRange,
                    iconTint = if (expiredItemsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                StatisticCard(
                    title = "Verschwendet",
                    value = totalWastedItemsCount.toString(),
                    description = "Artikel wurden verschwendet",
                    icon = Icons.Default.Delete,
                    iconTint = if (totalWastedItemsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Button zum Senden einer Test-Benachrichtigung
            item {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                // Modified call: Pass an offset
                                NotificationHelper.sendNotification(context, "Test Benachrichtigung", "Dies ist eine Testnachricht vom Kühlschrank.", notificationIdOffset = 999)
                            } else {
                                // Berechtigung anfordern über den spezifischen Launcher für den Test-Button
                                requestNotificationPermissionLauncherForTestButton.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            // Für Android-Versionen unter 13 ist die POST_NOTIFICATIONS-Berechtigung zur Laufzeit nicht erforderlich.
                            // Sie wird standardmäßig erteilt, wenn sie in der AndroidManifest.xml deklariert ist.
                            // Modified call: Pass an offset
                            NotificationHelper.sendNotification(context, "Test Benachrichtigung", "Dies ist eine Testnachricht vom Kühlschrank.", notificationIdOffset = 999)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test Benachrichtigung senden")
                }
            }
        }
    }
}

/**
 * Composable-Funktion zur Darstellung einer einzelne Statistikkarte auf dem FridgeOverviewScreen.
 */
@Composable
fun StatisticCard(
    title: String,
    value: String,
    description: String,
    icon: ImageVector,
    iconTint: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // Content description wird durch Titel und Beschreibung abgedeckt
                tint = iconTint,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = iconTint
            )
        }
    }
}


/**
 * Composable-Funktion, die die Liste der Kühlschrankartikel anzeigt.
 * Erlaubt das Hinzufügen neuer Artikel und das Bearbeiten bestehender Artikel.
 *
 * HINWEIS: Diese Funktion ist die ursprüngliche `FridgeItemListScreen` und ist nun
 * nicht mehr direkt über `AppScreen.ListScreen` in der Bottom Navigation erreichbar.
 * Sie bleibt als eigenständige Komponente bestehen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeItemListScreen(
    fridgeViewModel: FridgeViewModel,
    // Signatur von onNavigateToDetail auf String? aktualisiert
    onNavigateToDetail: (String?) -> Unit,
    onShowReceiptScanner: () -> Unit // Callback für den Kassenzettel-Scanner
) {
    // `fridgeViewModel.allItems` wird als `State<List<FridgeItem>>` gesammelt
    val allItems by fridgeViewModel.allItems.collectAsState(initial = emptyList())
    val wastedItems by fridgeViewModel.wastedItems.collectAsState(initial = emptyList())

    // NEU: Zustand für den Favoritenfilter
    var showFavoritesOnly by remember { mutableStateOf(false) }

    // NEU: Gefilterte Liste basierend auf dem Favoritenfilter
    val filteredItems = remember(allItems, showFavoritesOnly) {
        if (showFavoritesOnly) {
            allItems.filter { it.isFavorite }
        } else {
            allItems
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showFavoritesOnly) "Meine Favoriten (Liste)" else "Alle Artikel (Liste)") }, // Titel ändert sich mit Filter
                actions = {
                    // NEU: Favoriten Filter Button
                    IconButton(
                        onClick = { showFavoritesOnly = !showFavoritesOnly },
                    ) {
                        Icon(
                            imageVector = if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (showFavoritesOnly) "Filter: Nur Favoriten anzeigen" else "Filter: Alle Artikel anzeigen",
                            tint = if (showFavoritesOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onShowReceiptScanner) { // Ruft den übergebenen Callback auf
                        Icon(Icons.Default.ReceiptLong, contentDescription = "Beleg scannen") // Icon geändert
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToDetail(null) }) {
                Icon(Icons.Filled.Add, "Neuen Artikel hinzufügen")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {

            // Aktuelle Artikel
            Text(
                text = if (showFavoritesOnly) "Favorisierte Artikel" else "Aktuelle Artikel",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
            if (filteredItems.isEmpty()) { // Überprüfe die gefilterte Liste auf Leere
                Text(
                    text = if (showFavoritesOnly) "Keine Favoriten gefunden." else "Keine Artikel im Kühlschrank. Füge einen hinzu!",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                // Die `LazyColumn` zeigt die gefilterten Artikel an.
                LazyColumn(
                    modifier = Modifier
                        .weight(1f) // Nimmt den verbleibenden Platz ein
                        .fillMaxWidth()
                ) {
                    items(filteredItems, key = { it.id }) { item -> // Verwende filteredItems
                        FridgeItemCard(
                            item = item,
                            onClick = { onNavigateToDetail(item.id) }, // Übergibt die ID
                            onWasteItem = { fridgeViewModel.wasteItem(item) },
                            onDelete = { fridgeViewModel.deleteItem(item) },
                            onToggleFavorite = { itemId, isFavorite -> // NEU: Implementierung des Callbacks
                                fridgeViewModel.toggleFavoriteStatus(itemId, isFavorite)
                            }
                        )
                    }
                }
            }

            // Verschwendete Artikel (Nur anzeigen, wenn der Favoritenfilter nicht aktiv ist)
            if (wastedItems.isNotEmpty() && !showFavoritesOnly) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Verschwendete Artikel",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 200.dp) // Begrenzte Höhe für verschwendete Artikel
                        .fillMaxWidth()
                ) {
                    items(wastedItems, key = { it.id }) { item ->
                        WastedItemCard(
                            item = item,
                            onDelete = { fridgeViewModel.deleteItem(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Überarbeitete Composable-Funktion, die eine einzelne Kühlschrankartikelkarte anzeigt.
 *
 * @param item Das anzuzeigende [FridgeItem].
 * @param onClick Callback, der aufgerufen wird, wenn auf die Karte geklickt wird (z.B. zur Detailansicht).
 * @param onToggleFavorite Callback, der aufgerufen wird, um den Favoritenstatus des Artikels umzuschalten.
 *                         Übergibt die ID des Artikels und den neuen Favoritenstatus.
 * @param onWasteItem Callback, der aufgerufen wird, wenn der Artikel als verschwendet markiert werden soll.
 * @param onDelete Callback, der aufgerufen wird, wenn der Artikel gelöscht werden soll.
 */
@OptIn(ExperimentalFoundationApi::class) // Für combinedClickable
@Composable
fun FridgeItemCard(
    item: FridgeItem,
    onClick: (String) -> Unit, // Für Navigation zur Detailansicht
    onToggleFavorite: (String, Boolean) -> Unit, // Für das Herz-Icon
    onWasteItem: (FridgeItem) -> Unit, // Für das Kontextmenü
    onDelete: (FridgeItem) -> Unit // Für das Kontextmenü
) {
    var showMenu by remember { mutableStateOf(false) } // Zustand für das Kontextmenü

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // combinedClickable ermöglicht Klick und langen Klick
            .combinedClickable(
                onClick = { onClick(item.id) }, // Normaler Klick navigiert zur Detailansicht
                onLongClick = { showMenu = true } // Langer Klick öffnet das Kontextmenü
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Item Image or Category Icon
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = "Artikelbild",
                    modifier = Modifier
                        .size(60.dp)
                        .padding(end = 16.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = categoryIcons[item.category] ?: Icons.Default.Fastfood,
                    contentDescription = "Kategorie-Symbol für ${item.category}",
                    modifier = Modifier
                        .size(60.dp)
                        .padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Herz-Icon zum Umschalten des Favoritenstatus
                    IconButton(onClick = { onToggleFavorite(item.id, !item.isFavorite) }) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (item.isFavorite) "Als Favorit markiert" else "Als Favorit entmarkieren",
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.quantity} ${item.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.daysUntilExpiry?.let { days ->
                    val expiryText = when {
                        days > 7 -> "Haltbar für $days Tage"
                        days > 0 -> "Haltbar für $days Tage (bald abgelaufen!)"
                        days == 0 -> "Läuft heute ab!"
                        else -> "Abgelaufen vor ${-days} Tagen!"
                    }
                    val textColor = when {
                        days < 0 -> MaterialTheme.colorScheme.error
                        days <= 7 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(text = expiryText, style = MaterialTheme.typography.bodySmall, color = textColor)
                }
            }

            // Dropdown-Menü für "Verschwenden" und "Löschen"
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Als verschwendet markieren") },
                    onClick = {
                        onWasteItem(item)
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Warning, contentDescription = "Als verschwendet markieren") }
                )
                DropdownMenuItem(
                    text = { Text("Löschen") },
                    onClick = {
                        onDelete(item)
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Löschen") }
                )
            }
        }
    }
}

@Composable
fun WastedItemCard(
    item: FridgeItem,
    onDelete: (FridgeItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Verschwendet",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Verschwendet: ${item.quantity} ${item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onDelete(item) }) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Composable-Funktion, die die UI für die Bearbeitung/Hinzufügung eines Kühlschrankartikels darstellt.
 * Sie beinhaltet Bildauswahl, Kamera-Aufnahme, Barcode-Scanner (Dummy) und einen Speichern-Button
 * mit Validierung und Bestätigungsdialog.
 *
 * MODIFIZIERT: Akzeptiert jetzt `itemId: String?` anstelle von `itemToEdit: FridgeItem?`
 * und lädt den Artikel selbst aus dem ViewModel.
 *
 * @param itemId Optionaler Artikel ID, der bearbeitet werden soll. Wenn null, wird ein neuer Artikel erstellt.
 * @param fridgeViewModel Der ViewModel zur Interaktion mit den Artikeldaten.
 * @param onNavigateBack Callback, der aufgerufen wird, wenn der Benutzer zurück zur Liste navigieren möchte (ohne zu speichern).
 * @param onItemSaved Callback, der nach erfolgreichem Speichern oder Aktualisieren des Artikels aufgerufen wird.
 */
@OptIn(ExperimentalMaterial3Api::class) // Erforderlich für z.B. AlertDialog, OutlinedTextField, DatePicker
@Composable
fun FridgeItemDetailScreen(
    itemId: String?, // MODIFIZIERT: item ID für Detailansicht
    fridgeViewModel: FridgeViewModel, // ViewModel wird jetzt explizit übergeben
    onNavigateBack: () -> Unit,
    onItemSaved: () -> Unit // NEU: Callback für erfolgreiches Speichern
) {
    val context = LocalContext.current
    // Der Zustand des FridgeItem, das bearbeitet wird. `var` wird für `by remember` benötigt.
    // Initialisiert mit einem leeren Artikel.
    var item by remember { mutableStateOf(FridgeItem(name = "")) }

    // Lade Artikel, wenn `itemId` bereitgestellt wird, ansonsten erstelle einen neuen leeren Artikel
    LaunchedEffect(itemId) {
        if (itemId != null) {
            fridgeViewModel.dao.getItemById(itemId).firstOrNull()?.let {
                item = it
            } ?: run {
                // Artikel nicht gefunden, behandle es als neuen Artikel
                item = FridgeItem(name = "")
                Toast.makeText(context, "Artikel mit ID $itemId nicht gefunden. Erstelle neuen Artikel.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Neuer Artikel
            item = FridgeItem(name = "")
        }
    }

    // Zustand für den Bestätigungsdialog
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Zustand für das Textfeld des Artikelnamens (für eventuelle Validierungsanzeige)
    var itemNameText by remember { mutableStateOf(TextFieldValue(item.name)) }
    var itemNameError by remember { mutableStateOf(false) }

    // Zustand für die Sichtbarkeit des DatePicker-Dialogs
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // NEU: Zustand für die Sichtbarkeit des DummyScannerDialogs
    var showUnifiedScannerDialog by remember { mutableStateOf(false) }


    // DatePickerState, initialisiert mit dem aktuellen item.expiryDate
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = item.expiryDate
    )

    // Formatter für die Anzeige des Datums im TextField
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    // Synchronisiere itemNameText mit item.name, falls item von außen aktualisiert wird (z.B. bei Edit-Mode,
    // oder wenn item durch den ReceiptScannerDialog oder BarcodeScannerDialog aktualisiert wird).
    LaunchedEffect(item.name) {
        if (itemNameText.text != item.name) {
            itemNameText = itemNameText.copy(text = item.name)
        }
    }
    // Synchronisiere expiryDate des DatePickerState, wenn der Artikel geladen wird.
    // Der DatePickerState muss über einen Key neu erstellt werden, wenn sich initialSelectedDateMillis ändert.
    // Hier reicht es, ihn bei jedem Öffnen des Dialogs neu zu initialisieren oder
    // ihn nur einmal zu erstellen und darauf zu vertrauen, dass `item.expiryDate` korrekt ist.


    /**
     * Helferfunktion, um ein Bitmap-Bild in einen Base64 Data URI String umzuwandeln.
     * Dies ermöglicht es, Bilder, die mit `TakePicturePreview` aufgenommen wurden,
     * als String in `item.imageUrl` zu speichern und mit `AsyncImage` zu zeigen.
     */
    fun bitmapToBase64DataUri(bitmap: Bitmap?): String? {
        if (bitmap == null) return null
        val byteArrayOutputStream = ByteArrayOutputStream()
        // Bild im JPEG-Format komprimieren, um die Dateigröße zu reduzieren.
        // Qualität auf 80 setzen (0-100).
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        val base64String = Base64.encodeToString(byteArray, Base64.DEFAULT)
        // Rückgabe als Data URI, das von AsyncImage direkt interpretiert werden kann.
        return "data:image/jpeg;base64,$base64String"
    }

    // Launcher für die Auswahl eines Bildes aus der Galerie
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> // Expliziter Typ für den Callback-Parameter
        uri?.let {
            item = item.copy(imageUrl = it.toString()) // Speichere die URI als String
        }
    }

    // Launcher für das Aufnehmen eines Bildes mit der Kamera (liefert ein Thumbnail-Bitmap)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? -> // Expliziter Typ für den Callback-Parameter
        bitmap?.let {
            // Wandle das Bitmap in einen Base64 Data URI String um und speichere es
            item = item.copy(imageUrl = bitmapToBase64DataUri(it))
        }
    }

    // Launcher für die Anforderung der CAMERA-Berechtigung
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean -> // Expliziter Typ für den Callback-Parameter
        if (isGranted) {
            // Berechtigung erteilt, Kamera starten
            cameraLauncher.launch(null) // TakePicturePreview benötigt keinen Uri-Input
        } else {
            // Berechtigung verweigert, optional eine Nachricht anzeigen (z.B. Snackbar)
            Toast.makeText(context, "Kamera-Berechtigung verweigert.", Toast.LENGTH_SHORT).show()
        }
    }

    // Funktion zur Validierung der Eingabe und Anzeige des Bestätigungsdialogs
    fun validateAndShowDialog() {
        // Überprüfe, ob der Artikelname nicht leer ist
        itemNameError = item.name.isBlank()
        if (itemNameError) {
            Toast.makeText(context, "Artikelname darf nicht leer sein.", Toast.LENGTH_SHORT).show()
        } else {
            showConfirmDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) "Neuen Artikel hinzufügen" else "Artikel bearbeiten") }, // MODIFIZIERT
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { // Dies wird für das Zurückgehen ohne Speichern verwendet
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück zur Liste")
                    }
                },
                actions = {
                    // Favoriten-Toggle IconButton in der TopAppBar
                    IconButton(
                        onClick = {
                            item = item.copy(isFavorite = !item.isFavorite) // Aktualisiere den lokalen Zustand sofort
                        }
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (item.isFavorite) "Als Favorit markiert" else "Als Favorit entmarkieren",
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .imePadding(), // Passt den Inhalt an, wenn die Tastatur angezeigt wird
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top // Geändert zu Top, um das Scrollen zu ermöglichen, falls erforderlich
        ) {
            // Eingabefeld für den Artikelnamen
            OutlinedTextField(
                value = itemNameText,
                onValueChange = { newValue ->
                    itemNameText = newValue
                    item = item.copy(name = newValue.text)
                    itemNameError = false // Fehler zurücksetzen, wenn der Benutzer tippt
                },
                label = { Text("Artikelname") },
                isError = itemNameError,
                supportingText = {
                    if (itemNameError) {
                        Text("Name ist erforderlich")
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // QUANTITY AND UNIT INPUT ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Menge:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f), // Takes available space
                    horizontalArrangement = Arrangement.End // Align content to the end
                ) {
                    IconButton(
                        onClick = {
                            if (item.quantity > 1) { // Menge nicht unter 1 fallen lassen
                                item = item.copy(quantity = item.quantity - 1)
                            }
                        },
                        enabled = item.quantity > 1 // Button deaktivieren, wenn Menge 1 ist
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Menge reduzieren")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Anzeige der Menge
                    Text(
                        text = "${item.quantity}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .width(40.dp) // Feste Breite für die Menge
                            .wrapContentWidth(Alignment.CenterHorizontally) // Zentriert den Text
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // OutlinedTextField für die Einheit
                    OutlinedTextField(
                        value = item.unit,
                        onValueChange = { newValue ->
                            item = item.copy(unit = newValue)
                        },
                        label = { Text("Einheit") },
                        modifier = Modifier
                            .width(100.dp) // Feste Breite für das Einheiten-Feld
                            .height(56.dp), // Standardhöhe für Material3 OutlinedTextField
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            item = item.copy(quantity = item.quantity + 1)
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Menge erhöhen")
                    }
                }
            }
            // END QUANTITY AND UNIT INPUT ROW

            // Anzeige des aktuellen Bildes, falls vorhanden
            item.imageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl, // Coil's AsyncImage kann sowohl Uri-Strings als auch Data URIs (Base64) verarbeiten
                    contentDescription = "Artikelbild",
                    modifier = Modifier
                        .size(200.dp)
                        .aspectRatio(1f) // Sorgt für ein quadratisches Bild
                        .padding(bottom = 16.dp),
                    contentScale = ContentScale.Crop // Beschneidet das Bild, um den Bereich zu füllen
                )
            } ?: Text("Kein Bild ausgewählt", modifier = Modifier.padding(bottom = 16.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Bild aus Galerie wählen") // Icon hinzugefügt
                    Spacer(Modifier.width(8.dp))
                    Text("Galerie")
                }

                Button(
                    onClick = {
                        when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                            PackageManager.PERMISSION_GRANTED -> {
                                cameraLauncher.launch(null)
                            }
                            else -> {
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Bild mit Kamera aufnehmen") // Icon hinzugefügt
                    Spacer(Modifier.width(8.dp))
                    Text("Kamera")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // NEU: Button zum Öffnen des vereinheitlichten Scanner-Dialogs
            Button(
                onClick = { showUnifiedScannerDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner öffnen")
                Spacer(Modifier.width(8.dp))
                Text("Scanner öffnen (Input)")
            }

            Spacer(modifier = Modifier.height(16.dp))


            // Ablaufdatum Auswahlfeld
            OutlinedTextField(
                value = item.expiryDate?.let { dateFormatter.format(Date(it)) } ?: "",
                onValueChange = { /* Read-only, changes handled by DatePickerDialog */ },
                label = { Text("Ablaufdatum (Optional)") },
                readOnly = true, // Wichtig, damit der Benutzer nicht direkt tippen kann
                trailingIcon = {
                    Icon(
                        Icons.Default.DateRange, // Icon für Datumsauswahl
                        contentDescription = "Ablaufdatum wählen",
                        modifier = Modifier.clickable { showDatePickerDialog = true }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { showDatePickerDialog = true } // Macht das gesamte Feld klickbar
            )

            // Kategorie Auswahl (Optional: Dropdown oder Textfeld, hier als einfaches Textfeld)
            // NEU: Row für Icon und Textfeld
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Anzeige des Kategorie-Icons
                Icon(
                    imageVector = categoryIcons[item.category] ?: Icons.Default.Fastfood,
                    contentDescription = "Kategorie-Symbol für ${item.category}",
                    modifier = Modifier.size(48.dp), // Eine passende Größe für das Icon
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = item.category,
                    onValueChange = { newValue ->
                        item = item.copy(category = newValue)
                    },
                    label = { Text("Kategorie (z.B. Milchprodukte, Obst & Gemüse)") },
                    modifier = Modifier.weight(1f) // Nimmt den verbleibenden Platz ein
                )
            }


            // Speichern-Button
            Button(
                onClick = { validateAndShowDialog() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = "Artikel speichern") // Icon hinzugefügt
                Spacer(Modifier.width(8.dp))
                Text("Speichern")
            }

            Spacer(modifier = Modifier.height(16.dp))
            // Debug-Anzeige des aktuellen Image URL/Base64-Strings
            Text("Aktueller Image URL/Base64 (für Debug):")
            Text(item.imageUrl ?: "null", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(8.dp))
            // Debug-Anzeige des aktuellen Barcodes
            Text("Aktueller Barcode (für Debug):")
            Text(item.barcode ?: "null", style = MaterialTheme.typography.bodySmall)
        }
    }

    // DatePickerDialog
    if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            // Setze die Zeit auf das Ende des Tages, um den ganzen Tag als gültig zu betrachten
                            val date = Date(selectedMillis)
                            val calendar = java.util.Calendar.getInstance()
                            calendar.time = date
                            calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
                            calendar.set(java.util.Calendar.MINUTE, 59)
                            calendar.set(java.util.Calendar.SECOND, 59)
                            calendar.set(java.util.Calendar.MILLISECOND, 999)
                            item = item.copy(expiryDate = calendar.timeInMillis)
                        }
                        showDatePickerDialog = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePickerDialog = false }
                ) { Text("Abbrechen")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Bestätigungsdialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Artikel speichern?") },
            text = { Text("Möchten Sie den Artikel '${item.name}' wirklich speichern?") },
            confirmButton = {
                TextButton(onClick = {
                    if (itemId == null) { // MODIFIZIERT: Prüfe auf itemId
                        fridgeViewModel.addItem(item)
                        Toast.makeText(context, "Artikel '${item.name}' hinzugefügt!", Toast.LENGTH_SHORT).show()
                    } else {
                        fridgeViewModel.updateItem(item)
                        Toast.makeText(context, "Artikel '${item.name}' aktualisiert!", Toast.LENGTH_SHORT).show()
                    }
                    showConfirmDialog = false
                    onItemSaved() // Rufe onItemSaved auf, um zur Liste zurückzukehren
                }) {
                    Text("Ja")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Nein")
                }
            }
        )
    }

    // NEU: Vereinheitlichter Scanner-Dialog (DummyScannerDialog)
    if (showUnifiedScannerDialog) {
        DummyScannerDialog(
            onDismiss = { showUnifiedScannerDialog = false },
            onBarcodeScanned = { barcode, productName ->
                item = item.copy(barcode = barcode, name = productName)
                itemNameText = itemNameText.copy(text = productName)
                Toast.makeText(context, "Barcode erkannt: $productName (Barcode: $barcode)", Toast.LENGTH_LONG).show()
                showUnifiedScannerDialog = false
            },
            onReceiptScanned = { recognizedItems ->
                if (recognizedItems.isNotEmpty()) {
                    // Für die Detailansicht übernehmen wir nur den ersten erkannten Artikel,
                    // oder fusionieren die Daten, wenn es einen bestehenden Artikel gibt.
                    // Für eine echte Implementierung würde man hier eine Liste von Artikeln vorschlagen.
                    val firstRecognized = recognizedItems.first()
                    item = item.copy(
                        id = itemId ?: UUID.randomUUID().toString(), // Behalte bestehende ID oder generiere neue
                        name = firstRecognized.name,
                        quantity = firstRecognized.quantity,
                        unit = firstRecognized.unit,
                        category = firstRecognized.category,
                        expiryDate = firstRecognized.expiryDate,
                        price = firstRecognized.price,
                        barcode = firstRecognized.barcode // Auch den Barcode vom erkannten Kassenzettelartikel übernehmen
                    )
                    itemNameText = itemNameText.copy(text = firstRecognized.name)
                    Toast.makeText(context, "${recognizedItems.size} Artikel vom Kassenzettel erkannt. Ersten Artikel ('${firstRecognized.name}') vorbefüllt.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Keine Artikel vom Kassenzettel erkannt.", Toast.LENGTH_SHORT).show()
                }
                showUnifiedScannerDialog = false
            }
        )
    }
}

/**
 * NEU: Überarbeitete Composable-Funktion, die einen Scanner-Dialog mit einem Texteingabefeld anzeigt.
 * Der Benutzer kann einen Barcode oder einen simulierten Kassenzettel-Text eingeben.
 * Die Funktion versucht, den Eingabetyp zu erkennen und ruft den entsprechenden Callback auf.
 *
 * @param onDismiss Callback, der aufgerufen wird, wenn der Dialog geschlossen werden soll (Abbrechen).
 * @param onBarcodeScanned Callback, der aufgerufen wird, wenn ein Barcode erkannt wurde.
 *                         Gibt den Barcode-String und einen simulierten Produktnamen zurück.
 * @param onReceiptScanned Callback, der aufgerufen wird, wenn ein Kassenzettel-Text erkannt wurde.
 *                         Gibt eine Liste simulierter FridgeItem-Artikel zurück.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DummyScannerDialog(
    onDismiss: () -> Unit,
    onBarcodeScanned: (barcode: String, productName: String) -> Unit, // Geänderte Signatur, um Produktname zu übergeben
    onReceiptScanned: (List<FridgeItem>) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scanner-Simulation (Texteingabe)") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Barcode oder Kassenzettel-Text eingeben") },
                    placeholder = { Text("z.B. Barcode: 1234567890123 oder Kassenzettel: Milch 1.29; Brot 2.50") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = "Nutzen Sie die Buttons unten, um Barcode- oder Kassenzettel-Scans zu simulieren.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button for Barcode scanning simulation
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val scannedBarcode = inputText
                            // Simple simulation of product lookup. Product name from barcode is often external lookup.
                            val productName = "Produkt ${scannedBarcode.take(5)} (simuliert)"
                            onBarcodeScanned(scannedBarcode, productName)
                            Toast.makeText(context, "Barcode gescannt: $scannedBarcode ($productName)", Toast.LENGTH_LONG).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Bitte geben Sie einen Barcode ein.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode scannen")
                    Spacer(Modifier.width(8.dp))
                    Text("Barcode scannen simulieren")
                }

                // Button for Receipt scanning simulation
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val recognizedItems = parseReceiptText(inputText)
                            onReceiptScanned(recognizedItems)
                            if (recognizedItems.isNotEmpty()) {
                                Toast.makeText(context, "${recognizedItems.size} Artikel vom Kassenzettel erkannt.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Keine Artikel vom Kassenzettel erkannt.", Toast.LENGTH_SHORT).show()
                            }
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Bitte geben Sie Kassenzettel-Text ein.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "Kassenzettel scannen")
                    Spacer(Modifier.width(8.dp))
                    Text("Kassenzettel scannen simulieren")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

/**
 * Helferfunktion zum Parsen von simuliertem Kassenzettel-Text in eine Liste von FridgeItem-Objekten.
 * Die Logik ist stark vereinfacht für Demonstrationszwecke.
 */
private fun parseReceiptText(text: String): List<FridgeItem> {
    val items = mutableListOf<FridgeItem>()
    val entries = text.split(";")
    entries.forEach { entry ->
        val trimmedEntry = entry.trim()
        if (trimmedEntry.isNotBlank()) {
            val parts = trimmedEntry.split(" ")
            if (parts.isNotEmpty()) {
                val name = parts[0]
                val priceString = parts.getOrNull(1)?.replace(",", ".")
                val price = priceString?.toDoubleOrNull() ?: 0.0

                // Basic logic to guess category and expiry
                val category = when {
                    name.contains("Milch", ignoreCase = true) || name.contains("Joghurt", ignoreCase = true) -> "Milchprodukte"
                    name.contains("Brot", ignoreCase = true) -> "Backwaren"
                    name.contains("Äpfel", ignoreCase = true) || name.contains("Banane", ignoreCase = true) -> "Obst"
                    name.contains("Fleisch", ignoreCase = true) || name.contains("Wurst", ignoreCase = true) -> "Fleisch & Fisch"
                    else -> "Sonstiges"
                }
                val expiryOffsetDays = when(category) {
                    "Milchprodukte" -> 7
                    "Backwaren" -> 5
                    "Obst" -> 14
                    "Fleisch & Fisch" -> 3
                    else -> 10
                }
                val expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(expiryOffsetDays.toLong())

                items.add(
                    FridgeItem(
                        name = name,
                        quantity = 1, // Default zu 1 für Einfachheit
                        unit = "Stk.",
                        category = category,
                        expiryDate = expiryDate,
                        price = price,
                        // Generiere einen Dummy-Barcode für erkannte Kassenzettelartikel
                        barcode = UUID.randomUUID().toString().take(13).filter { it.isDigit() }.padStart(13, '0') // Einfacher Weg, eine 13-stellige Ziffernfolge zu erzeugen
                    )
                )
            }
        }
    }
    return items
}


/**
 * NEU: Mock-Screen für Angebote.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OffersScreen() {
    val context = LocalContext.current
    // Dummy-Angebotsdaten
    val offers = remember {
        listOf(
            SupermarketOffer(
                store = "EDEKA",
                item = "Bio-Milch 1L",
                originalPrice = 1.89,
                offerPrice = 1.49,
                endDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3),
                imageUrl = "https://picsum.photos/id/1070/200/200" // Milch
            ),
            SupermarketOffer(
                store = "LIDL",
                item = "Äpfel Jonagold",
                originalPrice = 2.99,
                offerPrice = 1.99,
                endDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7),
                imageUrl = "https://picsum.photos/id/1084/200/200" // Äpfel
            ),
            SupermarketOffer(
                store = "ALDI SÜD",
                item = "Frischkäse 200g",
                originalPrice = 1.29,
                offerPrice = 0.89,
                endDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(5),
                imageUrl = "https://picsum.photos/id/1015/200/200" // Käse
            ),
            SupermarketOffer(
                store = "REWE",
                item = "Mineralwasser 6x1.5L",
                originalPrice = 3.49,
                offerPrice = 2.49,
                endDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(4),
                imageUrl = "https://picsum.photos/id/1076/200/200" // Wasserflaschen
            ),
            SupermarketOffer(
                store = "EDEKA",
                item = "Hähnchenbrustfilet 400g",
                originalPrice = 5.99,
                offerPrice = 4.29,
                endDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2),
                imageUrl = "https://picsum.photos/id/1005/200/200" // Fleisch
            ),
            SupermarketOffer(
                store = "KAUFLAND",
                item = "Toastbrot Vollkorn",
                originalPrice = 1.79,
                offerPrice = 1.19,
                endDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(6),
                imageUrl = "https://picsum.photos/id/1060/200/200" // Brot
            ),
            SupermarketOffer(
                store = "NETTO",
                item = "Schokolade Alpenmilch",
                originalPrice = 0.99,
                offerPrice = 0.69,
                endDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(10),
                imageUrl = "https://picsum.photos/id/1043/200/200" // Schokolade
            )
        )
    }
    
    val dateFormatter = remember { SimpleDateFormat("dd.MM.", Locale.getDefault()) }

    // Launcher für POST_NOTIFICATIONS Berechtigung, spezifisch für den Test-Benachrichtigungs-Button
    val requestNotificationPermissionLauncherForOffers = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val selectedOffer = offers.firstOrNull() // Nimm das erste Angebot als Beispiel
            selectedOffer?.let {
                NotificationHelper.sendNotification(
                    context,
                    "Neues Angebot!",
                    "${it.item} bei ${it.store} für nur %.2f€!".format(it.offerPrice),
                    notificationIdOffset = 2000 // Eindeutiger Offset für Angebote
                )
            }
        } else {
            Toast.makeText(context, "Berechtigung zum Senden von Benachrichtigungen wurde verweigert.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Angebote") },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val selectedOffer = offers.firstOrNull()
                            selectedOffer?.let {
                                NotificationHelper.sendNotification(
                                    context,
                                    "Neues Angebot!",
                                    "${it.item} bei ${it.store} für nur %.2f€!".format(it.offerPrice),
                                    notificationIdOffset = 2000
                                )
                            } ?: Toast.makeText(context, "Kein Dummy-Angebot zum Benachrichtigen.", Toast.LENGTH_SHORT).show()
                        } else {
                            requestNotificationPermissionLauncherForOffers.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        // Für Android-Versionen unter 13 ist die POST_NOTIFICATIONS-Berechtigung zur Laufzeit nicht erforderlich.
                        val selectedOffer = offers.firstOrNull()
                        selectedOffer?.let {
                            NotificationHelper.sendNotification(
                                context,
                                "Neues Angebot!",
                                "${it.item} bei ${it.store} für nur %.2f€!".format(it.offerPrice),
                                notificationIdOffset = 2000
                            )
                        } ?: Toast.makeText(context, "Kein Dummy-Angebot zum Benachrichtigen.", Toast.LENGTH_SHORT).show()
                    }
                },
                icon = { Icon(Icons.Default.TrendingUp, contentDescription = "Angebote aktualisieren / Benachrichtigungen testen") }, // Icon für Angebote
                text = { Text("Angebote Test-Benachrichtigung") }
            )
        },
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        if (offers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = "Angebote", modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Hier würden bald attraktive Angebote für dich angezeigt werden!",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Diese Funktion ist noch in Entwicklung.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Aktuelle Supermarkt-Angebote:",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(offers) { offer ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            offer.imageUrl?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = offer.item,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .padding(end = 16.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } ?: Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = offer.item,
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(end = 16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = offer.item, style = MaterialTheme.typography.titleMedium)
                                Text(text = offer.store, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Angebot bis: ${dateFormatter.format(Date(offer.endDate))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "%.2f €".format(offer.originalPrice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    // Durchstreichen für Originalpreis
                                    textDecoration = TextDecoration.LineThrough
                                )
                                Text(
                                    text = "%.2f €".format(offer.offerPrice),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.error // Hervorhebung für Angebotspreis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * NEU: Mock-Screen für Einkaufsliste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einkaufsliste") },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.AddShoppingCart, contentDescription = "Einkaufsliste", modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Deine intelligente Einkaufsliste kommt bald!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Artikel mit geringem Bestand oder favorisierte Artikel könnten hier automatisch vorgeschlagen werden.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * NEU: Mock-Screen für Verschwendungs-Statistiken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteStatisticsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verschwendungs-Statistiken") },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.PieChart, contentDescription = "Verschwendungs-Statistiken", modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Erfahre mehr über deine Lebensmittelverschwendung!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Hier werden bald detaillierte Statistiken und Tipps zur Reduzierung deiner Verschwendung angezeigt.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * Minimale Theme-Definition für die Anwendung.
 * Dies ist hier definiert, um die Anforderung "alles in Jetpack Compose in MainActivity.kt"
 * zu erfüllen und `Unresolved reference 'MyApplicationTheme'` Fehler zu beheben.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme() // Verwendet das Material3 dunkle Farbschema
    } else {
        lightColorScheme() // Verwendet das Material3 helle Farbschema
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(), // Standard-Typografie
        content = content
    )
}

/**
 * Preview-Composable für die ursprüngliche `FridgeOverviewScreen` (jetzt Dashboard-Ansicht).
 * Umbenannt für Klarheit.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun PreviewFridgeDashboardScreen() {
    MyApplicationTheme {
        val context = LocalContext.current
        val dao = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // Für Previews in der Regel OK, aber nicht für produktiven Code
            .build()
            .fridgeItemDao()

        val factory = FridgeViewModelFactory(dao)
        val fridgeViewModel: FridgeViewModel = viewModel(factory = factory)

        // Simuliert, dass ein paar Artikel existieren
        LaunchedEffect(Unit) {
            fridgeViewModel.addItem(FridgeItem(name = "Äpfel", quantity = 5, category = "Obst & Gemüse", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(10), imageUrl = "https://picsum.photos/id/1084/200/200"))
            fridgeViewModel.addItem(FridgeItem(name = "Joghurt", quantity = 2, category = "Milchprodukte", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3), imageUrl = "https://picsum.photos/id/1070/200/200"))
            fridgeViewModel.addItem(FridgeItem(name = "Brot", quantity = 1, category = "Backwaren", expiryDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))) // Abgelaufen
            fridgeViewModel.addItem(FridgeItem(name = "Käse", quantity = 1, unit = "Stk.", category = "Milchprodukte", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)))
            fridgeViewModel.addItem(FridgeItem(name = "Orangensaft", quantity = 1, unit = "L", category = "Getränke", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(14)))
            fridgeViewModel.wasteItem(FridgeItem(name = "Salat (verschwendet)", quantity = 1, category = "Obst & Gemüse", expiryDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(5)))
        }

        FridgeOverviewScreen( // Aufruf der ursprünglichen Funktion
            fridgeViewModel = fridgeViewModel,
            onNavigateToDetail = { /* No-op for preview */ },
            onShowReceiptScanner = { /* No-op for preview */ }
        )
    }
}

/**
 * NEU: Preview-Composable für die `OverviewScreen` (die neue Hauptliste).
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun PreviewNewOverviewScreen() {
    MyApplicationTheme {
        val context = LocalContext.current
        val dao = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .fridgeItemDao()

        val factory = FridgeViewModelFactory(dao)
        val fridgeViewModel: FridgeViewModel = viewModel(factory = factory)

        LaunchedEffect(Unit) {
            fridgeViewModel.addItem(FridgeItem(name = "Äpfel", quantity = 5, category = "Obst & Gemüse", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(10), imageUrl = "https://picsum.photos/id/1084/200/200"))
            fridgeViewModel.addItem(FridgeItem(name = "Joghurt", quantity = 2, category = "Milchprodukte", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3), imageUrl = "https://picsum.photos/id/1070/200/200", isFavorite = true))
            fridgeViewModel.addItem(FridgeItem(name = "Brot", quantity = 1, category = "Backwaren", expiryDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))) // Expired
            fridgeViewModel.addItem(FridgeItem(name = "Käse", quantity = 1, unit = "Stk.", category = "Milchprodukte", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)))
            fridgeViewModel.addItem(FridgeItem(name = "Rinderhackfleisch", quantity = 500, unit = "g", category = "Fleisch & Fisch", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2))) // Expiring soon
            fridgeViewModel.addItem(FridgeItem(name = "Cola Zero", quantity = 6, unit = "Flaschen", category = "Getränke", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(90), isFavorite = true))
        }

        OverviewScreen(
            fridgeViewModel = fridgeViewModel,
            onNavigateToDetail = { /* No-op for preview */ },
            onWasteItem = { /* No-op for preview */ },
            onToggleFavorite = { _, _ -> /* No-op for preview */ },
            onDeleteItem = { /* No-op for preview */ },
            onShowReceiptScanner = { /* No-op for preview */ }
        )
    }
}


/**
 * Preview-Composable für die `FridgeItemDetailScreen` im "Hinzufügen"-Modus.
 * MODIFIZIERT: Übergibt `null` für `itemId`.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun PreviewFridgeItemDetailScreen() {
    MyApplicationTheme {
        val context = LocalContext.current
        val dao = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .fridgeItemDao()
        val factory = FridgeViewModelFactory(dao)
        val fridgeViewModel: FridgeViewModel = viewModel(factory = factory)

        // Preview für den "Hinzufügen"-Modus (neuer Artikel)
        FridgeItemDetailScreen(
            itemId = null, // MODIFIZIERT: Übergabe von null für einen neuen Artikel
            fridgeViewModel = fridgeViewModel,
            onNavigateBack = {}, // Leeres Lambda für Preview
            onItemSaved = {}     // Leeres Lambda für Preview
        )
    }
}

/**
 * Preview-Composable für die `FridgeItemDetailScreen` im "Bearbeiten"-Modus.
 * MODIFIZIERT: Übergibt eine simulierte `itemId` und stellt sicher, dass der Artikel im DAO vorhanden ist.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun PreviewFridgeItemDetailScreenEditMode() {
    MyApplicationTheme {
        val context = LocalContext.current
        val dao = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .fridgeItemDao()
        val factory = FridgeViewModelFactory(dao)
        val fridgeViewModel: FridgeViewModel = viewModel(factory = factory)

        // Simulieren, dass ein Artikel bereits im ViewModel/DB vorhanden ist.
        val existingItemId = "test-id-123"
        LaunchedEffect(Unit) {
            val existingItem = FridgeItem(
                id = existingItemId,
                name = "Bio-Milch 3.5%",
                quantity = 2,
                unit = "Liter",
                category = "Milchprodukte",
                expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7),
                storageLocation = "Kühlschrank",
                imageUrl = "https://picsum.photos/id/1015/200/200", // Beispiel-URL für Coil
                isFavorite = true // Zeigt einen bereits favorisierten Artikel an
            )
            dao.insert(existingItem) // Füge den Artikel zum In-Memory-DAO hinzu
        }

        // Preview für den "Bearbeiten"-Modus (bestehender Artikel)
        FridgeItemDetailScreen(
            itemId = existingItemId, // MODIFIZIERT: Übergabe der Item-ID
            fridgeViewModel = fridgeViewModel,
            onNavigateBack = {}, // Leeres Lambda für Preview
            onItemSaved = {}     // Leeres Lambda für Preview
        )
    }
}

/**
 * Preview-Composable für die ursprüngliche `FridgeItemListScreen`.
 * Diese Vorschau ist noch vorhanden, obwohl `FridgeItemListScreen` nicht mehr
 * über die Hauptnavigation erreichbar ist.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun PreviewFridgeItemListScreen() {
    MyApplicationTheme {
        val context = LocalContext.current
        val dao = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .fridgeItemDao()
        val factory = FridgeViewModelFactory(dao)
        val fridgeViewModel: FridgeViewModel = viewModel(factory = factory)

        // Simuliert, dass ein paar Artikel existieren
        LaunchedEffect(Unit) {
            fridgeViewModel.addItem(FridgeItem(name = "Äpfel", quantity = 5, category = "Obst & Gemüse", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(10), imageUrl = "https://picsum.photos/id/1084/200/200"))
            fridgeViewModel.addItem(FridgeItem(name = "Joghurt", quantity = 2, category = "Milchprodukte", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3), imageUrl = "https://picsum.photos/id/1070/200/200", isFavorite = true)) // Add one favorite item for preview
            fridgeViewModel.addItem(FridgeItem(name = "Brot", quantity = 1, category = "Backwaren", expiryDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))) // Abgelaufen
            fridgeViewModel.addItem(FridgeItem(name = "Käse", quantity = 1, unit = "Stk.", category = "Milchprodukte", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30)))
            fridgeViewModel.addItem(FridgeItem(name = "Rinderhackfleisch", quantity = 500, unit = "g", category = "Fleisch & Fisch", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2)))
            fridgeViewModel.addItem(FridgeItem(name = "Cola Zero", quantity = 6, unit = "Flaschen", category = "Getränke", expiryDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(90), isFavorite = true)) // Another favorite
            fridgeViewModel.wasteItem(FridgeItem(name = "Salat (verschwendet)", quantity = 1, category = "Obst & Gemüse", expiryDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(5)))
        }

        FridgeItemListScreen(fridgeViewModel = fridgeViewModel, onNavigateToDetail = { /* No-op for preview */ }, onShowReceiptScanner = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun PreviewOffersScreen() {
    MyApplicationTheme {
        OffersScreen()
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun PreviewShoppingListScreen() {
    MyApplicationTheme {
        ShoppingListScreen()
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
fun PreviewWasteStatisticsScreen() {
    MyApplicationTheme {
        WasteStatisticsScreen()
    }
}