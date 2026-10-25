package com.example.myapplication

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

object MhdOcrHelper {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun parseExpiryDateFromUri(context: Context, imageUri: Uri): String? {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val visionText = recognizer.process(image).await()
            extractDate(visionText.text)
        } catch (e: Exception) {
            null
        }
    }

    fun extractDate(rawText: String): String? {
        if (rawText.isBlank()) return null
        val patterns = listOf(
            Regex("""\b(\d{1,2})[./\-](\d{1,2})[./\-](\d{4})\b"""),
            Regex("""\b(\d{1,2})[./\-](\d{1,2})[./\-](\d{2})\b"""),
            Regex("""\b(\d{1,2})[./\-](\d{4})\b""")
        )
        for (regex in patterns) {
            val match = regex.find(rawText)
            if (match != null) {
                return match.value
            }
        }
        return null
    }

    fun parseDateToTimestamp(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val formats = listOf(
            "dd.MM.yyyy", "dd.MM.yy", "dd/MM/yyyy", "dd/MM/yy", "dd-MM-yyyy",
            "MM/yyyy", "MM.yyyy", "MM-yyyy"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.GERMANY).apply { isLenient = false }
                return sdf.parse(dateStr)?.time
            } catch (_: Exception) {}
        }
        return null
    }
}
