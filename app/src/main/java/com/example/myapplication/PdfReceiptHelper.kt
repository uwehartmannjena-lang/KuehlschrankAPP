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

object PdfReceiptHelper {

    private const val TAG = "PdfReceiptHelper"
    private const val CHUNK_MAX_HEIGHT = 2000

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
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

            page = renderer.openPage(pageIndex)
            val scale = 1200f / page.width.toFloat()
            val targetWidth = 1200
            val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            bitmap
        } catch (e: Throwable) {
            Log.e(TAG, "Fehler beim Rendern der PDF-Seite", e)
            System.gc()
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
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

            // 1. Vorrangige Textextraktion (Offline)
            val embeddedText = extractEmbeddedText(pdfFile)
            if (embeddedText.isNotBlank()) {
                val parsedProducts = try {
                    ReceiptParser.parseReceiptText(embeddedText, corrections)
                } catch (e: Throwable) {
                    emptyList()
                }
                if (parsedProducts.isNotEmpty()) {
                    val uiPreviewBitmap = renderPageBitmap(pdfFile, 0)
                    return Pair(parsedProducts, uiPreviewBitmap)
                }
            }

            // 2. OCR-Fallback mit ML Kit (Offline)
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
                            val scale = 1200f / page.width.toFloat()
                            val targetWidth = 1200
                            val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)

                            fullPageBitmap = try {
                                Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            } catch (oom: OutOfMemoryError) {
                                System.gc()
                                continue
                            }
                            page.render(fullPageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            var y = 0
                            while (y < targetHeight) {
                                val currentChunkHeight = minOf(CHUNK_MAX_HEIGHT, targetHeight - y)
                                val chunkBitmap = Bitmap.createBitmap(fullPageBitmap, 0, y, targetWidth, currentChunkHeight)
                                try {
                                    val image = InputImage.fromBitmap(chunkBitmap, 0)
                                    // REINES OFFLINE ML-KIT!
                                    val visionText = Tasks.await(recognizer.process(image))
                                    if (visionText != null && visionText.text.isNotBlank()) {
                                        ocrTextChunks.add(visionText.text)
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
                renderer?.close()
                pfd?.close()
            }

            val ocrProducts = if (ocrTextChunks.isNotEmpty()) {
                ReceiptParser.parseReceiptChunks(ocrTextChunks, corrections)
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