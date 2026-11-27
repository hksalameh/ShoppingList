package com.hksalameh.shoppinglist

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingTextParserTest {
    @Test
    fun readsRtlQuantitiesAndIgnoresHeadingsAndNumbering() {
        val text = """
            قائمة المشتريات:
            ١. حليب ٢ لتر
            • ٣ علب تونة
            أرز بسمتي : ١٫٥ كيلو
            4 × بيض
            خبز - ربطات ٢
            تفاح 1/2 كغ
            سائل غسيل الملابس
            ١٢٣
        """.trimIndent()

        assertEquals(
            listOf(
                ImportItem("حليب", "٢ لتر"),
                ImportItem("تونة", "٣ علب"),
                ImportItem("أرز بسمتي", "١٫٥ كيلو"),
                ImportItem("بيض", "4"),
                ImportItem("خبز", "٢ ربطات"),
                ImportItem("تفاح", "1/2 كغ"),
                ImportItem("سائل غسيل الملابس")
            ),
            parseShoppingText(text)
        )
    }

    @Test
    fun reordersVisualArabicWithoutReversingNumbersOrLatin() {
        assertEquals("حليب ١٫٥ لتر", logicalArabicText("رتل ١٫٥ بيلح"))
        assertEquals("طحين2", logicalArabicText("2نيحط"))
        assertEquals(
            listOf(ImportItem("رز بسمتي", "١٫٥ كيلو")),
            parseShoppingText(logicalArabicText("وليك ١ر٥يتمسب زر"))
        )
        assertEquals("رز 1.5 kg", logicalArabicText("kg 1.5 زر"))
        assertEquals(listOf(ImportItem("طحين", "2")), parseShoppingText("طحين2"))
    }

    @Test
    fun normalizedDuplicatesRetainTheFirstSpecifiedQuantity() {
        val rows = listOf(
            ImportItem(" أَرُزّ   بسمتي "),
            ImportItem("ارز-بسمتي", "٢ كغ"),
            ImportItem("أرز بسمتي", "٥ كغ"),
            ImportItem("أرز عادي", "١ كغ"),
            ImportItem(" — ")
        )

        assertEquals(
            listOf(ImportItem("أَرُزّ   بسمتي", "٢ كغ"), ImportItem("أرز عادي", "١ كغ")),
            uniqueImportItems(rows)
        )
    }
}
