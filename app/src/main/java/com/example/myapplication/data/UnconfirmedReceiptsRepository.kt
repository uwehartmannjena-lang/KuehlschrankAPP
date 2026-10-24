package com.example.myapplication.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class UnconfirmedReceiptItem(
    val id: String = UUID.randomUUID().toString(),
    val rawText: String,
    val suggestedName: String,
    val price: Double = 0.0,
    val quantity: Int = 1,
    val unit: String = "Stk.",
    val category: String = "Sonstiges",
    val sourceFile: String? = null
)

object UnconfirmedReceiptsRepository {
    private val _unconfirmedItems = MutableStateFlow<List<UnconfirmedReceiptItem>>(emptyList())
    val unconfirmedItems: StateFlow<List<UnconfirmedReceiptItem>> = _unconfirmedItems.asStateFlow()

    fun addItem(item: UnconfirmedReceiptItem) {
        _unconfirmedItems.update { current ->
            if (current.none { it.rawText.equals(item.rawText, ignoreCase = true) }) {
                current + item
            } else {
                current
            }
        }
    }

    fun addItems(items: List<UnconfirmedReceiptItem>) {
        _unconfirmedItems.update { current ->
            val existing = current.map { it.rawText.lowercase().trim() }.toSet()
            val newItems = items.filterNot { it.rawText.lowercase().trim() in existing }
            current + newItems
        }
    }

    fun removeItem(id: String) {
        _unconfirmedItems.update { current -> current.filterNot { it.id == id } }
    }

    fun clear() {
        _unconfirmedItems.value = emptyList()
    }
}
