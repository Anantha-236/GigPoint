package com.example.gigpoint

import com.example.gigpoint.domain.Product

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
