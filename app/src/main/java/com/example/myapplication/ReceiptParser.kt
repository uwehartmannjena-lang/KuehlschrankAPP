package com.example.myapplication

import com.example.myapplication.data.Product
import com.google.mlkit.vision.text.Text

/**
 * ReceiptParser als Alias & Wrapper für KassenzettelParser.
 * Sämtliche Aufrufe sind try-catch abgesichert, um NullPointerExceptions und Abstürze zu verhindern.
 * Bietet eine saubere Zusammenführung von OCR-Text-Chunks vor der Parser-Verarbeitung.
 */
object ReceiptParser {

    private val TRAILING_TAX_SUFFIX_REGEX = Regex("""\s+(?:[AB12*#€]\s*|\d\s*)+$""", RegexOption.IGNORE_CASE)

    fun cleanGlobusTaxSuffix(text: String): String {
        if (text.isBlank()) return ""
        return text.lines().joinToString("\n") { line ->
            line.replace(TRAILING_TAX_SUFFIX_REGEX, "").trim()
        }
    }

    fun parseReceipt(visionText: Text, corrections: Map<String, String> = emptyMap()): List<Product> = try {
        KassenzettelParser.parseReceipt(visionText, corrections)
    } catch (e: Exception) {
        emptyList()
    }

    fun parseReceiptText(text: String, corrections: Map<String, String> = emptyMap()): List<Product> = try {
        val cleanedText = cleanGlobusTaxSuffix(text)
        KassenzettelParser.parseReceiptText(cleanedText, corrections)
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Führt OCR-Ergebnisse aus mehreren Chunks (z.B. bei langen Kassenbons)
     * sauber zusammen, bevor die Regex-Prüfungen auf Markennamen und Preise starten.
     */
    fun parseReceiptChunks(chunks: List<String>, corrections: Map<String, String> = emptyMap()): List<Product> = try {
        val mergedText = chunks.filter { it.isNotBlank() }.joinToString("\n") { cleanGlobusTaxSuffix(it) }
        KassenzettelParser.parseReceiptText(mergedText, corrections)
    } catch (e: Exception) {
        emptyList()
    }
}
