package com.hksalameh.shoppinglist

internal object ShoppingCategories {
    const val CLEANING = "مواد تنظيف"
    const val SPICES = "بهارات"
    const val CANNED = "معلبات"
    const val PANTRY = "مواد تموينية"
    const val MEAT = "لحوم ومجمدات"
    const val DAIRY = "ألبان وأجبان وبيض"
    const val CARE = "عناية شخصية"
    const val FRESH = "خضار وفواكه"
    const val OTHER = "أخرى"
    const val LEGACY_IMPORT = "من صورة"

    val options = listOf(PANTRY, CANNED, SPICES, CLEANING, MEAT, DAIRY, CARE, FRESH, OTHER)

    // More specific matches come first, such as "بهارات دجاج" and "بازيلا مجمدة".
    private val rules = listOf(
        SPICES to "بهار بهارات فلفل ملح هيل هال كمون كركم كاري بابريكا قرفة زنجبيل كزبرة سماق زعتر كيجن خلطة",
        CANNED to "معلب معلبة معلبات تونة تونا طون ساردين سردين ذرة كونسروة مرتديلا",
        CLEANING to "غسيل جلي كلور مبيض منظف منظفات غسالة مسحوق معطر مطهر سيتريس سترس محارم مناديل مشمع مشمعات أكياس سفرة قصدير إسفنج اسفنج توري برسيل",
        CARE to "أسنان اسنان فراشي شامبو صابون استحمام للاستحمام عرق حلاقة مزيل فوط ليفة بودرة حمام",
        MEAT to "دجاج لحمة لحم زنجر سمك مجمد مجمدة برغر كفتة روبيان نقانق",
        DAIRY to "حليب لبن لبنة جبن جبنة أجبان موزاريلا بيض قشطة زبدة سمنة جميد زبادي",
        FRESH to "تفاح موز خيار بندورة نارنج برتقال بصل بطاطا خس خضار فواكه",
        PANTRY to "رز أرز بسمتي مكرونة معكرونة معكرونه عدس حمص فاصوليا فاصولية فول سكر شاي قهوة نسكافيه طحين دقيق زيت زيتون عسل زبيب فستق بندق لوز كتشب كاتشب باربيكيو صوص فطر أناناس خبز شوفان جوز"
    ).map { (category, words) -> category to words.split(' ').map(::classificationKey).toSet() }

    private val phrases = mapOf(
        "مية بندورة" to CANNED, "معجون بندورة" to CANNED, "رب البندورة" to CANNED,
        "جل حمام" to CLEANING, "جل للحمام" to CLEANING, "ليف الجلي" to CLEANING,
        "معجون أسنان" to CARE
    ).mapKeys { classificationKey(it.key) }

    fun forName(name: String): String {
        val key = classificationKey(name)
        phrases[key]?.let { return it }
        val words = key.split(' ').flatMap { word ->
            listOf(word, word.removePrefix("ال"), word.removePrefix("لل"), word.removePrefix("وال"))
        }
        return rules.firstOrNull { (_, terms) -> words.any { it in terms } }?.first ?: OTHER
    }

    fun fromStored(name: String, category: String): String =
        if (category.isBlank() || category == LEGACY_IMPORT) forName(name) else category

    private fun classificationKey(value: String) = itemKey(value).replace('ة', 'ه')
}
