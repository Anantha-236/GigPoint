package com.example.gigpoint.domain

data class AppliedMutation(
    val transactionId: String?,
    val productId: Long,
    val variantId: Long,
    val operation: InventoryOperation,
    val quantity: Double?,
    val resultingQuantity: Double?
)

data class ActionResult(
    val success: Boolean,
    val commandId: String,
    val message: String,
    val applied: List<AppliedMutation> = emptyList(),
    val warnings: List<String> = emptyList(),
    val duplicate: Boolean = false
)
