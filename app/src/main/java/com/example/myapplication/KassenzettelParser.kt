package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import com.example.myapplication.data.CategoryDetector
import com.example.myapplication.data.FoodCategory
import com.example.myapplication.data.Product
import com.google.mlkit.vision.text.Text
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.text.TextRecognizer
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.round

object KassenzettelParser {

    data class ParseResult(
        val products: List<Product>,
        val supermarket: String?,
        val purchaseDate: Long,
        val totalAmount: Double,
        val sumItems: Double,
        val sumDeposit: Double,
        val sumDiscounts: Double,
        val calculatedTotal: Double,
        val isPlausible: Boolean,
        val difference: Double
    )

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

    // Kaufland: "Lübzer Pils 2 * 1,09"
    private val KAUFLAND_INLINE_MULT = Regex(
        """^(.*?)\s+(\d+(?:[,.]\d+)?)\s*[*xX]\s*(\d+[,.]\d{2})\s*(?:€\s*)?$"""
    )

    private val FOOTER_STOP_WORDS = setOf(
        "SUMME", "GESAMT", "ZU ZAHLEN", "TOTAL", "KARTENZAHLUNG",
        "BARGELD", "GEG. BAR", "RÜCKGELD", "RUECKGELD", "ZAHLBETRAG", "GUTHABEN",
        "STEUER %", "KUNDENBELEG", "TERMINAL", "VISA", "GIROCARD",
        "MASTERCARD", "TSE-SIGNATUR", "PAYBACK", "LIDL PAY", "EC-CASH",
        "EMV-AID", "TERMINAL-ID", "TRACE", "BELEG-NR", "KARTEN-NR",
        "BAR", "KREDITKARTE", "EC-KARTE", "MWST", "MEHRWERTSTEUER",
        "UST", "STEUER", "ENDSUMME", "ZWISCHENSUMME", "GESAMTBETRAG",
        "UNBAR", "GUTSCHEIN", "COUPON", "APPLE PAY", "GOOGLE PAY"
    )

    private val IGNORE_KEYWORDS = setOf(
        "PFANDWERT", "PFAND", "LEERGUT", "MEHRWEG", "EINWEG", "DPG",
        "PAPIERTRAGETASCHE", "KNOTENBEUTEL", "TRAGETASCHE", "PAPIERTASCHE", "EINKAUFSTÜTE", "EINKAUFSTUETE", "PERMANENTTASCHE",
        "BELEGKOPIE", "BONKOPIE", "HERZLICH WILLKOMMEN", "VIELEN DANK", "AUF WIEDERSEHEN", "IHR EINKAUF",
        "KAUFLAND", "LIDL", "REWE", "NAHKAUF", "ALDI", "DM-DROGERIE", "GLOBUS", "NETTO", "EDEKA", "PENNY", "ROSSMANN", "NORMA", "TEGUT",
        "STRASSE", "STR.", "WEIMAR", "JENA", "ERFURT", "ISSERSTEDT", "GMBH", "UST-ID", "DE1", "DE2",
        "EUR", "PREIS EUR", "PREIS", "SUMME", "GESAMT", "RABATT", "SOFORT-RABATT", "AKTION", "PFANDRÜCKGABE", "PFANDRUECKGABE",
        "MWST", "MEHRWERTSTEUER", "UST", "STEUER", "KARTENZAHLUNG", "EC-KARTE", "GIROCARD", "VISA", "MASTERCARD",
        "DATUM", "DATE", "UHRZEIT", "ZEIT", "TERMINAL", "KUNDE", "KASSIERER", "BEDIENER"
    )

    fun isDepositLine(upper: String): Boolean {
        return upper.contains("PFAND") || upper.contains("LEERGUT") ||
               upper.contains("MEHRWEG") || upper.contains("EINWEG") ||
               upper.contains("DPG") || upper.contains("TRAEGER") ||
               upper.contains("TRÄGER") || upper.contains("KASTEN")
    }

    fun isTaxLine(upper: String): Boolean {
        return upper.contains("MWST") || upper.contains("MEHRWERTSTEUER") ||
               Regex("""\bUST\b""").containsMatchIn(upper) || upper.contains("STEUERSATZ") ||
               upper.contains("STEUERBETRAG") || upper.contains("STEUER %") ||
               upper.contains("STEUER-BETRAG") ||
               Regex("""\b(?:A|B|1|2)\s*=\s*\d+([.,]\d+)?\s*%""").containsMatchIn(upper) ||
               Regex("""\b\d+([.,]\d+)?\s*%\s*(?:MWST|UST|STEUER)\b""").containsMatchIn(upper) ||
               (upper.contains("STEUER") && !upper.contains("STEUERMARKE"))
    }

    fun isPaymentLine(upper: String): Boolean {
        return upper.contains("KARTENZAHLUNG") || upper.contains("EC-KARTE") ||
               upper.contains("EC-CASH") || upper.contains("GIROCARD") ||
               upper.contains("VISA") || upper.contains("MASTERCARD") ||
               upper.contains("MAESTRO") || upper.contains("AMEX") ||
               upper.contains("V-PAY") || upper.contains("KREDITKARTE") ||
               upper.contains("TERMINAL") || upper.contains("KUNDENBELEG") ||
               upper.contains("HÄNDLERBELEG") || upper.contains("HAENDLERBELEG") ||
               upper.contains("PAYBACK") || upper.contains("LIDL PAY") ||
               upper.contains("APPLE PAY") || upper.contains("GOOGLE PAY") ||
               upper.contains("BARGELD") || upper.contains("GEG. BAR") ||
               upper.contains("GEGEBEN") || upper.contains("RÜCKGELD") ||
               upper.contains("RUECKGELD") || upper.contains("GUTSCHEIN") ||
               upper.contains("GUTSCHRIFT") || upper.contains("COUPON") ||
               upper.contains("BARAUSZAHLUNG") ||
               Regex("""\bBAR\b""").containsMatchIn(upper) ||
               Regex("""\bUNBAR\b""").containsMatchIn(upper)
    }

