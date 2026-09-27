package com.example.myapplication

import android.content.Context
import com.example.myapplication.data.CategoryDetector
import com.example.myapplication.data.FoodCategory
import com.example.myapplication.data.Product
import com.google.mlkit.vision.text.Text
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*
import kotlin.math.round

object KassenzettelParser {

    private val gson = Gson()
    private var supermarketProfiles: List<SupermarketProfile> = emptyList()

    private val PRICE_REGEX = Regex("""(-?\d+[,.]\d{2})""")
    private val TAX_SUFFIX_REGEX = Regex("""\s+(?:[AB12*#€]\s*|\d\s*)*$""")
    private val MULTIPLIER_REGEX = Regex("""^(\d+(?:[,.]\d+)?)\s*(?:kg|g|l|ml|stk|stück)?\s*[*xX]\s*(\d+[,.]\d{2})""", RegexOption.IGNORE_CASE)
    private val WEIGHING_REGEX = Regex("""^(\d+[,.]\d+)\s*(kg|g)\s*[*xX]\s*(\d+[,.]\d{2})\s*(?:€/kg|EUR/kg|€|EUR)?(?:\s+(-?\d+[,.]\d{2}))?""", RegexOption.IGNORE_CASE)

    // Aldi: "8 x 0,89 € ASTRA URTYP DOSE 7,12 € 2"
    private val ALDI_MULT_REGEX = Regex(
        """^(\d+)\s*(?:[xX*]|Stk\.?\s*[aáà])\s*(\d+[,.]\d{2})\s*€?\s+(.*?)\s+(-?\d+[,.]\d{2})\s*(?:€\s*)?[AB12]?$""",
        RegexOption.IGNORE_CASE
    )

    // Lidl: "Catsan Katzenstreu 8,99 X 2 17,98 B"
    private val LIDL_MULT_REGEX = Regex(
        """^(.*?)\s+(\d+[,.]\d{2})\s*(?:[xX*]|Stk\.?\s*[aáà])\s*(\d+)\s+(-?\d+[,.]\d{2})\s*[AB12]?$""",
        RegexOption.IGNORE_CASE
    )

    // Standard: "Name 1,29 B" oder "Name 0,85 2" oder "Kart.vfk 2,5kg 1,39 2"
    private val STANDARD_LINE_REGEX = Regex(
        """^(.*?)\s+(-?\d+[,.]\d{2})\s*(?:€\s*)?(?:[AB12*#€]\s*|\d\s*)*$"""
    )

    private val FOOTER_STOP_WORDS = setOf(
        "SUMME", "GESAMT", "ZU ZAHLEN", "TOTAL", "KARTENZAHLUNG",
        "BARGELD", "GEG. BAR", "RÜCKGELD", "ZAHLBETRAG", "GUTHABEN",
        "STEUER %", "KUNDENBELEG", "TERMINAL", "VISA", "GIROCARD",
        "MASTERCARD", "TSE-SIGNATUR", "PAYBACK", "LIDL PAY", "EC-CASH",
        "EMV-AID", "TERMINAL-ID", "TRACE", "BELEG-NR", "KARTEN-NR",
        "BAR", "KREDITKARTE", "EC-KARTE"
    )

    // KOPF- UND WÄHRUNGSMÜLL FILTERN (inklusive "EUR", "PREIS", "DE12345...")
    private val IGNORE_KEYWORDS = setOf(
        "PFANDWERT", "PFAND", "LEERGUT", "PAPIERTRAGETASCHE", "KNOTENBEUTEL",
        "BELEGKOPIE", "BONKOPIE", "HERZLICH WILLKOMMEN", "VIELEN DANK",
        "KAUFLAND", "LIDL", "REWE", "NAHKAUF", "ALDI", "DM-DROGERIE", "GLOBUS",
        "STRASSE", "STR.", "WEIMAR", "JENA", "ERFURT", "ISSERSTEDT", "GMBH", "UST-ID", "DE1", "DE2",
        "EUR", "PREIS EUR", "PREIS", "SUMME", "RABATT", "SOFORT-RABATT", "AKTION", "PFANDRÜCKGABE"
    )

