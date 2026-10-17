package com.example.myapplication

import android.content.Context
import com.example.myapplication.data.Product
import com.google.mlkit.vision.text.Text
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

object KassenzettelParser {

    private val gson = Gson()
    private var supermarketProfiles: List<SupermarketProfile> = emptyList()

    private val PRICE_REGEX = Regex("""(-?\d+[,.]\d{2})""")
    private val TAX_SUFFIX_REGEX = Regex("""\s+[AB12*#€]\b""")
    private val MULTIPLIER_REGEX = Regex("""^(\d+)\s*[*xX]\s*(\d+[,.]\d{2})""")

    // Aldi: "8 x 0,89 € ASTRA URTYP DOSE 7,12 € 2"
    private val ALDI_MULT_REGEX = Regex(
        """^(\d+)\s*[xX*]\s*(\d+[,.]\d{2})\s*€?\s+(.*?)\s+(-?\d+[,.]\d{2})\s*(?:€\s*)?[AB12]?$"""
    )

    // Lidl: "Catsan Katzenstreu 8,99 X 2 17,98 B"
    private val LIDL_MULT_REGEX = Regex(
        """^(.*?)\s+(\d+[,.]\d{2})\s*[xX*]\s*(\d+)\s+(-?\d+[,.]\d{2})\s*[AB12]?$"""
    )

    // Standard: "Name 1,29 B" oder "Name 0,85 2"
    private val STANDARD_LINE_REGEX = Regex(
        """^(.*?)\s+(-?\d+[,.]\d{2})\s*(?:€\s*)?[AB12]?$"""
    )

    private val FOOTER_STOP_WORDS = setOf(
        "SUMME", "GESAMT", "ZU ZAHLEN", "TOTAL", "KARTENZAHLUNG",
        "BARGELD", "GEG. BAR", "RÜCKGELD", "ZAHLBETRAG", "GUTHABEN",
        "STEUER %", "KUNDENBELEG", "TERMINAL", "VISA", "GIROCARD",
        "MASTERCARD", "TSE-SIGNATUR", "PAYBACK", "LIDL PAY", "EC-CASH"
    )

    // KOPF- UND WÄHRUNGSMÜLL FILTERN (inklusive "EUR" und "PREIS")
    private val IGNORE_KEYWORDS = setOf(
        "PFANDWERT", "PFAND", "LEERGUT", "PAPIERTRAGETASCHE", "KNOTENBEUTEL",
        "BELEGKOPIE", "BONKOPIE", "HERZLICH WILLKOMMEN", "VIELEN DANK",
        "KAUFLAND", "LIDL", "REWE", "NAHKAUF", "ALDI", "DM-DROGERIE",
        "STRASSE", "STR.", "WEIMAR", "JENA", "ERFURT", "GMBH", "UST-ID", "DE1", "DE2",
        "EUR", "PREIS EUR", "PREIS", "SUMME"
    )