    fun isTotalOrSumLine(upper: String): Boolean {
        return upper.contains("SUMME") || upper.contains("GESAMT") ||
               upper.contains("ZU ZAHLEN") || upper.contains("TOTAL") ||
               upper.contains("ZAHLBETRAG") || upper.contains("GESAMTBETRAG") ||
               upper.contains("ENDSUMME") || upper.contains("ZWISCHENSUMME") ||
               upper.contains("RECHNUNGSBETRAG") || upper.contains("SALDO")
    }

    fun isDateOrTimeLine(line: String, upper: String): Boolean {
        if (upper.contains("DATUM") || upper.contains("UHRZEIT") ||
            (upper.contains("ZEIT") && !upper.contains("ZEITSCHRIFT") && !upper.contains("MAHLZEIT")) ||
            upper.contains("STAND:") || upper.contains("DATE")) return true
        val dateMatch = Regex("""\b\d{2}[./]\d{2}[./]\d{2,4}\b""").find(line)
        if (dateMatch != null) {
            val withoutDate = line.replace(dateMatch.value, "").trim()
            if (withoutDate.isBlank() || withoutDate.matches(Regex("""[\d:/\s.-]+"""))) return true
        }
        val timeMatch = Regex("""\b\d{2}:\d{2}(:\d{2})?\b""").find(line)
        if (timeMatch != null) {
            val withoutTime = line.replace(timeMatch.value, "").trim()
            if (withoutTime.isBlank() || withoutTime.matches(Regex("""[\d:/\s.-]+"""))) return true
        }
        return false
    }

    fun isNonFoodOrGarbage(upper: String): Boolean {
        if (upper.contains("PAPIERTRAGETASCHE") || upper.contains("TRAGETASCHE") ||
            upper.contains("KNOTENBEUTEL") || upper.contains("PAPIERTASCHE") ||
            upper.contains("PERMANENTTASCHE") || upper.contains("EINKAUFSTASCHE") ||
            upper.contains("EINKAUFSTÜTE") || upper.contains("EINKAUFSTUETE")) return true

        if (upper.contains("TSE-SIGNATUR") || upper.contains("TSE-SERIENNUMMER") ||
            upper.contains("TRANSAKTION") || upper.contains("BELEG-NR") ||
            upper.contains("BON-NR") || upper.contains("BONNR") ||
            upper.contains("TRACE-NR") || upper.contains("EMV-AID") ||
            upper.contains("TERMINAL-ID") || upper.contains("KASSEN-ID") ||
            upper.contains("KASSIERER") || upper.contains("BEDIENER") ||
            upper.contains("BELEGKOPIE") || upper.contains("BONKOPIE") ||
            upper.contains("HERZLICH WILLKOMMEN") || upper.contains("VIELEN DANK") ||
            upper.contains("AUF WIEDERSEHEN") || upper.contains("IHR EINKAUF")) return true

        if (upper.contains("WWW.") || upper.contains("HTTP:") || upper.contains("HTTPS:") ||
            upper.contains(".DE/") || upper.contains(".COM/") ||
            upper.contains("HOTLINE") || upper.contains("KUNDENSERVICE")) return true

        return false
    }

    fun isUnwantedReceiptLine(line: String, upper: String): Boolean {
        return isTotalOrSumLine(upper) || isTaxLine(upper) ||
               isPaymentLine(upper) || isDepositLine(upper) ||
               isDateOrTimeLine(line, upper) || isNonFoodOrGarbage(upper)
    }

