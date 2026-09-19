package com.example.gigpoint

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "gigpoint_inventory.db"
        private const val DATABASE_VERSION = 1

        const val SYNC_PENDING = "PENDING"
        const val SYNC_SYNCED = "SYNCED"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                unit TEXT NOT NULL,
                quantity REAL NOT NULL DEFAULT 0,
                minimum_stock REAL NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                sync_status TEXT NOT NULL DEFAULT 'PENDING'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE stock_transactions (
                id TEXT PRIMARY KEY,
                product_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                quantity REAL NOT NULL,
                unit TEXT NOT NULL,
                source TEXT NOT NULL,
                transcript TEXT,
                created_at INTEGER NOT NULL,
                sync_status TEXT NOT NULL DEFAULT 'PENDING',
                FOREIGN KEY(product_id) REFERENCES products(id)
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX idx_transactions_created_at ON stock_transactions(created_at DESC)"
        )
        db.execSQL(
            "CREATE INDEX idx_transactions_sync_status ON stock_transactions(sync_status)"
        )

        seedDemoData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Prototype v1. Future versions should use migrations instead of destructive changes.
    }

    private fun seedDemoData(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val demoProducts = listOf(
            arrayOf("Rice", "bag", 20.0, 5.0),
            arrayOf("Sugar", "kg", 12.0, 10.0),
            arrayOf("Sunflower Oil", "carton", 4.0, 5.0),
            arrayOf("Coca-Cola", "carton", 8.0, 4.0),
            arrayOf("Parle-G", "box", 0.0, 3.0)
        )

        demoProducts.forEach { row ->
            val values = ContentValues().apply {
                put("name", row[0] as String)
                put("unit", row[1] as String)
                put("quantity", row[2] as Double)
                put("minimum_stock", row[3] as Double)
                put("updated_at", now)
                put("sync_status", SYNC_SYNCED)
            }
            db.insert("products", null, values)
        }
    }

    fun addProduct(
        name: String,
        unit: String,
        openingQuantity: Double,
        minimumStock: Double
    ): Long {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("unit", unit.trim().lowercase())
            put("quantity", openingQuantity)
            put("minimum_stock", minimumStock)
            put("updated_at", System.currentTimeMillis())
            put("sync_status", SYNC_PENDING)
        }
        return writableDatabase.insert("products", null, values)
    }

    fun getProducts(): List<Product> {
        val result = mutableListOf<Product>()
        readableDatabase.rawQuery(
            """
            SELECT id, name, unit, quantity, minimum_stock, sync_status
            FROM products
            ORDER BY name COLLATE NOCASE
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Product(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    unit = cursor.getString(2),
                    quantity = cursor.getDouble(3),
                    minimumStock = cursor.getDouble(4),
                    syncStatus = cursor.getString(5)
                )
            }
        }
        return result
    }

    fun getProductById(id: Long): Product? {
        readableDatabase.rawQuery(
            """
            SELECT id, name, unit, quantity, minimum_stock, sync_status
            FROM products WHERE id = ?
            """.trimIndent(),
            arrayOf(id.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                Product(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    unit = cursor.getString(2),
                    quantity = cursor.getDouble(3),
                    minimumStock = cursor.getDouble(4),
                    syncStatus = cursor.getString(5)
                )
            } else null
        }
    }

    fun adjustStock(
        productId: Long,
        type: String,
        quantity: Double,
        source: String,
        transcript: String? = null
    ): Pair<Boolean, String> {
        if (quantity <= 0) return false to "Quantity must be greater than zero."

        val db = writableDatabase
        db.beginTransaction()
        try {
            val product = getProductById(productId)
                ?: return false to "Product not found."

            val change = if (type == "STOCK_OUT") -quantity else quantity
            val newQuantity = product.quantity + change

            if (newQuantity < 0) {
                return false to "Not enough ${product.name} in stock."
            }

            val productValues = ContentValues().apply {
                put("quantity", newQuantity)
                put("updated_at", System.currentTimeMillis())
                put("sync_status", SYNC_PENDING)
            }
            db.update(
                "products",
                productValues,
                "id = ?",
                arrayOf(productId.toString())
            )

            val txValues = ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("product_id", productId)
                put("type", type)
                put("quantity", quantity)
                put("unit", product.unit)
                put("source", source)
                put("transcript", transcript)
                put("created_at", System.currentTimeMillis())
                put("sync_status", SYNC_PENDING)
            }
            db.insertOrThrow("stock_transactions", null, txValues)

            db.setTransactionSuccessful()
            return true to "${product.name} updated to ${formatQuantity(newQuantity)} ${product.unit}."
        } finally {
            db.endTransaction()
        }
    }

    fun getLowStockProducts(): List<Product> =
        getProducts().filter { it.quantity <= it.minimumStock }

    fun getTransactions(limit: Int = 30): List<StockTransaction> {
        val result = mutableListOf<StockTransaction>()
        readableDatabase.rawQuery(
            """
            SELECT
                t.id,
                t.product_id,
                p.name,
                t.type,
                t.quantity,
                t.unit,
                t.source,
                t.transcript,
                t.created_at,
                t.sync_status
            FROM stock_transactions t
            JOIN products p ON p.id = t.product_id
            ORDER BY t.created_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += StockTransaction(
                    id = cursor.getString(0),
                    productId = cursor.getLong(1),
                    productName = cursor.getString(2),
                    type = cursor.getString(3),
                    quantity = cursor.getDouble(4),
                    unit = cursor.getString(5),
                    source = cursor.getString(6),
                    transcript = if (cursor.isNull(7)) null else cursor.getString(7),
                    createdAt = cursor.getLong(8),
                    syncStatus = cursor.getString(9)
                )
            }
        }
        return result
    }

    fun getStats(): DashboardStats {
        val products = getProducts()
        val pendingTransactions = count(
            "SELECT COUNT(*) FROM stock_transactions WHERE sync_status = ?",
            arrayOf(SYNC_PENDING)
        )
        val pendingProducts = count(
            "SELECT COUNT(*) FROM products WHERE sync_status = ?",
            arrayOf(SYNC_PENDING)
        )

        return DashboardStats(
            totalProducts = products.size,
            lowStock = products.count { it.quantity <= it.minimumStock },
            outOfStock = products.count { it.quantity <= 0.0 },
            pendingSync = pendingTransactions + pendingProducts
        )
    }

    fun markEverythingSynced(): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val p = ContentValues().apply { put("sync_status", SYNC_SYNCED) }
            val t = ContentValues().apply { put("sync_status", SYNC_SYNCED) }

            val productsUpdated = db.update(
                "products",
                p,
                "sync_status = ?",
                arrayOf(SYNC_PENDING)
            )
            val txUpdated = db.update(
                "stock_transactions",
                t,
                "sync_status = ?",
                arrayOf(SYNC_PENDING)
            )

            db.setTransactionSuccessful()
            return productsUpdated + txUpdated
        } finally {
            db.endTransaction()
        }
    }

    private fun count(sql: String, args: Array<String>): Int {
        readableDatabase.rawQuery(sql, args).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun formatQuantity(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString()
        else "%.2f".format(value)
    }
}
