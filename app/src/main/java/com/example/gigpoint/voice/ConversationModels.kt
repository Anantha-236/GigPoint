package com.example.gigpoint.voice

import com.example.gigpoint.Product

enum class InventoryAction {
    STOCK_IN,
    SALE,
    DAMAGE,
    EXPIRED,
    CUSTOMER_RETURN,
    SUPPLIER_RETURN,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    SET_STOCK,
    SET_REORDER_LEVEL,
    CHECK_STOCK,
    LOW_STOCK,
    ARCHIVE_PRODUCT,
    OUTBOUND_UNSPECIFIED,
    UNKNOWN
}

enum class MissingSlot {
    ACTION,
    PRODUCT,
    QUANTITY,
    OUTBOUND_REASON
}

data class CommandDraft(
    val action: InventoryAction? = null,
    val product: Product? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val originalUtterances: List<String> = emptyList()
)

sealed class VoiceTurn {
    data class Ask(
        val question: String,
        val slot: MissingSlot,
        val draft: CommandDraft,
        val choices: List<String> = emptyList()
    ) : VoiceTurn()

    data class Confirm(
        val prompt: String,
        val draft: CommandDraft
    ) : VoiceTurn()

    data class Answer(
        val text: String
    ) : VoiceTurn()

    data class Error(
        val message: String,
        val keepContext: Boolean = true
    ) : VoiceTurn()
}
