package com.hksalameh.shoppinglist

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ShoppingItem(
    val id: String,
    val name: String,
    val category: String,
    val selected: Boolean = false,
    val purchased: Boolean = false,
    val quantity: String = ""
)

class ShoppingViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("shopping_list", Context.MODE_PRIVATE)

    val items = mutableStateListOf<ShoppingItem>()

    init {
        items.addAll(loadItems())
    }

    val selectedCount: Int
        get() = items.count { it.selected }

    val remainingCount: Int
        get() = items.count { it.selected && !it.purchased }

    val purchasedCount: Int
        get() = items.count { it.selected && it.purchased }

    fun toggleSelected(id: String) {
        update(id) { item ->
            val nextSelected = !item.selected
            item.copy(
                selected = nextSelected,
                purchased = if (nextSelected) false else item.purchased,
                quantity = if (nextSelected) item.quantity else ""
            )
        }
    }

    fun togglePurchased(id: String) {
        update(id) { item -> item.copy(purchased = !item.purchased) }
    }

    fun addItem(name: String, category: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        items.add(
            ShoppingItem(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                category = category.ifBlank { "أخرى" }
            )
        )
        persist()
    }

    fun editItem(id: String, name: String, category: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        update(id) { it.copy(name = cleanName, category = category.ifBlank { "أخرى" }) }
    }

    fun deleteItem(id: String) {
        items.removeAll { it.id == id }
        persist()
    }

    fun clearAllItems() {
        items.clear()
        persist()
    }

    fun clearPurchasedFromCart() {
        for (index in items.indices) {
            val item = items[index]
            if (item.selected && item.purchased) {
                items[index] = item.copy(selected = false, purchased = false, quantity = "")
            }
        }
        persist()
    }

    fun finishShopping() {
        for (index in items.indices) {
            val item = items[index]
            if (item.selected || item.purchased) {
                items[index] = item.copy(selected = false, purchased = false, quantity = "")
            }
        }
        persist()
    }

    internal fun selectImported(rows: List<ImportItem>): Int {
        var pending = 0
        for (row in uniqueImportItems(rows)) {
            val key = itemKey(row.name)
            // Prefer an existing cart item if an older list already contains duplicates.
            val existingIndex = items.indexOfFirst { it.selected && !it.purchased && itemKey(it.name) == key }
                .takeIf { it >= 0 }
                ?: items.indexOfFirst { it.selected && itemKey(it.name) == key }.takeIf { it >= 0 }
                ?: items.indexOfFirst { itemKey(it.name) == key }
            if (existingIndex >= 0) {
                val existing = items[existingIndex]
                if (existing.selected && existing.purchased) continue
                items[existingIndex] = existing.copy(
                    selected = true,
                    purchased = false,
                    quantity = row.quantity.ifBlank { existing.quantity }
                )
            } else {
                items.add(
                    ShoppingItem(
                        id = UUID.randomUUID().toString(),
                        name = row.name,
                        category = "من صورة",
                        selected = true,
                        quantity = row.quantity
                    )
                )
            }
            pending++
        }
        if (pending > 0) persist()
        return pending
    }

    private fun update(id: String, transform: (ShoppingItem) -> ShoppingItem) {
        val index = items.indexOfFirst { it.id == id }
        if (index == -1) return
        items[index] = transform(items[index])
        persist()
    }

    private fun persist() {
        val json = JSONArray()
        items.forEach { item ->
            json.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("category", item.category)
                    .put("selected", item.selected)
                    .put("purchased", item.purchased)
                    .put("quantity", item.quantity)
            )
        }
        prefs.edit().putString("items", json.toString()).apply()
    }

    private fun loadItems(): List<ShoppingItem> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ShoppingItem(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            category = obj.optString("category", "أخرى"),
                            selected = obj.optBoolean("selected", false),
                            purchased = obj.optBoolean("purchased", false),
                            quantity = obj.optString("quantity", "")
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

}
