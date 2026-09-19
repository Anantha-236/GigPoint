package com.example.gigpoint.voice

import com.example.gigpoint.Product

enum class VoiceInventoryAction {
    STOCK_IN,
    STOCK_OUT,
    CHECK_STOCK,
    LOW_STOCK,
    SET_STOCK,
    SET_REORDER_LEVEL,
    DELETE_PRODUCT,
    UNKNOWN
}

data class VoiceInventoryCommand(
    val action: VoiceInventoryAction,
    val originalText: String,
    val product: Product? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val error: String? = null
)