    private val BRAND_DATABASE = mapOf(
        "SCHWW SCHINKEN" to "Schwarzwälder Schinken",
        "KNORR FIX CHILI C CAR" to "Knorr Fix Chili con Carne",
        "CASH CRANB MIX" to "Cashew-Cranberry-Mix",
        "ERDN GERÖS GES" to "Erdnüsse geröstet gesalzen",
        "H BRUSTFILET" to "Hähnchen-Brustfilet",
        "WAGNER ST PIZZA" to "Wagner Steinofen Pizza",
        "OUÄSE" to "Quäse",
        "EXSREIS" to "Expressreis",
        "AROTTENKRÜSTCHEN" to "Karottenkrüstchen",
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
        "WIENER" to "Wiener Würstchen",
        "KLC KIDNEYBOHNEN" to "K-Classic Kidneybohnen",
        "KLC.GEMÜSEMAIS" to "K-Classic Gemüsemais",
        "KLC.CASH.CRANB.MIX" to "K-Classic Cashew-Cranberry-Mix",
        "KLC.ERDN.GERÖS/GES" to "K-Classic Erdnüsse geröstet gesalzen",
        "K.SCHWW.SCHINKEN" to "K-Classic Schwarzwälder Schinken",
        "KPUR.H.BRUSTFILET" to "K-Purland Hähnchenbrustfilet",
        "KLC.WALNUSSKERNE" to "K-Classic Walnusskerne",
        "KLC CANTUCCINI" to "K-Classic Cantuccini",
        "BO SCHNITTBROETCHEN" to "Schnittbrötchen",
        "BO-BIO WEIZENBROETCHEN" to "Bio Weizenbrötchen",
        "BO-BIO DINKELRUSTI" to "Bio Dinkelrusti Brötchen",
        "BO-BIO BB VITALRUSTI" to "Bio Vitalrusti Brötchen",
        "TWQ MEDIUM" to "Thüringer Waldquell Medium",
        "TWQ KLASSIK" to "Thüringer Waldquell Classic",
        "GL CREME FRAICHE" to "Crème Fraîche",
        "GL JOGH." to "Joghurt",
        "BIO BB EIER" to "Bio Eier",
        "BIO BB ZITRONENSSAFT" to "Bio Zitronensaft",
        "BIO BB KRAEUT." to "Bio Kräuter",
        "MINIPFLAUMEN TOMATEN" to "Mini-Pflaumentomaten",
        "MILR.MUERITZER" to "Milram Müritzer Käse",
        "MILRAM RAHMKAESE" to "Milram Rahmkäse",
        "GW GARTENSALAT" to "Gartensalat",
        "MAGGI FIX CARBO" to "Maggi Fix Carbonara",
        "MOENCH.LANDBIER" to "Mönchshof Landbier",
        "MOENCHSHOF ZWICK" to "Mönchshof Zwickl",
        "MIREE DATTEL-CUR" to "Miree Dattel-Curry",
        "KOCHSCHINKEN O." to "Kochschinken",
        "CHERRYRISPENTOM." to "Cherry-Rispentomaten",
        "FIN.SWEET CHILI" to "Finesse Sweet Chili",
        "FINESSE HÄHNCHEN" to "Finesse Hähnchen",
        "FINESSE HAEHNCHEN" to "Finesse Hähnchen",
        "GG ENTE/SPINAT" to "Gourmet Gold Ente & Spinat",
        "GG LAMM/BOHN" to "Gourmet Gold Lamm & Bohnen",
        "GG PAST" to "Gourmet Gold Pastete",
        "GOUR.PASTETE" to "Gourmet Gold Pastete",
        "GOUR.PAST." to "Gourmet Gold Pastete",
        "BROTAUFSTR.THUNFISCH" to "Brotaufstrich Thunfisch",
        "BROT TOMA/MOZARELLA" to "Brotaufstrich Tomate-Mozzarella"
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
        val threshold = (avgHeight * 0.6).toInt().coerceIn(10, 15)

        for (line in sortedLines) {
            val top = line.boundingBox?.top ?: 0
            val existingRow = rows.find { abs((it.first().boundingBox?.top ?: 0) - top) < threshold }
            if (existingRow != null) existingRow.add(line) else rows.add(mutableListOf(line))
        }

        val reconstructedText = rows.joinToString("\n") { row ->
            row.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") { it.text }
        }

        return parseReceiptText(reconstructedText, corrections)
    }

    fun normalizeText(text: String): String {
        return text
            .replace('\u202F', ' ')
            .replace('\u00A0', ' ')
            .replace('\u2007', ' ')
            .replace('\uFEFF', ' ')
    }

    fun parseReceiptText(text: String, corrections: Map<String, String> = emptyMap(), filename: String? = null): List<Product> {
        return parseReceiptDetailed(text, corrections, filename).products
    }

    fun parseReceiptDetailed(text: String, corrections: Map<String, String> = emptyMap(), filename: String? = null): ParseResult {
        val normalizedText = normalizeText(text)
        val rawLines = normalizedText.lines()
        val upperText = normalizedText.uppercase()

        val supermarket = detectSupermarket(upperText, filename)
        val purchaseDate = extractDate(normalizedText, filename)
        val extractedTotal = extractBonTotal(rawLines)

        val isKaufland = supermarket == "Kaufland" || upperText.contains("K CARD") || upperText.contains("PREIS EUR")

        val (items, sumDeposit, sumDiscounts) = if (isKaufland) {
            parseKauflandInterleavedDetailed(rawLines, corrections)
        } else {
            parseStandardReceiptDetailed(rawLines, corrections, supermarket)
        }

        val finalItems = items.map { it.copy(supermarket = supermarket, purchaseDate = purchaseDate) }
        val sumItems = finalItems.sumOf { it.price }
        val calculatedTotal = Math.round((sumItems + sumDeposit - sumDiscounts) * 100.0) / 100.0
        val totalToCompare = if (extractedTotal > 0.0) extractedTotal else calculatedTotal
        val diff = Math.round((calculatedTotal - totalToCompare) * 100.0) / 100.0
        val isPlausible = abs(diff) <= 0.05

        return ParseResult(
            products = finalItems,
            supermarket = supermarket,
            purchaseDate = purchaseDate,
            totalAmount = totalToCompare,
            sumItems = Math.round(sumItems * 100.0) / 100.0,
            sumDeposit = Math.round(sumDeposit * 100.0) / 100.0,
            sumDiscounts = Math.round(sumDiscounts * 100.0) / 100.0,
            calculatedTotal = calculatedTotal,
            isPlausible = isPlausible,
            difference = diff
        )
    }

    private fun extractBonTotal(rawLines: List<String>): Double {
        val totalKeywords = listOf("SUMME EUR", "SUMME", "GESAMT", "ZU ZAHLEN", "TOTAL", "ZAHLBETRAG", "GESAMTBETRAG")
        for (line in rawLines.reversed()) {
            val upper = line.uppercase()
            if (totalKeywords.any { upper.contains(it) }) {
                val match = PRICE_REGEX.find(line)
                if (match != null) {
                    val price = abs(match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0)
                    if (price > 0.0) return price
                }
            }
        }
        return 0.0
    }

