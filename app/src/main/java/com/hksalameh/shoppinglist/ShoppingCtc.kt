package com.hksalameh.shoppinglist

import kotlin.math.abs
import kotlin.math.ln

internal data class ShoppingSpelling(val name: String, val text: String)

internal data class ShoppingCtcMatch(val name: String, val loss: Float, val margin: Float)

/** Compares catalog names against the actual sequence of OCR character probabilities. */
internal class ShoppingCtc(dictionary: List<String>, spellings: List<ShoppingSpelling>) {
    private data class Candidate(val name: String, val positions: IntArray)
    private val classes = dictionary.size
    private val known = dictionary.withIndex().associate { it.value to it.index }
    private val candidates = spellings.mapNotNull { variant ->
        val visual = logicalArabicText(variant.text)
        val ids = visual.map { known[it.toString()] ?: return@mapNotNull null }
        if (ids.size < 2 || ids.size > 44) return@mapNotNull null
        val positions = IntArray(ids.size * 2 + 1)
        for ((index, id) in ids.withIndex()) positions[index * 2 + 1] = id
        Candidate(variant.name, positions)
    }

    fun match(probabilities: FloatArray, length: Int): ShoppingCtcMatch? {
        if (length < 2 || probabilities.isEmpty() || probabilities.size % classes != 0) return null
        val steps = probabilities.size / classes
        val logProbs = FloatArray(probabilities.size)
        var greedy = 0f
        for (step in 0 until steps) {
            var peak = 0f
            for (i in step * classes until (step + 1) * classes) {
                val p = probabilities[i].coerceIn(1e-12f, 1f)
                peak = maxOf(peak, p)
                logProbs[i] = ln(p)
            }
            greedy += ln(peak)
        }
        val scores = mutableMapOf<String, Float>()
        for (candidate in candidates) {
            val count = candidate.positions.size / 2
            if (abs(count - length) > maxOf(3, length / 4) || count > steps) continue
            val likelihood = score(candidate.positions, logProbs, steps)
            val previous = scores[candidate.name]
            if (previous == null || likelihood > previous) scores[candidate.name] = likelihood
        }
        val sorted = scores.entries.sortedByDescending { it.value }
        val best = sorted.firstOrNull() ?: return null
        val margin = sorted.getOrNull(1)?.let { best.value - it.value } ?: return null
        return ShoppingCtcMatch(best.key, (greedy - best.value).coerceAtLeast(0f), margin)
    }

    private fun score(positions: IntArray, probabilities: FloatArray, steps: Int): Float {
        var previous = FloatArray(positions.size) { Float.NEGATIVE_INFINITY }
        var current = FloatArray(positions.size)
        previous[0] = 0f
        for (step in 0 until steps) {
            val offset = step * classes
            for (i in positions.indices) {
                val token = positions[i]
                var best = previous[i]
                if (i > 0) best = maxOf(best, previous[i - 1])
                if (i > 1 && token != 0 && token != positions[i - 2]) {
                    best = maxOf(best, previous[i - 2])
                }
                current[i] = best + probabilities[offset + token]
            }
            val swap = previous
            previous = current
            current = swap
        }
        return maxOf(previous.last(), previous[previous.lastIndex - 1])
    }
}

internal fun chooseShoppingCtc(matches: List<ShoppingCtcMatch>): String? {
    val ranked = matches.filter { it.loss.isFinite() }.sortedBy { it.loss }
    val strongest = ranked.firstOrNull() ?: return null
    val length = strongest.name.count { !it.isWhitespace() }
    val limit = (length * 0.9f + 2f).coerceIn(6f, 13f)
    val conflicting = ranked.drop(1).firstOrNull { itemKey(it.name) != itemKey(strongest.name) }
    if (strongest.loss > limit || strongest.margin < 2.8f ||
        conflicting != null && conflicting.loss - strongest.loss < 2.3f
    ) return null
    return strongest.name
}

internal data class RecognizedLine(
    val text: String, val confidence: Float, val catalogMatch: ShoppingCtcMatch?
)

internal data class ReviewedOcr(val item: ImportItem, val suggestion: String?)

internal fun resolveShoppingOcr(
    reads: List<RecognizedLine>, vocabulary: ShoppingVocabulary, handwriting: Boolean
): ReviewedOcr? {
    val candidates = reads.mapNotNull candidate@ { result ->
        val text = result.text
        val item = parseShoppingText(text).singleOrNull() ?: return@candidate null
        if (handwriting && text.count { it.isLetter() && Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC } < 2) {
            return@candidate null
        }
        item to result.confidence
    }
    val best = candidates.map { (item, confidence) ->
        val suggestion = vocabulary.suggest(item.name)
        val match = when {
            confidence < 0.45f -> 0f
            vocabulary.isExact(item.name) -> 0.30f
            suggestion != null -> 0.12f
            else -> 0f
        }
        val mixedScript = handwriting && item.name.any { it in 'A'..'Z' || it in 'a'..'z' }
        val score = confidence + match - if (mixedScript) 0.10f else 0f
        Triple(item, suggestion, score)
    }.maxByOrNull { it.third }
    if (best == null && !handwriting) return null
    val raw = best?.first ?: ImportItem("")
    val hasExplicitAmount = raw.quantity.any { it.isLetter() } ||
        (raw.quantity.isNotBlank() && candidates.count { it.first.quantity == raw.quantity } > 1)
    val match = chooseShoppingCtc(reads.mapNotNull { it.catalogMatch })
    val item = if (match != null && !hasExplicitAmount &&
        (!vocabulary.isExact(raw.name) || itemKey(match) == itemKey(raw.name) ||
            vocabulary.suggest(raw.name) == match)
    ) ImportItem(match) else raw
    return ReviewedOcr(item, best?.second.takeIf { item == raw })
}
