package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.CameraHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FridgeRepository(
    private val context: Context,
    private val dao: FridgeDao = AppDatabase.getDatabase(context).fridgeItemDao()
) {
    suspend fun deleteItem(item: FridgeItem) = withContext(Dispatchers.IO) {
        cleanLocalImageFile(item.imageUrl)
        dao.deleteItem(item)
    }

    suspend fun consumeItem(item: FridgeItem) = withContext(Dispatchers.IO) {
        cleanLocalImageFile(item.imageUrl)
        dao.deleteItem(item)
    }

    suspend fun wasteItem(item: FridgeItem) = withContext(Dispatchers.IO) {
        cleanLocalImageFile(item.imageUrl)
        dao.deleteItem(item)
    }

    private fun cleanLocalImageFile(imageUrl: String?) {
        if (imageUrl.isNullOrBlank()) return
        CameraHelper.deleteLocalImage(context, imageUrl)
    }
}
