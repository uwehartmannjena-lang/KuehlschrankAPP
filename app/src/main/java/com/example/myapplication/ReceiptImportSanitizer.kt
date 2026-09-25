package com.example.myapplication

import android.content.Context
import com.example.myapplication.data.Product
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

data class FoodDictionaryEntry(
    val name: String,
    val category: String = "Sonstiges",
    val synonyms: List<String> = emptyList(),
    val abbreviations: List<String> = emptyList(),
    val defaultStorageLocation: String = "Kühlschrank",
    val defaultExpiryDays: Int = 7
)

/**
 * Bereitet OCR-Ergebnisse für die Importvorschau vor und führt Fuzzy Matching mit dem Lebensmittel-Wörterbuch durch.
 */
object ReceiptImportSanitizer {

    private var dictionary: List<FoodDictionaryEntry> = emptyList()

    private val fallbackDictionary: List<FoodDictionaryEntry> = listOf(
        FoodDictionaryEntry("Vollmilch", "Kühlung & Milch", listOf("Milch", "M1lch", "H-Milch", "Frischmilch"), listOf("MILCH", "M1LCH", "VOLLMILCH")),
        FoodDictionaryEntry("Butter", "Kühlung & Milch", listOf("Bvtter", "Süßrahmbutter", "Markenbutter"), listOf("BUTTER", "BVTTER", "MARKENB.")),
        FoodDictionaryEntry("Magerquark", "Kühlung & Milch", listOf("Quark", "Speisequark"), listOf("QUARK", "MAGERQUARK")),
        FoodDictionaryEntry("Naturjoghurt", "Kühlung & Milch", listOf("Joghurt", "Jogurt"), listOf("JOGHURT", "JOGURT")),
        FoodDictionaryEntry("Gouda Käse", "Kühlung & Milch", listOf("Gouda", "G0uda", "Käse", "Kaese"), listOf("GOUDA", "G0UDA", "KAESE")),
        FoodDictionaryEntry("Eier", "Kühlung & Milch", listOf("Hühnereier", "Freilandeier"), listOf("EIER", "FREILANDEIER")),
        FoodDictionaryEntry("Rinderhackfleisch", "Fleisch & Fisch", listOf("Hackfleisch", "Hack"), listOf("HACKFLEISCH", "HACK")),
        FoodDictionaryEntry("Hähnchenbrustfilet", "Fleisch & Fisch", listOf("Hähnchen", "Haehnchen", "Geflügel"), listOf("HAEHNCHEN", "GEFLUEGEL")),
        FoodDictionaryEntry("Salami", "Fleisch & Fisch", listOf("Schinkensalami"), listOf("SALAMI", "SCHINKENSALAMI")),
        FoodDictionaryEntry("Kochschinken", "Fleisch & Fisch", listOf("Schinken", "Hinterschinken"), listOf("SCHINKEN", "KOCHSCHINKEN")),
        FoodDictionaryEntry("Lachsfilet", "Fleisch & Fisch", listOf("Lachs", "Räucherlachs"), listOf("LACHS", "LACHSFILET")),
        FoodDictionaryEntry("Baguette", "Brot & Backwaren", listOf("Steinofenbaguette", "Brötchen"), listOf("BAGUETTE", "BROETCHEN")),
        FoodDictionaryEntry("Vollkornbrot", "Brot & Backwaren", listOf("Brot", "Toastbrot", "Toast"), listOf("BROT", "TOAST")),
        FoodDictionaryEntry("Äpfel", "Obst & Gemüse", listOf("Apfel", "Braeburn"), listOf("AEPFEL", "APFEL")),
        FoodDictionaryEntry("Bananen", "Obst & Gemüse", listOf("Banane"), listOf("BANANEN", "BANANE")),
        FoodDictionaryEntry("Tomaten", "Obst & Gemüse", listOf("Tomate", "Rispentomaten"), listOf("TOMATEN", "TOMATE")),
        FoodDictionaryEntry("Salatgurke", "Obst & Gemüse", listOf("Gurke", "Gurken"), listOf("GURKE", "GURKEN")),
        FoodDictionaryEntry("Paprika Mix", "Obst & Gemüse", listOf("Paprika"), listOf("PAPRIKA")),
        FoodDictionaryEntry("Möhren", "Obst & Gemüse", listOf("Karotten", "Möhre"), listOf("MOEHREN", "KAROTTEN")),
        FoodDictionaryEntry("Speisekartoffeln", "Obst & Gemüse", listOf("Kartoffeln", "Kartoffel"), listOf("KARTOFFELN", "KARTOFFEL")),
        FoodDictionaryEntry("Spaghetti", "Vorrat & Konserven", listOf("Nudeln", "Penne", "Pasta"), listOf("SPAGHETTI", "NUDELN")),
        FoodDictionaryEntry("Basmati Reis", "Vorrat & Konserven", listOf("Reis"), listOf("REIS", "BASMATI")),
        FoodDictionaryEntry("Mineralwasser", "Getränke", listOf("Wasser", "Sprudel"), listOf("MINERALWASSER", "WASSER")),
        FoodDictionaryEntry("Pils Bier", "Getränke", listOf("Bier", "Pils", "Radler"), listOf("BIER", "PILS"))
    )

