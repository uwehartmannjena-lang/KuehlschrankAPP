package com.example.myapplication

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.UserLearnedCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val userLearnedDao = db.userLearnedDao()
    private val prefs = application.getSharedPreferences("receipt_folder_prefs", Context.MODE_PRIVATE)

    private val _learnedCorrections = MutableStateFlow<List<UserLearnedCorrection>>(emptyList())
    val learnedCorrections: StateFlow<List<UserLearnedCorrection>> = _learnedCorrections.asStateFlow()

    private val _scanIntervalMinutes = MutableStateFlow(prefs.getLong("receipt_scan_interval", 60L))
    val scanIntervalMinutes: StateFlow<Long> = _scanIntervalMinutes.asStateFlow()

    init {
        loadLearnedCorrections()
    }

    fun loadLearnedCorrections() {
        viewModelScope.launch(Dispatchers.IO) {
            _learnedCorrections.value = userLearnedDao.getAllLearnedCorrections()
        }
    }

    fun updateCorrection(rawText: String, newCorrectedName: String) {
        val trimmed = newCorrectedName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = UserLearnedCorrection(
                receiptRawText = rawText,
                correctedName = trimmed,
                timestamp = System.currentTimeMillis()
            )
            userLearnedDao.insertCorrection(updated)
            ReceiptImportSanitizer.addLearnedCorrection(rawText, trimmed)
            loadLearnedCorrections()
        }
    }

    fun deleteCorrection(correction: UserLearnedCorrection) {
        viewModelScope.launch(Dispatchers.IO) {
            userLearnedDao.deleteCorrection(correction)
            loadLearnedCorrections()
        }
    }

    fun setScanInterval(minutes: Long) {
        _scanIntervalMinutes.value = minutes
        FolderObserverWorker.updateInterval(getApplication(), minutes)
    }
}
