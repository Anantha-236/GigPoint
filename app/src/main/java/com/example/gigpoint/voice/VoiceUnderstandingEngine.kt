package com.example.gigpoint.voice

import com.example.gigpoint.Product
import java.util.Locale

class VoiceUnderstandingEngine {

    private val stockIn = listOf(
        "add", "stock in", "received", "came", "plus",
        "vachindi", "vachayi", "add cheyyi", "add chey",
        "stock lo pettu", "add karo", "stock mein daalo"
    )

    private val sale = listOf(
        "sold", "sale", "sell", "ammindi", "ammayi",
        "sale ayyindi", "sale ayyayi", "bik gaya", "bik gaye"
    )

    private val genericRemove = listOf(
        "remove", "take out", "teesey", "teese", "nikalo", "minus"
    )

    private val damage = listOf(
        "damaged", "damage", "broken", "spoiled",
        "poyindi", "damage ayyindi", "kharab", "toot gaya"
    )

    private val expired = listOf(
        "expired", "expiry", "expire", "date ayipoyindi",
        "expiry ayindi", "expire ho gaya"
    )

    private val customerReturn = listOf(
        "customer return", "returned by customer", "customer returned",
        "customer ichadu", "customer return vachindi"
    )

    private val supplierReturn = listOf(
        "supplier return", "return to supplier", "supplier ki return",
        "vendor return", "supplier ko return"
    )

    private val checkStock = listOf(
        "how much", "available", "check stock",
        "stock entha", "entha undi", "stock kitna", "kitna hai"
    )

    private val lowStock = listOf(
        "low stock", "running low", "needs reorder", "need reorder",
        "takkuva", "thakkuva", "తక్కువ", "kam stock", "कम स्टॉक"
    )

    private val setStock = listOf(
        "set stock", "stock set", "set quantity",
        "stock ni set", "stock set karo"
    )

    private val setMinimum = listOf(
        "set minimum", "minimum stock", "reorder level",
        "low stock limit", "minimum set", "reorder set"
    )

    private val archive = listOf(
        "delete product", "delete item", "remove product",
        "archive product", "product delete", "item delete"
    )

    private val numberWords = mapOf(
        "zero" to 0.0, "one" to 1.0, "two" to 2.0, "three" to 3.0,
        "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0,
        "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
        "eleven" to 11.0, "twelve" to 12.0, "fifteen" to 15.0,
        "twenty" to 20.0, "half" to 0.5,

        "oka" to 1.0, "okati" to 1.0, "rendu" to 2.0, "moodu" to 3.0,
        "nalugu" to 4.0, "aidu" to 5.0, "aaru" to 6.0, "edu" to 7.0,
        "enimidi" to 8.0, "tommidi" to 9.0, "padi" to 10.0,

        "ek" to 1.0, "do" to 2.0, "teen" to 3.0, "char" to 4.0,
        "paanch" to 5.0, "chhe" to 6.0, "saat" to 7.0,
        "aath" to 8.0, "nau" to 9.0, "das" to 10.0
    )

    private val units = linkedMapOf(
        "quintals" to "quintal", "quintal" to "quintal",
        "cartons" to "carton", "carton" to "carton",
        "packets" to "packet", "packet" to "packet",
        "pieces" to "piece", "piece" to "piece", "pcs" to "piece",
        "boxes" to "box", "box" to "box",
        "bags" to "bag", "bag" to "bag",
        "kilograms" to "kg", "kilogram" to "kg", "kilos" to "kg",
        "kilo" to "kg", "kgs" to "kg", "kg" to "kg",
        "litres" to "litre", "liters" to "litre",
        "litre" to "litre", "liter" to "litre",
        "dozens" to "dozen", "dozen" to "dozen",
        "grams" to "gram", "gram" to "gram"
    )

