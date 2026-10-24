package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object DocumentHelper {
    private const val TAG = "DocumentHelper"
    const val ARCHIVE_FOLDER_NAME = "Kassenbons_Archiv"

    fun copyUriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val contentResolver = context.contentResolver
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val tempFile = File(context.cacheDir, "receipt_import_${System.currentTimeMillis()}.pdf")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Kopieren der PDF-Uri", e)
            null
        }
    }

    fun archivePdfDocument(context: Context, sourceUri: Uri, archiveFolderName: String = ARCHIVE_FOLDER_NAME): Boolean {
        return try {
            val contentResolver = context.contentResolver
            val archiveDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), archiveFolderName)
            if (!archiveDir.exists()) {
                archiveDir.mkdirs()
            }
            val targetFile = File(archiveDir, "Kassenbon_${System.currentTimeMillis()}.pdf")
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            try {
                DocumentsContract.deleteDocument(contentResolver, sourceUri)
            } catch (e: Exception) {
                Log.d(TAG, "Originaldatei konnte nicht per DocumentsContract gelöscht werden: ${e.message}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Archivieren des Dokuments", e)
            false
        }
    }
}
