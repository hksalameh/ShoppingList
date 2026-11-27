package com.hksalameh.shoppinglist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLayoutTest {
    @Test
    fun columnsAreReadRightToLeftAndRowsDoNotMergeAcrossColumns() {
        val width = 380
        val height = 300
        val mask = BooleanArray(width * height)
        fun ink(left: Int, top: Int, right: Int, bottom: Int) {
            for (y in top until bottom) {
                for (x in left until right) mask[y * width + x] = true
            }
        }
        ink(15, 22, 96, 40)
        ink(132, 34, 215, 50)
        ink(270, 22, 352, 41)
        ink(15, 53, 96, 71)
        ink(132, 62, 214, 83)
        ink(270, 50, 350, 76)
        ink(207, 155, 248, 170)
        ink(276, 155, 355, 170)
        ink(180, 177, 355, 197)
        ink(30, 156, 93, 171)

        val rows = photoTextRegions(mask, width, height)
        assertEquals(10, rows.size)
        assertTrue(rows[0].left > 250 && rows[1].left > 250)
        assertTrue(rows[2].left in 110..140 && rows[3].left in 110..140)
        assertTrue(rows[4].left < 25 && rows[5].left < 25)
        assertTrue(rows[6].left > 250 && rows[7].left in 190..220)
        assertTrue(rows[8].left in 160..185 && rows[9].left < 40)
    }

    @Test
    fun onlySuggestsUnambiguousNearbyNamesWithoutReplacingTheInput() {
        val vocabulary = ShoppingVocabulary(listOf("بطاطا بلدية"))
        assertEquals("جبنة موزاريلا", vocabulary.suggest("جبنه موزاريلا"))
        assertEquals("بطاطا بلدية", vocabulary.suggest("بطاطا بلديه"))
        assertTrue(vocabulary.isExact("زيت ز يتون"))
        assertNull(vocabulary.suggest("رقم فاتورة"))
        assertNull(vocabulary.suggest("سم"))
    }
}
