package com.example.gigpoint

import com.example.gigpoint.data.DatabaseHelper

data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String?
)

data class MerchantProfile(
    val userId: String,
    val ownerName: String,
    val phone: String? = null,
    val preferredLanguage: String = "en",
    val theme: String = "system",
    val syncStatus: String = DatabaseHelper.SYNC_SYNCED
)

data class ShopProfile(
    val id: String,
    val ownerId: String,
    val shopName: String,
    val gstin: String? = null,
    val businessType: String,
    val city: String? = null,
    val area: String? = null,
    val syncStatus: String = DatabaseHelper.SYNC_SYNCED
)

data class MerchantContext(
    val profile: MerchantProfile?,
    val shop: ShopProfile?,
    val setupComplete: Boolean
)
