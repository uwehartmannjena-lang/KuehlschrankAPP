package com.example.myapplication

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.ConsumedItem
import com.example.myapplication.data.FridgeDao
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.StatisticsHelper
import com.example.myapplication.data.StatisticsTimeFrame
import com.example.myapplication.data.WastedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatisticsViewModel(
    application: Application,
    private val dao: FridgeDao = AppDatabase.getDatabase(application).fridgeItemDao()
) : AndroidViewModel(application) {

    val allItems: StateFlow<List<FridgeItem>> = dao.getAllFridgeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wastedItems: StateFlow<List<WastedItem>> = dao.getAllWastedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val consumedItems: StateFlow<List<ConsumedItem>> = dao.getAllConsumedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isExportingPdf = MutableStateFlow(false)
    val isExportingPdf: StateFlow<Boolean> = _isExportingPdf.asStateFlow()

    fun exportStatisticsPdf(
        context: Context,
        timeFrame: StatisticsTimeFrame = StatisticsTimeFrame.DIESER_MONAT,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        if (_isExportingPdf.value) return
        _isExportingPdf.value = true

        viewModelScope.launch(Dispatchers.IO) {
            var pdfDocument: PdfDocument? = null
            try {
                val currentItems = dao.getAllFridgeItems().first()
                val allWasted = dao.getAllWastedItems().first()
                val allConsumed = dao.getAllConsumedItems().first()

                val filteredWasted = StatisticsHelper.filterWastedByTimeframe(allWasted, timeFrame)
                val filteredConsumed = StatisticsHelper.filterConsumedByTimeframe(allConsumed, timeFrame)
                val sustainability = StatisticsHelper.calculateSustainabilityStats(filteredConsumed, filteredWasted)
                val topWasted = StatisticsHelper.calculateTopWasted(filteredWasted, 5)

                val totalValue = currentItems.sumOf { it.price * it.quantity }
                val totalCount = currentItems.sumOf { it.quantity }

                pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // DIN A4 (72 DPI)
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Farben & Schriftarten definieren
                val titlePaint = Paint().apply {
                    color = Color.rgb(46, 125, 50) // #2E7D32
                    textSize = 22f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                val subtitlePaint = Paint().apply {
                    color = Color.rgb(100, 100, 100)
                    textSize = 11f
                    isAntiAlias = true
                }
                val sectionHeaderPaint = Paint().apply {
                    color = Color.rgb(46, 125, 50)
                    textSize = 14f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                val labelPaint = Paint().apply {
                    color = Color.rgb(60, 60, 60)
                    textSize = 12f
                    isAntiAlias = true
                }
                val valuePaint = Paint().apply {
                    color = Color.rgb(20, 20, 20)
                    textSize = 12f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                val lossValuePaint = Paint().apply {
                    color = Color.rgb(198, 40, 40) // #C62828
                    textSize = 12f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                val linePaint = Paint().apply {
                    color = Color.rgb(220, 220, 220)
                    strokeWidth = 1f
                    isAntiAlias = true
                }
                val cardBgPaint = Paint().apply {
                    color = Color.rgb(247, 249, 247)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                // Header
                canvas.drawText("Kühlschrank Profi 🍏 - Monatsstatistik", 40f, 55f, titlePaint)
                val creationDateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
                canvas.drawText("Zeitraum: ${timeFrame.title} • Erstellt am: $creationDateStr", 40f, 75f, subtitlePaint)
                canvas.drawLine(40f, 90f, 555f, 90f, linePaint)

                // 1. Vorratsbestand
                canvas.drawText("1. Vorrat & Inventar", 40f, 115f, sectionHeaderPaint)
                val card1 = RectF(40f, 125f, 555f, 185f)
                canvas.drawRoundRect(card1, 10f, 10f, cardBgPaint)

                canvas.drawText("Aktueller Vorratswert:", 55f, 150f, labelPaint)
                canvas.drawText(String.format(Locale.GERMANY, "%.2f €", totalValue), 240f, 150f, valuePaint)

                canvas.drawText("Artikelanzahl im Vorrat:", 55f, 172f, labelPaint)
                canvas.drawText("$totalCount Artikel", 240f, 172f, valuePaint)

                // 2. Nachhaltigkeit & Müll-Verlust
                canvas.drawText("2. Nachhaltigkeit & Müll-Verlust", 40f, 215f, sectionHeaderPaint)
                val card2 = RectF(40f, 225f, 555f, 340f)
                canvas.drawRoundRect(card2, 10f, 10f, cardBgPaint)

                canvas.drawText("Finanzieller Müll-Verlust:", 55f, 250f, labelPaint)
                canvas.drawText(String.format(Locale.GERMANY, "%.2f €", sustainability.wastedValue), 240f, 250f, lossValuePaint)

                canvas.drawText("Weggeworfene Artikel:", 55f, 275f, labelPaint)
                canvas.drawText("${sustainability.wastedCount} Artikel", 240f, 275f, valuePaint)

                canvas.drawText("Verbrauchte / Gerettete Artikel:", 55f, 300f, labelPaint)
                canvas.drawText("${sustainability.consumedCount} Artikel", 240f, 300f, valuePaint)

                canvas.drawText("CO₂-Belastung durch Abfall:", 55f, 325f, labelPaint)
                canvas.drawText(String.format(Locale.GERMANY, "%.2f kg CO₂", sustainability.wastedCo2), 240f, 325f, valuePaint)

                // 3. Häufigste Müll-Artikel
                canvas.drawText("3. Häufigste Müll-Artikel (Anti-Food-Waste-Fokus)", 40f, 370f, sectionHeaderPaint)
                val card3Top = 380f
                val card3Bottom = (380f + (if (topWasted.isEmpty()) 50f else (topWasted.size * 28f + 25f))).coerceAtLeast(440f)
                val card3 = RectF(40f, card3Top, 555f, card3Bottom)
                canvas.drawRoundRect(card3, 10f, 10f, cardBgPaint)

                if (topWasted.isEmpty()) {
                    canvas.drawText("Keine weggeworfenen Artikel in diesem Zeitraum – weiter so!", 55f, 410f, valuePaint)
                } else {
                    var yOffset = 405f
                    topWasted.forEachIndexed { index, item ->
                        val itemLine = "${index + 1}. ${item.name}"
                        val detailLine = "${item.count}x weggeworfen (${String.format(Locale.GERMANY, "%.2f €", item.totalLoss)} Verlust)"
                        canvas.drawText(itemLine, 55f, yOffset, valuePaint)
                        canvas.drawText(detailLine, 320f, yOffset, lossValuePaint)
                        yOffset += 26f
                    }
                }

                // Footer
                canvas.drawLine(40f, 790f, 555f, 790f, linePaint)
                canvas.drawText("Erstellt mit Kühlschrank Profi 🍏 • Für nachhaltiges Haushalten & weniger Lebensmittelverschwendung", 40f, 810f, subtitlePaint)

                pdfDocument.finishPage(page)

                // PDF temporär im Cache speichern
                val exportFile = File(context.cacheDir, "Kuehlschrank_Profi_Statistik_${System.currentTimeMillis()}.pdf")
                FileOutputStream(exportFile).use { fos ->
                    pdfDocument.writeTo(fos)
                }

                // FileProvider Uri holen & Android-Teilen-Dialog öffnen
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Kühlschrank Profi - Monatsstatistik (${timeFrame.title})")
                    putExtra(Intent.EXTRA_TEXT, "Hier ist meine Monatsstatistik aus Kühlschrank Profi 🍏")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(sendIntent, "Monatsstatistik als PDF teilen").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                withContext(Dispatchers.Main) {
                    _isExportingPdf.value = false
                    context.startActivity(chooserIntent)
                    onComplete?.invoke(true, null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isExportingPdf.value = false
                    Toast.makeText(context, "Fehler beim Erstellen des PDFs: ${e.localizedMessage ?: "Unbekannt"}", Toast.LENGTH_SHORT).show()
                    onComplete?.invoke(false, e.localizedMessage)
                }
            } finally {
                try {
                    pdfDocument?.close()
                } catch (_: Exception) {}
            }
        }
    }
}
