package com.example.gigpoint

/**
 * UI-side permission vocabulary.
 *
 * IMPORTANT:
 * This is NOT the security boundary.
 * PostgreSQL RLS/RPC authorization remains authoritative.
 */
enum class Permission(val code: String) {
    SHOP_READ("shop.read"),
    SETTINGS_READ("settings.read"),
    SETTINGS_UPDATE("settings.update"),
    MEMBER_READ("member.read"),
    MEMBER_INVITE("member.invite"),
    MEMBER_UPDATE_ROLE("member.update_role"),
    MEMBER_REMOVE("member.remove"),
    PRODUCT_READ("product.read"),
    PRODUCT_CREATE("product.create"),
    PRODUCT_UPDATE("product.update"),
    PRODUCT_DELETE("product.delete"),
    PRICE_UPDATE("price.update"),
    INVENTORY_READ("inventory.read"),
    INVENTORY_ADJUST("inventory.adjust"),
    INVENTORY_TRANSFER("inventory.transfer"),
    INVENTORY_TRANSACTION_READ("inventory.transaction.read"),
    SUPPLIER_READ("supplier.read"),
    SUPPLIER_MANAGE("supplier.manage"),
    SALE_READ("sale.read"),
    SALE_CREATE("sale.create"),
    SALE_REFUND("sale.refund"),
    SALE_VOID("sale.void"),
    PURCHASE_READ("purchase.read"),
    PURCHASE_CREATE("purchase.create"),
    PURCHASE_APPROVE("purchase.approve"),
    ALERT_READ("alert.read"),
    ALERT_MANAGE("alert.manage"),
    REPORT_READ("report.read"),
    COST_PRICE_READ("cost_price.read"),
    PROFIT_READ("profit.read"),
    AUDIT_READ("audit.read"),
    BUSINESS_DELETE("business.delete")
}

enum class ShopRole {
    OWNER,
    MANAGER,
    CASHIER,
    INVENTORY_MANAGER,
    ACCOUNTANT,
    VIEWER,
    CUSTOM,
    UNKNOWN;

    companion object {
        fun fromCode(value: String?): ShopRole {
            if (value.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CUSTOM
        }
    }
}

data class ShopAccess(
    val shopId: String,
    val membershipId: String?,
    val roleCode: String,
    val isOwner: Boolean,
    val permissions: Set<String>
) {
    val role: ShopRole
        get() = ShopRole.fromCode(roleCode)

    fun can(permission: Permission): Boolean =
        isOwner || permissions.contains(permission.code)

    fun can(permissionCode: String): Boolean =
        isOwner || permissions.contains(permissionCode)

    fun canAny(vararg required: Permission): Boolean =
        isOwner || required.any { permissions.contains(it.code) }

    fun canAll(vararg required: Permission): Boolean =
        isOwner || required.all { permissions.contains(it.code) }
}
