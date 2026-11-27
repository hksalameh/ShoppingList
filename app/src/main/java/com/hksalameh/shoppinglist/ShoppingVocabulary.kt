package com.hksalameh.shoppinglist

internal class ShoppingVocabulary(knownNames: List<String>) {
    private val names = (commonNames + knownNames).associateBy(::matchingKey)

    fun spellings(): List<ShoppingSpelling> = names.values.flatMap { label ->
        val variants = listOf(label) + aliases[label].orEmpty()
        variants.flatMap { variant ->
            val plain = itemKey(variant)
            listOf(variant, plain, variant.replace('ة', 'ه'), plain.replace('ة', 'ه'))
        }.distinct().map { ShoppingSpelling(label, it) }
    }

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
        val aliases = mapOf(
            "تونة" to listOf("طون", "تون", "تونا"),
            "مكرونة أشكال" to listOf("معكرونه اشكال", "معكرونة أشكال"),
            "ذرة" to listOf("دره"),
            "فستق" to listOf("فزدق"),
            "كتشب" to listOf("كاتشب", "كتش اب"),
            "البهارات السبعة" to listOf("بهارات سبعة", "بهارات ٧", "بهارات 7"),
            "جبنة مثلثة" to listOf("جبنة مثلثات"),
            "سائل جلي" to listOf("دوا جلي", "سائل للجلي")
        )

        val commonNames = listOf(
            "دجاج", "لحمة", "زنجر", "سمك", "لبنة", "بيض", "جبنة موزاريلا", "جبنة مثلثة",
            "بازيلا معلبة", "بازيلا مجمدة", "زبدة", "زيتون", "زيت زيتون", "سمنة بلدية",
            "رز عادي", "رز بسمتي", "حليب", "قهوة", "عسل", "مكرونة أشكال", "جميد",
            "فستق", "بندق", "زبيب", "فطر", "قشطة", "تونة", "ساردين", "ذرة", "أناناس",
            "مية بندورة", "فول أبيض", "فاصوليا بيضاء", "عدس حب", "عدس مجروش",
            "هيل", "كمون", "فلفل أسود", "ملح", "البهارات السبعة",
            "سائل غسيل الملابس", "سائل جلي", "شامبو", "محارم عادية", "محارم مطبخ",
            "محارم حمام", "مشمعات سفرة", "قصدير", "ورق تغليف الطعام", "كلور",
            "معطر", "فوط نسائية", "مزيل عرق", "معجون أسنان", "فرشاة أسنان", "جل للحمام",
            "جوز الهند", "نسكافيه", "كتشب", "باربيكيو صوص", "محارم مبللة", "ليف للجلي",
            "بودرة للجسم", "ليفة للاستحمام", "بهارات منسف", "بهارات دجاج", "بهارات لحمة",
            "بودرة غسيل", "سائل غسيل", "معطر ملابس", "منظف أرضيات", "صابون", "معقم",
            "مخلل", "طحينة", "سماق", "بابريكا", "قرفة", "شوفان", "خبز", "طحين", "شاي",
            "سكر", "بطاطا", "بصل", "بندورة", "خيار", "تفاح", "برتقال", "موز"
        )
    }
}