    fun detectSupermarket(text: String, filename: String? = null): String? {
        val upper = text.uppercase()
        val fnUpper = filename?.uppercase() ?: ""

        // Globus must be checked before Netto to avoid false positive from VAT table ("Netto Steuer Brutto")
        if (upper.contains("GLOBUS")) return "Globus"
        if (upper.contains("KAUFLAND") || upper.contains("K CARD") || upper.contains("KAUFLAND CARD")) return "Kaufland"
        if (upper.contains("REWE") || upper.contains("NAHKAUF")) return "Rewe"
        if (upper.contains("NETTO-ONLINE") || upper.contains("NETTO APP") || upper.contains("NETTO PLUS") ||
            upper.contains("DEIN EINKAUF VOM") ||
            fnUpper.contains("NETTO") ||
            Regex("""\bNETTO\b(?!\s*(?:EUR|%|STEUER|BRUTTO))""").containsMatchIn(upper) ||
            Regex("""FILIALE\s+\d+\s*/\s*POS\s+\d+\s*/\s*BON-NR""").containsMatchIn(upper)
        ) {
            return "Netto"
        }
        if (upper.contains("LIDL")) return "Lidl"
        if (upper.contains("ALDI")) return "Aldi"
        if (upper.contains("PENNY")) return "Penny"
        if (upper.contains("EDEKA")) return "Edeka"
        if (upper.contains("DM-DROGERIE") || upper.contains(" DM ")) return "dm"
        if (upper.contains("ROSSMANN")) return "Rossmann"
        if (upper.contains("NORMA")) return "Norma"
        if (upper.contains("TEGUT")) return "Tegut"
        return supermarketProfiles.find { p -> p.trigger.any { upper.contains(it.uppercase()) } }?.name
    }

    fun extractDate(text: String, filename: String? = null): Long {
        // 1. Explicit keyword: Datum / Einkauf vom / Stand / am
        val explicitPattern = Regex("""(?:Datum|Einkauf vom|Stand:?)\s*(\d{2})[./](\d{2})[./](\d{2,4})""", RegexOption.IGNORE_CASE)
        val mExplicit = explicitPattern.find(text)
        if (mExplicit != null) {
            val d = mExplicit.groupValues[1].toInt()
            val m = mExplicit.groupValues[2].toInt() - 1
            var y = mExplicit.groupValues[3].toInt()
            if (y < 100) y += 2000
            if (d in 1..31 && m in 0..11) {
                return createCalendarMillis(y, m, d)
            }
        }

        // 2. ISO timestamp (e.g. TSE-Start / TSE-Stop: 2026-09-18)
        val isoPattern = Regex("""\b(20\d{2})[-/.](\d{2})[-/.](\d{2})\b""")
        val mIso = isoPattern.find(text)
        if (mIso != null) {
            val y = mIso.groupValues[1].toInt()
            val m = mIso.groupValues[2].toInt() - 1
            val d = mIso.groupValues[3].toInt()
            if (d in 1..31 && m in 0..11) {
                return createCalendarMillis(y, m, d)
            }
        }

        // 3. General DD.MM.YYYY or DD.MM.YY with sanity check (day 1..31, month 1..12, year 2020..2035)
        val genPattern = Regex("""\b(\d{2})[./](\d{2})[./](\d{2,4})\b""")
        for (m in genPattern.findAll(text)) {
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt() - 1
            var y = m.groupValues[3].toInt()
            if (y < 100) y += 2000
            if (d in 1..31 && mo in 0..11 && y in 2020..2035) {
                return createCalendarMillis(y, mo, d)
            }
        }

        // 4. Fallback from filename (e.g. Netto_Kassenbon_20260502-130704.pdf or 20260726_...)
        if (filename != null) {
            val fnPattern = Regex("""(20\d{2})[-_]?(\d{2})[-_]?(\d{2})""")
            val mFn = fnPattern.find(filename)
            if (mFn != null) {
                val y = mFn.groupValues[1].toInt()
                val m = mFn.groupValues[2].toInt() - 1
                val d = mFn.groupValues[3].toInt()
                if (d in 1..31 && m in 0..11) {
                    return createCalendarMillis(y, m, d)
                }
            }
        }

        return System.currentTimeMillis()
    }