    // Erweitertes Marken- & Artikel-Lexikon für Thüringen
    private val BRAND_DATABASE = mapOf(
        "TH.WQ. MEDIUM" to "Thüringer Waldquell Medium",
        "TH.WQ. CLASSIC" to "Thüringer Waldquell Classic",
        "TH.WQ." to "Thüringer Waldquell",
        "PERF.FIT SENIOR" to "Katzenfutter Senior",
        "PERF.FIT" to "Perfect Fit Katzenfutter",
        "ES. BLUSE" to "Esmara Bluse",
        "SÖHNLEIN WHITE ICE" to "Söhnlein White Ice",
        "APEROL APERITIVO" to "Aperol Aperitivo",
        "K.BLATTSPINAT" to "K-Classic Blattspinat",
        "KUSCHELWEICH" to "Kuschelweich Weichspüler",
        "PROTEIN KÄSE MILD" to "Protein Käse mild",
        "BAUTZ.SENF MS" to "Bautz'ner Senf mittelscharf",
        "PRES. LA BRIQUE" to "Président La Brique Weichkäse",
        "SCHEIBENKL. APFEL" to "Scheibenklar Apfel Scheibenreiniger",
        "GOURMET PERLE" to "Gourmet Perle Katzenfutter",
        "HÄ-GESCHNETZELTES" to "Hähnchen-Geschnetzeltes",
        "GEFL.FLEISCHWURST" to "Geflügelfleischwurst",
        "EDELSCHI." to "Edelschimmelkäse",
        "BENS EXPRESSREIS" to "Ben's Original Expressreis",
        "DELV.SPAGHETTI" to "Delverde Spaghetti",
        "KLC GEH. TOM." to "K-Classic Gehackte Tomaten",
        "KLC.AMER.COOKIES" to "K-Classic American Cookies",
        "ROTW. WEISSB." to "Rotwein Weißburgunder",
        "KN.FIX" to "Knorr Fix",
        "FOL EPI LEGERE" to "Fol Epi Légère",
        "CHURRO BITES" to "Churro Bites",
        "K.NATURJOGHURT" to "K-Classic Naturjoghurt",
        "ÜLTJE ERDNÜSSE" to "Ültje Erdnüsse",
        "WAGNER RUSTIPANI" to "Wagner Rustipani",
        "DBIO" to "dmBio",
        "DABIO" to "dmBio",
        "DEBIO" to "dmBio",
        "DEIN BESTES" to "Dein Bestes",
        "HACKF. GEM." to "Hackfleisch gemischt",
        "LANDLEBERWURST" to "Landleberwurst",
        "ASTRA URTYP" to "Astra Urtyp",
        "MORTADELLA PAPRI" to "Mortadella Paprika",
        "GEWUERZSPEKULAT." to "Gewürzspekulatius",
        "HIO EIER OKT" to "Bio Eier 10er",
        "KÄSESCHEIBENBUTTERK." to "Butterkäse Scheiben",
        "LIGHT ROHSCHINKEN" to "Rohschinken Light",
        "CATSAN" to "Catsan Katzenstreu",
        "K-CLASSIC" to "K-Classic",
        "KLC" to "K-Classic",
        "GUT&G" to "Gut & Günstig",
        "G&G" to "Gut & Günstig",
        "CORNEDBEEF" to "Corned Beef",
        "CORNED BEEF" to "Corned Beef",
        "STREICHMETTW." to "Streichmettwurst",
        "TEEWURST" to "Teewurst",
        "WIENER" to "Wiener Würstchen"
    )

    data class SupermarketProfile(
        val name: String,
        val trigger: List<String>,
        val stopWords: List<String>,
        val ignorePatterns: List<String>,
        val priceRegex: String
    )