    fun loadDictionaryFromJson(jsonString: String) {
        try {
            val type = object : TypeToken<List<FoodDictionaryEntry>>() {}.type
            val loaded: List<FoodDictionaryEntry> = Gson().fromJson(jsonString, type)
            if (loaded.isNotEmpty()) {
                dictionary = loaded
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadDictionaryFromAssets(context: Context) {
        try {
            val jsonString = context.assets.open("food_dictionary.json").bufferedReader().use { it.readText() }
            loadDictionaryFromJson(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Berechnet die Levenshtein-Distanz zwischen zwei Zeichenketten.
     */
    fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // Deletion
                    dp[i][j - 1] + 1,       // Insertion
                    dp[i - 1][j - 1] + cost // Substitution
                )
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * Normalisiert typische OCR-Zeichenfehler (z.B. '1' -> 'i', '0' -> 'o', 'v' -> 'u').
     */
    fun normalizeOcrChars(input: String): String {
        return input
            .replace('1', 'i')
            .replace('0', 'o')
            .replace('v', 'u')
            .replace('5', 's')
            .replace('8', 'b')
            .trim()
    }

    /**
     * Sucht den besten Wörterbucheintrag mittels Levenshtein-Distanz und OCR-Korrektur.
     */
    fun findBestMatch(rawText: String): FoodDictionaryEntry? {
        val cleaned = cleanSearchTerm(rawText)
        if (cleaned.length < 3) return null

        val normalized = normalizeOcrChars(cleaned)

        var bestEntry: FoodDictionaryEntry? = null
        var minDistance = Int.MAX_VALUE

        val currentDict = if (dictionary.isNotEmpty()) dictionary else fallbackDictionary

        for (entry in currentDict) {
            val candidates = mutableListOf(entry.name)
            candidates.addAll(entry.synonyms)
            candidates.addAll(entry.abbreviations)

            for (candidate in candidates) {
                val distExact = calculateLevenshteinDistance(cleaned, candidate)
                val distNorm = calculateLevenshteinDistance(normalized, normalizeOcrChars(candidate))
                val dist = minOf(distExact, distNorm)

                val maxAllowedDistance = when {
                    candidate.length <= 4 -> 1
                    candidate.length <= 8 -> 2
                    else -> 3
                }

                if (cleaned.equals(candidate, ignoreCase = true) || normalized.equals(normalizeOcrChars(candidate), ignoreCase = true)) {
                    return entry
                }

                if (dist <= maxAllowedDistance && dist < minDistance) {
                    minDistance = dist
                    bestEntry = entry
                }
            }
        }

        return bestEntry
    }

    /**
     * Korrigiert einen OCR-Namen mit Fuzzy Matching ("M1lch" -> "Vollmilch", "Bvtter" -> "Butter").
     */
    fun correctNameWithFuzzyMatching(rawName: String): String {
        val match = findBestMatch(rawName)
        return match?.name ?: cleanSearchTerm(rawName)
    }

    /**
     * 4. Harte Map für Kürzel-Übersetzung.
     */
    private val HARD_TRANSLATIONS = mapOf(
        "BAUTZ." to "",
        "PRES." to "",
        "ES." to "",
        "Kbb Lachsfil." to "Lachsfilet",
        "Kbb Lachsfil" to "Lachsfilet",
        "Fin. Hähnchenbrust C" to "Hähnchenbrust",
        "Fin. Hähnchenbrust" to "Hähnchenbrust",
        "Fin. Hähnchenbr" to "Hähnchenbrust",
        "Fin.hähnchenbr" to "Hähnchenbrust",
        "KLOSSTEIG 750 G" to "Kloßteig",
        "KLOSSTEIG" to "Kloßteig",
        "KLOßTEIG" to "Kloßteig",
        "Kart.vfk 2,5kg" to "Kartoffeln",
        "Kart.vfk" to "Kartoffeln",
        "K.Blattspinat" to "Blattspinat",
        "Hä-Geschnetzeltes" to "Hähnchen-Geschnetzeltes",
        "Harzbube Edelschi." to "Harzer Käse",
        "Gefl." to "Geflügel",
        "KLC" to "K-Classic",
        "Edelschi." to "Edelschimmel",
        "Kn. Fixe" to "Knorr Fix",
        "TH.WQ." to "Thüringer Waldquell",
        "Gut&G." to "Gut & Günstig"
    )

    /**
     * Regex-Engine in cleanReceiptText(rawText: String) zur drastischen Verbesserung der API-Suchergebnisse:
     * 1. Regex für EAN-Zeilen (#...), Preise & Steuerkürzel (z.B. "1,39 2", "2,49 2", "1,99 B", "0,49 A", "1,29") am Ende entfernen.
     * 2. Regex für Prozente, Mengen & Multiplikatoren entfernen (z.B. "1,5%", "2 *", "5x", "3x" am Anfang oder mittig).
     * 3. Regex für Einheiten entfernen (z.B. "Kg", "g", "ml", "L", "Stück", "St.").
     * 4. Harte Map für Kürzel-Übersetzung anwenden ("Gefl." -> "Geflügel", "KLC" -> "K-Classic", Globus-Kürzel).
     * 5. Bereinigung: Sonderzeichen (-, *, +) durch Leerzeichen ersetzen und trimmen.
     */
    fun cleanReceiptText(rawText: String): String {
        var text = rawText

        // 0. EAN/Barcode-Zeilen (#4000582188093) entfernen
        text = text.replace(Regex("""^#\d+.*""", RegexOption.IGNORE_CASE), "")
                   .replace(Regex("""#\d+"""), "")

        // 1. Regex für Preise & Steuerkürzel/Zeilenend-Kennzeichen entfernen (z.B. "1,99 B", "1,39 2", "2,49 2", "0,49 A", "1,29")
        text = text.replace(Regex("""\s+\d+[,.]\d{2}\s*[A-Za-z0-9*#€]?\s*$"""), "")
                   .replace(Regex("""\d+[,.]\d{2}\s*€?"""), "")
                   .replace(Regex("""\s+[AB12*#€0-9]\s*$"""), "")

        // 2. Regex für Prozente, Mengen & Multiplikatoren (z.B. "1,5%", "2 *", "5x", "3x")
        text = text.replace(Regex("""\b\d+([.,]\d+)?%\b"""), "")
                   .replace(Regex("""^\s*\d+\s*[*xX]\s*""", RegexOption.IGNORE_CASE), "")
                   .replace(Regex("""\b\d+\s*[*xX]\b""", RegexOption.IGNORE_CASE), "")

        // 3. Regex für Einheiten entfernen ("Kg", "g", "ml", "L", "Stück", "St.") & führendes K.
        text = text.replace(Regex("""\b\d+([.,]\d+)?\s*(kg|g|ml|l|stück|stk|st)\b""", RegexOption.IGNORE_CASE), "")
                   .replace(Regex("""\b(kg|g|ml|l|stück|stk|st)\b""", RegexOption.IGNORE_CASE), "")
                   .replace(Regex("""^K[.-]\s*""", RegexOption.IGNORE_CASE), "")

        // 4. Harte Map für Kürzel-Übersetzung
        for ((key, value) in HARD_TRANSLATIONS) {
            text = text.replace(key, value, ignoreCase = true)
        }

        // Zahlen- und Grammanhänge am Zeilenende ("Tomaten 425", "Katzenfutter 5") entfernen
        text = text.replace(Regex("""\s+\d+$"""), "")

        // 5. Sonderzeichen (-, *, +) durch Leerzeichen ersetzen und .trim()
        return text.replace(Regex("""[-*+]"""), " ")
                   .replace(Regex("""\s+"""), " ")
                   .trim()
    }

    @Deprecated("Use cleanReceiptText instead", ReplaceWith("cleanReceiptText(rawName)"))
    fun cleanSearchTerm(rawName: String): String = cleanReceiptText(rawName)

    fun prepareForImport(products: List<Product>): List<Product> = products
        .asSequence()
        .filter { product ->
            val cleanNameUpper = product.name.uppercase().trim()
            val isGarbage = cleanNameUpper.matches(Regex("""^DE\d+.*""", RegexOption.IGNORE_CASE)) ||
                cleanNameUpper.matches(Regex("""^UST[-.\s]*ID.*""", RegexOption.IGNORE_CASE)) ||
                cleanNameUpper.matches(Regex("""^ST[-.\s]*NR.*""", RegexOption.IGNORE_CASE)) ||
                cleanNameUpper.matches(Regex("""^TSE.*""", RegexOption.IGNORE_CASE)) ||
                cleanNameUpper.matches(Regex("""^BELEG.*""", RegexOption.IGNORE_CASE)) ||
                cleanNameUpper.matches(Regex("""^KASSE.*""", RegexOption.IGNORE_CASE)) ||
                cleanNameUpper.matches(Regex("""^BON\d+.*""", RegexOption.IGNORE_CASE)) ||
                cleanNameUpper.matches(Regex("""^NR\.?\s*\d+.*""", RegexOption.IGNORE_CASE)) ||
                cleanNameUpper.matches(Regex("""^PREIS.*""", RegexOption.IGNORE_CASE)) ||
                cleanNameUpper.matches(Regex("""^\d{5}\s+[A-ZÄÖÜß-]+$""")) || // PLZ + Ort z.B. 99425 Weimar
                cleanNameUpper.matches(Regex("""^\d+\s*(KG|G|ML|L|STÜCK|STK|ST)$""")) ||
                cleanNameUpper.contains("PFANDARTIKEL") ||
                cleanNameUpper.contains("PFAND") || cleanNameUpper.contains("LEERGUT") ||
                cleanNameUpper.contains("TEL.") || cleanNameUpper.contains("TEL:") || cleanNameUpper.contains("TEL ") ||
                cleanNameUpper.startsWith("TEL") || cleanNameUpper.matches(Regex("""^0\d{3,5}[/-]?\d+.*""")) || // Telefonnummer z.B. 03641/46440
                cleanNameUpper.contains("GMBH") || cleanNameUpper.contains("FILIALE") ||
                cleanNameUpper.contains("STRASSE") || cleanNameUpper.contains("STR.") ||
                cleanNameUpper.contains("FAX") || cleanNameUpper.contains("K CARD") ||
                cleanNameUpper.contains("RABATT") || cleanNameUpper.contains("KARTENZAHLUNG") ||
                cleanNameUpper.contains("GUTSCHRIFT") || cleanNameUpper.contains("STEUER") ||
                cleanNameUpper.contains("BRUTTO") || cleanNameUpper == "EUR" ||
                cleanNameUpper == "SUMME" || cleanNameUpper == "WEIMAR"

            product.name.length >= 3 &&
                product.name != "___IGNORE___" &&
                product.price in 0.01..399.99 &&
                !isGarbage
        }
        .map { product ->
            var safeQty = product.quantity
            if (safeQty >= 99 || safeQty <= 0) safeQty = 1

            val trimmedName = product.name.trim()
            val marketMatch = MarketDictionaryHelper.findBestMatch(trimmedName)
            val dictionaryMatch = findBestMatch(trimmedName)
            val isAlreadyClean = trimmedName.lowercase() in setOf("milch", "butter", "käse", "brot", "eier", "reis", "wasser", "bier", "gurke", "tomaten", "äpfel", "bananen")
            val finalName = when {
                marketMatch != null -> marketMatch.clean_name
                isAlreadyClean -> trimmedName
                dictionaryMatch != null && dictionaryMatch.name.equals(trimmedName, ignoreCase = true) -> dictionaryMatch.name
                else -> trimmedName
            }

            product.copy(
                name = finalName,
                quantity = safeQty,
                purchaseDate = System.currentTimeMillis(),
                category = marketMatch?.category ?: product.category,
                defaultStorage = marketMatch?.default_storage,
                expiryDays = marketMatch?.default_shelf_life_days
            )
        }
        .groupBy { product ->
            product.name.lowercase() to String.format(Locale.US, "%.2f", product.price)
        }
        .values
        .map { matchingProducts ->
            val product = matchingProducts.first()
            val sumQty = matchingProducts.sumOf { it.quantity }.coerceAtMost(99)
            product.copy(quantity = sumQty)
        }
}

/**
 * Multi-API Fallback-Logik für Produktbilder (z.B. für loses Obst/Gemüse).
 */
object ImageSearchFallback {
    private val GENERIC_FOOD_IMAGES = mapOf(
        "apfel" to "https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=400",
        "äpfel" to "https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=400",
        "banane" to "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=400",
        "bananen" to "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=400",
        "tomate" to "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=400",
        "tomaten" to "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=400",
        "gurke" to "https://images.unsplash.com/photo-1449300079323-02e209d9d3a6?w=400",
        "kartoffel" to "https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=400",
        "kartoffeln" to "https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=400",
        "möhre" to "https://images.unsplash.com/photo-1598170845058-12ef4a457511?w=400",
        "karotte" to "https://images.unsplash.com/photo-1598170845058-12ef4a457511?w=400",
        "paprika" to "https://images.unsplash.com/photo-1563565375-f3fdfdbefa83?w=400",
        "fleischwurst" to "https://images.unsplash.com/photo-1588168333986-5078d3ae3976?w=400",
        "milch" to "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400",
        "käse" to "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d?w=400",
        "brot" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400",
        "eier" to "https://images.unsplash.com/photo-1582722872445-44dc5f7e3c8f?w=400"
    )

    fun getFallbackImageUrl(productName: String): String {
        val lower = productName.lowercase()
        for ((key, url) in GENERIC_FOOD_IMAGES) {
            if (lower.contains(key)) return url
        }
        return "https://images.unsplash.com/photo-1542838132-92c53300491e?w=400"
    }
}