    private fun createCalendarMillis(year: Int, month0: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month0)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun parseKauflandInterleavedDetailed(
        rawLines: List<String>,
        corrections: Map<String, String>
    ): Triple<List<Product>, Double, Double> {
        val items = mutableListOf<Product>()
        var depositSum = 0.0
        var discountSum = 0.0
        var pendingName: String? = null
        var pendingQty = 1
        var pendingUnitPrice: Double? = null
        var pendingDeposit = false
        val supermarket = "Kaufland"

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val upper = line.uppercase()

            if (items.isNotEmpty() && isStopLine(upper)) break

            if (isDepositLine(upper)) {
                val inlineMult = KAUFLAND_INLINE_MULT.find(line) ?: MULTIPLIER_REGEX.find(line)
                if (inlineMult != null) {
                    val isKauflandMult = inlineMult == KAUFLAND_INLINE_MULT.find(line)
                    val qtyVal = inlineMult.groupValues[if (isKauflandMult) 2 else 1].replace(',', '.').toDoubleOrNull()?.toInt() ?: 1
                    val unitPrice = inlineMult.groupValues[if (isKauflandMult) 3 else 2].replace(',', '.').toDoubleOrNull() ?: 0.0
                    pendingDeposit = true
                    pendingQty = qtyVal
                    pendingUnitPrice = unitPrice
                    pendingName = null
                    continue
                }
                val match = PRICE_REGEX.find(line)
                if (match != null) {
                    val pfandVal = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
                    depositSum += pfandVal
                    pendingDeposit = false
                } else {
                    pendingDeposit = true
                }
                pendingName = null
                continue
            }

            if (isTaxLine(upper) || isPaymentLine(upper) || isDateOrTimeLine(line, upper)) {
                pendingName = null
                continue
            }

            if (isHeaderOrNoiseLine(line, upper) && !upper.contains("RABATT") && !upper.contains("SPAREN") && !upper.contains("MENGENRABATT") && !line.startsWith("-")) {
                pendingName = null
                continue
            }

            if (upper.contains("RABATT") || upper.contains("SPAREN") || upper.contains("MENGENRABATT") || line.startsWith("-")) {
                val discountMatch = PRICE_REGEX.find(line)
                if (discountMatch != null) {
                    val discountVal = abs(discountMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0)
                    if (items.isNotEmpty()) {
                        val last = items.last()
                        items[items.lastIndex] = last.copy(
                            price = Math.max(0.01, Math.round((last.price - discountVal) * 100.0) / 100.0)
                        )
                    } else {
                        discountSum += discountVal
                    }
                }
                pendingName = null
                pendingQty = 1
                pendingUnitPrice = null
                continue
            }

            // Kaufland inline multiplier: e.g. "Lübzer Pils 2 * 1,09" or "Gourmet Perle 2 * 0,33"
            val inlineMultMatch = KAUFLAND_INLINE_MULT.find(line)
            if (inlineMultMatch != null) {
                val articlePart = inlineMultMatch.groupValues[1].trim()
                val qtyVal = inlineMultMatch.groupValues[2].replace(',', '.').toDoubleOrNull()?.toInt() ?: 1
                val unitPrice = inlineMultMatch.groupValues[3].replace(',', '.').toDoubleOrNull() ?: 0.0
                if (isDepositLine(articlePart.uppercase())) {
                    pendingDeposit = true
                    pendingQty = qtyVal
                    pendingUnitPrice = unitPrice
                    pendingName = null
                } else {
                    pendingName = articlePart
                    pendingQty = qtyVal
                    pendingUnitPrice = unitPrice
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
                    abs(priceMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: (parsedQty * unitPrice))
                } else {
                    Math.round(parsedQty * unitPrice * 100.0) / 100.0
                }

                if (pendingName != null) {
                    addProductSafely(pendingName, lineTotal, parsedQty, items, corrections, supermarket)
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
                val priceVal = abs(priceMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0)
                if (pendingDeposit) {
                    val totPfand = if (pendingQty > 1 && pendingUnitPrice != null && abs(priceVal - pendingUnitPrice) < 0.05) {
                        pendingQty * pendingUnitPrice
                    } else priceVal
                    depositSum += totPfand
                    pendingDeposit = false
                    pendingName = null
                    pendingQty = 1
                    pendingUnitPrice = null
                    continue
                }
                var inlineArticle = line.substring(0, priceMatch.range.first).trim()
                inlineArticle = TAX_SUFFIX_REGEX.replace(inlineArticle, "").trim()
                inlineArticle = inlineArticle.replace(Regex("""[*#xX\s]+$"""), "").trim()

                if (inlineArticle.length >= 2 && !isHeaderOrNoiseLine(inlineArticle, inlineArticle.uppercase())) {
                    val totalToUse = if (pendingQty > 1 && pendingUnitPrice != null && pendingUnitPrice > 0.0 && abs(priceVal - pendingUnitPrice) < 0.05) {
                        pendingQty * pendingUnitPrice
                    } else priceVal
                    addProductSafely(inlineArticle, totalToUse, pendingQty, items, corrections, supermarket)
                    pendingName = null
                    pendingQty = 1
                    pendingUnitPrice = null
                } else if (pendingName != null && priceVal > 0.05) {
                    val totalToUse = if (pendingQty > 1 && pendingUnitPrice != null && pendingUnitPrice > 0.0 && abs(priceVal - pendingUnitPrice) < 0.05) {
                        pendingQty * pendingUnitPrice
                    } else priceVal
                    addProductSafely(pendingName, totalToUse, pendingQty, items, corrections, supermarket)
                    pendingName = null
                    pendingQty = 1
                    pendingUnitPrice = null
                }
                continue
            }

            if (line.length >= 2 && !isHeaderOrNoiseLine(line, upper)) {
                pendingName = line
            }
        }

        return Triple(bundleIdenticalProducts(items), depositSum, discountSum)
    }

