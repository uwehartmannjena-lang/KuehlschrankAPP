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
     * Bereinigt Kassenbon-Kürzel und Mengenangaben für eine höhere Trefferquote bei OpenFoodFacts.
     */
    fun cleanSearchTerm(rawName: String): String {
        return rawName
            .replace(Regex("""\s+[AB12*#€]\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bK\.""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bKLC\.""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bTH\.WQ\.?""", RegexOption.IGNORE_CASE), "Thüringer Waldquell")
            .replace(Regex("""\bWaldqüll\b""", RegexOption.IGNORE_CASE), "Waldquell")
            .replace(Regex("""\bPRES\.\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bES\.\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bBAUTZ\.\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\d+([.,]\d+)?\s*(g|kg|ml|l|stk|er)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+\d+[,.]\d{2}\s*€?$"""), "")
            .replace(Regex("""\s+\d{1,2}\s*$"""), "")
            .replace(Regex("""[,.]\d{2}\s*$"""), "")
            .replace(Regex("""[*#_]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun prepareForImport(products: List<Product>): List<Product> = products
        .asSequence()
        .map { product ->
            var safeQty = product.quantity
            if (safeQty >= 99 || safeQty <= 0) safeQty = 1

            val fuzzyMatch = findBestMatch(product.name)
            val finalName = fuzzyMatch?.name ?: product.name.trim()

            product.copy(
                name = finalName,
                quantity = safeQty
            )
        }
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
