package com.example.gigpoint.data

import android.content.ContentValues
import com.example.gigpoint.domain.InventoryRecord

class InventoryRepository(
    private val db: DatabaseHelper
) {
    fun get(variantId: Long): InventoryRecord? =
        db.readableDatabase.rawQuery(
            """
            SELECT variant_id, quantity, reorder_level, updated_at, sync_status
            FROM inventory
            WHERE variant_id = ?
            """.trimIndent(),
            arrayOf(variantId.toString())
        ).use { c ->
            if (!c.moveToFirst()) null
            else InventoryRecord(
                variantId = c.getLong(0),
                quantity = c.getDouble(1),
                reorderLevel = c.getDouble(2),
                updatedAt = c.getLong(3),
                syncStatus = c.getString(4)
            )
        }

    fun setQuantity(variantId: Long, quantity: Double): Int =
        db.writableDatabase.update(
            "inventory",
            ContentValues().apply {
                put("quantity", quantity)
                put("updated_at", System.currentTimeMillis())
                put("sync_status", DatabaseHelper.SYNC_PENDING)
            },
            "variant_id = ?",
            arrayOf(variantId.toString())
        )

    fun setReorderLevel(variantId: Long, level: Double): Int =
        db.writableDatabase.update(
            "inventory",
            ContentValues().apply {
                put("reorder_level", level)
                put("updated_at", System.currentTimeMillis())
                put("sync_status", DatabaseHelper.SYNC_PENDING)
            },
            "variant_id = ?",
            arrayOf(variantId.toString())
        )
}