    // Erweitertes Marken- & Artikel-Lexikon für Thüringen
    private val BRAND_DATABASE = mapOf(
        "KART.VFK 2,5KG" to "Kartoffeln",
        "KART.VFK" to "Kartoffeln",
        "FIN. HÄHNCHENBRUST C" to "Hähnchenbrust",
        "FIN. HÄHNCHENBRUST" to "Hähnchenbrust",
        "FIN. HÄHNCHENBR" to "Hähnchenbrust",
        "KLOSSTEIG 750 G" to "Kloßteig",
        "KLOSSTEIG" to "Kloßteig",
        "KLOßTEIG" to "Kloßteig",
        "K.BLATTSPINAT" to "K-Classic Blattspinat",
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
        "MOPRO JOGHURT" to "Molkerei Joghurt",
        "MOPRO" to "Molkerei",
        "MÖNCHOF" to "Mönchshof",
        "MÖNCH." to "Mönchshof",
        "JA! MILCH 1,5%" to "Ja Milch",
        "JA! MILCH" to "Ja Milch",
        "JA!" to "Ja",
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
        "CLC" to "K-Classic",
        "MEG. FEINESÜSSRAHM" to "Meggler Feine Süßrahmbutter",
        "SENS EXPRESSREIS" to "Ben's Original Expressreis",
        "CAROTTENKRÜSTCHEN" to "Karottenkrüstchen",
        "CATSAN" to "Catsan Katzenstreu",
        "K-CLASSIC" to "K-Classic",
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
        val threshold = (avgHeight * 0.6).toInt().coerceIn(10, 15) // Hochpräzise 10-15 Pixel Toleranz für Y-Bündelung

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
        val result = products.map { it.copy(supermarket = supermarket, purchaseDate = purchaseDate) }
        println("DEBUG PARSE_RECEIPT_TEXT RESULT: $result")
        return result
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
        var pendingUnitPrice: Double? = null
        var foundFirstItem = false
        val supermarket = "Kaufland"

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val upper = line.uppercase()

            // 1. Abbruch bei Kassenbereich / Summenzeile (erst wenn bereits Artikel vorhanden sind)
            if (items.isNotEmpty() && isStopLine(upper)) {
                break
            }

            // 2. Kopf- und Stördaten ignorieren
            if (isHeaderOrNoiseLine(line, upper) && !upper.contains("RABATT") && !line.startsWith("-")) {
                if (!foundFirstItem) pendingName = null
                continue
            }

            // 3. Rabatte
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

            // 4. Multiplikator
            val multMatch = MULTIPLIER_REGEX.find(line)
            if (multMatch != null) {
                val qtyStr = multMatch.groupValues[1].replace(',', '.')
                val parsedQty = if (qtyStr.contains('.')) 1 else qtyStr.toIntOrNull() ?: 1
                val unitPrice = multMatch.groupValues[2].replace(',', '.').toDoubleOrNull() ?: 0.0
                
                val lineNoMult = line.substring(multMatch.range.last + 1).trim()
                val priceMatch = PRICE_REGEX.find(lineNoMult)
                val lineTotal = if (priceMatch != null) {
                    Math.abs(priceMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: (parsedQty * unitPrice))
                } else {
                    Math.round(parsedQty * unitPrice * 100.0) / 100.0
                }

                if (pendingName != null) {
                    addProductSafely(pendingName, lineTotal, parsedQty, items, corrections, supermarket)
                    foundFirstItem = true
                    pendingName = null
                    pendingQty = 1
                    pendingUnitPrice = null
                } else {
                    pendingQty = parsedQty
                    pendingUnitPrice = unitPrice
                }
                continue
            }

            // 5. Preiszeile
            val priceMatch = PRICE_REGEX.find(line)
            if (priceMatch != null) {
                val priceStr = priceMatch.groupValues[1].replace(',', '.')
                val priceVal = Math.abs(priceStr.toDoubleOrNull() ?: 0.0)

                var inlineArticle = line.substring(0, priceMatch.range.first).trim()
                inlineArticle = TAX_SUFFIX_REGEX.replace(inlineArticle, "").trim()
                
                val lineWithoutPrice = line.replace(PRICE_REGEX, "").replace(TAX_SUFFIX_REGEX, "").trim()

                if (pendingName != null && lineWithoutPrice.isEmpty() && priceVal > 0.05) {
                    val totalToUse = if (pendingQty > 1 && pendingUnitPrice != null && pendingUnitPrice > 0.0 && Math.abs(priceVal - pendingUnitPrice) < 0.05) {
                        pendingQty * pendingUnitPrice
                    } else priceVal
                    addProductSafely(pendingName, totalToUse, pendingQty, items, corrections, supermarket)
                    foundFirstItem = true
                    pendingName = null
                    pendingQty = 1
                    pendingUnitPrice = null
                } else {
                    if (pendingName == null && inlineArticle.length >= 3 && !isHeaderOrNoiseLine(inlineArticle, inlineArticle.uppercase())) {
                        pendingName = inlineArticle
                    }

                    if (pendingName != null && priceVal > 0.05) {
                        val totalToUse = if (pendingQty > 1 && pendingUnitPrice != null && pendingUnitPrice > 0.0 && Math.abs(priceVal - pendingUnitPrice) < 0.05) {
                            pendingQty * pendingUnitPrice
                        } else priceVal
                        addProductSafely(pendingName, totalToUse, pendingQty, items, corrections, supermarket)
                        foundFirstItem = true
                        pendingName = null
                        pendingQty = 1
                        pendingUnitPrice = null
                    }

                    var nextArticle = line.substring(priceMatch.range.last + 1)
                    nextArticle = TAX_SUFFIX_REGEX.replace(nextArticle, "")
                    nextArticle = nextArticle.replace(Regex("""^[-*#\s.+]+"""), "").trim()

                    if (nextArticle.length >= 3 && !isHeaderOrNoiseLine(nextArticle, nextArticle.uppercase())) {
                        pendingName = nextArticle
                    }
                }
            } else if (line.length >= 3 && !isHeaderOrNoiseLine(line, upper)) {
                pendingName = line
            }
        }

