package com.example.gigpoint.catalog

import com.example.gigpoint.data.AliasRepository
import com.example.gigpoint.data.ProductRepository
import com.example.gigpoint.domain.Confidence
import com.example.gigpoint.domain.Product
import com.example.gigpoint.domain.Resolution
import com.example.gigpoint.domain.ResolutionStatus

class ProductResolver(
    private val products: ProductRepository,
    private val aliases: AliasRepository
) {
    fun resolve(mention: String): Resolution<Product> {
        val normalized = CatalogNormalizer.normalize(mention)
        if (normalized.isBlank()) {
            return Resolution(ResolutionStatus.NOT_FOUND)
        }

        aliases.findExact(normalized)?.let { alias ->
            products.getById(alias.productId)?.let { product ->
                return Resolution(
                    ResolutionStatus.RESOLVED,
                    value = product,
                    confidence = Confidence(
                        alias.confidence.coerceIn(0.0, 1.0),
                        "catalog/merchant alias"
                    )
                )
            }
        }

        val candidates =
            products.getActiveProducts()
                .map { it to CatalogNormalizer.similarity(mention, it.name) }
                .sortedByDescending { it.second }

        val best = candidates.firstOrNull()
            ?: return Resolution(ResolutionStatus.NOT_FOUND)

        val secondScore = candidates.getOrNull(1)?.second ?: 0.0
        val margin = best.second - secondScore

        return when {
            best.second >= 0.90 ->
                Resolution(
                    ResolutionStatus.RESOLVED,
                    best.first,
                    Confidence(best.second, "catalog name match")
                )

            best.second >= 0.62 && margin >= 0.18 ->
                Resolution(
                    ResolutionStatus.RESOLVED,
                    best.first,
                    Confidence(best.second, "token similarity")
                )

            best.second >= 0.45 ->
                Resolution(
                    status = ResolutionStatus.AMBIGUOUS,
                    confidence = Confidence(best.second, "multiple catalog matches"),
                    alternatives = candidates.take(3).map { it.first }
                )

            else ->
                Resolution(
                    ResolutionStatus.NOT_FOUND,
                    confidence = Confidence(best.second, "no reliable match")
                )
        }
    }
}
