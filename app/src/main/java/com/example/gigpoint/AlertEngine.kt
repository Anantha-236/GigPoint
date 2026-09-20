package com.example.gigpoint

import com.example.gigpoint.data.DatabaseHelper
import com.example.gigpoint.domain.Product

/**
 * Calculates inventory alerts from the current LOCAL Android database.
 *
 * Current local Product model supports:
 *
 * - quantity
 * - minimumStock
 * - syncStatus
 *
 * Therefore this engine can correctly calculate:
 *
 * - OUT_OF_STOCK
 * - LOW_STOCK
 * - SYNC_PENDING
 *
 * OLD_STOCK, NEAR_EXPIRY and EXPIRED require batch/date information.
 * They must NOT be guessed from Product.updated_at because "updated" is
 * not the same thing as "stock received".
 *
 * Those alert types are included here so the engine can be extended
 * after the local Product/SKU/Batch database migration.
 */
class AlertEngine(
    private val db: DatabaseHelper
) {

    enum class AlertType {
        OUT_OF_STOCK,
        LOW_STOCK,
        REORDER,
        OLD_STOCK,
        NEAR_EXPIRY,
        EXPIRED,
        FAST_MOVING,
        SLOW_MOVING,
        DEAD_STOCK,
        SYNC_PENDING,
        SYNC_FAILED
    }

    enum class AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }

    data class InventoryAlert(
        val id: String,

        val type: AlertType,

        val severity: AlertSeverity,

        val productId: Long?,

        val productName: String?,

        val title: String,

        val message: String,

        val quantity: Double? = null,

        val unit: String? = null,

        /**
         * Human-readable action that the UI may display.
         *
         * Examples:
         *
         * "Refill"
         * "View product"
         * "Sync now"
         */
        val actionLabel: String? = null
    )

    /**
     * Evaluate every alert that can be determined safely from
     * the CURRENT local Android database.
     */
    fun evaluateAll(): List<InventoryAlert> {

        val alerts =
            mutableListOf<InventoryAlert>()

        val products =
            db.getProducts()

        for (product in products) {

            evaluateProduct(product)
                ?.let {
                    alerts += it
                }
        }

        createPendingSyncAlert()
            ?.let {
                alerts += it
            }

        return alerts.sortedWith(
            compareBy<InventoryAlert> {
                severityPriority(
                    it.severity
                )
            }.thenBy {
                it.productName ?: ""
            }
        )
    }

    /**
     * Evaluate alerts for one product.
     *
     * OUT_OF_STOCK takes priority over LOW_STOCK because an
     * out-of-stock product is automatically also below its
     * reorder level.
     */
    fun evaluateProduct(
        product: Product
    ): InventoryAlert? {

        return when {

            product.quantity <= 0.0 -> {

                InventoryAlert(
                    id =
                        "OUT_OF_STOCK_${product.id}",

                    type =
                        AlertType.OUT_OF_STOCK,

                    severity =
                        AlertSeverity.CRITICAL,

                    productId =
                        product.id,

                    productName =
                        product.name,

                    title =
                        "${product.name} is out of stock",

                    message =
                        "No ${product.unit} of " +
                                "${product.name} is currently available.",

                    quantity =
                        product.quantity,

                    unit =
                        product.unit,

                    actionLabel =
                        "Refill"
                )
            }

            product.minimumStock > 0.0 &&
                    product.quantity <=
                    product.minimumStock -> {

                InventoryAlert(
                    id =
                        "LOW_STOCK_${product.id}",

                    type =
                        AlertType.LOW_STOCK,

                    severity =
                        AlertSeverity.WARNING,

                    productId =
                        product.id,

                    productName =
                        product.name,

                    title =
                        "${product.name} is running low",

                    message =
                        "${format(product.quantity)} " +
                                "${product.unit} remaining. " +
                                "Low-stock level is " +
                                "${format(product.minimumStock)} " +
                                "${product.unit}.",

                    quantity =
                        product.quantity,

                    unit =
                        product.unit,

                    actionLabel =
                        "Refill"
                )
            }

            else ->
                null
        }
    }

    /**
     * Convenience function used by the dashboard.
     */
    fun getLowStockAlerts():
            List<InventoryAlert> {

        return evaluateAll()
            .filter {
                it.type ==
                        AlertType.LOW_STOCK
            }
    }

    /**
     * Convenience function used by the dashboard.
     */
    fun getOutOfStockAlerts():
            List<InventoryAlert> {

        return evaluateAll()
            .filter {
                it.type ==
                        AlertType.OUT_OF_STOCK
            }
    }

    /**
     * Alerts that need immediate merchant attention.
     */
    fun getCriticalAlerts():
            List<InventoryAlert> {

        return evaluateAll()
            .filter {
                it.severity ==
                        AlertSeverity.CRITICAL
            }
    }

    /**
     * Returns how many inventory alerts currently exist.
     *
     * SYNC_PENDING is intentionally not counted as an inventory
     * shortage alert.
     */
    fun inventoryAlertCount(): Int {

        return evaluateAll()
            .count {
                it.type !=
                        AlertType.SYNC_PENDING
            }
    }

    /**
     * Generates a single sync alert if local records are waiting
     * for cloud synchronization.
     *
     * This uses DatabaseHelper.getStats(), which already counts
     * local pending products and stock transactions.
     */
    private fun createPendingSyncAlert():
            InventoryAlert? {

        val pending =
            db.getStats()
                .pendingSync

        if (pending <= 0) {
            return null
        }

        return InventoryAlert(
            id =
                "SYNC_PENDING",

            type =
                AlertType.SYNC_PENDING,

            severity =
                AlertSeverity.INFO,

            productId =
                null,

            productName =
                null,

            title =
                "Inventory backup pending",

            message =
                "$pending local record" +
                        if (pending == 1) {
                            " is waiting to sync."
                        } else {
                            "s are waiting to sync."
                        },

            actionLabel =
                "Sync now"
        )
    }

    /**
     * Used after stock changes.
     *
     * Example:
     *
     * STOCK OUT
     *     ↓
     * DatabaseHelper.adjustStock()
     *     ↓
     * AlertEngine.evaluateAfterInventoryChange()
     *
     * This lets the UI immediately tell the merchant whether the
     * operation caused LOW_STOCK or OUT_OF_STOCK.
     */
    fun evaluateAfterInventoryChange(
        productId: Long
    ): InventoryAlert? {

        val product =
            db.getProductById(
                productId
            )
                ?: return null

        return evaluateProduct(
            product
        )
    }

    /**
     * Returns a message suitable for TTS after an inventory update.
     *
     * Example:
     *
     * "Rice updated to 4 bags. Rice is now below the
     * low-stock level of 5 bags."
     */
    fun buildVoiceAlertMessage(
        productId: Long
    ): String? {

        val alert =
            evaluateAfterInventoryChange(
                productId
            )
                ?: return null

        return when (alert.type) {

            AlertType.OUT_OF_STOCK ->
                "${alert.productName} is now out of stock."

            AlertType.LOW_STOCK -> {

                val product =
                    db.getProductById(
                        productId
                    )
                        ?: return alert.message

                "${product.name} is now low on stock. " +
                        "${format(product.quantity)} " +
                        "${product.unit} remaining. " +
                        "The low-stock level is " +
                        "${format(product.minimumStock)} " +
                        "${product.unit}."
            }

            else ->
                alert.message
        }
    }

    /**
     * Returns a short textual summary for voice queries such as:
     *
     * "What is low on stock?"
     * "What needs refilling?"
     */
    fun buildLowStockVoiceSummary(): String {

        val products =
            db.getLowStockProducts()

        if (products.isEmpty()) {

            return "No products are currently low on stock."
        }

        val outOfStock =
            products.filter {
                it.quantity <= 0.0
            }

        val lowStock =
            products.filter {
                it.quantity > 0.0
            }

        return buildString {

            if (outOfStock.isNotEmpty()) {

                append(
                    "Out of stock: "
                )

                append(
                    outOfStock.joinToString(
                        separator = ", "
                    ) {
                        it.name
                    }
                )

                append(". ")
            }

            if (lowStock.isNotEmpty()) {

                append(
                    "Low stock: "
                )

                append(
                    lowStock.joinToString(
                        separator = "; "
                    ) {
                            product ->

                        "${product.name}, " +
                                "${format(product.quantity)} " +
                                product.unit
                    }
                )

                append(".")
            }
        }.trim()
    }

    /**
     * OLD STOCK
     *
     * Do NOT implement this using products.updated_at.
     *
     * Correct implementation requires:
     *
     * StockBatch
     * - receivedAt / purchaseDate
     * - remainingQuantity
     * - expiryDate
     *
     * Then old stock could safely mean:
     *
     * remainingQuantity > 0 &&
     * receivedAt <= today - configuredOldStockDays
     */
    fun getOldStockAlerts():
            List<InventoryAlert> {

        // Not possible safely with current flat local schema.
        return emptyList()
    }

    /**
     * NEAR EXPIRY
     *
     * Requires StockBatch.expiryDate.
     */
    fun getNearExpiryAlerts():
            List<InventoryAlert> {

        // Will be implemented after local batch migration.
        return emptyList()
    }

    /**
     * EXPIRED
     *
     * Requires StockBatch.expiryDate.
     */
    fun getExpiredAlerts():
            List<InventoryAlert> {

        // Will be implemented after local batch migration.
        return emptyList()
    }

    /**
     * FAST / SLOW / DEAD stock require time-window sales analysis.
     *
     * The existing stock transaction history can later be used to
     * calculate:
     *
     * sales in last 7 days
     * sales in last 30 days
     * last sale timestamp
     */
    fun getMovementAlerts():
            List<InventoryAlert> {

        // Implement after the transaction analytics query is added.
        return emptyList()
    }

    private fun severityPriority(
        severity: AlertSeverity
    ): Int {

        return when (severity) {

            AlertSeverity.CRITICAL ->
                0

            AlertSeverity.WARNING ->
                1

            AlertSeverity.INFO ->
                2
        }
    }

    private fun format(
        value: Double
    ): String {

        return if (
            value % 1.0 == 0.0
        ) {

            value.toLong()
                .toString()

        } else {

            "%.2f".format(
                value
            )
        }
    }
}