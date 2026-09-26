package com.example.myapplication

import android.content.Context
import com.example.myapplication.data.MarketProductDatabase
import com.example.myapplication.data.MarketProductEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Importer-Klasse zum schnellen Einlesen strukturierter Sortimente (Open Food Facts JSON/CSV, Markt-JSONs).
 * Speichert bis zu 20.000 Artikel vorindiziert in der SQLite/Room-Struktur mit FTS.
 */
object MarketDictionaryImporter {

    /**
     * Liest JSON-Dateien aus Assets oder externen Quellen ein und speichert sie in Chunks in Room.
     */
    suspend fun importFromJsonString(context: Context, jsonString: String, marketName: String = "general") = withContext(Dispatchers.IO) {
        try {
            val type = object : TypeToken<List<MarketDictionaryEntry>>() {}.type
            val rawEntries: List<MarketDictionaryEntry> = Gson().fromJson(jsonString, type) ?: emptyList()

            val roomEntries = rawEntries.map { entry ->
                MarketProductEntry(
                    market = marketName,
                    receiptPattern = entry.receipt_pattern,
                    cleanName = entry.clean_name,
                    category = entry.category,
                    defaultStorage = entry.default_storage,
                    defaultShelfLifeDays = entry.default_shelf_life_days
                )
            }

            val dao = MarketProductDatabase.getInstance(context).marketProductDao()
            roomEntries.chunked(500).forEach { chunk ->
                dao.insertMarketProducts(chunk)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Liest Open Food Facts CSV-Exporte (barcode;name;category;brand;...) im Hintergrund ein.
     */
    suspend fun importFromOpenFoodFactsCsv(context: Context, inputStream: InputStream, marketName: String = "general") = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val entries = mutableListOf<MarketProductEntry>()
            reader.readLine() // Header überspringen
            var line: String? = reader.readLine()
            while (line != null) {
                val tokens = line.split(";")
                if (tokens.size >= 2) {
                    val rawName = tokens[1].trim()
                    if (rawName.length >= 3) {
                        val category = if (tokens.size >= 3 && tokens[2].isNotBlank()) tokens[2].trim().uppercase() else "SONSTIGES"
                        val cleanName = ReceiptImportSanitizer.cleanReceiptText(rawName)
                        entries.add(
                            MarketProductEntry(
                                market = marketName,
                                receiptPattern = rawName,
                                cleanName = if (cleanName.isNotBlank()) cleanName else rawName,
                                category = category
                            )
                        )
                    }
                }
                line = reader.readLine()
            }

            val dao = MarketProductDatabase.getInstance(context).marketProductDao()
            entries.chunked(500).forEach { chunk ->
                dao.insertMarketProducts(chunk)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Befüllt die Room-Datenbank initial aus den vorhandenen Assets (kaufland.json, general.json, etc.) und der generierten Marken-Datenbank.
     */
    suspend fun seedDatabaseFromAssets(context: Context) = withContext(Dispatchers.IO) {
        val dao = MarketProductDatabase.getInstance(context).marketProductDao()
        val count = dao.getMarketProductCount()

        val markets = listOf("kaufland", "lidl", "aldi", "rewe", "edeka", "netto", "general")
        if (count == 0) {
            for (m in markets) {
                try {
                    val json = context.assets.open("markets/$m.json").bufferedReader().use { it.readText() }
                    importFromJsonString(context, json, m)
                } catch (e: Exception) {
                    // Asset eventuell nicht vorhanden, ignorieren
                }
            }
        }

        // Immer mit massiven Markenprodukten anreichern
        val brandProducts = generateMassiveBrandProducts()
        brandProducts.chunked(500).forEach { chunk ->
            dao.insertMarketProducts(chunk)
        }
    }

    private data class BrandDef(val brand: String, val category: String, val items: List<String>)

    fun generateMassiveBrandProducts(): List<MarketProductEntry> {
        val list = mutableListOf<MarketProductEntry>()

        val brandDefs = listOf(
            BrandDef(
                brand = "Gutfried",
                category = "Fleisch & Fisch",
                items = listOf(
                    "Hähnchenbrust Klassik", "Hähnchenbrust Kräuter", "Putenbrust Klassik",
                    "Hähnchen-Salami", "Puten-Salami", "Geflügel-Fleischwurst", "Puten-Jagdwurst", "Geflügel-Schilder-Salami",
                    "Wie Landleberwurst", "Buffet Hähnchenbrust", "Puten-Lachsschinken", "Geflügel-Mettwurst"
                )
            ),
            BrandDef(
                brand = "Herta",
                category = "Fleisch & Fisch",
                items = listOf(
                    "Finesse Hähnchenbrust Klassik", "Finesse Hähnchenbrust Kräuter", "Finesse Hähnchenbrust Paprika",
                    "Finesse Hähnchenbrust Curry", "Finesse Hähnchenbrust Honig", "Finesse Putenbrust Klassik",
                    "Finesse Putenbrust Kräuter", "Finesse Putenbrust Pfeffer", "Finesse Putenbrust Honig-Senf",
                    "Saftschinken", "Katenrauchschinken", "Genuss-Schinken"
                )
            ),
            BrandDef(
                brand = "Meggle",
                category = "Kühlung & Milch",
                items = listOf(
                    "Feine Süßrahmbutter", "Streichzart ungesalzen", "Streichzart gesalzen", "Kräuter-Butter Original",
                    "Kräuter-Butter Riegel", "Knoblauch-Butter", "Bärlauch-Butter", "Diabolo-Butter", "Baguette Kräuter",
                    "Baguette Knoblauch", "Baguette Kräuter-Käse", "Grill-Knoper", "Ofenbrot Kräuter", "Kräuter-Favoriten",
                    "Alpenbutter", "Sauerrahmbutter", "Vegan Kräuter-Aufstrich"
                )
            ),
            BrandDef(
                brand = "Bergader",
                category = "Kühlung & Milch",
                items = listOf(
                    "Bavaria Blu Der Würzige", "Bavaria Blu Der Sanfte", "Bavaria Blu Fitness", "Bergbauern Käse Mild",
                    "Bergbauern Käse Würzig", "Bergbauern Käse Nussig", "Almzeit Cremig-Mild", "Almzeit Pilze",
                    "Almzeit Bärlauch", "Edelpilz", "Bergbauern Butter", "Bergbauern Scheiben Rahmig"
                )
            ),
            BrandDef(
                brand = "Gazi",
                category = "Kühlung & Milch",
                items = listOf(
                    "Hirtenkäse 45%", "Hirtenkäse Leicht 30%", "Grill- und Pfannenkäse Natur", "Grill- und Pfannenkäse Kräuter",
                    "Grill- und Pfannenkäse Chili", "Hellim Grillkäse", "Ziegenkäse", "Schafskäse", "Feta g.U.",
                    "Yoğurt 3.5%", "Yoğurt 10%", "Cacik", "Kefir", "Süzme Yoğurt", "Labne"
                )
            ),
            BrandDef(
                brand = "Coca-Cola",
                category = "Getränke",
                items = listOf(
                    "Original Taste", "Zero Sugar", "Zero Sugar Cherry", "Zero Sugar Lemon", "Light Taste",
                    "Cherry", "Vanilla", "Zero Sugar Vanilla", "Caffeine Free", "Zero Sugar Lime"
                )
            ),
            BrandDef(
                brand = "Schweppes",
                category = "Getränke",
                items = listOf(
                    "Indian Tonic Water", "Tonic Water Zero", "Dry Tonic Water", "Bitter Lemon", "Bitter Lemon Zero",
                    "Wild Berry", "Wild Berry Zero", "Ginger Ale", "Ginger Ale Zero", "Ginger Beer", "Ruschian", "Fruity Citrus"
                )
            ),
            BrandDef(
                brand = "Milram",
                category = "Kühlung & Milch",
                items = listOf(
                    "Frühlingsquark", "Frühlingsquark Leicht", "Gewürzquark Kartoffel", "Knoblauchquark", "Schnittlauchquark",
                    "Burlander Mild", "Burlander Würzig", "Müritz Herzhaft", "Sylter Scheiben", "Gouda Scheiben",
                    "Tilsiter Scheiben", "Butterkäse Scheiben", "Körniger Frischkäse", "Sour Cream", "Smuldier"
                )
            ),
            BrandDef(
                brand = "Almette",
                category = "Kühlung & Milch",
                items = listOf(
                    "Natur", "Kräuter", "Meerrettich", "Knoblauch", "Frühlingszwiebel", "Paprika-Nuss",
                    "Tomate-Gorgonzola", "Ziegenkäse", "Balance", "Hüttenkäse", "Feine Kräuter Leicht"
                )
            ),
            BrandDef(
                brand = "Haribo",
                category = "Süßwaren & Snacks",
                items = listOf(
                    "Goldbären", "Goldbären Sauer", "Tropifrutti", "Phantasia", "Colorado", "Macht Kinder Froh",
                    "Saure Pommes", "Quaxi", "Happy Cola", "Happy Cola Sauer", "Balla Stixx Erdbeere",
                    "Balla Stixx Apfel", "Vampire", "Pico-Balla", "Saure Bären", "Primavera Erdbeeren"
                )
            ),
            BrandDef(
                brand = "Milka",
                category = "Süßwaren & Snacks",
                items = listOf(
                    "Alpenmilch Schokolade", "Haselnuss", "Ganze Haselnüsse", "Oreo", "Daim", "Kuhflecken",
                    "Erdnuss Caramel", "Weiße Schokolade", "Triolade", "Caramel", "Strawberry Cheesecake",
                    "Lu", "Tuc", "Noisette", "Joghurt", "Zartherb"
                )
            ),
            BrandDef(
                brand = "Knorr",
                category = "Vorrat & Konserven",
                items = listOf(
                    "Fix Spaghetti Bolognese", "Fix Lasagne", "Fix Chili con Carne", "Fix Gulasch", "Fix Hackbraten",
                    "Fix Ofen-Fleisch", "Fix Geschnetzeltes", "Delikatess Brühe", "Salatkrönung Gartenkräuter",
                    "Salatkrönung Italian", "Snack Pot Spaghetti Carbonara", "Schlemmersauce Knoblauch", "Aromat",
                    "Tomaten-Suppe", "Feinschmecker Sauce"
                )
            ),
            BrandDef(
                brand = "Maggi",
                category = "Vorrat & Konserven",
                items = listOf(
                    "Fix für Spaghetti Bolognese", "Fix für Lasagne", "Fix für Chili con Carne", "Fix für Gulasch",
                    "Fix für Sauerbraten", "Würze Original", "Klare Brühe", "Guten Appetit Suppe",
                    "5 Minuten Terrine Kartoffelbrei", "5 Minuten Terrine Spaghetti", "Saftiger Braten", "Meisterklasse Sauce"
                )
            ),
            BrandDef(
                brand = "Dr. Oetker",
                category = "Tiefkühlwaren & Backen",
                items = listOf(
                    "Ristorante Pizza Salame", "Ristorante Pizza Mozzarella", "Ristorante Pizza Speciale",
                    "Ristorante Pizza Tonno", "Ristorante Pizza Hawaii", "Ristorante Pizza Quattro Formaggi",
                    "Die Ofenfrische Salami", "Die Ofenfrische Vier Käse", "Paula Pudding Schoko",
                    "Paula Pudding Vanille", "Backin Backpulver", "Bourbon Vanillezucker", "Gelfix Extra",
                    "Paradies Creme Vanille"
                )
            ),
            BrandDef(
                brand = "Wagner",
                category = "Tiefkühlwaren",
                items = listOf(
                    "Original Steinofen Pizza Salami", "Original Steinofen Pizza Speciale", "Original Steinofen Pizza Mozzarella",
                    "Original Steinofen Pizza Thunfisch", "Original Steinofen Pizza Margherita", "Big City Pizza Sydney",
                    "Big City Pizza Boston", "Big City Pizza London", "Piccolinis Salami", "Piccolinis Drei Käse",
                    "Piccolinis Schinken", "Ernst Wagners Original Salami"
                )
            ),
            BrandDef(
                brand = "Iglo",
                category = "Tiefkühlwaren",
                items = listOf(
                    "Rahm-Spinat", "Junge Erbsen", "Fischstäbchen 15er", "Backfisch-Stäbchen", "Schlemmer-Filet Bordelaise",
                    "Schlemmer-Filet Italiano", "Schlemmer-Filet Champignon", "Green Cuisine Vegane Fischstäbchen",
                    "Chicken Nuggets", "Gemüse-Ideen Mexikanisch", "Feine Wok-Gemüse", "Königsgemüse"
                )
            )
        )

        val variations = listOf(
            "", "100g", "150g", "200g", "250g", "300g", "400g", "500g", "750g", "1kg",
            "0.33l", "0.5l", "1.0l", "1.25l", "1.5l", "2.0l",
            "Packung", "Becher", "Flasche", "Dose", "Glas", "12er", "6er", "XXL"
        )

        for (b in brandDefs) {
            for (item in b.items) {
                val fullName = "${b.brand} $item"
                list.add(
                    MarketProductEntry(
                        market = "general",
                        receiptPattern = fullName,
                        cleanName = fullName,
                        category = b.category
                    )
                )
                for (v in variations) {
                    if (v.isNotBlank()) {
                        val patternWithVar = "$fullName $v"
                        list.add(
                            MarketProductEntry(
                                market = "general",
                                receiptPattern = patternWithVar,
                                cleanName = fullName,
                                category = b.category
                            )
                        )
                    }
                }
            }
        }
        return list
    }
}