    fun loadProfiles(context: Context) {
        try {
            val json = context.assets.open("supermarkets.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<SupermarketProfile>>() {}.type
            supermarketProfiles = gson.fromJson(json, type)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun parseReceipt(visionText: Text, corrections: Map<String, String> = emptyMap()): List<Product> {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) return emptyList()

        val sortedLines = allLines.sortedBy { it.boundingBox?.top ?: 0 }
        val rows = mutableListOf<MutableList<Text.Line>>()

        val avgHeight = allLines.map { it.boundingBox?.height() ?: 0 }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 20.0
        val threshold = (avgHeight * 0.75).toInt().coerceIn(8, 35)

        for (line in sortedLines) {
            val top = line.boundingBox?.top ?: 0
            val existingRow = rows.find { Math.abs((it.first().boundingBox?.top ?: 0) - top) < threshold }
            if (existingRow != null) existingRow.add(line) else rows.add(mutableListOf(line))
        }

        val reconstructedText = rows.joinToString("\n") { row ->
            row.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") { it.text }
        }

        return parseReceiptText(reconstructedText, corrections)
    }

    fun parseReceiptText(text: String, corrections: Map<String, String> = emptyMap()): List<Product> {
        val rawLines = text.lines()
        val upperText = text.uppercase()

        // 1. Kopfzeilen-Extraktion (Händler & Datum)
        val supermarket = detectSupermarket(upperText)
        val purchaseDate = extractDate(text)

        // 2. Zuverlässige Kaufland-Erkennung
        val isKaufland = supermarket == "Kaufland" || upperText.contains("K CARD") || upperText.contains("PREIS EUR")

        val products = if (isKaufland) {
            parseKauflandInterleaved(rawLines, corrections)
        } else {
            parseStandardReceipt(rawLines, corrections)
        }

        // Metadaten an Produkte hängen
        return products.map { it.copy(supermarket = supermarket, purchaseDate = purchaseDate) }
    }

    private fun detectSupermarket(text: String): String? {
        if (text.contains("KAUFLAND")) return "Kaufland"
        if (text.contains("LIDL")) return "Lidl"
        if (text.contains("REWE")) return "Rewe"
        if (text.contains("ALDI")) return "Aldi"
        if (text.contains("PENNY")) return "Penny"
        if (text.contains("NETTO")) return "Netto"
        if (text.contains("EDEKA")) return "Edeka"
        if (text.contains("DM-DROGERIE") || text.contains(" DM ")) return "dm"
        if (text.contains("ROSSMANN")) return "Rossmann"
        if (text.contains("GLOBUS")) return "Globus"
        return supermarketProfiles.find { p -> p.trigger.any { text.contains(it, ignoreCase = true) } }?.name
    }

    private fun extractDate(text: String): Long {
        val dateRegex = Regex("""(\d{2})[./](\d{2})[./](\d{2,4})""")
        val match = dateRegex.find(text)
        return if (match != null) {
            try {
                val day = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt() - 1
                var year = match.groupValues[3].toInt()
                if (year < 100) year += 2000
                val cal = Calendar.getInstance()
                cal.set(year, month, day, 12, 0)
                cal.timeInMillis
            } catch (e: Exception) { System.currentTimeMillis() }
        } else System.currentTimeMillis()
    }

    private fun parseKauflandInterleaved(rawLines: List<String>, corrections: Map<String, String>): List<Product> {
        val items = mutableListOf<Product>()
        var pendingName: String? = null
        var pendingQty = 1
        val supermarket = "Kaufland"

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val upper = line.uppercase()

            // 1. Abbruch bei Kassenbereich
            if (FOOTER_STOP_WORDS.any { upper.startsWith(it) || upper.contains("SUMME ") || upper.contains("KARTENZAHLUNG") }) {
                break
            }

            // 2. Kopf- und Stördaten ignorieren
            if (isNoise(upper) && !upper.contains("RABATT") && !line.startsWith("-")) {
                continue
            }

            // 3. Rabatte (z. B. "K Card XTRA Rabatt -5,00")
            if (upper.contains("RABATT") || upper.contains("SPAREN") || line.startsWith("-")) {
                val discountMatch = PRICE_REGEX.find(line)
                if (discountMatch != null && items.isNotEmpty()) {
                    val discountStr = discountMatch.groupValues[1].replace(',', '.')
                    val discount = Math.abs(discountStr.toDoubleOrNull() ?: 0.0)
                    val last = items.last()
                    items[items.lastIndex] = last.copy(
                        price = Math.max(0.01, Math.round((last.price - discount) * 100.0) / 100.0)
                    )
                }
                continue
            }

            // 4. Multiplikator (z. B. "2 * 4,99" oder "3 * 0,65")
            val multMatch = MULTIPLIER_REGEX.find(line)
            if (multMatch != null) {
                pendingQty = multMatch.groupValues[1].toIntOrNull() ?: 1
                val unitPriceStr = multMatch.groupValues[2].replace(',', '.')
                val unitPrice = unitPriceStr.toDoubleOrNull() ?: 0.0
                val lineNoMult = line.substring(multMatch.range.last + 1).trim()
                val priceMatch = PRICE_REGEX.find(lineNoMult)

                // Steht auf derselben Zeile bereits der Gesamtpreis?
                if (priceMatch != null && pendingName != null) {
                    val totalPriceStr = priceMatch.groupValues[1].replace(',', '.')
                    val totalPrice = Math.abs(totalPriceStr.toDoubleOrNull() ?: (pendingQty * unitPrice))
                    addProductSafely(pendingName, totalPrice, pendingQty, items, corrections, supermarket)
                    pendingName = null
                    pendingQty = 1
                }
                continue
            }

            // 5. Preiszeile (z. B. "9,98 A" oder "2,39 B Kuschelweich")
            val priceMatch = PRICE_REGEX.find(line)
            if (priceMatch != null) {
                val priceStr = priceMatch.groupValues[1].replace(',', '.')
                val priceVal = Math.abs(priceStr.toDoubleOrNull() ?: 0.0)

                // 5a. Preis dem wartenden Artikel mit der gemerkten Stückzahl zuweisen
                if (pendingName != null && priceVal > 0.05) {
                    addProductSafely(pendingName, priceVal, pendingQty, items, corrections, supermarket)
                    pendingName = null
                    pendingQty = 1
                }

                // 5b. Prüfen, ob dahinter der nächste Artikelname steht
                var nextArticle = line.replace(priceMatch.groupValues[0], "")
                nextArticle = TAX_SUFFIX_REGEX.replace(nextArticle, "")
                nextArticle = nextArticle.replace(Regex("""^[-*#\s.+]+"""), "").trim()

                if (nextArticle.length >= 3 && !isNoise(nextArticle.uppercase())) {
                    pendingName = nextArticle
                }
            } else if (line.length >= 3 && !isNoise(upper)) {
                pendingName = line
            }
        }

        return bundleIdenticalProducts(items)
    }

    private fun parseStandardReceipt(rawLines: List<String>, corrections: Map<String, String>): List<Product> {
        val items = mutableListOf<Product>()
        val textSnippet = rawLines.take(5).joinToString(" ")
        val supermarket = supermarketProfiles.find { p -> 
            p.trigger.any { textSnippet.contains(it, ignoreCase = true) } 
        }?.name

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val upper = line.uppercase()

            if (FOOTER_STOP_WORDS.any { upper.startsWith(it) || upper.contains("SUMME ") || upper.contains("ZU ZAHLEN") }) {
                break
            }

            if (isNoise(upper) && !ALDI_MULT_REGEX.matches(line)) {
                if (upper.contains("RABATT") || upper.contains("PREISVORTEIL")) {
                    applyDiscountToLastItem(line, items)
                }
                continue
            }

            if (line.contains("kg x", ignoreCase = true) || line.contains("EUR/kg", ignoreCase = true)) {
                continue
            }

            // Aldi
            val aldiMatch = ALDI_MULT_REGEX.matchEntire(line)
            if (aldiMatch != null) {
                val qty = aldiMatch.groupValues[1].toIntOrNull() ?: 1
                val rawName = aldiMatch.groupValues[3].trim()
                val priceStr = aldiMatch.groupValues[4].replace(',', '.')
                val price = priceStr.toDoubleOrNull() ?: 0.0
                addProductSafely(rawName, price, qty, items, corrections, supermarket)
                continue
            }

            // Lidl
            val lidlMatch = LIDL_MULT_REGEX.matchEntire(line)
            if (lidlMatch != null) {
                val rawName = lidlMatch.groupValues[1].trim()
                val qty = lidlMatch.groupValues[3].toIntOrNull() ?: 1
                val priceStr = lidlMatch.groupValues[4].replace(',', '.')
                val price = priceStr.toDoubleOrNull() ?: 0.0
                addProductSafely(rawName, price, qty, items, corrections, supermarket)
                continue
            }

            // Standard (dm / Nahkauf / Rewe)
            val stdMatch = STANDARD_LINE_REGEX.matchEntire(line)
            if (stdMatch != null) {
                val rawName = stdMatch.groupValues[1].trim()
                val priceStr = stdMatch.groupValues[2].replace(',', '.')
                val price = priceStr.toDoubleOrNull() ?: 0.0
                addProductSafely(rawName, price, 1, items, corrections, supermarket)
                continue
            }

            if (line.startsWith("-") || upper.contains("RABATT")) {
                applyDiscountToLastItem(line, items)
            }
        }

        return bundleIdenticalProducts(items)
    }

