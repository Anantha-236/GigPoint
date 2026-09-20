package com.example.gigpoint.domain

data class ProductAlias(
    val id: Long,
    val productId: Long,
    val variantId: Long?,
    val alias: String,
    val normalizedAlias: String,
    val language: String?,
    val source: String,
    val confidence: Double
)
