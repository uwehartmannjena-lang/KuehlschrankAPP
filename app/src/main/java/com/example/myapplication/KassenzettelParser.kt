package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.myapplication.data.CategoryDetector
import com.example.myapplication.data.FoodCategory
import com.example.myapplication.data.Product
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.text.Text
import java.io.File
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import java.util.*
import kotlin.math.round

object KassenzettelParser {

    private val gson = Gson()
    private var supermarketProfiles: List<SupermarketProfile> = emptyList()

    private val PRICE_REGEX = Regex("""(-?\d+[,.]\d{2})""")
    private val TAX_SUFFIX_REGEX = Regex("""\s+(?:[AB12*#€]\s*|\d\s*)*$""")
    private val MULTIPLIER_REGEX = Regex("""^(\d+(?:[,.]\d+)?)\s*(?:kg|g|l|ml|stk|stück)?\s*[*xX]\s*(\d+[,.]\d{2})""", RegexOption.IGNORE_CASE)
    private val WEIGHING_REGEX = Regex("""^(\d+[,.]\d+)\s*(kg|g)\s*[*xX]\s*(\d+[,.]\d{2})\s*(?:€/kg|EUR/kg|€|EUR)?(?:\s+(-?\d+[,.]\d{2}))?""", RegexOption.IGNORE_CASE)

    private val ALDI_MULT_REGEX = Regex(
        """^(\d+)\s*(?:[xX*]|Stk\.?\s*[aáà])\s*(\d+[,.]\d{2})\s*€?\s+(.*?)\s+(-?\d+[,.]\d{2})\s*(?:€\s*)?[AB12]?$""",
        RegexOption.IGNORE_CASE
    )

    private val LIDL_MULT_REGEX = Regex(
        """^(.*?)\s+(\d+[,.]\d{2})\s*(?:[xX*]|Stk\.?\s*[aáà])\s*(\d+)\s+(-?\d+[,.]\d{2})\s*[AB12]?$""",
        RegexOption.IGNORE_CASE
    )

    private val STANDARD_LINE_REGEX = Regex(
        """^(.*?)\s+(-?\d+[,.]\d{2})\s*(?:€\s*)?(?:[AB12*#€]\s*|\d\s*)*$"""
    )

    private val GLOBUS_LINE_REGEX = Regex(
        """^(.*?)\s+(-?\d+[,.]\d{2})\s+(?:\d\s*|A|B)$"""
    )

    private val FOOTER_STOP_WORDS = setOf(
        "SUMME", "GESAMT", "ZU ZAHLEN", "TOTAL", "KARTENZAHLUNG",
        "BARGELD", "GEG. BAR", "RÜCKGELD", "ZAHLBETRAG", "GUTHABEN",
        "STEUER %", "KUNDENBELEG", "TERMINAL", "VISA", "GIROCARD",
        "MASTERCARD", "TSE-SIGNATUR", "LIDL PAY", "EC-CASH",
        "EMV-AID", "TERMINAL-ID", "TRACE", "BELEG-NR",
        "KREDITKARTE", "EC-KARTE", "ANZ. ARTIKEL", "ANZAHL ARTIKEL"
    )

    private val IGNORE_KEYWORDS = setOf(
        "PFANDWERT", "PFAND", "LEERGUT", "PAPIERTRAGETASCHE", "KNOTENBEUTEL",
        "BELEGKOPIE", "BONKOPIE", "HERZLICH WILLKOMMEN", "VIELEN DANK",
        "KAUFLAND", "LIDL", "REWE", "NAHKAUF", "ALDI", "DM-DROGERIE", "GLOBUS",
        "STRASSE", "STR.", "WEIMAR", "JENA", "ERFURT", "ISSERSTEDT", "GMBH", "UST-ID", "DE1", "DE2",
        "EUR", "PREIS EUR", "PREIS", "RABATT", "SOFORT-RABATT", "AKTION", "PFANDRÜCKGABE",
        "PAYBACK", "KARTENNR", "KARTEN-NR"
    )