    private fun parseStandardReceiptDetailed(
        rawLines: List<String>,
        corrections: Map<String, String>,
        detectedSupermarket: String? = null
    ): Triple<List<Product>, Double, Double> {
        val items = mutableListOf<Product>()
        var depositSum = 0.0
        var discountSum = 0.0
        var pendingName: String? = null
        var pendingQty = 1
        var pendingUnitPrice: Double? = null
        var pendingDeposit = false
        var pendingBarcode: String? = null
        var pendingDiscount = false

        val textSnippet = rawLines.take(5).joinToString(" ")
        val supermarket = detectedSupermarket ?: supermarketProfiles.find { p -> 
            p.trigger.any { textSnippet.contains(it, ignoreCase = true) } 
        }?.name

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val upper = line.uppercase()

            if (items.isNotEmpty() && isStopLine(upper)) break

            // Pfand / Leergut / Träger
            if (isDepositLine(upper)) {
                val priceMatch = PRICE_REGEX.find(line)
                if (priceMatch != null) {
                    val pfandVal = priceMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
                    depositSum += pfandVal
                    pendingDeposit = false
                } else {
                    pendingDeposit = true
                }
                pendingName = null
                continue
            }

            if (isTaxLine(upper) || isPaymentLine(upper) || isDateOrTimeLine(line, upper)) {
                pendingName = null
                continue
            }

            // Discount handling (including Netto Preisaenderung and negative amounts)
            if (line.startsWith("-") || upper.contains("RABATT") || upper.contains("PREISVORTEIL") ||
                upper.contains("AKTION") || upper.contains("SOFORT-RABATT") ||
                upper.contains("PREISAENDERUNG") || upper.contains("PREISÄNDERUNG") ||
                upper.contains("MENGENRABATT") || pendingDiscount) {
                val discountMatch = PRICE_REGEX.find(line)
                if (discountMatch != null) {
                    val discountVal = abs(discountMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0)
                    if (items.isNotEmpty()) {
                        val last = items.last()
                        items[items.lastIndex] = last.copy(
                            price = Math.max(0.01, Math.round((last.price - discountVal) * 100.0) / 100.0)
                        )
                    } else {
                        discountSum += discountVal
                    }
                    pendingDiscount = false
                } else {
                    pendingDiscount = true
                }
                pendingName = null
                pendingQty = 1
                pendingUnitPrice = null
                continue
            }

            // Barcode (e.g. Globus "#4009268333093" before product)
            val eanMatch = Regex("""^#\s*(\d{8,14})""").find(line)
            if (eanMatch != null) {
                pendingBarcode = eanMatch.groupValues[1]
                continue
            }

            if (isHeaderOrNoiseLine(line, upper) && !ALDI_MULT_REGEX.matches(line) && !LIDL_MULT_REGEX.matches(line) && !MULTIPLIER_REGEX.containsMatchIn(line) && !WEIGHING_REGEX.containsMatchIn(line)) {
                pendingName = null
                continue
            }

            // Weighing (e.g. "0,224 kg x 4,99 €/kg 1,12 2" or Netto "0,40 g x 1,79 EUR/g")
            val weighMatch = WEIGHING_REGEX.find(line)
            if (weighMatch != null) {
                val weightVal = weighMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 1.0
                val unitPrice = weighMatch.groupValues[3].replace(',', '.').toDoubleOrNull() ?: 0.0
                val g4 = weighMatch.groupValues.getOrNull(4) ?: ""
                val lineTotal = if (g4.isNotBlank()) {
                    abs(g4.replace(',', '.').toDoubleOrNull() ?: (weightVal * unitPrice))
                } else {
                    Math.round(weightVal * unitPrice * 100.0) / 100.0
                }

                if (pendingName != null) {
                    addProductSafely(pendingName, lineTotal, 1, items, corrections, supermarket, pendingBarcode)
                    if (items.isNotEmpty()) {
                        items[items.lastIndex] = items.last().copy(unit = "kg")
                    }
                    pendingBarcode = null
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
                val inlineName = aldiMatch.groupValues[3].trim()
                val priceStr = aldiMatch.groupValues[4].replace(',', '.')
                val price = priceStr.toDoubleOrNull() ?: 0.0
                val rawName = if (inlineName.count { it.isLetter() } >= 2) inlineName else pendingName
                if (rawName != null) {
                    addProductSafely(rawName, price, qty, items, corrections, supermarket, pendingBarcode)
                }
                pendingBarcode = null
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
                addProductSafely(rawName, price, qty, items, corrections, supermarket, pendingBarcode)
                pendingBarcode = null
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
                val priceMatches = PRICE_REGEX.findAll(lineNoMult).toList()
                val linePrice = if (priceMatches.isNotEmpty()) {
                    abs(priceMatches.last().groupValues[1].replace(',', '.').toDoubleOrNull() ?: (parsedQty * unitPrice))
                } else 0.0

                if (priceMatches.isNotEmpty()) {
                    val lastPriceMatch = priceMatches.last()
                    var itemNamePart = lineNoMult.substring(0, lastPriceMatch.range.first).trim()
                    itemNamePart = TAX_SUFFIX_REGEX.replace(itemNamePart, "").trim()
                    itemNamePart = itemNamePart.replace(Regex("""^[AB12*#€]\s+""", RegexOption.IGNORE_CASE), "").trim()
                    
                    if (itemNamePart.length >= 2 && itemNamePart.count { it.isLetter() } >= 2) {
                        addProductSafely(itemNamePart, linePrice, parsedQty, items, corrections, supermarket, pendingBarcode)
                        pendingBarcode = null
                        pendingName = null
                        pendingQty = 1
                        continue
                    }
                }

                if (pendingName != null) {
                    val totalToUse = if (linePrice > 0.05) linePrice else unitPrice
                    addProductSafely(pendingName, totalToUse, parsedQty, items, corrections, supermarket, pendingBarcode)
                    pendingBarcode = null
                    pendingName = null
                    pendingQty = 1
                } else if (items.isNotEmpty()) {
                    val last = items.last()
                    val expectedTotal = Math.round(parsedQty * unitPrice * 100.0) / 100.0
                    if (abs(last.price - expectedTotal) < 0.08 || abs(last.price - unitPrice) < 0.08 || abs((last.price / parsedQty) - unitPrice) < 0.08) {
                        val updatedPrice = if (last.price < expectedTotal - 0.05) expectedTotal else last.price
                        items[items.lastIndex] = last.copy(quantity = parsedQty, price = updatedPrice)
                        pendingQty = 1
                    } else {
                        pendingQty = parsedQty
                        pendingUnitPrice = unitPrice
                    }
                } else {
                    pendingQty = parsedQty
                    pendingUnitPrice = unitPrice
                }
                continue
            }

            val stdMatch = STANDARD_LINE_REGEX.matchEntire(line)
            if (stdMatch != null && !MULTIPLIER_REGEX.containsMatchIn(line)) {
                val rawName = stdMatch.groupValues[1].trim()
                val priceStr = stdMatch.groupValues[2].replace(',', '.')
                val price = priceStr.toDoubleOrNull() ?: 0.0

                if (rawName.count { it.isLetter() } >= 2) {
                    val totalToUse = if (pendingQty > 1 && pendingUnitPrice != null && abs(price - pendingUnitPrice) < 0.05) {
                        pendingQty * pendingUnitPrice
                    } else price
                    addProductSafely(rawName, totalToUse, pendingQty, items, corrections, supermarket, pendingBarcode)
                    pendingBarcode = null
                    pendingName = null
                    pendingQty = 1
                    pendingUnitPrice = null
                    continue
                }
            }

            val priceMatch = PRICE_REGEX.find(line)
            if (priceMatch != null) {
                val priceVal = abs(priceMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0)
                if (pendingDeposit) {
                    depositSum += priceVal
                    pendingDeposit = false
                    pendingName = null
                    continue
                }
                val lineWithoutPrice = line.replace(PRICE_REGEX, "")
                    .replace(Regex("""^[*#\s.-]+"""), "")
                    .replace(Regex("""[AB12*#€0-9\s]+$"""), "")
                    .trim()
                if (pendingName != null && lineWithoutPrice.isEmpty() && priceVal > 0.05) {
                    val totalToUse = if (pendingQty > 1 && pendingUnitPrice != null && abs(priceVal - pendingUnitPrice) < 0.05) {
                        pendingQty * pendingUnitPrice
                    } else priceVal
                    addProductSafely(pendingName, totalToUse, pendingQty, items, corrections, supermarket, pendingBarcode)
                    pendingBarcode = null
                    pendingName = null
                    pendingQty = 1
                    pendingUnitPrice = null
                } else if (pendingName == null && lineWithoutPrice.isEmpty() && items.isNotEmpty() && priceVal > 0.05) {
                    val last = items.last()
                    if (last.quantity > 1) {
                        items[items.lastIndex] = last.copy(price = priceVal)
                    }
                } else if (lineWithoutPrice.length >= 3 && !isHeaderOrNoiseLine(lineWithoutPrice, lineWithoutPrice.uppercase())) {
                    val totalToUse = if (pendingQty > 1 && pendingUnitPrice != null && abs(priceVal - pendingUnitPrice) < 0.05) {
                        pendingQty * pendingUnitPrice
                    } else priceVal
                    addProductSafely(lineWithoutPrice, totalToUse, pendingQty, items, corrections, supermarket, pendingBarcode)
                    pendingBarcode = null
                    pendingName = null
                    pendingQty = 1
                    pendingUnitPrice = null
                }
                continue
            }

            if (line.length >= 2 && !isHeaderOrNoiseLine(line, upper) && !isStopLine(upper)) {
                pendingName = line
            }
        }

        return Triple(bundleIdenticalProducts(items), depositSum, discountSum)
    }

