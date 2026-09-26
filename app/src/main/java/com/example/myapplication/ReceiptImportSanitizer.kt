package com.example.myapplication

import android.content.Context
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.Product
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    private val learnedCorrections: MutableMap<String, String> = HashMap()

    fun loadLearnedCorrections(context: Context) {
        try {
            val db = AppDatabase.getDatabase(context)
            runBlocking(Dispatchers.IO) {
                val list = db.fridgeItemDao().getAllUserLearnedCorrectionsSync()
                for (item in list) {
                    learnedCorrections[item.receiptRawText.lowercase().trim()] = item.correctedName
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setLearnedCorrections(map: Map<String, String>) {
        learnedCorrections.clear()
        for ((k, v) in map) {
            learnedCorrections[k.lowercase().trim()] = v
        }
    }

    fun addLearnedCorrection(rawText: String, correctedName: String) {
        if (rawText.isNotBlank() && correctedName.isNotBlank()) {
            learnedCorrections[rawText.lowercase().trim()] = correctedName
        }
    }

    fun getLearnedCorrection(rawText: String): String? {
        if (rawText.isBlank()) return null
        return learnedCorrections[rawText.lowercase().trim()]
    }

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
            loadLearnedCorrections(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }

    fun normalizeOcrChars(input: String): String {
        return input
            .replace('1', 'i')
            .replace('0', 'o')
            .replace('v', 'u')
            .replace('5', 's')
            .replace('8', 'b')
            .trim()
    }

    fun toTitleCase(input: String): String {
        if (input.isBlank()) return ""
        return input.trim()
            .lowercase(Locale.GERMAN)
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() && (it.length > 1 || it.matches(Regex("""[a-zäöüß0-9]"""))) }
            .joinToString(" ") { word ->
                if (word.contains("-")) {
                    word.split("-").joinToString("-") { part ->
                        part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMAN) else it.toString() }
                    }
                } else {
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMAN) else it.toString() }
                }
            }
    }

    fun findBestMatch(rawText: String): FoodDictionaryEntry? {
        val learned = getLearnedCorrection(rawText)
        if (learned != null) {
            return FoodDictionaryEntry(learned)
        }

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

    fun correctNameWithFuzzyMatching(rawName: String): String {
        val learned = getLearnedCorrection(rawName)
        if (learned != null) return learned

        val match = findBestMatch(rawName)
        if (match != null) return match.name
        val cleaned = cleanSearchTerm(rawName)
        return toTitleCase(cleaned)
    }

    private val HARD_TRANSLATIONS = mapOf(
        "exsreis" to "Expressreis",
        "arottenkrüstchen" to "Karottenkrüstchen",
        "arottenkruestchen" to "Karottenkrüstchen",
        "gewürzs" to "Gewürzgurken",
        "gewuerzs" to "Gewürzgurken",
        "erdn gerös ges" to "Erdnüsse geröstet",
        "erdn geros ges" to "Erdnüsse geröstet",
        "schww schinken" to "Schinken",
        "h brustfilet" to "Hähnchen-Brustfilet",
        "herzhaf" to "Herzhaft",
        "puten-lachsschinke n" to "Puten-Lachsschinken",
        "puten lachsschinke n" to "Puten-Lachsschinken",
        "tom pass" to "Passierte Tomaten",
        "tom. pass." to "Passierte Tomaten",
        "tom.pass." to "Passierte Tomaten",
        "schw.tonicw" to "Schweppes Tonic Water",
        "schw. tonicw" to "Schweppes Tonic Water",
        "sw wild berry" to "Schweppes Wild Berry",
        "hxm weissherbst" to "Hex vom Dasenstein Weissherbst",
        "dinkel joh beerrührk" to "Dinkel Johannisbeer Rührkuchen",
        "haus handkäse" to "Hausmacher Handkäse",
        "haus handkaese" to "Hausmacher Handkäse",
        "finesse pfeffer" to "Herta Finesse Pfeffer",
        "fin. pfeffer" to "Herta Finesse Pfeffer",
        "Finesse Hähnchenbr.K" to "Herta Finesse Hähnchenbrust",
        "Finesse Hähnchenbr. K" to "Herta Finesse Hähnchenbrust",
        "Finesse Hähnchenbr." to "Herta Finesse Hähnchenbrust",
        "Finesss Hähnchenbrust" to "Herta Finesse Hähnchenbrust",
        "Finesss Hähnchenbr" to "Herta Finesse Hähnchenbrust",
        "Fin. Hähnchenbrust M" to "Herta Finesse Hähnchenbrust",
        "Fin. Hähnchenbrust C" to "Herta Finesse Hähnchenbrust",
        "Finesse Hähnchenbrust" to "Herta Finesse Hähnchenbrust",
        "Fin. Hähnchenbrust" to "Herta Finesse Hähnchenbrust",
        "Fin. Hähnchenbr" to "Herta Finesse Hähnchenbrust",
        "Fin.hähnchenbr" to "Herta Finesse Hähnchenbrust",
        "Gutfried Hähnchenbrust M" to "Gutfried Hähnchenbrust",
        "Gutfried Hähnchenbrust K" to "Gutfried Hähnchenbrust",
        "Gutfried Hähnchenbrust" to "Gutfried Hähnchenbrust",
        "ja!" to "",
        "ja !" to "",
        "KLC" to "",
        "BAUTZ." to "",
        "BAUTZ" to "",
        "PRES." to "",
        "PRES " to "",
        "ES." to "",
        "Kbb Lachsfil." to "Lachsfilet",
        "Kbb Lachsfil" to "Lachsfilet",
        "KLOSSTEIG 750 G" to "Kloßteig",
        "KLOSSTEIG" to "Kloßteig",
        "KLOßTEIG" to "Kloßteig",
        "Kart.vfk 2,5kg" to "Kartoffeln",
        "Kart.vfk" to "Kartoffeln",
        "K.Blattspinat" to "Blattspinat",
        "Hä-Geschnetzeltes" to "Hähnchen-Geschnetzeltes",
        "Harzbube Edelschi." to "Harzer Käse",
        "Gefl." to "Geflügel",
        "Edelschi." to "Edelschimmel",
        "Kn. Fixe" to "Knorr Fix",
        "TH.WQ." to "Thüringer Waldquell",
        "TH.WQ" to "Thüringer Waldquell",
        "TH WQ" to "Thüringer Waldquell",
        "Gut&G." to "Gut & Günstig",
        "Meg.feinesüssrahm" to "Meggler Feine Süßrahmbutter",
        "Meg.feinesuessrahm" to "Meggler Feine Süßrahmbutter",
        "Wm-chia-krüstchen" to "Weltmeister Chia Krüstchen",
        "Wm-chia-kruestchen" to "Weltmeister Chia Krüstchen"
    )

    fun cleanReceiptText(rawText: String): String {
        var text = rawText

        // 11. Lern-Vorrang: ZUERST UserLearnedCorrections prüfen
        val learned = getLearnedCorrection(text)
        if (learned != null) return learned

        // Mappe Frischetheken- und Waagen-Codes (PLU) auf lesbare Kategorien
        if (text.contains(Regex("""(?i)Metzgerei.*PLU.*"""))) return "Fleisch-/Wurstwaren (Theke)"
        if (text.contains(Regex("""(?i)Käse.*PLU.*"""))) return "Käse (Frischetheke)"
        if (text.contains(Regex("""(?i)Backwaren.*PLU.*"""))) return "Backwaren (Frischetheke)"

        // 1. Zuerst gezielte Übersetzungen für bekannte Kürzel anwenden (z.B. "TH.WQ." -> "Thüringer Waldquell")
        for ((key, value) in HARD_TRANSLATIONS) {
            if (value.isNotBlank() && text.trim().equals(value.trim(), ignoreCase = true)) continue
            text = text.replace(key, value, ignoreCase = true)
        }

        // Müll und typische OCR-Lese-Fehler wie "0UZ" gezielt entfernen sowie Marken-Tippfehler korrigieren
        text = text.replace(Regex("""(?i)\b(0uz|0uz\.|[09][A-Z]{2,})\b"""), " ")
                   .replace(Regex("""(?i)\bfin?ess[es]*\b"""), "Finesse")
                   .replace(Regex("""(?i)\b(?<!Herta\s)Finesse\b"""), "Herta Finesse")
                   .replace(Regex("""(?i)\bHerta\s+Finesse\s+Gutfried\b"""), "Herta Finesse")
                   .replace(Regex("""(?i)\bGutfried\s+Finesse\b"""), "Herta Finesse")

        // Quantitäten & Einheiten VOR dem Ersetzen von Kommas/Punkten entfernen ("1,5l", "1 Mg", "Nat6x" -> "")
        // Inkl. Zahlenfolgen direkt an Wörtern ("Zero1,25" -> "Zero")
        text = text.replace(Regex("""(?i)(\b\d+([.,]\d+)?\s*(kg|g|mg|ml|l|stück|stk|st|x)\b|(?<=[a-zäöüß])\d+([.,]\d+)?\s*(kg|g|mg|ml|l|stück|stk|st|x|€)?\b)"""), " ")

        // 1. Zwingend ALLE Punkte (.), Kommas (,), Bindestriche (-) und Sternchen (*) durch Leerzeichen ersetzen
        text = text.replace(Regex("""[.,\-*]"""), " ")

        text = text.replace(Regex("""^#\d+.*""", RegexOption.IGNORE_CASE), "")
                   .replace(Regex("""#\d+"""), "")

        text = text.replace(Regex("""\s+\d+[,.]\d{2}\s*[A-Za-z0-9*#€]?\s*$"""), "")
                   .replace(Regex("""\d+[,.]\d{2}\s*€?"""), "")
                   .replace(Regex("""\s+[AB12*#€0-9]\s*$"""), "")

        text = text.replace(Regex("""\b\d+([.,]\d+)?%\b"""), "")
                   .replace(Regex("""^\s*\d+\s*[*xX]\s*""", RegexOption.IGNORE_CASE), "")
                   .replace(Regex("""\b\d+\s*[*xX]\b""", RegexOption.IGNORE_CASE), "")

        // 2 & 3. Marken-Präfixe, Kaufland/Globus-Kürzel & Füllwörter/Gewichtsanhängsel aggressiv entfernen
        text = text.replace(Regex("""(?i)\b(ja!|klc|rewe\s*beste\s*wahl|k[- ]?classic|kpur|kfav|\bk\s+|allg\s*büble|allg|büble|purland|spreewh|meg|bautz|dit|fin|pres|möv)\b"""), " ")
                   .replace(Regex("""(?i)\b(xxl|disc|gem|ms\s+l|ger\d+g|\d+g|\d+kg|\d+ml|\d+l)\b"""), " ")

        // Blacklist-Regex: Störende Einzelwörter am Ende ohne sinnvollen Kontext entfernen (Zero, Light, Classic, Premium)
        text = text.replace(Regex("""(?i)\s+\b(zero|light|classic|premium)\b\s*$"""), " ")

        // Verwaiste Einzelbuchstaben am Zeilenende nach einem Wort entfernen (z.B. "schinke n" -> "schinke")
        text = text.replace(Regex("""(?i)(?<=\b[a-zäöüß]{3,})\s+[a-z]\s*$"""), "")

        var cleaned = text.replace(Regex("""[-*+_/\\():;!?#,="'<>]"""), " ")
                          .replace(Regex("""\s+"""), " ")
                          .trim()

        cleaned = cleaned.replace(Regex("""(?i)\bh\s+milch\b"""), "H-Milch")
                         .replace(Regex("""(?i)\bhähnchen\s+brustfilet\b"""), "Hähnchen-Brustfilet")
                         .replace(Regex("""(?i)\bputen\s+lachsschinken\b"""), "Puten-Lachsschinken")

        // 13. Leere Zeilen (< 2 Zeichen) abfangen
        if (cleaned.length < 2) return ""

        return cleaned
    }

    fun intelligentSanitize(input: String): String {
        var text = input.lowercase(Locale.GERMAN)
        
        text = text.replace(Regex("""\b(klc|ja!|ja)\b"""), " ")
        text = text.replace(Regex("""\b\d+([.,]\d+)?\s*(g|kg|ml|l)\b"""), " ")
        text = text.replace(Regex("""[.,*\-]"""), " ")
        
        val stopWords = setOf("der", "die", "das", "mit", "im", "in", "für", "von", "und", "aus", "bei", "den", "dem")
        val words = text.split(Regex("""\s+""")).filter { it.isNotBlank() && it !in stopWords }
        
        return words.joinToString(" ")
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
                cleanNameUpper.matches(Regex("""^\d{5}\s+[A-ZÄÖÜß-]+$""")) ||
                cleanNameUpper.matches(Regex("""^\d+\s*(KG|G|ML|L|STÜCK|STK|ST)$""")) ||
                cleanNameUpper.contains("PFANDARTIKEL") ||
                cleanNameUpper.contains("PFAND") || cleanNameUpper.contains("LEERGUT") ||
                cleanNameUpper.contains("TEL.") || cleanNameUpper.contains("TEL:") || cleanNameUpper.contains("TEL ") ||
                cleanNameUpper.startsWith("TEL") || cleanNameUpper.matches(Regex("""^0\d{3,5}[/-]?\d+.*""")) ||
                cleanNameUpper.contains("GMBH") || cleanNameUpper.contains("FILIALE") ||
                cleanNameUpper.contains("STRASSE") || cleanNameUpper.contains("STR.") ||
                cleanNameUpper.contains("FAX") || cleanNameUpper.contains("K CARD") ||
                cleanNameUpper.contains("RABATT") || cleanNameUpper.contains("KARTENZAHLUNG") ||
                cleanNameUpper.contains("GUTSCHRIFT") || cleanNameUpper.contains("STEUER") ||
                cleanNameUpper.contains("BRUTTO") || cleanNameUpper == "EUR" ||
                cleanNameUpper == "SUMME" || cleanNameUpper == "WEIMAR" || cleanNameUpper == "ISSERSTEDT"

            product.name.length >= 3 &&
                product.name != "___IGNORE___" &&
                product.price in 0.01..399.99 &&
                !isGarbage
        }
        .map { product ->
            var safeQty = product.quantity
            if (safeQty >= 99 || safeQty <= 0) safeQty = 1

            val trimmedName = product.name.trim()
            val learned = getLearnedCorrection(trimmedName) ?: getLearnedCorrection(product.rawText ?: "")
            val marketMatch = MarketDictionaryHelper.findBestMatch(trimmedName)
            val dictionaryMatch = findBestMatch(trimmedName)
            val isAlreadyClean = trimmedName.lowercase() in setOf("milch", "butter", "käse", "brot", "eier", "reis", "wasser", "bier", "gurke", "tomaten", "äpfel", "bananen")

            // 12. Kategorie- & Haltbarkeit-Sicherheit bei Smart-Guess / Lernautomatik
            val finalName = when {
                learned != null -> learned
                marketMatch != null -> marketMatch.clean_name
                isAlreadyClean -> trimmedName
                dictionaryMatch != null && dictionaryMatch.name.equals(trimmedName, ignoreCase = true) -> dictionaryMatch.name
                else -> {
                    val cleaned = cleanReceiptText(trimmedName)
                    val beautyName = toTitleCase(cleaned)
                    if (beautyName.length >= 2) beautyName else toTitleCase(trimmedName)
                }
            }

            var guessedStorage = marketMatch?.default_storage
            if (guessedStorage == null) {
                val lowerName = finalName.lowercase()
                if (lowerName.contains("wurst") || lowerName.contains("käse") || lowerName.contains("milch") || lowerName.contains("joghurt")) {
                    guessedStorage = "Kühlschrank"
                }
            }

            product.copy(
                name = finalName,
                quantity = safeQty,
                purchaseDate = System.currentTimeMillis(),
                category = marketMatch?.category ?: product.category,
                defaultStorage = guessedStorage,
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
