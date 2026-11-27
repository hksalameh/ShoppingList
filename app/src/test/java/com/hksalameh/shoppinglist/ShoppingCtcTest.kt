package com.hksalameh.shoppinglist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingCtcTest {
    @Test
    fun usesNearMissCharacterProbabilities() {
        val dictionary = listOf("", "ح", "ل", "ي", "ب", "ت", "س")
        val decoder = ShoppingCtc(dictionary, listOf(
            ShoppingSpelling("حليب", "حليب"), ShoppingSpelling("حليت", "حليت")
        ))
        val target = intArrayOf(0, 4, 0, 3, 0, 2, 0, 1, 0)
        val frame = FloatArray(dictionary.size * target.size) { .001f }
        for ((step, token) in target.withIndex()) frame[step * dictionary.size + token] = .94f
        // The first nonblank argmax is an unknown character, with a strong second choice.
        frame[1 * dictionary.size + 6] = .53f
        frame[1 * dictionary.size + 4] = .44f
        val match = decoder.match(frame, 4)!!
        assertEquals("حليب", chooseShoppingCtc(listOf(match)))
    }

    @Test
    fun leavesAmbiguousNamesAndExplicitQuantitiesForReview() {
        val near = ShoppingCtcMatch("حليب", 2.0f, 4f)
        assertNull(chooseShoppingCtc(listOf(near, ShoppingCtcMatch("لبن", 2.1f, 5f))))
        val vocabulary = ShoppingVocabulary(emptyList())
        val resolved = resolveShoppingOcr(
            listOf(RecognizedLine("حليب ٢ لتر", .8f, near)), vocabulary, handwriting = true
        )!!
        assertEquals(ImportItem("حليب", "٢ لتر"), resolved.item)
    }
}
