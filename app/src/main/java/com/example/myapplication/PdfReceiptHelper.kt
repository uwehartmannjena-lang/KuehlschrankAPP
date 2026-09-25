package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.myapplication.data.Product
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Hilfsklasse für den PDF-Import von Kassenbons.
 * - Vorrangige Textextraktion: Eingebetteten Text direkt aus dem PDF-Datenstrom auslesen.
 * - Hochauflösendes Rendern für OCR-Fallback via PdfRenderer mit exakter Seitenverhältnistreue (mind. 3.0f - 4.0f Skalierung, >= 300 DPI).
 */
object PdfReceiptHelper {

    private const val TAG = "PdfReceiptHelper"
    const val OCR_SCALE_FACTOR = 3.5f

    /**
     * Versucht zuerst eingebetteten Text direkt aus dem PDF auszulesen (PDFBox).
     */
    fun extractEmbeddedText(pdfFile: File): String {
        return try {
            val document = PDDocument.load(pdfFile)
            val stripper = PDFTextStripper()
            val textBuilder = StringBuilder()
            for (pageIndex in 0 until document.numberOfPages) {
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                val text = stripper.getText(document)
                if (!text.isNullOrBlank()) {
                    textBuilder.append(text).append("\n")
                }
            }
            document.close()
            textBuilder.toString().trim()
        } catch (e: Exception) {
            Log.w(TAG, "Eingebetteter PDF-Text konnte nicht gelesen werden", e)
            ""
        }
    }

    private fun String?.isNullOrBlank(): Boolean = this == null || this.isBlank()

    /**
     * Rendert eine PDF-Seite hochauflösend (mind. 3.5f Skalierung) mit strikter Einhaltung des Seitenverhältnisses.
     */
    fun renderPageBitmap(pdfFile: File, pageIndex: Int = 0, scale: Float = OCR_SCALE_FACTOR): Bitmap? {
        return try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return null
            }
            val page = renderer.openPage(pageIndex)
            val targetWidth = (page.width * scale).toInt()
            val targetHeight = (page.height * scale).toInt()
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()
            renderer.close()
            pfd.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Rendern der PDF-Seite $pageIndex mit Skalierung $scale", e)
            null
        }
    }

    /**
     * Führt den vollständigen PDF-Import durch:
     * 1. Vorrangig eingebetteten Text extrahieren.
     * 2. Falls kein Text oder 0 Artikel gefunden, hochauflösende OCR-Seitenverarbeitung durchführen.
     */
    fun processPdf(
        context: Context,
        pdfFile: File,
        recognizer: TextRecognizer,
        corrections: Map<String, String> = emptyMap()
    ): Pair<List<Product>, Bitmap?> {
        // 1. Vorrangige Textextraktion
        val embeddedText = extractEmbeddedText(pdfFile)
        if (embeddedText.isNotBlank()) {
            val parsedProducts = KassenzettelParser.parseReceiptText(embeddedText, corrections)
            if (parsedProducts.isNotEmpty()) {
                val previewBitmap = renderPageBitmap(pdfFile, 0, scale = 2.0f)
                return Pair(parsedProducts, previewBitmap)
            }
        }

        // 2. Hochauflösendes Rendern für OCR-Fallback
        val ocrProducts = mutableListOf<Product>()
        var previewBitmap: Bitmap? = null

        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val targetWidth = (page.width * OCR_SCALE_FACTOR).toInt()
                val targetHeight = (page.height * OCR_SCALE_FACTOR).toInt()
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                if (previewBitmap == null) {
                    previewBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                }

                val image = InputImage.fromBitmap(bitmap, 0)
                val visionText = Tasks.await(recognizer.process(image))
                if (visionText != null && visionText.text.isNotBlank()) {
                    val products = KassenzettelParser.parseReceipt(visionText, corrections)
                    ocrProducts.addAll(products)
                }
                bitmap.recycle()
                page.close()
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei OCR-Fallback für PDF", e)
        }

        return Pair(ocrProducts, previewBitmap)
    }
}
