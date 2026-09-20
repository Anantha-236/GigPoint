package com.example.gigpoint.domain

data class Product(
    val id: Long,
    val name: String,
    val unit: String,
    val quantity: Double,
    val minimumStock: Double,
    val syncStatus: String,
    val brand: String? = null,
    val category: String? = null,
    val active: Boolean = true
)
