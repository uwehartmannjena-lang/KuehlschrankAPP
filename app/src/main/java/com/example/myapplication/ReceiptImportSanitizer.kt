package com.example.myapplication

import com.example.myapplication.data.Product
import java.util.Locale

/** Bereitet OCR-Ergebnisse für die Importvorschau vor, ohne Android-Abhängigkeiten. */
object ReceiptImportSanitizer {

    /**
     * Bereinigt Kassenbon-Kürzel und Mengenangaben für eine höhere Trefferquote bei OpenFoodFacts.
     */
    fun cleanSearchTerm(rawName: String): String {
        return rawName
            .replace(Regex("""\bK\.""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bKLC\.""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bTH\.WQ\.?\b""", RegexOption.IGNORE_CASE), "Thüringer Waldquell")
            .replace(Regex("""\bWaldqüll\b""", RegexOption.IGNORE_CASE), "Waldquell")
            .replace(Regex("""\bPRES\.?\b""", RegexOption.IGNORE_CASE), "Président")
            .replace(Regex("""\bES\.?\b""", RegexOption.IGNORE_CASE), "Esmara")
            .replace(Regex("""\bBAUTZ\.?\b""", RegexOption.IGNORE_CASE), "Bautz'ner")
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
            
            product.copy(
                name = product.name.trim(),
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
            val unitPrice = if (product.quantity > 0) product.price / product.quantity else product.price
            product.name.lowercase() to String.format(Locale.US, "%.2f", unitPrice)
        }
        .values
        .map { matchingProducts ->
            val product = matchingProducts.first()
            val sumQty = matchingProducts.sumOf { it.quantity }.coerceAtMost(99)
            val sumPrice = matchingProducts.sumOf { it.price }
            product.copy(quantity = sumQty, price = sumPrice)
        }
}
