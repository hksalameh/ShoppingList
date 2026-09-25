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
    val purchased: Boolean = false
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
            item.copy(selected = nextSelected, purchased = if (nextSelected) false else item.purchased)
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

    fun finishShopping() {
        for (index in items.indices) {
            val item = items[index]
            if (item.selected || item.purchased) {
                items[index] = item.copy(selected = false, purchased = false)
            }
        }
        persist()
    }

    fun selectImported(names: List<String>) {
        names.map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { importedName ->
                val existingIndex = items.indexOfFirst { it.name == importedName }
                if (existingIndex >= 0) {
                    val existing = items[existingIndex]
                    items[existingIndex] = existing.copy(selected = true, purchased = false)
                } else {
                    items.add(
                        ShoppingItem(
                            id = UUID.randomUUID().toString(),
                            name = importedName,
                            category = "من صورة",
                            selected = true,
                            purchased = false
                        )
                    )
                }
            }
        persist()
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
            )
        }
        prefs.edit().putString("items", json.toString()).apply()
    }

    private fun loadItems(): List<ShoppingItem> {
        val raw = prefs.getString("items", null) ?: return defaultItems()
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
                            purchased = obj.optBoolean("purchased", false)
                        )
                    )
                }
            }
        }.getOrElse { defaultItems() }
    }

    private fun defaultItems(): List<ShoppingItem> {
        val groups = linkedMapOf(
            "لحوم ومجمدات" to listOf(
                "دجاج", "لحمة", "زنجر", "سمك", "لبنة تركية", "بيض",
                "جبنة موزاريلا", "بازيلا معلبة", "زبدة", "زيتون"
            ),
            "مواد غذائية" to listOf(
                "رز عادي", "رز بسمتي", "حليب", "قهوة", "عسل", "مكرونة أشكال",
                "جميد", "فستق", "زبيب", "فطر", "قشطة", "تونة", "ساردين",
                "مية بندورة", "فول أبيض"
            ),
            "بهارات" to listOf(
                "هيل", "كمون", "فلفل أسود", "ملح", "البهارات السبعة"
            ),
            "منظفات وعناية" to listOf(
                "سائل غسيل الملابس", "سائل جلي", "شامبو للشعر والجسم",
                "محارم عادية", "محارم مطبخ", "محارم حمام", "مشمعات سفرة",
                "قصدير", "ورق تغليف الطعام", "كلور", "معطر", "فوط نسائية", "مزيل عرق"
            )
        )

        return groups.flatMap { (category, names) ->
            names.mapIndexed { index, name ->
                ShoppingItem(
                    id = "seed-${category.hashCode()}-$index",
                    name = name,
                    category = category
                )
            }
        }
    }
}
