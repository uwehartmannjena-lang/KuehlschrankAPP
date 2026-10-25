package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.CategoryDetector
import com.example.myapplication.data.FridgeDao
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.ShoppingItem
import com.example.myapplication.data.ShoppingListDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShoppingListViewModel(
    application: Application,
    private val shoppingListDao: ShoppingListDao = AppDatabase.getDatabase(application).shoppingListDao(),
    private val fridgeDao: FridgeDao = AppDatabase.getDatabase(application).fridgeItemDao()
) : AndroidViewModel(application) {

    val shoppingItems: StateFlow<List<ShoppingItem>> = shoppingListDao.getAllShoppingItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onItemChecked(item: ShoppingItem, onTransferSuccess: ((String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val cat = CategoryDetector.detectCategory(item.name)
            val expiryMs = System.currentTimeMillis() + (cat.expiryDays * 24 * 60 * 60 * 1000L)
            val fridgeItem = FridgeItem(
                name = item.name.trim(),
                quantity = item.quantity.coerceAtLeast(1),
                unit = item.unit.ifBlank { "Stk." },
                category = cat.displayName,
                storageLocation = "Kühlschrank",
                expiryDate = expiryMs,
                price = item.priceEstimate,
                purchaseDate = System.currentTimeMillis()
            )
            fridgeDao.insertItem(fridgeItem)
            shoppingListDao.deleteShoppingItem(item)

            withContext(Dispatchers.Main) {
                onTransferSuccess?.invoke("${item.name} in den Kühlschrank eingeräumt")
            }
        }
    }

    fun addItem(name: String, quantity: Int = 1, unit: String = "Stk.") {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val newItem = ShoppingItem(
                name = cleanName,
                quantity = quantity.coerceAtLeast(1),
                unit = unit.ifBlank { "Stk." },
                isChecked = false
            )
            shoppingListDao.insertShoppingItem(newItem)
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch(Dispatchers.IO) {
            shoppingListDao.deleteShoppingItem(item)
        }
    }

    fun updateQuantity(item: ShoppingItem, newQuantity: Int) {
        if (newQuantity <= 0) {
            deleteItem(item)
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                shoppingListDao.updateShoppingItem(item.copy(quantity = newQuantity))
            }
        }
    }
}
