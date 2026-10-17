package com.example.myapplication

import com.example.myapplication.data.Product
import java.util.Locale

/** Bereitet OCR-Ergebnisse für die Importvorschau vor, ohne Android-Abhängigkeiten. */
object ReceiptImportSanitizer {

    fun prepareForImport(products: List<Product>): List<Product> = products
        .asSequence()
        .map { product ->
            product.copy(
                name = product.name.trim(),
                quantity = product.quantity.coerceIn(1, 99)
            )
        }
        .filter { product ->
            product.name.length >= 3 &&
                product.name != "___IGNORE___" &&
                product.price in 0.01..399.99
        }
        .groupBy { product ->
            product.name.lowercase() to String.format(Locale.US, "%.2f", product.price)
        }
        .values
        .map { matchingProducts ->
            val product = matchingProducts.first()
            product.copy(quantity = matchingProducts.sumOf { it.quantity }.coerceAtMost(99))
        }
}
