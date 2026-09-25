package com.example.myapplication

import com.example.myapplication.data.Product
import com.google.mlkit.vision.text.Text

/**
 * ReceiptParser als Alias & Wrapper für KassenzettelParser.
 */
object ReceiptParser {
    fun parseReceipt(visionText: Text, corrections: Map<String, String> = emptyMap()): List<Product> =
        KassenzettelParser.parseReceipt(visionText, corrections)

    fun parseReceiptText(text: String, corrections: Map<String, String> = emptyMap()): List<Product> =
        KassenzettelParser.parseReceiptText(text, corrections)
}
