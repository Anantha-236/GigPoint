package com.example.gigpoint.domain

data class StockBatch(
    val id: String,
    val variantId: Long,
    val quantityReceived: Double,
    val quantityRemaining: Double,
    val purchasePrice: Double?,
    val supplier: String?,
    val receivedAt: Long,
    val manufacturedAt: Long?,
    val expiresAt: Long?,
    val syncStatus: String
)
