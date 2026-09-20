package com.example.gigpoint.domain

data class Product(
    val id: Long,
    val name: String,
    val unit: String,
    val quantity: Double,
    val minimumStock: Double,
    val syncStatus: String
)
