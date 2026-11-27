package com.hksalameh.shoppinglist

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingCategoriesTest {
    @Test
    fun recognizesArabicGroceryCategoriesAndSpecificTermsFirst() {
        val expected = mapOf(
            "سائل غسيل توري" to ShoppingCategories.CLEANING,
            "مشمعات سفرة" to ShoppingCategories.CLEANING,
            "البهارات السبعة" to ShoppingCategories.SPICES,
            "بهارات دجاج" to ShoppingCategories.SPICES,
            "تونة" to ShoppingCategories.CANNED,
            "بازيلا معلبة" to ShoppingCategories.CANNED,
            "مية بندورة" to ShoppingCategories.CANNED,
            "بازيلا مجمدة" to ShoppingCategories.MEAT,
            "رز بسمتي" to ShoppingCategories.PANTRY,
            "فاصوليا بيضاء" to ShoppingCategories.PANTRY,
            "جبنه موزاريلا" to ShoppingCategories.DAIRY,
            "معجون أسنان" to ShoppingCategories.CARE,
            "تفاح" to ShoppingCategories.FRESH,
            "منتج غير معروف" to ShoppingCategories.OTHER
        )
        for ((name, category) in expected) assertEquals(name, category, ShoppingCategories.forName(name))
    }

    @Test
    fun migratesOnlyUnclassifiedImportsAndUsesFinalReviewedName() {
        assertEquals(ShoppingCategories.CANNED, ShoppingCategories.fromStored("تونة", "من صورة"))
        assertEquals("تصنيف شخصي", ShoppingCategories.fromStored("تونة", "تصنيف شخصي"))
        val row = ImportItem("سائل جلي", "٢ لتر")
        assertEquals(ShoppingCategories.CLEANING, row.effectiveCategory)
        assertEquals(ShoppingCategories.DAIRY, row.copy(name = "حليب").effectiveCategory)
        val manuallyClassified = row.copy(category = "تصنيف شخصي")
        val merged = uniqueImportItems(listOf(row, manuallyClassified)).single()
        assertEquals("تصنيف شخصي", merged.effectiveCategory)
        assertEquals("٢ لتر", merged.quantity)
    }
}
