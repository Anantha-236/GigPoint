package com.example.gigpoint.data

import android.content.ContentValues
import com.example.gigpoint.domain.ProductAlias

class AliasRepository(
    private val db: DatabaseHelper
) {
    fun add(
        productId: Long,
        alias: String,
        variantId: Long? = null,
        language: String? = null,
        source: String = "MERCHANT",
        confidence: Double = 1.0
    ): Long =
        db.writableDatabase.insert(
            "product_aliases",
            null,
            ContentValues().apply {
                put("product_id", productId)
                put("variant_id", variantId)
                put("alias", alias.trim())
                put("normalized_alias", DatabaseMigrations.normalizeAlias(alias))
                put("language", language)
                put("source", source)
                put("confidence", confidence.coerceIn(0.0, 1.0))
                put("created_at", System.currentTimeMillis())
            }
        )

    fun findExact(normalizedAlias: String): ProductAlias? =
        db.readableDatabase.rawQuery(
            """
            SELECT id, product_id, variant_id, alias,
                   normalized_alias, language, source, confidence
            FROM product_aliases
            WHERE normalized_alias = ?
            ORDER BY confidence DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(normalizedAlias)
        ).use { c ->
            if (!c.moveToFirst()) null
            else ProductAlias(
                id = c.getLong(0),
                productId = c.getLong(1),
                variantId = if (c.isNull(2)) null else c.getLong(2),
                alias = c.getString(3),
                normalizedAlias = c.getString(4),
                language = if (c.isNull(5)) null else c.getString(5),
                source = c.getString(6),
                confidence = c.getDouble(7)
            )
        }
}
