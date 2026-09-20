package com.example.gigpoint.actions

import com.example.gigpoint.voice.CommandDraft
import com.example.gigpoint.voice.InventoryAction

import android.content.ContentValues
import com.example.gigpoint.data.DatabaseHelper
import com.example.gigpoint.domain.Product

class VoiceActionExecutor(
    private val db: DatabaseHelper
) {

    fun execute(
        draft: CommandDraft
    ): Pair<Boolean, String> {

        val product = draft.product
            ?: return false to "Product is missing."

        return when (draft.action) {
            InventoryAction.STOCK_IN,
            InventoryAction.CUSTOMER_RETURN,
            InventoryAction.ADJUSTMENT_IN -> {
                db.adjustStock(
                    productId = product.id,
                    type = "STOCK_IN",
                    quantity = draft.quantity!!,
                    source = "VOICE",
                    transcript =
                        draft.originalUtterances
                            .joinToString(" | ")
                )
            }

            InventoryAction.SALE,
            InventoryAction.DAMAGE,
            InventoryAction.EXPIRED,
            InventoryAction.SUPPLIER_RETURN,
            InventoryAction.ADJUSTMENT_OUT -> {
                db.adjustStock(
                    productId = product.id,
                    type = "STOCK_OUT",
                    quantity = draft.quantity!!,
                    source = "VOICE",
                    transcript =
                        "${draft.action}: " +
                        draft.originalUtterances
                            .joinToString(" | ")
                )
            }

            InventoryAction.SET_STOCK ->
                setExactStock(
                    product,
                    draft.quantity!!,
                    draft.originalUtterances
                        .joinToString(" | ")
                )

            InventoryAction.SET_REORDER_LEVEL ->
                setReorderLevel(
                    product,
                    draft.quantity!!
                )

            InventoryAction.ARCHIVE_PRODUCT ->
                archivePrototypeProduct(product)

            else ->
                false to
                    "This action cannot be executed."
        }
    }

    private fun setExactStock(
        product: Product,
        target: Double,
        transcript: String
    ): Pair<Boolean, String> {

        if (target < 0) {
            return false to
                "Stock cannot be negative."
        }

        val difference =
            target - product.quantity

        if (difference == 0.0) {
            return true to
                "${product.name} is already at ${fmt(target)} ${product.unit}."
        }

        return db.adjustStock(
            productId = product.id,
            type =
                if (difference > 0)
                    "STOCK_IN"
                else
                    "STOCK_OUT",
            quantity =
                kotlin.math.abs(difference),
            source = "VOICE",
            transcript =
                "SET_STOCK: $transcript"
        )
    }

    private fun setReorderLevel(
        product: Product,
        minimum: Double
    ): Pair<Boolean, String> {

        if (minimum < 0) {
            return false to
                "Minimum stock cannot be negative."
        }

        val values =
            ContentValues().apply {
                put(
                    "minimum_stock",
                    minimum
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
                "${product.name} low-stock level is now ${fmt(minimum)} ${product.unit}."
        } else {
            false to
                "Could not update ${product.name}."
        }
    }

    /**
     * Current v1 database has no soft-delete column.
     * We therefore refuse deletion once transaction history exists.
     *
     * After the Product/SKU migration this must become:
     * active=false, deleted_at=...
     */
    private fun archivePrototypeProduct(
        product: Product
    ): Pair<Boolean, String> {

        val history =
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

        if (history > 0) {
            return false to
                "I will not delete ${product.name} because it has transaction history. After the SKU migration this action will archive it instead."
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
                "${product.name} removed."
        } else {
            false to
                "Could not remove ${product.name}."
        }
    }

    private fun fmt(value: Double): String =
        if (value % 1.0 == 0.0)
            value.toLong().toString()
        else
            "%.2f".format(value)
}