    private val BRAND_DATABASE = mapOf(
        "EXSREIS" to "Expressreis",
        "AROTTENKRÜSTCHEN" to "Karottenkrüstchen",
        "AROTTENKRUESTCHEN" to "Karottenkrüstchen",
        "GEWÜRZS" to "Gewürzgurken",
        "ERDN GERÖS GES" to "Erdnüsse geröstet",
        "SCHWW SCHINKEN" to "Schinken",
        "H BRUSTFILET" to "Hähnchen-Brustfilet",
        "HERZHAF" to "Herzhaft",
        "PUTEN-LACHSSCHINKE N" to "Puten-Lachsschinken",
        "KART.VFK 2,5KG" to "Kartoffeln",
        "KART.VFK" to "Kartoffeln",
        "HAUS HANDKÄSE" to "Hausmacher Handkäse",
        "HAUS HANDKAESE" to "Hausmacher Handkäse",
        "FINESSE PFEFFER" to "Herta Finesse Pfeffer",
        "FIN. PFEFFER" to "Herta Finesse Pfeffer",
        "FIN. HÄHNCHENBRUST C" to "Herta Finesse Hähnchenbrust",
        "FIN. HÄHNCHENBRUST M" to "Herta Finesse Hähnchenbrust",
        "FIN. HÄHNCHENBRUST" to "Herta Finesse Hähnchenbrust",
        "FIN. HÄHNCHENBR" to "Herta Finesse Hähnchenbrust",
        "FINESSE HÄHNCHENBRUST" to "Herta Finesse Hähnchenbrust",
        "FINESSE HÄHNCHENBR" to "Herta Finesse Hähnchenbrust",
        "FINESSS HÄHNCHENBRUST" to "Herta Finesse Hähnchenbrust",
        "GUTFRIED HÄHNCHENBRUST M" to "Gutfried Hähnchenbrust",
        "GUTFRIED HÄHNCHENBRUST" to "Gutfried Hähnchenbrust",
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
        "MEG.FEINESÜSSRAHM" to "Meggler Feine Süßrahmbutter",
        "WM-CHIA-KRÜSTCHEN" to "Weltmeister Chia Krüstchen",
        "WM CHIA KRÜSTCHEN" to "Weltmeister Chia Krüstchen",
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

        val avgHeight = allLines.mapNotNull { it.boundingBox?.height() }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 20.0
        val threshold = (avgHeight * 0.6).toInt().coerceIn(10, 20)

        for (line in sortedLines) {
            val top = line.boundingBox?.top ?: 0
            val existingRow = rows.find { Math.abs((it.first().boundingBox?.top ?: 0) - top) < threshold }
            if (existingRow != null) {
                existingRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        val reconstructedText = rows.joinToString("\n") { row ->
            row.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") { it.text }
        }

        return parseReceiptText(reconstructedText, corrections)
    }

    fun parseReceiptText(text: String, corrections: Map<String, String> = emptyMap()): List<Product> {
        val rawLines = text.lines()
        val upperText = text.uppercase()

        val supermarket = detectSupermarket(upperText)
        val purchaseDate = extractDate(text)

        val isKaufland = supermarket == "Kaufland" || upperText.contains("K CARD") || upperText.contains("PREIS EUR")
        val isGlobus = supermarket == "Globus"

        val products = if (isKaufland) {
            parseKauflandInterleaved(rawLines, corrections)
        } else if (isGlobus) {
            parseGlobusReceipt(rawLines, corrections)
        } else {
            parseStandardReceipt(rawLines, corrections)
        }

        val result = products.map { it.copy(supermarket = supermarket, purchaseDate = purchaseDate) }
        try { Log.i("KassenzettelParser", "DEBUG PARSE_RECEIPT_TEXT RESULT: ${result.size} Artikel gefunden.") } catch (t: Throwable) {}
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
        val headerText = text.lines().take(25).joinToString("\n")
        val dateRegex = Regex("""(\d{2})[./-](\d{2})[./-](\d{2,4})""")
        val match = dateRegex.find(headerText) ?: dateRegex.find(text)

        return if (match != null) {
            try {
                val day = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt() - 1
                var year = match.groupValues[3].toInt()
                if (year < 100) year += 2000

                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                if (year > currentYear + 2 || year < 2020) year = currentYear

                val cal = Calendar.getInstance()
                cal.set(year, month, day, 12, 0)
                cal.timeInMillis
            } catch (e: Exception) { System.currentTimeMillis() }
        } else {
            System.currentTimeMillis()
        }
    }

    private fun parseGlobusReceipt(rawLines: List<String>, corrections: Map<String, String>): List<Product> {
        val items = mutableListOf<Product>()
        var pendingName: String? = null
        val supermarket = "Globus"

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val upper = line.uppercase()

            if (isStopLine(upper)) {
                pendingName = null
                break
            }

            val hasPrice = PRICE_REGEX.containsMatchIn(line)
            if (isHeaderOrNoiseLine(line, upper)) {
                if (!hasPrice) {
                    pendingName = null
                    continue
                }
            }

            val globusMatch = GLOBUS_LINE_REGEX.matchEntire(line)
            if (globusMatch != null) {
                val rawName = globusMatch.groupValues[1].trim()
                val priceStr = globusMatch.groupValues[2].replace(',', '.')
                val price = priceStr.toDoubleOrNull() ?: 0.0

                if (rawName.count { it.isLetter() } >= 2 && !rawName.startsWith("#")) {
                    addProductSafely(rawName, price, 1, items, corrections, supermarket)
                    pendingName = null
                    continue
                }
            }

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
                }
                continue
            }

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
                }
                continue
            }

            val priceMatch = PRICE_REGEX.find(line)
            if (priceMatch != null) {
                val priceVal = Math.abs(priceMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0)
                val lineWithoutPrice = line.replace(PRICE_REGEX, "")
                    .replace(Regex("""^[#\s.-]+"""), "")
                    .replace(Regex("""[AB12*#€0-9\s]+$"""), "")
                    .replace(Regex("""^\d{10,}$"""), "")
                    .trim()

                if (pendingName != null && lineWithoutPrice.isEmpty() && priceVal > 0.05) {
                    addProductSafely(pendingName, priceVal, 1, items, corrections, supermarket)
                    pendingName = null
                } else if (lineWithoutPrice.length >= 3 && !isHeaderOrNoiseLine(lineWithoutPrice, lineWithoutPrice.uppercase())) {
                    addProductSafely(lineWithoutPrice, priceVal, 1, items, corrections, supermarket)
                    pendingName = null
                }
                continue
            }

            if (line.startsWith("#") || Regex("""^\d{10,}$""").matches(line)) {
                continue
            }

            if (line.length >= 3 && !isHeaderOrNoiseLine(line, upper)) {
                pendingName = line
            }
        }
        return bundleIdenticalProducts(items)
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

            if (isStopLine(upper)) {
                pendingName = null
                break
            }

            val hasPrice = PRICE_REGEX.containsMatchIn(line)
            if (isHeaderOrNoiseLine(line, upper) && !upper.contains("RABATT") && !line.startsWith("-")) {
                if (!hasPrice) {
                    pendingName = null
                    continue
                }
            }

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

            if (isStopLine(upper)) {
                pendingName = null
                break
            }

            val hasPrice = PRICE_REGEX.containsMatchIn(line)
            if (isHeaderOrNoiseLine(line, upper) && !ALDI_MULT_REGEX.matches(line) && !LIDL_MULT_REGEX.matches(line) && !MULTIPLIER_REGEX.containsMatchIn(line)) {
                if (upper.contains("RABATT") || upper.contains("PREISVORTEIL")) {
                    applyDiscountToLastItem(line, items)
                }
                if (!hasPrice) {
                    pendingName = null
                    continue
                }
            }

            val eanMatch = Regex("""^#\s*(\d{8,14})""").find(line)
            if (eanMatch != null) {
                val eanCode = eanMatch.groupValues[1]
                if (items.isNotEmpty()) {
                    items[items.lastIndex] = items.last().copy(barcode = eanCode)
                }
                continue
            }

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

            if (line.length >= 3 && !isHeaderOrNoiseLine(line, upper)) {
                pendingName = line
            }
        }

        return bundleIdenticalProducts(items)
    }

    private fun isStopLine(upper: String): Boolean {
        if (FOOTER_STOP_WORDS.any { upper.contains(it) }) return true
        if (upper.matches(Regex("""^BAR\s*\d+.*"""))) return true
        return false
    }

    private fun isHeaderOrNoiseLine(line: String, upper: String): Boolean {
        if (upper.contains("PLZ") || upper.contains("STRASSE") || upper.contains("STRAßE") || upper.contains("STR.") ||
            upper.contains("JENA") || upper.contains("WEIMAR") || upper.contains("ERFURT") || upper.contains("FILIALE")) {
            if (Regex("""\b\d{5}\b""").containsMatchIn(line)) return true
        }
        if (upper.contains("STRASSE") || upper.contains("STRAßE") || upper.contains("STR.") ||
            Regex("""\bSTR\b""").containsMatchIn(upper) || upper.contains("WEG") || upper.contains("GASSE") ||
            upper.contains("ALLEE") || upper.contains("PLATZ") || upper.contains("HAUSNR") || upper.contains("PLZ") ||
            upper.contains("ISSERSTEDT") || upper.contains("JENA")) return true
        if (upper.contains("GMBH") || upper.contains("CO. KG") || upper.contains("CO.KG") ||
            upper.contains(" CO KG") || Regex("""\bKG\b""").containsMatchIn(upper) ||
            Regex("""\bAG\b""").containsMatchIn(upper) || upper.contains("E.K.") || upper.contains("E.V.")) return true
        if (upper.contains("TEL") || upper.contains("TELEFON") || upper.contains("FON") || upper.contains("FAX") ||
            upper.contains("UST-ID") || upper.contains("ST-NR") || upper.contains("STNR") || upper.contains("ST.-NR") ||
            upper.contains("STEUER") || Regex("""\bDE\d+""").containsMatchIn(upper) ||
            Regex("""(?:\+49|0\d{2,4})[\s/-]?\d{5,}""").containsMatchIn(line) ||
            Regex("""\b\d{3,5}[/-]\d{3,8}\b""").containsMatchIn(line)) return true
        return isNoise(upper)
    }

    private fun isNoise(upper: String): Boolean {
        if (upper.isBlank()) return true
        if (upper.matches(Regex("""^#\d+.*"""))) return true
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
            .replace(Regex("""\s+\d+[,.]\d{2}\s*€?$"""), "")
            .replace(Regex("""\s+\d{1,2}\s*$"""), "")
            .replace(Regex("""[,.]\d{2}\s*$"""), "")
            .replace(Regex("""\s+-QS\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^[-*#\s.+]+"""), "")
            .replace(Regex("""[-*#\s.+]+$"""), "")
            .replace(Regex("""\s+EUR$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val cleanUpper = clean.uppercase()
        if (cleanUpper == "EUR" || cleanUpper == "PREIS EUR" || cleanUpper == "PREIS" || cleanUpper.length < 3) return
        if (clean.count { it.isLetter() } < 2) return

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

        val sanitizedBySanitizer = ReceiptImportSanitizer.cleanReceiptText(clean)
        if (sanitizedBySanitizer.isNotBlank()) {
            clean = sanitizedBySanitizer
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
            combined.contains("kiste") || combined.contains("kasten") || combined.contains("20x") || combined.contains("24x") || combined.contains("12x") || combined.contains("träger") ||
                    ((combined.contains("wasser") || combined.contains("bier") || combined.contains("cola") || combined.contains("limo") || combined.contains("waldquell")) && price >= 3.50) -> "Kiste"
            combined.contains("becher") || combined.contains("sahnebecher") || combined.contains("sahne") || combined.contains("schlagsahne") || combined.contains("saure sahne") ||
                    combined.contains("sauerrahm") || combined.contains("schmand") || combined.contains("creme fraiche") || combined.contains("crème fraîche") || combined.contains("joghurt") || combined.contains("quark") ||
                    combined.contains("pudding") || combined.contains("milchreis") || combined.contains("margarine") || combined.contains("rama") || combined.contains("lätta") || combined.contains("tzatziki") ||
                    combined.contains("aioli") || combined.contains("fleischsalat") || combined.contains("krautsalat") || combined.contains("feinkostsalat") || combined.contains("mascarpone") || combined.contains("ricotta") ||
                    combined.contains("hüttenkäse") || combined.contains("miree") || combined.contains("frischkäse") || combined.contains("aufstrich") || combined.contains("dip") || combined.contains("eiscreme") || combined.contains("ben & jerry") -> "Becher"
            combined.contains("dose") || combined.contains("red bull") || combined.contains("monster energy") || combined.contains("energy") || combined.contains("thunfisch") || combined.contains("tuna") ||
                    combined.contains("konserve") || combined.contains("eintopf") || combined.contains("gestückelte tomaten") || combined.contains("schältomaten") || combined.contains("mais") || combined.contains("sardinen") -> "Dose"
            combined.contains("glas") || combined.contains("marmelade") || combined.contains("konfitüre") || combined.contains("honig") || combined.contains("senf") || combined.contains("nutella") ||
                    combined.contains("apfelmus") || combined.contains("gewürzgurten") || combined.contains("gurken") || combined.contains("rotkohl") || combined.contains("sauerkraut") || combined.contains("sauerkirschen") ||
                    combined.contains("pesto") || combined.contains("oliven") || combined.contains("kapern") || combined.contains("babybrei") -> "Glas"
            combined.contains("flasche") || combined.contains("fl.") || combined.contains("wein") || combined.contains("sekt") || combined.contains("prosecco") || combined.contains("champagner") ||
                    combined.contains("spirituose") || combined.contains("rum") || combined.contains("vodka") || combined.contains("gin") || combined.contains("whisky") || combined.contains("likör") ||
                    combined.contains("sirup") || combined.contains("essig") || combined.contains("olivenöl") || combined.contains("rapsöl") || combined.contains("sonnenblumenöl") || combined.contains("ketchup") ||
                    combined.contains("dressing") || combined.contains("smoothie") || combined.contains("mönchshof") || combined.contains("mönchof") || combined.contains("mönch") || combined.contains("zwickel") ||
                    combined.contains("pils") || combined.contains("radler") || combined.contains("export") || combined.contains("weizen") || combined.contains("helles") || combined.contains("köstritzer") ||
                    combined.contains("radeberger") || combined.contains("paulaner") || combined.contains("augustiner") || combined.contains("erdinger") || combined.contains("franziskaner") || combined.contains("krombacher") ||
                    combined.contains("oettinger") || combined.contains("bitburger") || combined.contains("jever") || combined.contains("becks") || combined.contains("astra") || combined.contains("urtyp") ||
                    combined.contains("bier") || combined.contains("spezi") || combined.contains("limonade") || combined.contains("eistee") || combined.contains("weichspüler") || combined.contains("kuschelweich") ||
                    combined.contains("lenor") || combined.contains("softlan") || combined.contains("vernel") || combined.contains("spülmittel") || combined.contains("pril") || combined.contains("palmolive") ||
                    combined.contains("fairy") || combined.contains("reiniger") || combined.contains("glasreiniger") || combined.contains("scheibenklar") || combined.contains("shampoo") || combined.contains("duschgel") ||
                    combined.contains("flüssigseife") || (detectedCat == FoodCategory.GETRAENKE && !combined.contains("tetrapak") && !combined.contains("pack") && !combined.contains("dose")) ||
                    (combined.contains("saft") && !combined.contains("tetrapak") && !combined.contains("pack")) || (combined.contains("wasser") && price < 3.50) -> "Flasche"
            combined.contains("tüte") || combined.contains("beutel") || combined.contains("chips") || combined.contains("flips") || combined.contains("popcorn") || combined.contains("gummibärchen") ||
                    combined.contains("haribo") || combined.contains("bonbons") || combined.contains("pommes") || combined.contains("gefriergemüse") || combined.contains("reibekäse") || combined.contains("streukäse") ||
                    combined.contains("salatbeutel") || combined.contains("nüsse") || combined.contains("studentenfutter") -> "Tüte"
            combined.contains("schale") || combined.contains("erdbeeren") || combined.contains("himbeeren") || combined.contains("heidelbeeren") || combined.contains("weintrauben") || combined.contains("champignons") ||
                    combined.contains("pilze") || combined.contains("cherrytomaten") || combined.contains("hackfleisch") -> "Schale"
            combined.contains("netz") || combined.contains("kartoffeln") || combined.contains("zwiebeln") || combined.contains("orangen") || combined.contains("mandarinen") || combined.contains("clementinen") ||
                    combined.contains("zitronen") || combined.contains("knoblauch") -> "Netz"
            combined.contains("bund") || combined.contains("radieschen") || combined.contains("lauchzwiebeln") || combined.contains("frühlingszwiebeln") || combined.contains("petersilie") || combined.contains("dill") ||
                    combined.contains("schnittlauch") -> "Bund"
            combined.contains("tafel") || combined.contains("schokolade") || combined.contains("ritter sport") || combined.contains("milka") || combined.contains("lindt") -> "Tafel"
            combined.contains("rolle") || combined.contains("küchenrolle") || combined.contains("toilettenpapier") || combined.contains("klopapier") || combined.contains("müllbeutel") || combined.contains("alufolie") ||
                    combined.contains("backpapier") || combined.contains("prinzenrolle") -> "Rolle"
            combined.contains("packung") || combined.contains("pack.") || combined.contains("pack") || combined.contains("käse") || combined.contains("aufschnitt") || combined.contains("schinken") ||
                    combined.contains("salami") || combined.contains("wurst") || combined.contains("butter") || combined.contains("toast") || combined.contains("brot") || combined.contains("nudeln") ||
                    combined.contains("spaghetti") || combined.contains("müsli") || combined.contains("cornflakes") || combined.contains("eier") || combined.contains("pizza") || combined.contains("tee") ||
                    combined.contains("kaffee") || combined.contains("milch") || combined.contains("h-milch") || combined.contains("frischmilch") || combined.contains("waschmittel") -> "Packung"
            else -> "Stk."
        }
    }

    fun processPdf(
        context: Context,
        pdfFile: File,
        recognizer: TextRecognizer,
        corrections: Map<String, String> = emptyMap()
    ): Pair<List<Product>, Bitmap?> {
        return try {
            if (!pdfFile.exists() || pdfFile.length() == 0L) {
                return Pair(emptyList(), null)
            }

            var uiPreviewBitmap: Bitmap? = null
            val ocrTextChunks = mutableListOf<String>()
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null

            try {
                pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                if (pfd != null) {
                    renderer = PdfRenderer(pfd)

                    for (i in 0 until renderer.pageCount) {
                        var page: PdfRenderer.Page? = null
                        var fullPageBitmap: Bitmap? = null
                        try {
                            page = renderer.openPage(i)
                            val scale = 1200f / page.width.toFloat()
                            val targetWidth = 1200
                            val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)

                            fullPageBitmap = try {
                                Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            } catch (oom: OutOfMemoryError) {
                                System.gc()
                                continue
                            }

                            // 🔥 DER ENTSCHEIDENDE FIX: Das PDF zwingend auf weißen Hintergrund zeichnen! 🔥
                            fullPageBitmap.eraseColor(android.graphics.Color.WHITE)

                            page.render(fullPageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            if (i == 0) {
                                uiPreviewBitmap = Bitmap.createScaledBitmap(fullPageBitmap, targetWidth / 2, targetHeight / 2, true)
                            }

                            var y = 0
                            val CHUNK_MAX_HEIGHT = 2000
                            while (y < targetHeight) {
                                val currentChunkHeight = minOf(CHUNK_MAX_HEIGHT, targetHeight - y)
                                val chunkBitmap = Bitmap.createBitmap(fullPageBitmap, 0, y, targetWidth, currentChunkHeight)
                                try {
                                    val image = InputImage.fromBitmap(chunkBitmap, 0)
                                    val visionText = Tasks.await(recognizer.process(image))
                                    if (visionText != null) {
                                        val allLines = visionText.textBlocks.flatMap { it.lines }
                                        if (allLines.isNotEmpty()) {
                                            val sortedLines = allLines.sortedBy { it.boundingBox?.top ?: 0 }
                                            val rows = mutableListOf<MutableList<Text.Line>>()
                                            val avgHeight = allLines.mapNotNull { it.boundingBox?.height() }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 20.0
                                            val threshold = (avgHeight * 0.6).toInt().coerceIn(10, 20)

                                            for (line in sortedLines) {
                                                val top = line.boundingBox?.top ?: 0
                                                val existingRow = rows.find { Math.abs((it.first().boundingBox?.top ?: 0) - top) < threshold }
                                                if (existingRow != null) {
                                                    existingRow.add(line)
                                                } else {
                                                    rows.add(mutableListOf(line))
                                                }
                                            }
                                            val reconstructedText = rows.joinToString("\n") { row ->
                                                row.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") { it.text }
                                            }
                                            ocrTextChunks.add(reconstructedText)
                                        }
                                    }
                                } finally {
                                    chunkBitmap.recycle()
                                }
                                y += currentChunkHeight
                            }
                        } finally {
                            fullPageBitmap?.recycle()
                            page?.close()
                        }
                    }
                }
            } finally {
                try { renderer?.close() } catch (e: Exception) {}
                try { pfd?.close() } catch (e: Exception) {}
            }

            val fullText = ocrTextChunks.joinToString("\n")

            // 🔥 SPIONAGE-LOG: Druckt den erkannten Text ins rote Logcat, falls mal wieder was hakt!
            try { Log.i("KassenzettelParser", "=== RAW OCR TEXT START ===\n$fullText\n=== RAW OCR TEXT END ===") } catch (_: Throwable) {}

            val ocrProducts = if (fullText.isNotBlank()) {
                parseReceiptText(fullText, corrections)
            } else {
                emptyList()
            }

            Pair(ocrProducts, uiPreviewBitmap)
        } catch (e: Throwable) {
            try { Log.e("KassenzettelParser", "Unerwarteter Fehler bei processPdf", e) } catch (_: Throwable) {}
            Pair(emptyList(), null)
        }
    }
}