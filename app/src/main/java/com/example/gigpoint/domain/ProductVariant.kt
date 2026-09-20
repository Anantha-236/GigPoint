package com.example.gigpoint.domain

data class ProductVariant(
    val id: Long,
    val productId: Long,
    val sku: String,
    val variantName: String?,
    val packageQuantity: Double?,
    val packageUnit: String?,
    val containerType: String?,
    val sellingUnit: String,
    val purchasePrice: Double?,
    val sellingPrice: Double?,
    val barcode: String?,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
