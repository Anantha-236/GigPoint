package com.example.gigpoint.voice

import com.example.gigpoint.DatabaseHelper
import com.example.gigpoint.Product

/**
 * Stateful slot-filling conversation around Whisper.
 *
 * A follow-up answer such as "five bags" is merged into the previous
 * incomplete command instead of being parsed as a brand-new request.
 */
class VoiceConversationManager(
    private val db: DatabaseHelper
) {

    private val understanding =
        VoiceUnderstandingEngine()

    private var waiting: VoiceTurn.Ask? = null

    fun reset() {
        waiting = null
    }

    fun handleTranscript(text: String): VoiceTurn {
        val products = db.getProducts()

        val pending = waiting

        if (pending != null) {
            return handleFollowUp(
                text,
                pending,
                products
            )
        }

        val draft =
            understanding.understand(
                text,
                products
            )

        return continueDraft(draft)
    }

    fun choose(choice: String): VoiceTurn {
        val pending = waiting
            ?: return VoiceTurn.Error(
                "There is no pending question.",
                keepContext = false
            )

        return handleFollowUp(
            choice,
            pending,
            db.getProducts()
        )
    }

    private fun handleFollowUp(
        text: String,
        ask: VoiceTurn.Ask,
        products: List<Product>
    ): VoiceTurn {

        val draft = ask.draft.copy(
            originalUtterances =
                ask.draft.originalUtterances + text
        )

        val merged =
            when (ask.slot) {
                MissingSlot.PRODUCT -> {
                    val product =
                        understanding.resolveProductOnly(
                            text,
                            products
                        )

                    if (product == null) {
                        return remember(
                            ask.copy(
                                question =
                                    "I still could not identify the product. Please say the product name again."
                            )
                        )
                    }

                    draft.copy(product = product)
                }

                MissingSlot.QUANTITY -> {
                    val (quantity, unit) =
                        understanding
                            .extractQuantityAndUnit(text)

                    if (
                        quantity == null ||
                        quantity <= 0
                    ) {
                        return remember(
                            ask.copy(
                                question =
                                    "I still need the quantity. For example, say five bags or two kilograms."
                            )
                        )
                    }

                    draft.copy(
                        quantity = quantity,
                        unit = unit ?: draft.unit
                    )
                }

                MissingSlot.ACTION -> {
                    val parsed =
                        understanding.understand(
                            text,
                            products
                        )

                    if (
                        parsed.action == null ||
                        parsed.action ==
                        InventoryAction.UNKNOWN
                    ) {
                        return remember(
                            ask.copy(
                                question =
                                    "What should I do with ${draft.product?.name ?: "that product"}? Say add, sell, check stock, set stock, or delete."
                            )
                        )
                    }

                    draft.copy(
                        action = parsed.action
                    )
                }

                MissingSlot.OUTBOUND_REASON -> {
                    val action =
                        understanding
                            .detectOutboundReason(text)

                    if (action == null) {
                        return remember(
                            ask.copy(
                                question =
                                    "I need the reason. Was it sold, damaged, expired, returned to supplier, or a stock correction?"
                            )
                        )
                    }

                    draft.copy(action = action)
                }
            }

        waiting = null
        return continueDraft(merged)
    }

    private fun continueDraft(
        draft: CommandDraft
    ): VoiceTurn {

        val action = draft.action

        if (
            action == null ||
            action == InventoryAction.UNKNOWN
        ) {
            return remember(
                VoiceTurn.Ask(
                    question =
                        if (draft.product != null)
                            "What should I do with ${draft.product.name}?"
                        else
                            "What inventory action do you want to perform?",
                    slot = MissingSlot.ACTION,
                    draft = draft,
                    choices = listOf(
                        "Add stock",
                        "Sell",
                        "Check stock",
                        "Set stock"
                    )
                )
            )
        }

        if (
            action == InventoryAction.LOW_STOCK
        ) {
            val low =
                db.getLowStockProducts()

            return VoiceTurn.Answer(
                if (low.isEmpty()) {
                    "No products are currently at or below their minimum stock level."
                } else {
                    "Low stock: " +
                        low.joinToString("; ") {
                            "${it.name}, ${fmt(it.quantity)} ${it.unit}"
                        }
                }
            )
        }

        if (draft.product == null) {
            return remember(
                VoiceTurn.Ask(
                    question =
                        "Which product?",
                    slot = MissingSlot.PRODUCT,
                    draft = draft
                )
            )
        }

        if (
            action ==
            InventoryAction.CHECK_STOCK
        ) {
            return VoiceTurn.Answer(
                "${draft.product.name} has ${fmt(draft.product.quantity)} ${draft.product.unit} available."
            )
        }

        if (
            action ==
            InventoryAction.ARCHIVE_PRODUCT
        ) {
            return VoiceTurn.Confirm(
                prompt =
                    "Archive ${draft.product.name}? This is a destructive inventory action and requires confirmation.",
                draft = draft
            )
        }

        if (
            action ==
            InventoryAction.OUTBOUND_UNSPECIFIED
        ) {
            return remember(
                VoiceTurn.Ask(
                    question =
                        "What happened to ${draft.product.name}? Was it sold, damaged, expired, returned to supplier, or a stock correction?",
                    slot =
                        MissingSlot.OUTBOUND_REASON,
                    draft = draft,
                    choices = listOf(
                        "Sold",
                        "Damaged",
                        "Expired",
                        "Supplier return",
                        "Correction"
                    )
                )
            )
        }

        val requiresQuantity =
            action in setOf(
                InventoryAction.STOCK_IN,
                InventoryAction.SALE,
                InventoryAction.DAMAGE,
                InventoryAction.EXPIRED,
                InventoryAction.CUSTOMER_RETURN,
                InventoryAction.SUPPLIER_RETURN,
                InventoryAction.ADJUSTMENT_IN,
                InventoryAction.ADJUSTMENT_OUT,
                InventoryAction.SET_STOCK,
                InventoryAction.SET_REORDER_LEVEL
            )

        if (
            requiresQuantity &&
            (
                draft.quantity == null ||
                draft.quantity <= 0
            )
        ) {
            return remember(
                VoiceTurn.Ask(
                    question =
                        when (action) {
                            InventoryAction.SET_STOCK ->
                                "What should the new stock quantity of ${draft.product.name} be?"

                            InventoryAction.SET_REORDER_LEVEL ->
                                "What should the low-stock level for ${draft.product.name} be?"

                            else ->
                                "How much ${draft.product.name}?"
                        },
                    slot =
                        MissingSlot.QUANTITY,
                    draft = draft
                )
            )
        }

        return VoiceTurn.Confirm(
            prompt = buildConfirmation(draft),
            draft = draft
        )
    }

    private fun remember(
        ask: VoiceTurn.Ask
    ): VoiceTurn.Ask {
        waiting = ask
        return ask
    }

    private fun buildConfirmation(
        draft: CommandDraft
    ): String {
        val p = draft.product!!
        val q = draft.quantity
        val unit =
            draft.unit ?: p.unit

        return when (draft.action) {
            InventoryAction.STOCK_IN ->
                "Add ${fmt(q!!)} $unit of ${p.name}? Current stock is ${fmt(p.quantity)} ${p.unit}."

            InventoryAction.SALE ->
                "Record a sale of ${fmt(q!!)} $unit of ${p.name}? Current stock is ${fmt(p.quantity)} ${p.unit}."

            InventoryAction.DAMAGE ->
                "Record ${fmt(q!!)} $unit of ${p.name} as damaged?"

            InventoryAction.EXPIRED ->
                "Record ${fmt(q!!)} $unit of ${p.name} as expired?"

            InventoryAction.CUSTOMER_RETURN ->
                "Add customer return of ${fmt(q!!)} $unit of ${p.name}?"

            InventoryAction.SUPPLIER_RETURN ->
                "Return ${fmt(q!!)} $unit of ${p.name} to the supplier?"

            InventoryAction.ADJUSTMENT_IN ->
                "Increase ${p.name} by ${fmt(q!!)} $unit as a stock correction?"

            InventoryAction.ADJUSTMENT_OUT ->
                "Reduce ${p.name} by ${fmt(q!!)} $unit as a stock correction?"

            InventoryAction.SET_STOCK ->
                "Set ${p.name} stock to ${fmt(q!!)} $unit? This will create an adjustment instead of deleting history."

            InventoryAction.SET_REORDER_LEVEL ->
                "Set ${p.name} low-stock level to ${fmt(q!!)} $unit?"

            else ->
                "Confirm this inventory action?"
        }
    }

    private fun fmt(value: Double): String =
        if (value % 1.0 == 0.0)
            value.toLong().toString()
        else
            "%.2f".format(value)
}