    private fun isNoise(upper: String): Boolean {
        if (upper == "EUR" || upper == "PREIS EUR" || upper == "PREIS" || upper == "LEERGUT" || upper.startsWith("PFAND")) return true
        return IGNORE_KEYWORDS.any { upper == it || upper.startsWith("$it ") || upper.contains(" $it ") }
    }

    private fun addProductSafely(
        rawName: String,
        price: Double,
        qty: Int,
        items: MutableList<Product>,
        corrections: Map<String, String>,
        supermarket: String? = null
    ) {
        if (price <= 0.05 || price > 250.0) return

        // Wenn der Name nur aus einer Menge besteht (z.B. "2 Stk x"), ist es kein echter Artikelname
        val cleanName = rawName.trim()
        if (cleanName.matches(Regex("""\d+\s*Stk.*""", RegexOption.IGNORE_CASE))) return
        if (cleanName.length < 2) return

        var clean = cleanName
            .replace(Regex("""^[-*#\s.+]+"""), "")
            .replace(Regex("""\s+[-*#\s.+]+$"""), "")
            .replace(Regex("""\s+-QS\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+\d+$"""), "") // Schneidet angehängte Multiplikator-Ziffern wie die " 3" ab
            .trim()

        val cleanUpper = clean.uppercase()
        // Absicherung: EUR oder reine Währungen niemals als Artikel anlegen!
        if (cleanUpper == "EUR" || cleanUpper == "PREIS EUR" || cleanUpper == "PREIS" || cleanUpper.length < 3) return
        if (clean.count { it.isLetter() } < 2) return

        // Marken- & Wörterbuch-Ersetzung
        for ((shortBrand, fullBrand) in BRAND_DATABASE) {
            clean = clean.replace(Regex("""\b${Regex.escape(shortBrand)}\b""", RegexOption.IGNORE_CASE), fullBrand)
        }

        val finalName = applyFuzzyCorrections(clean, corrections)

        // Intelligente Mengen- & Einheiten-Erkennung
        var finalQty = qty
        var finalUnit = "Stk."
        
        val finalNameLower = finalName.lowercase()
        
        if (finalNameLower.contains("waldquell") || finalNameLower.contains("wasser") || finalNameLower.contains("medium") || finalNameLower.contains("classic")) {
            if (price >= 3.00) {
                finalUnit = "Kiste"
                if (price >= 15.00) finalQty = 2
                else if (price >= 7.00) finalQty = 1
            }
        } else if (finalNameLower.contains("wurst") || finalNameLower.contains("aufschnitt") || finalNameLower.contains("schinken") || finalNameLower.contains("salami")) {
            finalUnit = "Packung"
        } else if (finalNameLower.contains("bund")) {
            finalUnit = "Bund"
        } else if (finalNameLower.contains("kiste")) {
            finalUnit = "Kiste"
        }

        items.add(
            Product(
                name = finalName,
                price = Math.round(price * 100.0) / 100.0,
                quantity = finalQty,
                unit = finalUnit,
                rawText = rawName,
                supermarket = supermarket,
                purchaseDate = System.currentTimeMillis()
            )
        )
    }

