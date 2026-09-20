package com.example.gigpoint.data

import android.content.ContentValues
import com.example.gigpoint.domain.StockTransaction
import java.util.UUID

class TransactionRepository(
    private val db: DatabaseHelper
) {
    fun insert(
        productId: Long,
        variantId: Long,
        commandId: String?,
        type: String,
        quantity: Double,
        unit: String,
        source: String,
        transcript: String?,
        reversesTransactionId: String? = null
    ): String {
        val id = UUID.randomUUID().toString()

        db.writableDatabase.insertOrThrow(
            "stock_transactions",
            null,
            ContentValues().apply {
                put("id", id)
                put("product_id", productId)
                put("variant_id", variantId)
                put("command_id", commandId)
                put("type", type)
                put("quantity", quantity)
                put("unit", unit)
                put("source", source)
                put("transcript", transcript)
                put("created_at", System.currentTimeMillis())
                put("sync_status", DatabaseHelper.SYNC_PENDING)
                put("reverses_transaction_id", reversesTransactionId)
            }
        )

        return id
    }

    fun markReversed(originalId: String, reversingId: String) {
        db.writableDatabase.update(
            "stock_transactions",
            ContentValues().apply {
                put("reversed_by_transaction_id", reversingId)
                put("sync_status", DatabaseHelper.SYNC_PENDING)
            },
            "id = ?",
            arrayOf(originalId)
        )
    }

    fun getLastUndoable(): StockTransaction? =
        db.readableDatabase.rawQuery(
            """
            SELECT t.id, t.product_id, p.name, t.type, t.quantity, t.unit,
                   t.source, t.transcript, t.created_at, t.sync_status,
                   t.variant_id, t.command_id,
                   t.reverses_transaction_id, t.reversed_by_transaction_id
            FROM stock_transactions t
            JOIN products p ON p.id = t.product_id
            WHERE t.reversed_by_transaction_id IS NULL
              AND t.reverses_transaction_id IS NULL
              AND t.variant_id IS NOT NULL
            ORDER BY t.created_at DESC
            LIMIT 1
            """.trimIndent(),
            null
        ).use { c ->
            if (!c.moveToFirst()) null
            else StockTransaction(
                id = c.getString(0),
                productId = c.getLong(1),
                productName = c.getString(2),
                type = c.getString(3),
                quantity = c.getDouble(4),
                unit = c.getString(5),
                source = c.getString(6),
                transcript = if (c.isNull(7)) null else c.getString(7),
                createdAt = c.getLong(8),
                syncStatus = c.getString(9),
                variantId = c.getLong(10),
                commandId = if (c.isNull(11)) null else c.getString(11),
                reversesTransactionId = if (c.isNull(12)) null else c.getString(12),
                reversedByTransactionId = if (c.isNull(13)) null else c.getString(13)
            )
        }
}