    fun understand(
        text: String,
        products: List<Product>
    ): CommandDraft {
        val n = normalize(text)

        return CommandDraft(
            action = detectAction(n),
            product = resolveProduct(n, products),
            quantity = extractQuantity(n),
            unit = extractUnit(n),
            originalUtterances = listOf(text)
        )
    }

    fun extractQuantityAndUnit(
        text: String
    ): Pair<Double?, String?> {
        val n = normalize(text)
        return extractQuantity(n) to extractUnit(n)
    }

    fun detectOutboundReason(text: String): InventoryAction? {
        val n = normalize(text)
        return when {
            containsAny(n, sale) -> InventoryAction.SALE
            containsAny(n, damage) -> InventoryAction.DAMAGE
            containsAny(n, expired) -> InventoryAction.EXPIRED
            containsAny(n, customerReturn) -> InventoryAction.CUSTOMER_RETURN
            containsAny(n, supplierReturn) -> InventoryAction.SUPPLIER_RETURN
            n.contains("correction") || n.contains("adjustment") ->
                InventoryAction.ADJUSTMENT_OUT
            else -> null
        }
    }

    fun resolveProductOnly(
        text: String,
        products: List<Product>
    ): Product? = resolveProduct(normalize(text), products)

    private fun detectAction(n: String): InventoryAction {
        return when {
            containsAny(n, lowStock) -> InventoryAction.LOW_STOCK
            containsAny(n, checkStock) -> InventoryAction.CHECK_STOCK
            containsAny(n, setMinimum) -> InventoryAction.SET_REORDER_LEVEL
            containsAny(n, setStock) -> InventoryAction.SET_STOCK
            containsAny(n, archive) -> InventoryAction.ARCHIVE_PRODUCT
            containsAny(n, customerReturn) -> InventoryAction.CUSTOMER_RETURN
            containsAny(n, supplierReturn) -> InventoryAction.SUPPLIER_RETURN
            containsAny(n, damage) -> InventoryAction.DAMAGE
            containsAny(n, expired) -> InventoryAction.EXPIRED
            containsAny(n, sale) -> InventoryAction.SALE
            containsAny(n, stockIn) -> InventoryAction.STOCK_IN
            containsAny(n, genericRemove) -> InventoryAction.OUTBOUND_UNSPECIFIED
            else -> InventoryAction.UNKNOWN
        }
    }

    private fun resolveProduct(
        n: String,
        products: List<Product>
    ): Product? {
        val exact = products
            .sortedByDescending { it.name.length }
            .firstOrNull { n.contains(normalize(it.name)) }

        if (exact != null) return exact

        val aliases = mapOf(
            "coke" to "coca cola",
            "coca cola" to "coca cola",
            "oil" to "sunflower oil",
            "parle" to "parle g",
            "parle biscuit" to "parle g"
        )

        aliases.forEach { (alias, canonical) ->
            if (n.contains(normalize(alias))) {
                return products.firstOrNull {
                    val pn = normalize(it.name)
                    pn.contains(normalize(canonical)) ||
                        normalize(canonical).contains(pn)
                }
            }
        }

        return null
    }

    private fun extractQuantity(n: String): Double? {
        Regex("""(?<![A-Za-z])\d+(?:\.\d+)?""")
            .find(n)
            ?.value
            ?.toDoubleOrNull()
            ?.let { return it }

        for (token in n.split(" ")) {
            numberWords[token]?.let { return it }
        }

        return null
    }

    private fun extractUnit(n: String): String? {
        units.forEach { (spoken, canonical) ->
            val regex = Regex("""(^|\s)${Regex.escape(spoken)}($|\s)""")
            if (regex.containsMatchIn(n)) {
                return canonical
            }
        }
        return null
    }

    private fun containsAny(n: String, values: List<String>): Boolean =
        values.any { n.contains(normalize(it)) }

    private fun normalize(value: String): String =
        value
            .lowercase(Locale.ROOT)
            .replace("-", " ")
            .replace(Regex("""[^\p{L}\p{N}.]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
