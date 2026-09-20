package com.example.gigpoint.catalog

import com.example.gigpoint.data.ProductRepository
import com.example.gigpoint.domain.Confidence
import com.example.gigpoint.domain.ProductVariant
import com.example.gigpoint.domain.Resolution
import com.example.gigpoint.domain.ResolutionStatus
import kotlin.math.abs

class VariantResolver(
    private val products: ProductRepository
) {
    data class VariantHints(
        val packageQuantity: Double? = null,
        val packageUnit: String? = null,
        val packaging: String? = null,
        val price: Double? = null,
        val freeText: String? = null
    )

    fun resolve(
        productId: Long,
        hints: VariantHints
    ): Resolution<ProductVariant> {
        val variants =
            products.getVariantsForProduct(productId)
                .filter { it.active }

        if (variants.isEmpty()) {
            return Resolution(ResolutionStatus.NOT_FOUND)
        }

        if (variants.size == 1) {
            return Resolution(
                ResolutionStatus.RESOLVED,
                variants.first(),
                Confidence(0.99, "only active variant")
            )
        }

        val scored =
            variants.map { variant ->
                var score = 0.0
                var signals = 0

                hints.packageQuantity?.let { expected ->
                    signals++
                    if (
                        variant.packageQuantity != null &&
                        abs(variant.packageQuantity - expected) <= 0.001
                    ) score += 1.0
                }

                hints.packageUnit?.let { expected ->
                    signals++
                    if (variant.packageUnit.equals(expected, true)) score += 1.0
                }

                hints.packaging?.let { expected ->
                    signals++
                    if (variant.containerType?.contains(expected, true) == true) {
                        score += 1.0
                    }
                }

                hints.price?.let { expected ->
                    signals++
                    if (
                        variant.sellingPrice != null &&
                        abs(variant.sellingPrice - expected) <= 0.01
                    ) score += 1.0
                }

                hints.freeText?.takeIf(String::isNotBlank)?.let { free ->
                    signals++
                    score += CatalogNormalizer.similarity(
                        free,
                        listOfNotNull(
                            variant.variantName,
                            variant.packageQuantity?.toString(),
                            variant.packageUnit,
                            variant.containerType,
                            variant.sellingPrice?.let { "₹$it" }
                        ).joinToString(" ")
                    )
                }

                variant to if (signals == 0) 0.0 else score / signals
            }.sortedByDescending { it.second }

        val best = scored.first()
        val second = scored.getOrNull(1)?.second ?: 0.0
        val margin = best.second - second

        return when {
            best.second >= 0.85 ->
                Resolution(
                    ResolutionStatus.RESOLVED,
                    best.first,
                    Confidence(best.second, "variant attributes match")
                )

            best.second >= 0.60 && margin >= 0.20 ->
                Resolution(
                    ResolutionStatus.RESOLVED,
                    best.first,
                    Confidence(best.second, "best variant match")
                )

            else ->
                Resolution(
                    status = ResolutionStatus.AMBIGUOUS,
                    confidence = Confidence(best.second, "variant requires clarification"),
                    alternatives = scored.take(4).map { it.first }
                )
        }
    }
}
