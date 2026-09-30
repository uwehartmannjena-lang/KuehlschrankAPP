package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.myapplication.data.Product
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

/**
 * Hilfsklasse für den PDF-Import von Kassenbons.
 * - Vorrangige Textextraktion: Eingebetteten Text direkt aus dem PDF-Datenstrom auslesen.
 * - Hochauflösendes Rendern für OCR-Fallback via PdfRenderer mit exakter Seitenverhältnistreue.
 * - Speicher- & UI-Schutz: Erzeugt ZWEI Bitmaps. Das hochauflösende OCR-Bitmap wird sofort recycled. Die Compose UI erhält nur ein leichtes Thumbnail (max. 800px).
 * - Sicheres Fehler-Handling: Fängt OutOfMemoryError und Throwables ab.
 */
object PdfReceiptHelper {

    private const val TAG = "PdfReceiptHelper"
    private const val CHUNK_MAX_HEIGHT = 2000

    /**
     * Versucht zuerst eingebetteten Text direkt aus dem PDF auszulesen (PDFBox).
     */
    fun extractEmbeddedText(pdfFile: File): String {
        return try {
            if (!pdfFile.exists() || pdfFile.length() == 0L) return ""
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
        } catch (e: Throwable) {
            Log.w(TAG, "Eingebetteter PDF-Text konnte nicht gelesen werden", e)
            ""
        }
    }

    private fun String?.isNullOrBlank(): Boolean = this == null || this.isBlank()

    /**
     * Rendert eine PDF-Seite hochauflösend mit exakter Seitenverhältnistreue.
     */
    fun renderPageBitmap(
        pdfFile: File,
        pageIndex: Int = 0
    ): Bitmap? {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY) ?: return null
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                return null
            }
            page = renderer.openPage(pageIndex)

            val aspectRatio = page.width.toFloat() / page.height.toFloat()
            val targetWidth = 1600
            val targetHeight = (targetWidth / aspectRatio).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            bitmap
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError beim Rendern der PDF-Seite $pageIndex", e)
            System.gc()
            null
        } catch (e: Throwable) {
            Log.e(TAG, "Fehler beim Rendern der PDF-Seite $pageIndex", e)
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Führt den vollständigen PDF-Import durch:
     * 1. Vorrangig eingebetteten Text extrahieren.
     * 2. Falls kein Text oder 0 Artikel gefunden, hochauflösende OCR-Seitenverarbeitung durchführen.
     * 3. Sendet das gerenderte Thumbnail an die UI zurück.
     */
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

            // 1. Vorrangige Textextraktion aus eingebettetem PDF-Textstream
            val embeddedText = extractEmbeddedText(pdfFile)
            if (embeddedText.isNotBlank()) {
                val parsedProducts = try {
                    KassenzettelParser.parseReceiptText(embeddedText, corrections)
                } catch (e: Throwable) {
                    emptyList()
                }
                if (parsedProducts.isNotEmpty()) {
                    val uiPreviewBitmap = renderPageBitmap(pdfFile, 0)
                    return Pair(parsedProducts, uiPreviewBitmap)
                }
            }

            // 2. OCR-Fallback: Hochauflösendes Bitmap mit fester Breite + Chunking in 2000px Blöcke
            val uiPreviewBitmap = renderPageBitmap(pdfFile, 0)
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
                            val aspectRatio = page.width.toFloat() / page.height.toFloat()
                            val targetWidth = 1600
                            val targetHeight = (targetWidth / aspectRatio).toInt().coerceAtLeast(1)

                            fullPageBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            page.render(fullPageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            // Chunking in handliche Blöcke (je 2000px Höhe)
                            val chunkHeightMax = CHUNK_MAX_HEIGHT
                            var y = 0
                            while (y < targetHeight) {
                                val currentChunkHeight = minOf(chunkHeightMax, targetHeight - y)
                                val chunkBitmap = Bitmap.createBitmap(fullPageBitmap, 0, y, targetWidth, currentChunkHeight)
                                try {
                                    val image = InputImage.fromBitmap(chunkBitmap, 0)
                                    val visionText = Tasks.await(recognizer.process(image))
                                    if (visionText != null && visionText.text.isNotBlank()) {
                                        ocrTextChunks.add(visionText.text)
                                    }
                                } catch (e: Throwable) {
                                    Log.e(TAG, "Fehler bei OCR-Chunk y=$y auf Seite $i", e)
                                } finally {
                                    try { chunkBitmap.recycle() } catch (_: Exception) {}
                                }
                                y += currentChunkHeight
                            }
                        } catch (oom: OutOfMemoryError) {
                            Log.e(TAG, "OutOfMemoryError auf PDF-Seite $i", oom)
                            System.gc()
                        } catch (t: Throwable) {
                            Log.e(TAG, "Fehler bei OCR-Verarbeitung von PDF-Seite $i", t)
                        } finally {
                            try { fullPageBitmap?.recycle() } catch (_: Exception) {}
                            try { page?.close() } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Fehler bei OCR-Fallback für PDF", e)
            } finally {
                try { renderer?.close() } catch (_: Exception) {}
                try { pfd?.close() } catch (_: Exception) {}
            }

            // 3. OCR-Ergebnisse der verschiedenen Chunks sauber zusammenführen, bevor der Parser startet
            val fullOcrText = ocrTextChunks.joinToString("\n")
            val ocrProducts = if (fullOcrText.isNotBlank()) {
                KassenzettelParser.parseReceiptText(fullOcrText, corrections)
            } else {
                emptyList()
            }

            Pair(ocrProducts, uiPreviewBitmap)
        } catch (e: Throwable) {
            Log.e(TAG, "Unerwarteter Fehler bei processPdf", e)
            Pair(emptyList(), null)
        }
    }
}
