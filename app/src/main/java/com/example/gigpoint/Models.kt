package com.example.gigpoint

data class Product(
    val id: Long,
    val name: String,
    val unit: String,
    val quantity: Double,
    val minimumStock: Double,
    val syncStatus: String
)

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

data class DashboardStats(
    val totalProducts: Int,
    val lowStock: Int,
    val outOfStock: Int,
    val pendingSync: Int
)

enum class CommandIntent {
    STOCK_IN,
    STOCK_OUT,
    CHECK_STOCK,
    LOW_STOCK,
    UNKNOWN
}

data class ParsedCommand(
    val intent: CommandIntent,
    val product: Product? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val originalText: String,
    val error: String? = null
)
