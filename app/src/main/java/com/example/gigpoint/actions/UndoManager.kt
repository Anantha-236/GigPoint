package com.example.gigpoint.actions

import com.example.gigpoint.data.*

class UndoManager(
    private val db: DatabaseHelper,
    private val inventory: InventoryRepository = InventoryRepository(db),
    private val transactions: TransactionRepository = TransactionRepository(db),
    private val commands: CommandRepository = CommandRepository(db)
) {
    fun undoLast(): Pair<Boolean, String> {
        val original = transactions.getLastUndoable()
            ?: return false to "There is no inventory transaction available to undo."

        val variantId = original.variantId
            ?: return false to "The last transaction cannot be safely undone."

        val current = inventory.get(variantId)
            ?: return false to "Current inventory record was not found."

        val reverseType =
            when (original.type) {
                "STOCK_IN" -> "STOCK_OUT"
                "STOCK_OUT" -> "STOCK_IN"
                else -> return false to "The last transaction type cannot be undone automatically."
            }

        val resulting =
            if (reverseType == "STOCK_OUT")
                current.quantity - original.quantity
            else
                current.quantity + original.quantity

        if (resulting < 0) {
            return false to "Undo would make stock negative, so it was blocked."
        }

        val database = db.writableDatabase
        database.beginTransaction()
        try {
            check(inventory.setQuantity(variantId, resulting) == 1)

            val reverseId = transactions.insert(
                productId = original.productId,
                variantId = variantId,
                commandId = null,
                type = reverseType,
                quantity = original.quantity,
                unit = original.unit,
                source = "UNDO",
                transcript = "UNDO ${original.id}",
                reversesTransactionId = original.id
            )

            transactions.markReversed(original.id, reverseId)

            commands.appendAudit(
                original.commandId,
                "TRANSACTION_UNDONE",
                "STOCK_TRANSACTION",
                original.id,
                "reverse=$reverseId"
            )

            database.setTransactionSuccessful()
            return true to "Last inventory change was undone."
        } finally {
            database.endTransaction()
        }
    }
}
