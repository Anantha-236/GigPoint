package com.example.gigpoint.data

import com.example.gigpoint.domain.Product
import com.example.gigpoint.domain.ProductVariant

class ProductRepository(
    private val db: DatabaseHelper
) {
    fun getActiveProducts(): List<Product> =
        db.getProducts().filter { it.active }

    fun getById(id: Long): Product? =
        db.getProductById(id)

    fun getVariantsForProduct(productId: Long): List<ProductVariant> {
        val result = mutableListOf<ProductVariant>()

        db.readableDatabase.rawQuery(
            """
            SELECT id, product_id, sku, variant_name,
                   package_quantity, package_unit,
                   container_type, selling_unit,
                   purchase_price, selling_price,
                   barcode, active, created_at, updated_at
            FROM product_variants
            WHERE product_id = ?
            ORDER BY active DESC, id
            """.trimIndent(),
            arrayOf(productId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result += ProductVariant(
                    id = c.getLong(0),
                    productId = c.getLong(1),
                    sku = c.getString(2),
                    variantName = if (c.isNull(3)) null else c.getString(3),
                    packageQuantity = if (c.isNull(4)) null else c.getDouble(4),
                    packageUnit = if (c.isNull(5)) null else c.getString(5),
                    containerType = if (c.isNull(6)) null else c.getString(6),
                    sellingUnit = c.getString(7),
                    purchasePrice = if (c.isNull(8)) null else c.getDouble(8),
                    sellingPrice = if (c.isNull(9)) null else c.getDouble(9),
                    barcode = if (c.isNull(10)) null else c.getString(10),
                    active = c.getInt(11) == 1,
                    createdAt = c.getLong(12),
                    updatedAt = c.getLong(13)
                )
            }
        }

        return result
    }

    fun getVariantById(variantId: Long): ProductVariant? =
        db.readableDatabase.rawQuery(
            """
            SELECT id, product_id, sku, variant_name,
                   package_quantity, package_unit,
                   container_type, selling_unit,
                   purchase_price, selling_price,
                   barcode, active, created_at, updated_at
            FROM product_variants
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(variantId.toString())
        ).use { c ->
            if (!c.moveToFirst()) null
            else ProductVariant(
                id = c.getLong(0),
                productId = c.getLong(1),
                sku = c.getString(2),
                variantName = if (c.isNull(3)) null else c.getString(3),
                packageQuantity = if (c.isNull(4)) null else c.getDouble(4),
                packageUnit = if (c.isNull(5)) null else c.getString(5),
                containerType = if (c.isNull(6)) null else c.getString(6),
                sellingUnit = c.getString(7),
                purchasePrice = if (c.isNull(8)) null else c.getDouble(8),
                sellingPrice = if (c.isNull(9)) null else c.getDouble(9),
                barcode = if (c.isNull(10)) null else c.getString(10),
                active = c.getInt(11) == 1,
                createdAt = c.getLong(12),
                updatedAt = c.getLong(13)
            )
        }
}
