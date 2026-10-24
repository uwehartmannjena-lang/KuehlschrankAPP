package com.example.myapplication

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object CameraHelper {
    private const val TAG = "CameraHelper"

    fun createTempPictureUri(context: Context): Uri {
        val imagesFolder = File(context.filesDir, "images")
        if (!imagesFolder.exists()) {
            imagesFolder.mkdirs()
        }
        val tempFile = File(imagesFolder, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    fun copyImageToLocalStorage(context: Context, sourceUri: Uri): Uri? {
        return try {
            val imagesFolder = File(context.filesDir, "images")
            if (!imagesFolder.exists()) imagesFolder.mkdirs()
            val destFile = File(imagesFolder, "product_${System.currentTimeMillis()}.jpg")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Speichern des Bildes", e)
            null
        }
    }

    fun deleteLocalImage(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val file = uri.path?.let { File(it) }
                file != null && file.exists() && file.delete()
            } else if (uri.scheme == "content" && uri.authority == "${context.packageName}.fileprovider") {
                val pathSegment = uri.lastPathSegment
                val imagesFolder = File(context.filesDir, "images")
                val targetFile = File(imagesFolder, pathSegment ?: "")
                if (targetFile.exists()) {
                    targetFile.delete()
                } else {
                    val fileInCache = File(context.cacheDir, pathSegment ?: "")
                    if (fileInCache.exists()) fileInCache.delete() else false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Löschen des lokalen Bildes", e)
            false
        }
    }
}
