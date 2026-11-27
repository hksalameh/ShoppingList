package com.hksalameh.shoppinglist

internal class ShoppingVocabulary(knownNames: List<String>) {
    private val names = (commonNames + knownNames).associateBy(::matchingKey)

    fun isExact(name: String): Boolean = matchingKey(name) in names

    fun suggest(name: String): String? {
        val key = matchingKey(name)
        if (key.length < 3) return null
        val exact = names[key]
        if (exact != null) return exact.takeIf { itemKey(it) != itemKey(name) }

        val limit = if (key.length < 7) 1 else 2
        var nearest: String? = null
        var best = limit + 1
        var tied = false
        for ((candidate, label) in names) {
            if (kotlin.math.abs(candidate.length - key.length) > limit) continue
            val distance = editDistance(key, candidate)
            if (distance < best) {
                nearest = label
                best = distance
                tied = false
            } else if (distance == best) {
                tied = true
            }
        }
        return nearest.takeIf { !tied }
    }

    private fun matchingKey(name: String): String =
        itemKey(name).replace(" ", "").replace('ة', 'ه')

    private fun editDistance(source: String, target: String): Int {
        var previous = IntArray(target.length + 1) { it }
        var current = IntArray(target.length + 1)
        for (i in source.indices) {
            current[0] = i + 1
            for (j in target.indices) {
                val change = if (source[i] == target[j]) 0 else 1
                current[j + 1] = minOf(previous[j + 1] + 1, current[j] + 1, previous[j] + change)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous.last()
    }

    private companion object {
        val commonNames = listOf(
            "دجاج", "لحمة", "زنجر", "سمك", "لبنة", "بيض", "جبنة موزاريلا", "جبنة مثلثة",
            "بازيلا معلبة", "بازيلا مجمدة", "زبدة", "زيتون", "زيت زيتون", "سمنة بلدية",
            "رز عادي", "رز بسمتي", "حليب", "قهوة", "عسل", "مكرونة أشكال", "جميد",
            "فستق", "بندق", "زبيب", "فطر", "قشطة", "تونة", "ساردين", "ذرة", "أناناس",
            "مية بندورة", "فول أبيض", "فاصوليا بيضاء", "عدس حب", "عدس مجروش",
            "هيل", "كمون", "فلفل أسود", "ملح", "البهارات السبعة",
            "سائل غسيل الملابس", "سائل جلي", "شامبو", "محارم عادية", "محارم مطبخ",
            "محارم حمام", "مشمعات سفرة", "قصدير", "ورق تغليف الطعام", "كلور",
            "معطر", "فوط نسائية", "مزيل عرق", "معجون أسنان", "فرشاة أسنان", "جل للحمام"
        )
    }
}
