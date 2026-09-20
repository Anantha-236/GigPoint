package com.example.gigpoint

import com.example.gigpoint.domain.Product

import java.util.Locale

class CommandParser {

    private val stockOutKeywords = listOf(
        "stock out", "remove", "sold", "sale", "sell",
        "teesey", "teese", "ammindi", "ammayi", "sale ayyindi", "sale ayyayi",
        "nikalo", "bik gaya", "bik gaye", "minus"
    )

    private val stockInKeywords = listOf(
        "stock in", "add", "received", "came", "plus",
        "add cheyyi", "add chey", "vachindi", "vachayi", "vachai", "stock lo pettu",
        "add karo", "aaya", "aayi", "daalo", "stock mein daalo"
    )

    private val lowStockKeywords = listOf(
        "low stock", "running low", "what needs to be ordered",
        "reorder", "takkuva", "thakkuva", "తక్కువ", "kam stock", "कम स्टॉक"
    )

    private val checkStockKeywords = listOf(
        "how much", "stock entha", "entha undi", "available",
        "check stock", "stock kitna", "kitna hai", "ఎంత", "कितना"
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

    private val unitAliases = linkedMapOf(
        "quintals" to "quintal",
        "quintal" to "quintal",
        "cartons" to "carton",
        "carton" to "carton",
        "packets" to "packet",
        "packet" to "packet",
        "pieces" to "piece",
        "piece" to "piece",
        "boxes" to "box",
        "box" to "box",
        "bags" to "bag",
        "bag" to "bag",
        "kilograms" to "kg",
        "kilogram" to "kg",
        "kilos" to "kg",
        "kilo" to "kg",
        "kgs" to "kg",
        "kg" to "kg",
        "litres" to "litre",
        "liters" to "litre",
        "litre" to "litre",
        "liter" to "litre",
        "dozens" to "dozen",
        "dozen" to "dozen",
        "grams" to "gram",
        "gram" to "gram",
        "pcs" to "piece"
    )

    private val productAliases = mapOf(
        "coke" to "coca cola",
        "coca cola" to "coca cola",
        "oil" to "sunflower oil",
        "parle biscuit" to "parle g",
        "parle" to "parle g"
    )

    fun parse(text: String, products: List<Product>): ParsedCommand {
        val normalized = normalize(text)

        if (normalized.isBlank()) {
            return ParsedCommand(
                intent = CommandIntent.UNKNOWN,
                originalText = text,
                error = "No command received."
            )
        }

        val intent = when {
            lowStockKeywords.any { normalized.contains(normalize(it)) } ->
                CommandIntent.LOW_STOCK

            checkStockKeywords.any { normalized.contains(normalize(it)) } ->
                CommandIntent.CHECK_STOCK

            stockOutKeywords.any { normalized.contains(normalize(it)) } ->
                CommandIntent.STOCK_OUT

            stockInKeywords.any { normalized.contains(normalize(it)) } ->
                CommandIntent.STOCK_IN

            else -> CommandIntent.UNKNOWN
        }

        if (intent == CommandIntent.LOW_STOCK) {
            return ParsedCommand(intent = intent, originalText = text)
        }

        val product = findProduct(normalized, products)

        if (intent == CommandIntent.CHECK_STOCK) {
            return if (product != null) {
                ParsedCommand(intent = intent, product = product, originalText = text)
            } else {
                ParsedCommand(
                    intent = intent,
                    originalText = text,
                    error = "I could not identify the product."
                )
            }
        }

        if (intent == CommandIntent.UNKNOWN) {
            return ParsedCommand(
                intent = intent,
                originalText = text,
                error = "I could not understand whether this is stock in, stock out, or a stock question."
            )
        }

        if (product == null) {
            return ParsedCommand(
                intent = intent,
                originalText = text,
                error = "I could not identify the product."
            )
        }

        val quantity = findQuantity(normalized)
        if (quantity == null || quantity <= 0) {
            return ParsedCommand(
                intent = intent,
                product = product,
                originalText = text,
                error = "I could not identify the quantity."
            )
        }

        val unit = findUnit(normalized) ?: product.unit

        return ParsedCommand(
            intent = intent,
            product = product,
            quantity = quantity,
            unit = unit,
            originalText = text
        )
    }

    private fun findProduct(command: String, products: List<Product>): Product? {
        val sortedProducts = products.sortedByDescending { it.name.length }

        sortedProducts.firstOrNull { command.contains(normalize(it.name)) }?.let {
            return it
        }

        productAliases.forEach { (alias, canonical) ->
            if (command.contains(normalize(alias))) {
                return products.firstOrNull {
                    normalize(it.name).contains(normalize(canonical)) ||
                            normalize(canonical).contains(normalize(it.name))
                }
            }
        }

        return null
    }

    private fun findQuantity(command: String): Double? {
        Regex("""(?<![A-Za-z])\d+(?:\.\d+)?""")
            .find(command)
            ?.value
            ?.toDoubleOrNull()
            ?.let { return it }

        val tokens = command.split(" ")
        tokens.forEach { token ->
            numberWords[token]?.let { return it }
        }

        return null
    }

    private fun findUnit(command: String): String? {
        unitAliases.forEach { (spoken, canonical) ->
            val regex = Regex("""(^|\s)${Regex.escape(spoken)}($|\s)""")
            if (regex.containsMatchIn(command)) return canonical
        }
        return null
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace("-", " ")
            .replace(Regex("""[^\p{L}\p{N}.]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
