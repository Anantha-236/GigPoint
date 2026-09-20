package com.example.gigpoint.actions

import com.example.gigpoint.catalog.ProductResolver
import com.example.gigpoint.catalog.VariantResolver
import com.example.gigpoint.data.ProductRepository
import com.example.gigpoint.domain.*

class InventoryActionPlanner(
    private val products: ProductRepository,
    private val productResolver: ProductResolver,
    private val variantResolver: VariantResolver,
    private val validator: InventoryActionValidator = InventoryActionValidator()
) {
    fun plan(command: MerchantCommand): ActionPlan {
        val mutations = mutableListOf<PlannedInventoryMutation>()
        val blocking = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        command.items.sortedBy { it.ordinal }.forEach { item ->
            val productResolution =
                item.resolvedProductId?.let { id ->
                    products.getById(id)?.let { p ->
                        Resolution(
                            ResolutionStatus.RESOLVED,
                            p,
                            Confidence(maxOf(item.confidence.score, 0.95), "pre-resolved product")
                        )
                    }
                } ?: productResolver.resolve(item.productMention)

            if (!productResolution.resolved) {
                blocking +=
                    if (productResolution.status == ResolutionStatus.AMBIGUOUS)
                        "Item ${item.ordinal}: product '${item.productMention}' is ambiguous."
                    else
                        "Item ${item.ordinal}: product '${item.productMention}' was not found."
                return@forEach
            }

            val product = productResolution.value!!

            val variantResolution =
                item.resolvedVariantId?.let { id ->
                    products.getVariantById(id)?.let { v ->
                        Resolution(
                            ResolutionStatus.RESOLVED,
                            v,
                            Confidence(maxOf(item.confidence.score, 0.95), "pre-resolved variant")
                        )
                    }
                } ?: variantResolver.resolve(
                    product.id,
                    VariantResolver.VariantHints(
                        packageQuantity = item.packageQuantity,
                        packageUnit = item.packageUnit,
                        packaging = item.packaging,
                        price = item.price,
                        freeText = item.productMention
                    )
                )

            if (!variantResolution.resolved) {
                blocking += "Item ${item.ordinal}: variant for '${item.productMention}' needs clarification."
                return@forEach
            }

            val variant = variantResolution.value!!
            val combined =
                minOf(
                    productResolution.confidence.score,
                    variantResolution.confidence.score,
                    if (item.confidence.score > 0.0) item.confidence.score else 1.0
                )

            val mutation =
                PlannedInventoryMutation(
                    ordinal = item.ordinal,
                    productId = product.id,
                    variantId = variant.id,
                    productName = product.name,
                    variantLabel =
                        listOfNotNull(
                            product.name,
                            variant.variantName,
                            variant.packageQuantity?.let { q ->
                                variant.packageUnit?.let { u -> "$q $u" }
                            },
                            variant.containerType
                        ).joinToString(" "),
                    operation = item.operation,
                    quantity = item.quantity,
                    unit = item.quantityUnit ?: variant.sellingUnit,
                    targetQuantity =
                        if (item.operation == InventoryOperation.SET_STOCK)
                            item.quantity else null,
                    reorderLevel =
                        if (item.operation == InventoryOperation.SET_REORDER_LEVEL)
                            item.quantity else null,
                    confidence = Confidence(combined, "combined resolution confidence")
                )

            val validation = validator.validate(mutation)
            if (!validation.valid) {
                blocking += validation.errors.map { "Item ${item.ordinal}: $it" }
            } else {
                warnings += validation.warnings
                mutations += mutation
            }
        }

        return ActionPlan(
            command = command,
            mutations = mutations,
            requiresConfirmation =
                mutations.size > 1 ||
                    mutations.any { it.operation.destructive || it.confidence.score < 0.90 },
            blockingReasons = blocking,
            warnings = warnings
        )
    }
}