    private fun isStopLine(upper: String): Boolean {
        if (isTotalOrSumLine(upper)) return true
        if (isPaymentLine(upper)) return true
        if (upper.contains("ANZ. ARTIKEL") || upper.contains("ANZ.ARTIKEL")) return true
        if (upper.contains("NETTO APP") || upper.contains("NETTO PLUS APP")) return true
        if (upper.contains("PAYBACK")) return true
        if (upper.contains("BARAUSZAHLUNG")) return true
        if (FOOTER_STOP_WORDS.any { 
            if (it.length <= 4) {
                upper == it || upper.startsWith("$it ") || upper.contains(" $it ") || upper.endsWith(" $it")
            } else {
                upper.contains(it)
            }
        }) return true
        if (Regex("""\bBAR\b""").containsMatchIn(upper)) return true
        return false
    }

    private fun isHeaderOrNoiseLine(line: String, upper: String): Boolean {
        if (upper in listOf("EUR", "PREIS EUR", "PREIS", "WÄHRUNG", "WAEHRUNG")) return true
        if (upper.startsWith("DEIN EINKAUF VOM") || upper.startsWith("FILIALE ")) return true
        if (upper.startsWith("*** EBON ***")) return true
        if (isUnwantedReceiptLine(line, upper)) return true
        if (Regex("""\b\d{5}\b""").containsMatchIn(line)) return true
        if (upper.contains("STRASSE") || upper.contains("STRAßE") || upper.contains("STR.") ||
            Regex("""\bSTR\b""").containsMatchIn(upper) ||
            Regex("""\b(?:WEG|GASSE|ALLEE|PLATZ|HAUSNR|PLZ|ISSERSTEDT|JENA)\b""").containsMatchIn(upper)) return true
        if (upper.contains("GMBH") || upper.contains("CO. KG") || upper.contains("CO.KG") ||
            upper.contains(" CO KG") || Regex("""\b(?:E\.K\.|E\.V\.)\b""").containsMatchIn(upper)) return true
        if (Regex("""\b(?:TEL|TELEFON|FON|FAX|UST-ID|ST-NR|STNR|ST\.-NR|STEUER)\b""").containsMatchIn(upper) ||
            Regex("""\bDE\d+""").containsMatchIn(upper) ||
            Regex("""(?:\+49|0\d{2,4})[\s/-]?\d{5,}""").containsMatchIn(line) ||
            Regex("""\b\d{3,5}[/-]\d{3,8}\b""").containsMatchIn(line)) return true

        return isNoise(upper)
    }

