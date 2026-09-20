package com.example.gigpoint.domain

data class StockTransaction(
    val id: String,
    val productId: Long,
    val productName: String,
    val type: String,
    val quantity: Double,
    val unit: String,
    val source: String,
    val transcript: String?,
    val createdAt: Long,
    val syncStatus: String
)
