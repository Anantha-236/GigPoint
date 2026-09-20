package com.example.gigpoint.domain

data class CommandItem(
    val ordinal: Int,
    val productMention: String,
    val operation: InventoryOperation,
    val resolvedProductId: Long? = null,
    val resolvedVariantId: Long? = null,
    val quantity: Double? = null,
    val quantityUnit: String? = null,
    val packageQuantity: Double? = null,
    val packageUnit: String? = null,
    val packaging: String? = null,
    val price: Double? = null,
    val confidence: Confidence = Confidence(0.0),
    val clarification: String? = null
)
