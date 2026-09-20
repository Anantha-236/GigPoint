package com.example.gigpoint.domain

data class PlannedInventoryMutation(
    val ordinal: Int,
    val productId: Long,
    val variantId: Long,
    val productName: String,
    val variantLabel: String,
    val operation: InventoryOperation,
    val quantity: Double?,
    val unit: String,
    val targetQuantity: Double? = null,
    val reorderLevel: Double? = null,
    val confidence: Confidence
)

data class ActionPlan(
    val command: MerchantCommand,
    val mutations: List<PlannedInventoryMutation>,
    val requiresConfirmation: Boolean,
    val blockingReasons: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val executable get() = blockingReasons.isEmpty() && mutations.isNotEmpty()
}
