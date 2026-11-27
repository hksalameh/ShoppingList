package com.hksalameh.shoppinglist

import java.text.Normalizer
import java.util.Locale

internal data class ImportItem(val name: String, val quantity: String = "", val category: String? = null) {
    val effectiveCategory: String get() = category ?: ShoppingCategories.forName(name)
}

private val formatting = Regex("[\\p{Cf}]")
private val spaces = Regex("[\\p{Z}\\s]+")
private val marks = Regex("[\\p{M}ـ]")
private val listMarker = Regex("""^(?:[•●▪◦*–—-]+\s*|\(?\p{Nd}{1,3}[.)،]\s+|\[\s*[xX✓ ]?\s*]\s*)""")
private val title = setOf(
    "قائمة المشتريات", "قائمة التسوق", "المشتريات", "مشترياتي", "قائمة", "طلبات البيت", "النواقص"
).map(::itemKey).toSet()

private val number = """(?:\p{Nd}+(?:[.٫,،/]\p{Nd}+)?|[¼½¾])"""
private val unit = """(?:كيلوغرام(?:ات)?|كيلو(?:ات)?|كغ|كجم|كغم|غم|غرام(?:ات)?|جرام|ملل?تر|مل|لتر(?:ات)?|حبات?|قطع(?:ة)?|علب(?:ة)?|عبوات?|أكياس|اكياس|كيس|ربطات?|زجاجات?|قناني|قنينة|دزينة|درزن|kg|g|l|ml)"""
private val separator = """(?:\s*[:：=×xXـ–—-]\s*|\s+)"""
private val prefixQuantity = Regex("""^($number(?:\s*$unit)?)$separator(.+)$""", RegexOption.IGNORE_CASE)
private val suffixQuantity = Regex("""^(.+?)$separator($number(?:\s*$unit)?)$""", RegexOption.IGNORE_CASE)
private val suffixUnitBeforeNumber = Regex("""^(.+?)$separator($unit)\s*($number)$""", RegexOption.IGNORE_CASE)
private val prefixUnitBeforeNumber = Regex("""^($unit)\s*($number)$separator(.+)$""", RegexOption.IGNORE_CASE)
private val letterNumberBoundary = Regex("""(?<=\p{L})(?=\p{Nd})|(?<=\p{Nd})(?=\p{L})""")
private val hasLetters = Regex("\\p{L}")
private val edgePunctuation = charArrayOf(' ', '.', ',', '،', ':', '：', ';', '؛', '-', '–', '—', '|')

internal fun itemKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC)
    .replace(formatting, "")
    .replace(marks, "")
    .replace(Regex("[أإآٱ]"), "ا")
    .replace('ى', 'ي')
    .replace('ی', 'ي')
    .replace('ک', 'ك')
    .replace(Regex("[\\p{P}\\p{S}]+"), " ")
    .replace(spaces, " ")
    .trim()
    .lowercase(Locale.ROOT)

internal fun parseShoppingText(text: String): List<ImportItem> =
    text.lineSequence().mapNotNull(::parseLine).toList()

private fun parseLine(raw: String): ImportItem? {
    val line = Normalizer.normalize(raw, Normalizer.Form.NFC)
        .replace(formatting, "")
        .replace('ی', 'ي')
        .replace('ک', 'ك')
        .replace(letterNumberBoundary, " ")
        .replace(spaces, " ")
        .trim()
        .replace(listMarker, "")
        .trim(*edgePunctuation)
    if (itemKey(line) in title || !hasLetters.containsMatchIn(line)) return null

    val prefix = prefixQuantity.matchEntire(line)
    val suffix = suffixQuantity.matchEntire(line)
    val prefixUnitFirst = prefixUnitBeforeNumber.matchEntire(line)
    val suffixUnitFirst = suffixUnitBeforeNumber.matchEntire(line)
    val item = when {
        prefix != null -> ImportItem(prefix.groupValues[2], prefix.groupValues[1])
        prefixUnitFirst != null -> ImportItem(prefixUnitFirst.groupValues[3], "${prefixUnitFirst.groupValues[2]} ${prefixUnitFirst.groupValues[1]}")
        suffixUnitFirst != null -> ImportItem(suffixUnitFirst.groupValues[1], "${suffixUnitFirst.groupValues[3]} ${suffixUnitFirst.groupValues[2]}")
        suffix != null -> ImportItem(suffix.groupValues[1], suffix.groupValues[2])
        else -> ImportItem(line)
    }
    val name = item.name.trim(*edgePunctuation)
    return item.copy(name = name).takeIf { hasLetters.containsMatchIn(name) }
}

internal fun uniqueImportItems(rows: List<ImportItem>): List<ImportItem> {
    val unique = linkedMapOf<String, ImportItem>()
    for (row in rows) {
        val item = row.copy(name = row.name.trim(), quantity = row.quantity.trim())
        val key = itemKey(item.name)
        if (key.isBlank()) continue
        val earlier = unique[key]
        if (earlier == null) {
            unique[key] = item
        } else {
            unique[key] = earlier.copy(
                quantity = earlier.quantity.ifBlank { item.quantity },
                category = earlier.category ?: item.category
            )
        }
    }
    return unique.values.toList()
}

// PaddleOCR emits glyphs in image order. Keep numbers and Latin text together when reversing Arabic.
private val visualTextTokens = Regex("""[\p{Nd}A-Za-z]+(?:[.,٫٬/ر]\p{Nd}+)*|.""")
private val misreadDecimal = Regex("""(?<=\p{Nd})ر(?=\p{Nd})""")

internal fun logicalArabicText(visual: String): String =
    visualTextTokens.findAll(visual).map { it.value }.toList().asReversed()
        .joinToString("").replace(misreadDecimal, "٫").trim()
