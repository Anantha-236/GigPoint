package com.example.gigpoint.voice

import android.content.ContentValues
import com.example.gigpoint.DatabaseHelper
import com.example.gigpoint.Product

/**
 * Executes only already-confirmed commands.
 *
 * Transaction history should not be deleted. Incorrect movements should be
 * corrected with an adjustment, preserving the audit trail.
 */
class LocalInventoryActionService(
    private val db: DatabaseHelper
) {

    fun setExactStock(
        product: Product,
        newQuantity: Double,
        transcript: String
    ): Pair<Boolean, String> {

        if (newQuantity < 0) {
            return false to
                "Stock cannot be negative."
        }

        val delta =
            newQuantity -
                product.quantity

        if (delta == 0.0) {
            return true to
                "${product.name} is already at ${format(newQuantity)} ${product.unit}."
        }

        return if (delta > 0) {
            db.adjustStock(
                productId =
                    product.id,
                type = "STOCK_IN",
                quantity = delta,
                source = "VOICE",
                transcript =
                    "SET_STOCK: $transcript"
            )
        } else {
            db.adjustStock(
                productId =
                    product.id,
                type = "STOCK_OUT",
                quantity = -delta,
                source = "VOICE",
                transcript =
                    "SET_STOCK: $transcript"
            )
        }
    }

    fun updateReorderLevel(
        product: Product,
        newMinimum: Double
    ): Pair<Boolean, String> {

        if (newMinimum < 0) {
            return false to
                "Minimum stock cannot be negative."
        }

        val values =
            ContentValues().apply {
                put(
                    "minimum_stock",
                    newMinimum
                )
                put(
                    "updated_at",
                    System.currentTimeMillis()
                )
                put(
                    "sync_status",
                    DatabaseHelper.SYNC_PENDING
                )
            }

        val changed =
            db.writableDatabase.update(
                "products",
                values,
                "id = ?",
                arrayOf(
                    product.id.toString()
                )
            )

        return if (changed == 1) {
            true to
                "${product.name} minimum stock updated to ${format(newMinimum)} ${product.unit}."
        } else {
            false to
                "Could not update ${product.name}."
        }
    }

    /**
     * Safe MVP deletion:
     * - no history -> physical delete allowed
     * - existing transactions -> refuse
     *
     * The Product/Variant migration should replace this with soft-delete
     * (active=false/deleted_at) so history is permanently preserved.
     */
    fun deleteProductIfUnused(
        product: Product
    ): Pair<Boolean, String> {

        val transactionCount =
            db.readableDatabase.rawQuery(
                """
                SELECT COUNT(*)
                FROM stock_transactions
                WHERE product_id = ?
                """.trimIndent(),
                arrayOf(
                    product.id.toString()
                )
            ).use {
                if (it.moveToFirst())
                    it.getInt(0)
                else
                    0
            }

        if (transactionCount > 0) {
            return false to
                "Cannot delete ${product.name} because it has transaction history. Archive it after the SKU migration instead."
        }

        val deleted =
            db.writableDatabase.delete(
                "products",
                "id = ?",
                arrayOf(
                    product.id.toString()
                )
            )

        return if (deleted == 1) {
            true to
                "${product.name} deleted."
        } else {
            false to
                "Could not delete ${product.name}."
        }
    }

    private fun format(
        value: Double
    ): String =
        if (value % 1.0 == 0.0)
            value.toLong()
                .toString()
        else
            "%.2f".format(value)
}