        return bundleIdenticalProducts(items)
    }

    private fun parseStandardReceipt(rawLines: List<String>, corrections: Map<String, String>): List<Product> {
        val items = mutableListOf<Product>()
        var pendingName: String? = null
        var pendingQty = 1

        val textSnippet = rawLines.take(5).joinToString(" ")
        val supermarket = supermarketProfiles.find { p -> 
            p.trigger.any { textSnippet.contains(it, ignoreCase = true) } 
        }?.name

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val upper = line.uppercase()

            // 1. Stopp-Bedingung absichern: Sobald Summen- oder Abschlusszeile nach Artikeln erkannt wird -> break
            if (items.isNotEmpty() && isStopLine(upper)) {
                break
            }

            // 2. Marktadresse / Kopfdaten zuverlässig ignorieren
            if (isHeaderOrNoiseLine(line, upper) && !ALDI_MULT_REGEX.matches(line) && !LIDL_MULT_REGEX.matches(line) && !MULTIPLIER_REGEX.containsMatchIn(line)) {
                if (upper.contains("RABATT") || upper.contains("PREISVORTEIL")) {
                    applyDiscountToLastItem(line, items)
                }
                continue
            }

            // 0. EAN-Zeilen (#4000582188093)
            val eanMatch = Regex("""^#\s*(\d{8,14})""").find(line)
            if (eanMatch != null) {
                val eanCode = eanMatch.groupValues[1]
                if (items.isNotEmpty()) {
                    items[items.lastIndex] = items.last().copy(barcode = eanCode)
                }
                continue
            }

            // 1. Wiegeartikel (z. B. "0,224 kg x 4,99 €/kg" oder "1.234 kg x 0,80")
            val weighMatch = WEIGHING_REGEX.find(line)
            if (weighMatch != null) {
                val weightVal = weighMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 1.0
                val unitPrice = weighMatch.groupValues[3].replace(',', '.').toDoubleOrNull() ?: 0.0
                val g4 = weighMatch.groupValues.getOrNull(4) ?: ""
                val lineTotal = if (g4.isNotBlank()) {
                    Math.abs(g4.replace(',', '.').toDoubleOrNull() ?: (weightVal * unitPrice))
                } else {
                    Math.round(weightVal * unitPrice * 100.0) / 100.0
                }

                if (pendingName != null) {
                    addProductSafely(pendingName, lineTotal, 1, items, corrections, supermarket)
                    pendingName = null
                    pendingQty = 1
                } else if (items.isNotEmpty()) {
                    val last = items.last()
                    val updatedPrice = if (lineTotal > 0.05) lineTotal else last.price
                    items[items.lastIndex] = last.copy(price = updatedPrice, unit = "kg")
                    pendingQty = 1
                }
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
                pendingName = null
                pendingQty = 1
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
                pendingName = null
                pendingQty = 1
                continue
            }

            // Standalone Multiplikator Line (e.g. "2 x 0,79 A" oder "1 x 0.99")
            val multMatch = MULTIPLIER_REGEX.find(line)
            if (multMatch != null) {
                val qtyStr = multMatch.groupValues[1].replace(',', '.')
                val parsedQty = if (qtyStr.contains('.')) 1 else qtyStr.toIntOrNull() ?: 1
                val unitPrice = multMatch.groupValues[2].replace(',', '.').toDoubleOrNull() ?: 0.0
                
                val lineNoMult = line.substring(multMatch.range.last + 1).trim()
                val priceMatchOnLine = PRICE_REGEX.find(lineNoMult)
                val linePrice = if (priceMatchOnLine != null) {
                    Math.abs(priceMatchOnLine.groupValues[1].replace(',', '.').toDoubleOrNull() ?: (parsedQty * unitPrice))
                } else {
                    unitPrice
                }

                if (pendingName != null) {
                    addProductSafely(pendingName, linePrice, parsedQty, items, corrections, supermarket)
                    pendingName = null
                    pendingQty = 1
                } else {
                    pendingQty = parsedQty
                }
                continue
            }

            // Standard line on same line (e.g. "Name 1,29 B")
            val stdMatch = STANDARD_LINE_REGEX.matchEntire(line)
            if (stdMatch != null && !MULTIPLIER_REGEX.containsMatchIn(line)) {
                val rawName = stdMatch.groupValues[1].trim()
                val priceStr = stdMatch.groupValues[2].replace(',', '.')
                val price = priceStr.toDoubleOrNull() ?: 0.0

                if (rawName.count { it.isLetter() } >= 2) {
                    addProductSafely(rawName, price, pendingQty, items, corrections, supermarket)
                    pendingName = null
                    pendingQty = 1
                    continue
                }
            }

            // Separate line price (e.g. line 1: "FRISCHMILCH", line 2: "1,09 A" or "* 0,88 A")
            val priceMatch = PRICE_REGEX.find(line)
            if (priceMatch != null) {
                val priceVal = Math.abs(priceMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0)
                val lineWithoutPrice = line.replace(PRICE_REGEX, "")
                    .replace(Regex("""^[*#\s.-]+"""), "")
                    .replace(Regex("""[AB12*#€0-9\s]+$"""), "")
                    .trim()
                if (pendingName != null && lineWithoutPrice.isEmpty() && priceVal > 0.05) {
                    addProductSafely(pendingName, priceVal, pendingQty, items, corrections, supermarket)
                    pendingName = null
                    pendingQty = 1
                } else if (pendingName == null && lineWithoutPrice.isEmpty() && items.isNotEmpty() && priceVal > 0.05) {
                    val last = items.last()
                    if (last.quantity > 1) {
                        items[items.lastIndex] = last.copy(price = priceVal)
                    }
                } else if (lineWithoutPrice.length >= 3 && !isHeaderOrNoiseLine(lineWithoutPrice, lineWithoutPrice.uppercase())) {
                    addProductSafely(lineWithoutPrice, priceVal, pendingQty, items, corrections, supermarket)
                    pendingName = null
                    pendingQty = 1
                }
                continue
            }

            if (line.startsWith("-") || upper.contains("RABATT") || upper.contains("PREISVORTEIL")) {
                applyDiscountToLastItem(line, items)
                continue
            }

            // Must be product name on its own line
            if (line.length >= 3 && !isHeaderOrNoiseLine(line, upper)) {
                pendingName = line
            }
        }

        return bundleIdenticalProducts(items)
    }

    private fun isStopLine(upper: String): Boolean {
        if (FOOTER_STOP_WORDS.any { upper.contains(it) }) return true
        if (Regex("""\bBAR\b""").containsMatchIn(upper)) return true
        return false
    }

    private fun isHeaderOrNoiseLine(line: String, upper: String): Boolean {
        // PLZ (5 Ziffern)
        if (Regex("""\b\d{5}\b""").containsMatchIn(line)) return true
        // Straßen & Orte (wie Jena-Isserstedt)
        if (upper.contains("STRASSE") || upper.contains("STRAßE") || upper.contains("STR.") ||
            Regex("""\bSTR\b""").containsMatchIn(upper) || upper.contains("WEG") || upper.contains("GASSE") ||
            upper.contains("ALLEE") || upper.contains("PLATZ") || upper.contains("HAUSNR") || upper.contains("PLZ") ||
            upper.contains("ISSERSTEDT") || upper.contains("JENA")) return true
        // Rechtsformen
        if (upper.contains("GMBH") || upper.contains("CO. KG") || upper.contains("CO.KG") ||
            upper.contains(" CO KG") || Regex("""\bKG\b""").containsMatchIn(upper) ||
            Regex("""\bAG\b""").containsMatchIn(upper) || upper.contains("E.K.") || upper.contains("E.V.")) return true
        // Telefon / Steuernummern
        if (upper.contains("TEL") || upper.contains("TELEFON") || upper.contains("FON") || upper.contains("FAX") ||
            upper.contains("UST-ID") || upper.contains("ST-NR") || upper.contains("STNR") || upper.contains("ST.-NR") ||
            upper.contains("STEUER") || Regex("""\bDE\d+""").containsMatchIn(upper) ||
            Regex("""(?:\+49|0\d{2,4})[\s/-]?\d{5,}""").containsMatchIn(line) ||
            Regex("""\b\d{3,5}[/-]\d{3,8}\b""").containsMatchIn(line)) return true

        return isNoise(upper)
    }

    private fun isNoise(upper: String): Boolean {
        if (upper.isBlank()) return true
        if (upper.startsWith("#") || upper.matches(Regex("""^#\d+.*"""))) return true
        if (upper == "EUR" || upper == "PREIS EUR" || upper == "PREIS" || upper == "LEERGUT" || upper.startsWith("PFAND")) return true
        if (upper.contains("STRASSE") || upper.contains("STRAßE") || upper.contains("STR.") || upper.contains("HAUSNR") || upper.contains("PLZ")) return true
        if (upper.matches(Regex("""^DE\d+.*""", RegexOption.IGNORE_CASE)) ||
            upper.matches(Regex("""^UST[-.\s]*ID.*""", RegexOption.IGNORE_CASE)) ||
            upper.matches(Regex("""^ST[-.\s]*NR.*""", RegexOption.IGNORE_CASE)) ||
            upper.matches(Regex("""^TSE.*""", RegexOption.IGNORE_CASE)) ||
            upper.matches(Regex("""^BELEG.*""", RegexOption.IGNORE_CASE)) ||
            upper.matches(Regex("""^KASSE.*""", RegexOption.IGNORE_CASE))) return true
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

        val cleanName = rawName.trim()
        val upperRaw = cleanName.uppercase()
        if (isHeaderOrNoiseLine(cleanName, upperRaw) || isStopLine(upperRaw)) return
        if (cleanName.matches(Regex("""\d+\s*Stk.*""", RegexOption.IGNORE_CASE))) return
        if (cleanName.length < 2) return

        val leadingQtyMatch = Regex("""^(\d+)\s*(?:5tk|stk|st|x)\s+""", RegexOption.IGNORE_CASE).find(cleanName)
        var initialQty = qty
        if (leadingQtyMatch != null && initialQty == 1) {
            initialQty = leadingQtyMatch.groupValues[1].toIntOrNull() ?: 1
        }

        var clean = cleanName
            .replace(Regex("""^\d+\s*(?:5tk|stk|st|x)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b\d+([.,]\d+)?%"""), "")
            .replace(Regex("""\s+\d+[,.]\d{2}\s*€?$"""), "") // Trailing prices like " 1.99" or " 1,99 €"
            .replace(Regex("""\s+\d{1,2}\s*$"""), "") // Trailing digits (e.g. OCR errors for cents like " 99")
            .replace(Regex("""[,.]\d{2}\s*$"""), "") // Trailing .99 or ,99 directly attached
            .replace(Regex("""\s+-QS\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^[-*#\s.+]+"""), "")
            .replace(Regex("""[-*#\s.+]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val cleanUpper = clean.uppercase()
        if (cleanUpper == "EUR" || cleanUpper == "PREIS EUR" || cleanUpper == "PREIS" || cleanUpper.length < 3) return
        if (clean.count { it.isLetter() } < 2) return

        // Absicherung gegen Steuer-IDs (z.B. DE145804122), Bon-Nummern und Müll
        if (cleanUpper.matches(Regex("""^DE\d+.*""", RegexOption.IGNORE_CASE)) ||
            cleanUpper.matches(Regex("""^UST[-.\s]*ID.*""", RegexOption.IGNORE_CASE)) ||
            cleanUpper.matches(Regex("""^ST[-.\s]*NR.*""", RegexOption.IGNORE_CASE)) ||
            cleanUpper.matches(Regex("""^TSE.*""", RegexOption.IGNORE_CASE)) ||
            cleanUpper.matches(Regex("""^BELEG.*""", RegexOption.IGNORE_CASE)) ||
            cleanUpper.matches(Regex("""^KASSE.*""", RegexOption.IGNORE_CASE)) ||
            cleanUpper.matches(Regex("""^BON\d+.*""", RegexOption.IGNORE_CASE)) ||
            cleanUpper.matches(Regex("""^NR\.?\s*\d+.*""", RegexOption.IGNORE_CASE))) {
            return
        }

        for ((shortBrand, fullBrand) in BRAND_DATABASE) {
            clean = clean.replace(Regex("""\b${Regex.escape(shortBrand)}""", RegexOption.IGNORE_CASE), fullBrand)
        }

        var finalName = applyFuzzyCorrections(clean, corrections)

        finalName = finalName.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                when {
                    word.equals("gut", true) -> "Gut"
                    word.equals("günstig", true) -> "Günstig"
                    word.equals("h-milch", true) -> "H-Milch"
                    word.equals("dmbio", true) -> "dmBio"
                    else -> word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMANY) else it.toString() }
                }
            }

        var finalQty = if (initialQty >= 99 || initialQty <= 0) 1 else initialQty
        var finalPrice = price
        val finalUnit = determineSmartUnit(finalName, finalPrice, finalQty, rawName)

        // TWQ / Kisten Logik: Wenn Gesamtpreis z.B. 9.98 € ist und 1 Kiste ca. 4.99 € kostet -> 2 Kisten à 4,99 €!
        if (finalUnit == "Kiste" || finalName.lowercase().contains("wasser") || finalName.lowercase().contains("waldquell")) {
            if (finalPrice >= 7.00 && finalQty == 1) {
                finalQty = round(finalPrice / 4.99).toInt().coerceAtLeast(2)
                finalPrice = Math.round((finalPrice / finalQty) * 100.0) / 100.0
            }
        }

        items.add(
            Product(
                name = finalName,
                price = Math.round(finalPrice * 100.0) / 100.0,
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

    private fun bundleIdenticalProducts(items: List<Product>): List<Product> {
        val bundled = mutableListOf<Product>()
        for (item in items) {
            val itemUnitPrice = if (item.quantity > 0) item.price / item.quantity else item.price
            
            val idx = bundled.indexOfFirst {
                val existingUnitPrice = if (it.quantity > 0) it.price / it.quantity else it.price
                it.name.equals(item.name, ignoreCase = true) &&
                Math.abs(existingUnitPrice - itemUnitPrice) < 0.05
            }
            if (idx != -1) {
                val existing = bundled[idx]
                bundled[idx] = existing.copy(
                    quantity = existing.quantity + item.quantity,
                    price = existing.price + item.price
                )
            } else {
                bundled.add(item)
            }
        }
        return bundled
    }

    private fun applyFuzzyCorrections(name: String, corrections: Map<String, String>): String {
        val upper = name.uppercase()
        for ((key, value) in corrections) {
            if (upper == key.uppercase() || upper.contains(key.uppercase())) {
                return value
            }
        }
        return name
    }

    fun determineSmartUnit(name: String, price: Double, quantity: Int, rawText: String? = null): String {
        val lower = name.lowercase()
        val rawLower = rawText?.lowercase() ?: ""
        val combined = "$lower $rawLower"
        val detectedCat = CategoryDetector.detectCategory(name)

        return when {
            // 1. Kiste / Kasten (Getränkekiste - Wasser, Bier, Cola Kästen)
            combined.contains("kiste") || combined.contains("kasten") || combined.contains("20x") || combined.contains("24x") || combined.contains("12x") || combined.contains("träger") ||
            ((combined.contains("wasser") || combined.contains("bier") || combined.contains("cola") || combined.contains("limo") || combined.contains("waldquell")) && price >= 3.50) -> "Kiste"

            // 2. Becher (Sahne, Schmand, Joghurt, Quark, Pudding, Margarine, Feinkost, Eis, Miree, Frischkäse)
            combined.contains("becher") || combined.contains("sahnebecher") ||
            combined.contains("sahne") || combined.contains("schlagsahne") || combined.contains("saure sahne") ||
            combined.contains("sauerrahm") || combined.contains("schmand") || combined.contains("creme fraiche") ||
            combined.contains("crème fraîche") || combined.contains("joghurt") || combined.contains("quark") ||
            combined.contains("pudding") || combined.contains("milchreis") || combined.contains("margarine") ||
            combined.contains("rama") || combined.contains("lätta") || combined.contains("tzatziki") ||
            combined.contains("aioli") || combined.contains("fleischsalat") || combined.contains("krautsalat") ||
            combined.contains("feinkostsalat") || combined.contains("mascarpone") || combined.contains("ricotta") ||
            combined.contains("hüttenkäse") || combined.contains("miree") || combined.contains("frischkäse") ||
            combined.contains("aufstrich") || combined.contains("dip") || combined.contains("eiscreme") || combined.contains("ben & jerry") -> "Becher"

            // 3. Dose (Konserven, Energy Drinks, Dosengetränke)
            combined.contains("dose") || combined.contains("red bull") || combined.contains("monster energy") ||
            combined.contains("energy") || combined.contains("thunfisch") || combined.contains("tuna") ||
            combined.contains("konserve") || combined.contains("eintopf") || combined.contains("gestückelte tomaten") ||
            combined.contains("schältomaten") || combined.contains("mais") || combined.contains("sardinen") -> "Dose"

            // 4. Glas (Marmelade, Honig, Senf, Gurken, Kirschen, Pesto, Nutella)
            combined.contains("glas") || combined.contains("marmelade") || combined.contains("konfitüre") ||
            combined.contains("honig") || combined.contains("senf") || combined.contains("nutella") ||
            combined.contains("apfelmus") || combined.contains("gewürzgurten") || combined.contains("gurken") ||
            combined.contains("rotkohl") || combined.contains("sauerkraut") || combined.contains("sauerkirschen") ||
            combined.contains("pesto") || combined.contains("oliven") || combined.contains("kapern") ||
            combined.contains("babybrei") -> "Glas"

            // 5. Flasche (Getränke, Bier, Wein, Sekt, Weichspüler, Kuschelweich, Spülmittel, Reiniger, Öle, Essig, Ketchup, Dressing, Saft, Wasser)
            combined.contains("flasche") || combined.contains("fl.") || combined.contains("wein") ||
            combined.contains("sekt") || combined.contains("prosecco") || combined.contains("champagner") ||
            combined.contains("spirituose") || combined.contains("rum") || combined.contains("vodka") ||
            combined.contains("gin") || combined.contains("whisky") || combined.contains("likör") ||
            combined.contains("sirup") || combined.contains("essig") || combined.contains("olivenöl") ||
            combined.contains("rapsöl") || combined.contains("sonnenblumenöl") || combined.contains("ketchup") ||
            combined.contains("dressing") || combined.contains("smoothie") || combined.contains("mönchshof") ||
            combined.contains("mönchof") || combined.contains("mönch") || combined.contains("zwickel") ||
            combined.contains("pils") || combined.contains("radler") || combined.contains("export") ||
            combined.contains("weizen") || combined.contains("helles") || combined.contains("köstritzer") ||
            combined.contains("radeberger") || combined.contains("paulaner") || combined.contains("augustiner") ||
            combined.contains("erdinger") || combined.contains("franziskaner") || combined.contains("krombacher") ||
            combined.contains("oettinger") || combined.contains("bitburger") || combined.contains("jever") ||
            combined.contains("becks") || combined.contains("astra") || combined.contains("urtyp") ||
            combined.contains("bier") || combined.contains("spezi") || combined.contains("limonade") ||
            combined.contains("eistee") || combined.contains("weichspüler") || combined.contains("kuschelweich") ||
            combined.contains("lenor") || combined.contains("softlan") || combined.contains("vernel") ||
            combined.contains("spülmittel") || combined.contains("pril") || combined.contains("palmolive") ||
            combined.contains("fairy") || combined.contains("reiniger") || combined.contains("glasreiniger") ||
            combined.contains("scheibenklar") || combined.contains("shampoo") || combined.contains("duschgel") ||
            combined.contains("flüssigseife") ||
            (detectedCat == FoodCategory.GETRAENKE && !combined.contains("tetrapak") && !combined.contains("pack") && !combined.contains("dose")) ||
            (combined.contains("saft") && !combined.contains("tetrapak") && !combined.contains("pack")) ||
            (combined.contains("wasser") && price < 3.50) -> "Flasche"

            // 6. Tüte / Beutel (Chips, Gummibärchen, TK-Gemüse, Pommes, Nüsse)
            combined.contains("tüte") || combined.contains("beutel") || combined.contains("chips") ||
            combined.contains("flips") || combined.contains("popcorn") || combined.contains("gummibärchen") ||
            combined.contains("haribo") || combined.contains("bonbons") || combined.contains("pommes") ||
            combined.contains("gefriergemüse") || combined.contains("reibekäse") || combined.contains("streukäse") ||
            combined.contains("salatbeutel") || combined.contains("nüsse") || combined.contains("studentenfutter") -> "Tüte"

            // 7. Schale (Erdbeeren, Beeren, Pilze, Hackfleisch)
            combined.contains("schale") || combined.contains("erdbeeren") || combined.contains("himbeeren") ||
            combined.contains("heidelbeeren") || combined.contains("weintrauben") || combined.contains("champignons") ||
            combined.contains("pilze") || combined.contains("cherrytomaten") || combined.contains("hackfleisch") -> "Schale"

            // 8. Netz (Kartoffeln, Zwiebeln, Orangen, Zitronen)
            combined.contains("netz") || combined.contains("kartoffeln") || combined.contains("zwiebeln") ||
            combined.contains("orangen") || combined.contains("mandarinen") || combined.contains("clementinen") ||
            combined.contains("zitronen") || combined.contains("knoblauch") -> "Netz"

            // 9. Bund (Radieschen, Kräuter, Lauchzwiebeln)
            combined.contains("bund") || combined.contains("radieschen") || combined.contains("lauchzwiebeln") ||
            combined.contains("frühlingszwiebeln") || combined.contains("petersilie") || combined.contains("dill") ||
            combined.contains("schnittlauch") -> "Bund"

            // 10. Tafel (Schokolade)
            combined.contains("tafel") || combined.contains("schokolade") || combined.contains("ritter sport") ||
            combined.contains("milka") || combined.contains("lindt") -> "Tafel"

            // 11. Rolle (Küchenrolle, Toilettenpapier, Alufolie, Prinzenrolle)
            combined.contains("rolle") || combined.contains("küchenrolle") || combined.contains("toilettenpapier") ||
            combined.contains("klopapier") || combined.contains("müllbeutel") || combined.contains("alufolie") ||
            combined.contains("backpapier") || combined.contains("prinzenrolle") -> "Rolle"

            // 12. Packung (Aufschnitt, Käse, Butter, Milch TetraPak, Toast, Nudeln, Müsli, Eier, Pizza, Tee, Kaffee, Wurst)
            combined.contains("packung") || combined.contains("pack.") || combined.contains("pack") ||
            combined.contains("käse") || combined.contains("aufschnitt") || combined.contains("schinken") ||
            combined.contains("salami") || combined.contains("wurst") || combined.contains("butter") ||
            combined.contains("toast") || combined.contains("brot") || combined.contains("nudeln") ||
            combined.contains("spaghetti") || combined.contains("müsli") || combined.contains("cornflakes") ||
            combined.contains("eier") || combined.contains("pizza") || combined.contains("tee") ||
            combined.contains("kaffee") || combined.contains("milch") || combined.contains("h-milch") ||
            combined.contains("frischmilch") || combined.contains("waschmittel") -> "Packung"

            else -> "Stk."
        }
    }
}
