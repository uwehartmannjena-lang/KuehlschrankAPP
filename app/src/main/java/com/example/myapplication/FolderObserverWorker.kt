package com.example.myapplication

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.CategoryDetector
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.Product
import com.example.myapplication.data.UnconfirmedReceiptItem
import com.example.myapplication.data.UnconfirmedReceiptsRepository
import com.example.myapplication.data.UserLearnedCorrection
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class FolderObserverWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val folderUriString = inputData.getString("folder_uri") ?: prefs.getString(KEY_FOLDER_URI, null)

        if (folderUriString.isNullOrBlank()) {
            return@withContext Result.success()
        }

        val treeUri = try {
            Uri.parse(folderUriString)
        } catch (e: Exception) {
            return@withContext Result.failure()
        }

        val lastScanTimestamp = prefs.getLong(KEY_LAST_SCAN, 0L)
        var maxObservedTimestamp = lastScanTimestamp

        val db = AppDatabase.getDatabase(appContext)
        val fridgeDao = db.fridgeItemDao()
        val userLearnedDao = db.userLearnedDao()

        // Wörterbuch und gelernte Einträge initialisieren
        ReceiptImportSanitizer.loadDictionaryFromAssets(appContext)
        ReceiptImportSanitizer.loadLearnedCorrections(appContext)

        val learnedList = userLearnedDao.getAllLearnedCorrections()
        val correctionsMap = learnedList.associate { it.receiptRawText.lowercase().trim() to it.correctedName }

        val treeDocumentFile = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: return@withContext Result.failure()

        try {
            val files = treeDocumentFile.listFiles()
            for (file in files) {
                if (!file.isFile) continue
                val displayName = file.name ?: continue

                // Filtere alle Dateien heraus, deren Name bereits mit "GELERNT_" beginnt
                if (displayName.startsWith(PREFIX_PROCESSED, ignoreCase = true)) {
                    continue
                }

                val lastModified = file.lastModified()
                if (lastModified in 1..lastScanTimestamp) {
                    continue
                }

                if (lastModified > maxObservedTimestamp) {
                    maxObservedTimestamp = lastModified
                }

                val isPdf = file.type == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)
                val isImage = (file.type?.startsWith("image/") == true) ||
                        displayName.endsWith(".jpg", ignoreCase = true) ||
                        displayName.endsWith(".jpeg", ignoreCase = true) ||
                        displayName.endsWith(".png", ignoreCase = true)

                if (!isPdf && !isImage) continue

                val products: List<Product> = if (isPdf) {
                    PdfReceiptHelper.processPdfUri(appContext, file.uri, correctionsMap, displayName)
                } else {
                    extractProductsFromImageUri(appContext, file.uri, correctionsMap)
                }

                if (products.isNotEmpty()) {
                    processProductsWithLearning(products, displayName, db)

                    // Benenne die bearbeitete Datei nach erfolgreichem Einlesen zwingend über DocumentsContract.renameDocument um
                    try {
                        val newDisplayName = "$PREFIX_PROCESSED$displayName"
                        DocumentsContract.renameDocument(appContext.contentResolver, file.uri, newDisplayName)
                    } catch (e: Exception) {
                        Log.e(TAG, "Dokument konnte nicht umbenannt werden: $displayName", e)
                    }
                }
            }

            prefs.edit().putLong(KEY_LAST_SCAN, maxObservedTimestamp).apply()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei FolderObserverWorker Ausführung", e)
            Result.failure()
        }
    }

    private suspend fun processProductsWithLearning(
        products: List<Product>,
        sourceFile: String,
        db: AppDatabase
    ) {
        val fridgeDao = db.fridgeItemDao()
        val userLearnedDao = db.userLearnedDao()

        for (product in products) {
            val rawName = product.name.trim()
            if (rawName.isBlank()) continue

            // 1. Bekannte Treffer in UserLearnedDao prüfen
            val learned = userLearnedDao.getCorrection(rawName)
                ?: userLearnedDao.getCorrection(rawName.lowercase().trim())

            // 2. Bekannte Treffer im Wörterbuch prüfen
            val dictMatch = ReceiptImportSanitizer.findBestMatch(rawName)

            val isHighConfidence = learned != null || dictMatch != null

            if (isHighConfidence) {
                val resolvedName = learned?.correctedName ?: dictMatch?.name ?: rawName
                val category = dictMatch?.category ?: CategoryDetector.detectCategory(resolvedName).displayName
                val storage = dictMatch?.defaultStorageLocation ?: "Kühlschrank"
                val expiryDays = dictMatch?.defaultExpiryDays ?: 7
                val expiryDate = System.currentTimeMillis() + (expiryDays * 24L * 60 * 60 * 1000L)

                val item = FridgeItem(
                    name = resolvedName,
                    quantity = product.quantity,
                    unit = product.unit,
                    category = category,
                    storageLocation = storage,
                    price = product.price,
                    expiryDate = expiryDate,
                    purchaseDate = System.currentTimeMillis()
                )
                fridgeDao.insertItem(item)

                userLearnedDao.insertCorrection(
                    UserLearnedCorrection(
                        receiptRawText = rawName,
                        correctedName = resolvedName,
                        timestamp = System.currentTimeMillis()
                    )
                )
                ReceiptImportSanitizer.addLearnedCorrection(rawName, resolvedName)
            } else {
                // Unbekannte Artikel in temporäre UI-Liste "Zu bestätigen"
                val suggested = ReceiptImportSanitizer.correctNameWithFuzzyMatching(rawName)
                UnconfirmedReceiptsRepository.addItem(
                    UnconfirmedReceiptItem(
                        rawText = rawName,
                        suggestedName = suggested,
                        price = product.price,
                        quantity = product.quantity,
                        unit = product.unit,
                        category = CategoryDetector.detectCategory(rawName).displayName,
                        sourceFile = sourceFile
                    )
                )
            }
        }
    }

    private fun extractProductsFromImageUri(
        context: Context,
        imageUri: Uri,
        corrections: Map<String, String>
    ): List<Product> {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = Tasks.await(recognizer.process(image))
            if (visionText != null && visionText.text.isNotBlank()) {
                KassenzettelParser.parseReceiptText(visionText.text, corrections)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR-Fehler bei Bild-Uri: $imageUri", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "FolderObserverWorker"
        const val PREFIX_PROCESSED = "GELERNT_"
        const val PREFS_NAME = "receipt_folder_prefs"
        const val KEY_FOLDER_URI = "receipt_folder_uri"
        const val KEY_SCAN_INTERVAL = "receipt_scan_interval"
        const val KEY_LAST_SCAN = "last_scan_timestamp"
        const val WORK_NAME = "FolderObserverPeriodicWork"

        fun schedule(context: Context, folderUri: String, intervalMinutes: Long = 60L) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_FOLDER_URI, folderUri)
                .putLong(KEY_SCAN_INTERVAL, intervalMinutes)
                .apply()

            if (intervalMinutes <= 0) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }

            val data = workDataOf("folder_uri" to folderUri)
            val request = PeriodicWorkRequestBuilder<FolderObserverWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun updateInterval(context: Context, intervalMinutes: Long) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val folderUri = prefs.getString(KEY_FOLDER_URI, null)
            prefs.edit().putLong(KEY_SCAN_INTERVAL, intervalMinutes).apply()

            if (intervalMinutes <= 0) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            } else if (!folderUri.isNullOrBlank()) {
                val data = workDataOf("folder_uri" to folderUri)
                val request = PeriodicWorkRequestBuilder<FolderObserverWorker>(intervalMinutes, TimeUnit.MINUTES)
                    .setInputData(data)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }

        fun runOnce(context: Context, folderUri: String? = null) {
            val uri = folderUri ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FOLDER_URI, null) ?: return

            val data = workDataOf("folder_uri" to uri)
            val request = OneTimeWorkRequestBuilder<FolderObserverWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
