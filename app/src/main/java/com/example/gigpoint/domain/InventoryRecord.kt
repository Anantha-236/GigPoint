package com.example.gigpoint.domain

data class InventoryRecord(
    val variantId: Long,
    val quantity: Double,
    val reorderLevel: Double,
    val updatedAt: Long,
    val syncStatus: String
)