    private fun isNoise(upper: String): Boolean {
        if (upper.isBlank()) return true
        if (upper.startsWith("#") || upper.matches(Regex("""^#\d+.*"""))) return true
        if (upper == "EUR" || upper == "PREIS EUR" || upper == "PREIS") return true
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
        supermarket: String? = null,
        barcode: String? = null
    ) {
        if (price <= 0.05 || price > 250.0) return

        val cleanName = rawName.trim()
        val upperRaw = cleanName.uppercase()
        if (isUnwantedReceiptLine(cleanName, upperRaw) || isHeaderOrNoiseLine(cleanName, upperRaw) || isStopLine(upperRaw)) return
        if (cleanName.matches(Regex("""\d+\s*Stk.*""", RegexOption.IGNORE_CASE))) return
        if (cleanName.length < 2) return

        val leadingQtyMatch = Regex("""^(\d+)\s*(?:5tk|stk|st|x)\s+""", RegexOption.IGNORE_CASE).find(cleanName)
        var initialQty = qty
        if (leadingQtyMatch != null && initialQty == 1) {
            initialQty = leadingQtyMatch.groupValues[1].toIntOrNull() ?: 1
        }

        var clean = cleanName
            .replace(Regex("""^\d+\s*(?:5tk|stk|st|x)\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^[AB12*#€]\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b\d+([.,]\d+)?%"""), "")
            .replace(Regex("""\s+\d+[,.]\d{2}\s*€?$"""), "")
            .replace(Regex("""\s+\d{1,2}\s*$"""), "")
            .replace(Regex("""[,.]\d{2}\s*$"""), "")
            .replace(Regex("""\s+-QS\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b\d+\s*(?:stk|st|stück|stueck)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b\d+([.,]\d+)?\s*(?:kg|g|l|ml)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^[-*#\s.+]+"""), "")
            .replace(Regex("""[-*#\s.+]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val cleanUpper = clean.uppercase()
        if (cleanUpper == "EUR" || cleanUpper == "PREIS EUR" || cleanUpper == "PREIS" || cleanUpper.length < 3) return
        if (clean.count { it.isLetter() } < 2) return
        if (isUnwantedReceiptLine(clean, cleanUpper)) return

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
        finalName = finalName.replace(Regex("""^[AB12*#€]\s+""", RegexOption.IGNORE_CASE), "").trim()

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

        val cleanUpperCheck = finalName.uppercase()
        if (isUnwantedReceiptLine(finalName, cleanUpperCheck)) return

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
                barcode = barcode,
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
            val discount = abs(discountStr.toDoubleOrNull() ?: 0.0)
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
                abs(existingUnitPrice - itemUnitPrice) < 0.05
            }
            if (idx != -1) {
                val existing = bundled[idx]
                bundled[idx] = existing.copy(
                    quantity = existing.quantity + item.quantity,
                    price = existing.price + item.price,
                    barcode = existing.barcode ?: item.barcode
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
            combined.contains("kiste") || combined.contains("kasten") || combined.contains("20x") || combined.contains("24x") || combined.contains("12x") || combined.contains("träger") || combined.contains("traeger") ||
            ((combined.contains("wasser") || combined.contains("bier") || combined.contains("cola") || combined.contains("limo") || combined.contains("waldquell")) && price >= 3.50) -> "Kiste"

            combined.contains("brötchen") || combined.contains("broetchen") || combined.contains("rusti") || combined.contains("krüstchen") -> "Stk."

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

            combined.contains("dose") || combined.contains("red bull") || combined.contains("monster energy") ||
            combined.contains("energy") || combined.contains("thunfisch") || combined.contains("tuna") ||
            combined.contains("konserve") || combined.contains("eintopf") || combined.contains("gestückelte tomaten") ||
            combined.contains("schältomaten") || combined.contains("mais") || combined.contains("sardinen") -> "Dose"

            combined.contains("glas") || combined.contains("marmelade") || combined.contains("konfitüre") ||
            combined.contains("honig") || combined.contains("senf") || combined.contains("nutella") ||
            combined.contains("apfelmus") || combined.contains("gewürzgurten") || combined.contains("gurken") ||
            combined.contains("rotkohl") || combined.contains("sauerkraut") || combined.contains("sauerkirschen") ||
            combined.contains("pesto") || combined.contains("oliven") || combined.contains("kapern") ||
            combined.contains("babybrei") -> "Glas"

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

            combined.contains("tüte") || combined.contains("beutel") || combined.contains("chips") ||
            combined.contains("flips") || combined.contains("popcorn") || combined.contains("gummibärchen") ||
            combined.contains("haribo") || combined.contains("bonbons") || combined.contains("pommes") ||
            combined.contains("gefriergemüse") || combined.contains("reibekäse") || combined.contains("streukäse") ||
            combined.contains("salatbeutel") || combined.contains("nüsse") || combined.contains("studentenfutter") -> "Tüte"

            combined.contains("schale") || combined.contains("erdbeeren") || combined.contains("himbeeren") ||
            combined.contains("heidelbeeren") || combined.contains("weintrauben") || combined.contains("champignons") ||
            combined.contains("pilze") || combined.contains("cherrytomaten") || combined.contains("hackfleisch") -> "Schale"

            combined.contains("netz") || combined.contains("kartoffeln") || combined.contains("zwiebeln") ||
            combined.contains("orangen") || combined.contains("mandarinen") || combined.contains("clementinen") ||
            combined.contains("zitronen") || combined.contains("knoblauch") -> "Netz"

            combined.contains("bund") || combined.contains("radieschen") || combined.contains("lauchzwiebeln") ||
            combined.contains("frühlingszwiebeln") || combined.contains("petersilie") || combined.contains("dill") ||
            combined.contains("schnittlauch") -> "Bund"

            combined.contains("tafel") || combined.contains("schokolade") || combined.contains("ritter sport") ||
            combined.contains("milka") || combined.contains("lindt") -> "Tafel"

            combined.contains("rolle") || combined.contains("küchenrolle") || combined.contains("toilettenpapier") ||
            combined.contains("klopapier") || combined.contains("müllbeutel") || combined.contains("alufolie") ||
            combined.contains("backpapier") || combined.contains("prinzenrolle") -> "Rolle"

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

    fun processPdf(
        context: Context,
        pdfFile: File,
        recognizer: TextRecognizer,
        corrections: Map<String, String> = emptyMap(),
        originalFileName: String? = null
    ): Pair<List<Product>, Bitmap?> {
        return PdfReceiptHelper.processPdf(context, pdfFile, recognizer, corrections, originalFileName)
    }
}