    private fun applyDiscountToLastItem(line: String, items: MutableList<Product>) {
        val match = PRICE_REGEX.find(line)
        if (match != null && items.isNotEmpty()) {
            val discountStr = match.groupValues[1].replace(',', '.')
            val discount = Math.abs(discountStr.toDoubleOrNull() ?: 0.0)
            val last = items.last()
            items[items.lastIndex] = last.copy(
                price = Math.max(0.01, Math.round((last.price - discount) * 100.0) / 100.0)
            )
        }
    }

    fun calculateSimilarity(s1: String, s2: String): Double {
        val a = s1.lowercase().trim()
        val b = s2.lowercase().trim()
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        val maxLen = maxOf(a.length, b.length)
        return 1.0 - (dp[a.length][b.length].toDouble() / maxLen)
    }

    private fun applyFuzzyCorrections(name: String, corrections: Map<String, String>): String {
        corrections[name]?.let { return it }

        var bestMatch: String? = null
        var highestSimilarity = 0.75

        for ((rawLearned, targetName) in corrections) {
            val sim = calculateSimilarity(name, rawLearned)
            if (sim > highestSimilarity) {
                highestSimilarity = sim
                bestMatch = targetName
            }
        }
        return bestMatch ?: name
    }

    private fun bundleIdenticalProducts(items: List<Product>): List<Product> {
        val grouped = items.groupBy { "${it.name.lowercase()}_${String.format(Locale.US, "%.2f", it.price / it.quantity)}" }
        return grouped.map { entry ->
            val first = entry.value.first()
            val totalQty = entry.value.sumOf { it.quantity }
            val totalPrice = entry.value.sumOf { it.price }
            first.copy(
                quantity = totalQty,
                price = Math.round(totalPrice * 100.0) / 100.0
            )
        }
    }

    fun validateChecksum(products: List<Product>, totalOnReceipt: Double): Boolean {
        val sum = products.sumOf { it.price }
        return Math.abs(sum - totalOnReceipt) < 0.01
    }
}
