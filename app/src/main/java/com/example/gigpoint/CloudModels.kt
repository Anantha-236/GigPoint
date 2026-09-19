package com.example.gigpoint

data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String?
)

data class MerchantProfile(
    val userId: String,
    val ownerName: String,
    val phone: String?,
    val preferredLanguage: String,
    val theme: String,
    val syncStatus: String = DatabaseHelper.SYNC_SYNCED
)

data class ShopProfile(
    val id: String,
    val ownerId: String,
    val shopName: String,
    val gstin: String?,
    val businessType: String,
    val city: String?,
    val area: String?,
    val syncStatus: String = DatabaseHelper.SYNC_SYNCED
)

data class MerchantContext(
    val profile: MerchantProfile?,
    val shop: ShopProfile?,
    val setupComplete: Boolean
)
