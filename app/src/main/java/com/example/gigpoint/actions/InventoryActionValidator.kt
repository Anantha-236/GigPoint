package com.example.gigpoint.actions

import com.example.gigpoint.domain.InventoryOperation
import com.example.gigpoint.domain.PlannedInventoryMutation

class InventoryActionValidator {
    data class Validation(
        val valid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    fun validate(mutation: PlannedInventoryMutation): Validation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (mutation.productId <= 0) errors += "Invalid product."
        if (mutation.variantId <= 0) errors += "Invalid product variant."

        when (mutation.operation) {
            InventoryOperation.STOCK_IN,
            InventoryOperation.SALE,
            InventoryOperation.DAMAGE,
            InventoryOperation.EXPIRED,
            InventoryOperation.CUSTOMER_RETURN,
            InventoryOperation.SUPPLIER_RETURN,
            InventoryOperation.ADJUSTMENT_IN,
            InventoryOperation.ADJUSTMENT_OUT ->
                if (mutation.quantity == null || mutation.quantity <= 0) {
                    errors += "Quantity must be greater than zero."
                }

            InventoryOperation.SET_STOCK ->
                if (mutation.targetQuantity == null || mutation.targetQuantity < 0) {
                    errors += "Target stock must be zero or greater."
                }

            InventoryOperation.SET_REORDER_LEVEL ->
                if (mutation.reorderLevel == null || mutation.reorderLevel < 0) {
                    errors += "Reorder level must be zero or greater."
                }

            else -> Unit
        }

        if (mutation.confidence.score < 0.65) {
            errors += "Product/variant confidence is too low."
        } else if (mutation.confidence.score < 0.85) {
            warnings += "This item requires explicit confirmation."
        }

        return Validation(errors.isEmpty(), errors, warnings)
    }
}
