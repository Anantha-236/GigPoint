package com.example.gigpoint

import android.content.Context

/**
 * Local cache used only to shape the UI.
 * Never treat this cache as the authoritative security decision.
 */
class RbacCache(context: Context) {

    private val prefs = context.getSharedPreferences(
        "dhwani_mitra_rbac",
        Context.MODE_PRIVATE
    )

    fun save(access: ShopAccess) {
        prefs.edit()
            .putString(KEY_SHOP_ID, access.shopId)
            .putString(KEY_MEMBERSHIP_ID, access.membershipId)
            .putString(KEY_ROLE, access.roleCode)
            .putBoolean(KEY_IS_OWNER, access.isOwner)
            .putStringSet(KEY_PERMISSIONS, access.permissions)
            .apply()
    }

    fun current(): ShopAccess? {
        val shopId = prefs.getString(KEY_SHOP_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return ShopAccess(
            shopId = shopId,
            membershipId = prefs.getString(KEY_MEMBERSHIP_ID, null)
                ?.takeIf { it.isNotBlank() },
            roleCode = prefs.getString(KEY_ROLE, "UNKNOWN") ?: "UNKNOWN",
            isOwner = prefs.getBoolean(KEY_IS_OWNER, false),
            permissions = prefs.getStringSet(KEY_PERMISSIONS, emptySet())?.toSet() ?: emptySet()
        )
    }

    fun can(permission: Permission): Boolean = current()?.can(permission) ?: false

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_SHOP_ID = "shop_id"
        private const val KEY_MEMBERSHIP_ID = "membership_id"
        private const val KEY_ROLE = "role"
        private const val KEY_IS_OWNER = "is_owner"
        private const val KEY_PERMISSIONS = "permissions"
    }
}
