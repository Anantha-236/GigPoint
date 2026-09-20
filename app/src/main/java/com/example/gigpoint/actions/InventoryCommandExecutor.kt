package com.example.gigpoint.actions

import android.content.ContentValues
import com.example.gigpoint.data.*
import com.example.gigpoint.domain.*
import kotlin.math.abs

class InventoryCommandExecutor(
    private val db: DatabaseHelper,
    private val inventory: InventoryRepository = InventoryRepository(db),
    private val transactions: TransactionRepository = TransactionRepository(db),
    private val commands: CommandRepository = CommandRepository(db)
) {
    fun execute(plan: ActionPlan): ActionResult {
        commands.getIdempotentResult(plan.command.id)?.let { previous ->
            return ActionResult(
                success = true,
                commandId = plan.command.id,
                message = previous,
                duplicate = true
            )
        }

        if (!plan.executable) {
            return ActionResult(
                false,
                plan.command.id,
                plan.blockingReasons.joinToString(" ")
                    .ifBlank { "This command cannot be executed." },
                warnings = plan.warnings
            )
        }

        commands.savePlanned(plan.command)
        val database = db.writableDatabase
        val applied = mutableListOf<AppliedMutation>()

        database.beginTransaction()
        try {
            plan.mutations.sortedBy { it.ordinal }.forEach { m ->
                val current = inventory.get(m.variantId)
                    ?: error("Inventory record is missing for ${m.variantLabel}.")

                if (m.operation == InventoryOperation.SET_REORDER_LEVEL) {
                    check(inventory.setReorderLevel(m.variantId, m.reorderLevel!!) == 1)
                    applied += AppliedMutation(
                        null, m.productId, m.variantId, m.operation,
                        m.reorderLevel, current.quantity
                    )
                    return@forEach
                }

                val resulting =
                    when (m.operation) {
                        InventoryOperation.STOCK_IN,
                        InventoryOperation.CUSTOMER_RETURN,
                        InventoryOperation.ADJUSTMENT_IN ->
                            current.quantity + m.quantity!!

                        InventoryOperation.SALE,
                        InventoryOperation.DAMAGE,
                        InventoryOperation.EXPIRED,
                        InventoryOperation.SUPPLIER_RETURN,
                        InventoryOperation.ADJUSTMENT_OUT ->
                            current.quantity - m.quantity!!

                        InventoryOperation.SET_STOCK ->
                            m.targetQuantity!!

                        else ->
                            error("Unsupported executable operation: ${m.operation}")
                    }

                if (resulting < 0) {
                    error("Not enough ${m.variantLabel} in stock.")
                }

                check(inventory.setQuantity(m.variantId, resulting) == 1)

                db.writableDatabase.update(
                    "products",
                    ContentValues().apply {
                        put("quantity", resulting)
                        put("updated_at", System.currentTimeMillis())
                        put("sync_status", DatabaseHelper.SYNC_PENDING)
                    },
                    "id = ?",
                    arrayOf(m.productId.toString())
                )

                val txType =
                    when {
                        m.operation.stockSign > 0 -> "STOCK_IN"
                        m.operation.stockSign < 0 -> "STOCK_OUT"
                        m.operation == InventoryOperation.SET_STOCK ->
                            if (resulting >= current.quantity) "STOCK_IN" else "STOCK_OUT"
                        else -> m.operation.name
                    }

                val txQty =
                    if (m.operation == InventoryOperation.SET_STOCK)
                        abs(resulting - current.quantity)
                    else
                        m.quantity ?: 0.0

                val txId =
                    if (txQty > 0.0) {
                        transactions.insert(
                            productId = m.productId,
                            variantId = m.variantId,
                            commandId = plan.command.id,
                            type = txType,
                            quantity = txQty,
                            unit = m.unit,
                            source = "VOICE",
                            transcript = "${m.operation.name}: ${plan.command.sourceTranscript}"
                        )
                    } else null

                commands.appendAudit(
                    plan.command.id,
                    "INVENTORY_MUTATION_APPLIED",
                    "PRODUCT_VARIANT",
                    m.variantId.toString(),
                    "${m.operation.name}|${m.variantLabel}|$resulting"
                )

                applied += AppliedMutation(
                    txId, m.productId, m.variantId, m.operation, m.quantity, resulting
                )
            }

            val message =
                if (applied.size == 1) "Inventory updated successfully."
                else "${applied.size} inventory items were updated successfully."

            commands.markExecuted(plan.command.id, message)
            database.setTransactionSuccessful()

            return ActionResult(
                true,
                plan.command.id,
                message,
                applied,
                plan.warnings
            )
        } catch (e: Exception) {
            commands.markFailed(
                plan.command.id,
                e.message ?: "Inventory command failed."
            )
            return ActionResult(
                false,
                plan.command.id,
                e.message ?: "Inventory command failed.",
                warnings = plan.warnings
            )
        } finally {
            database.endTransaction()
        }
    }
}
