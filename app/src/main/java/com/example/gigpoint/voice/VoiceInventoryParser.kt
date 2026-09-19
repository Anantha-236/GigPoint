package com.example.gigpoint.voice

import com.example.gigpoint.CommandIntent
import com.example.gigpoint.CommandParser
import com.example.gigpoint.Product
import java.util.Locale

/**
 * Extends the existing deterministic CommandParser with a few safe
 * inventory-management actions.
 *
 * Whisper itself never changes stock.
 */
class VoiceInventoryParser {

    private val existing =
        CommandParser()

    private val setStockKeywords =
        listOf(
            "set stock",
            "stock set",
            "set quantity",
            "quantity set",
            "stock ni",
            "stock ను",
            "stock ko set",
            "stock set karo"
        )

    private val reorderKeywords =
        listOf(
            "set minimum",
            "minimum stock",
            "reorder level",
            "low stock limit",
            "minimum ni",
            "minimum set",
            "reorder set"
        )

    private val deleteKeywords =
        listOf(
            "delete product",
            "remove product",
            "delete item",
            "remove item",
            "product delete",
            "item delete"
        )

    private val numberWords =
        mapOf(
            "zero" to 0.0,
            "one" to 1.0,
            "two" to 2.0,
            "three" to 3.0,
            "four" to 4.0,
            "five" to 5.0,
            "six" to 6.0,
            "seven" to 7.0,
            "eight" to 8.0,
            "nine" to 9.0,
            "ten" to 10.0,
            "eleven" to 11.0,
            "twelve" to 12.0,
            "fifteen" to 15.0,
            "twenty" to 20.0,

            "oka" to 1.0,
            "okati" to 1.0,
            "rendu" to 2.0,
            "moodu" to 3.0,
            "nalugu" to 4.0,
            "aidu" to 5.0,
            "aaru" to 6.0,
            "edu" to 7.0,
            "enimidi" to 8.0,
            "tommidi" to 9.0,
            "padi" to 10.0,

            "ek" to 1.0,
            "do" to 2.0,
            "teen" to 3.0,
            "char" to 4.0,
            "paanch" to 5.0,
            "chhe" to 6.0,
            "saat" to 7.0,
            "aath" to 8.0,
            "nau" to 9.0,
            "das" to 10.0
        )

    fun parse(
        text: String,
        products: List<Product>
    ): VoiceInventoryCommand {

        val normalized =
            normalize(text)

        if (normalized.isBlank()) {
            return VoiceInventoryCommand(
                action =
                    VoiceInventoryAction.UNKNOWN,
                originalText = text,
                error = "Nothing was heard."
            )
        }

        val product =
            findProduct(
                normalized,
                products
            )

        if (
            deleteKeywords.any {
                normalized.contains(it)
            }
        ) {
            return if (product == null) {
                VoiceInventoryCommand(
                    action =
                        VoiceInventoryAction.DELETE_PRODUCT,
                    originalText = text,
                    error =
                        "I could not identify the product to delete."
                )
            } else {
                VoiceInventoryCommand(
                    action =
                        VoiceInventoryAction.DELETE_PRODUCT,
                    originalText = text,
                    product = product
                )
            }
        }

        if (
            reorderKeywords.any {
                normalized.contains(it)
            }
        ) {
            val quantity =
                findQuantity(normalized)

            return when {
                product == null ->
                    VoiceInventoryCommand(
                        action =
                            VoiceInventoryAction.SET_REORDER_LEVEL,
                        originalText = text,
                        error =
                            "I could not identify the product."
                    )

                quantity == null ->
                    VoiceInventoryCommand(
                        action =
                            VoiceInventoryAction.SET_REORDER_LEVEL,
                        originalText = text,
                        product = product,
                        error =
                            "I could not identify the new minimum quantity."
                    )

                else ->
                    VoiceInventoryCommand(
                        action =
                            VoiceInventoryAction.SET_REORDER_LEVEL,
                        originalText = text,
                        product = product,
                        quantity = quantity
                    )
            }
        }

        if (
            setStockKeywords.any {
                normalized.contains(it)
            }
        ) {
            val quantity =
                findQuantity(normalized)

            return when {
                product == null ->
                    VoiceInventoryCommand(
                        action =
                            VoiceInventoryAction.SET_STOCK,
                        originalText = text,
                        error =
                            "I could not identify the product."
                    )

                quantity == null ->
                    VoiceInventoryCommand(
                        action =
                            VoiceInventoryAction.SET_STOCK,
                        originalText = text,
                        product = product,
                        error =
                            "I could not identify the new stock quantity."
                    )

                else ->
                    VoiceInventoryCommand(
                        action =
                            VoiceInventoryAction.SET_STOCK,
                        originalText = text,
                        product = product,
                        quantity = quantity,
                        unit = product.unit
                    )
            }
        }

        // Reuse the existing English/Telugu/Hindi parser for the
        // already-supported commands.
        val parsed =
            existing.parse(
                text,
                products
            )

        val action =
            when (parsed.intent) {
                CommandIntent.STOCK_IN ->
                    VoiceInventoryAction.STOCK_IN

                CommandIntent.STOCK_OUT ->
                    VoiceInventoryAction.STOCK_OUT

                CommandIntent.CHECK_STOCK ->
                    VoiceInventoryAction.CHECK_STOCK

                CommandIntent.LOW_STOCK ->
                    VoiceInventoryAction.LOW_STOCK

                else ->
                    VoiceInventoryAction.UNKNOWN
            }

        return VoiceInventoryCommand(
            action = action,
            originalText = text,
            product = parsed.product,
            quantity = parsed.quantity,
            unit = parsed.unit,
            error = parsed.error
        )
    }

    private fun findProduct(
        command: String,
        products: List<Product>
    ): Product? =
        products
            .sortedByDescending {
                it.name.length
            }
            .firstOrNull {
                command.contains(
                    normalize(it.name)
                )
            }

    private fun findQuantity(
        command: String
    ): Double? {

        Regex(
            """(?<![A-Za-z])\d+(?:\.\d+)?"""
        )
            .find(command)
            ?.value
            ?.toDoubleOrNull()
            ?.let {
                return it
            }

        command
            .split(" ")
            .forEach {
                numberWords[it]?.let {
                        number ->
                    return number
                }
            }

        return null
    }

    private fun normalize(
        value: String
    ): String =
        value
            .lowercase(Locale.ROOT)
            .replace("-", " ")
            .replace(
                Regex(
                    """[^\p{L}\p{N}.]+"""
                ),
                " "
            )
            .replace(
                Regex("""\s+"""),
                " "
            )
            .trim()
}
