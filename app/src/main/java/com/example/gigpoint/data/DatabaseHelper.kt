package com.example.gigpoint.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.gigpoint.DashboardStats
import com.example.gigpoint.MerchantProfile
import com.example.gigpoint.ShopProfile
import com.example.gigpoint.domain.Product
import com.example.gigpoint.domain.StockTransaction

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION
    ) {

    companion object {
        private const val DATABASE_NAME = "gigpoint_inventory.db"
        private const val DATABASE_VERSION = DatabaseMigrations.PRODUCTION_CORE_VERSION

        const val SYNC_PENDING = "PENDING"
        const val SYNC_SYNCED = "SYNCED"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createInventoryTables(db)
        createMerchantTables(db)
        seedDemoData(db)
        DatabaseMigrations.migrateToProductionCore(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            createMerchantTables(db)
        }

        if (oldVersion < DatabaseMigrations.PRODUCTION_CORE_VERSION) {
            DatabaseMigrations.migrateToProductionCore(db)
        }
    }

    private fun createInventoryTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS products (
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
            CREATE TABLE IF NOT EXISTS stock_transactions (
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
            """
            CREATE INDEX IF NOT EXISTS idx_transactions_created_at
            ON stock_transactions(created_at DESC)
            """.trimIndent()
        )
    }

    private fun createMerchantTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS merchant_profiles (
                user_id TEXT PRIMARY KEY,
                owner_name TEXT NOT NULL,
                phone TEXT,
                preferred_language TEXT NOT NULL DEFAULT 'en',
                theme TEXT NOT NULL DEFAULT 'system',
                updated_at INTEGER NOT NULL,
                sync_status TEXT NOT NULL DEFAULT 'PENDING'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shops (
                id TEXT PRIMARY KEY,
                owner_id TEXT NOT NULL UNIQUE,
                shop_name TEXT NOT NULL,
                gstin TEXT,
                business_type TEXT NOT NULL,
                city TEXT,
                area TEXT,
                updated_at INTEGER NOT NULL,
                sync_status TEXT NOT NULL DEFAULT 'PENDING'
            )
            """.trimIndent()
        )
    }

    private fun seedDemoData(db: SQLiteDatabase) {
        val existing =
            db.rawQuery(
                "SELECT COUNT(*) FROM products",
                null
            ).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }

        if (existing > 0) return

        val now = System.currentTimeMillis()

        listOf(
            arrayOf("Rice", "bag", 20.0, 5.0),
            arrayOf("Sugar", "kg", 12.0, 10.0),
            arrayOf("Sunflower Oil", "carton", 4.0, 5.0),
            arrayOf("Coca-Cola", "carton", 8.0, 4.0),
            arrayOf("Parle-G", "box", 0.0, 3.0)
        ).forEach { row ->
            db.insert(
                "products",
                null,
                ContentValues().apply {
                    put("name", row[0] as String)
                    put("unit", row[1] as String)
                    put("quantity", row[2] as Double)
                    put("minimum_stock", row[3] as Double)
                    put("updated_at", now)
                    put("sync_status", SYNC_SYNCED)
                }
            )
        }
    }

    fun saveMerchantProfile(profile: MerchantProfile) {
        writableDatabase.insertWithOnConflict(
            "merchant_profiles",
            null,
            ContentValues().apply {
                put("user_id", profile.userId)
                put("owner_name", profile.ownerName)
                put("phone", profile.phone)
                put("preferred_language", profile.preferredLanguage)
                put("theme", profile.theme)
                put("updated_at", System.currentTimeMillis())
                put("sync_status", profile.syncStatus)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getMerchantProfile(userId: String): MerchantProfile? =
        readableDatabase.rawQuery(
            """
            SELECT user_id, owner_name, phone,
                   preferred_language, theme, sync_status
            FROM merchant_profiles
            WHERE user_id = ?
            """.trimIndent(),
            arrayOf(userId)
        ).use { c ->
            if (!c.moveToFirst()) null
            else MerchantProfile(
                userId = c.getString(0),
                ownerName = c.getString(1),
                phone = if (c.isNull(2)) null else c.getString(2),
                preferredLanguage = c.getString(3),
                theme = c.getString(4),
                syncStatus = c.getString(5)
            )
        }

    fun saveShop(shop: ShopProfile) {
        writableDatabase.insertWithOnConflict(
            "shops",
            null,
            ContentValues().apply {
                put("id", shop.id)
                put("owner_id", shop.ownerId)
                put("shop_name", shop.shopName)
                put("gstin", shop.gstin)
                put("business_type", shop.businessType)
                put("city", shop.city)
                put("area", shop.area)
                put("updated_at", System.currentTimeMillis())
                put("sync_status", shop.syncStatus)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getShopForOwner(userId: String): ShopProfile? =
        readableDatabase.rawQuery(
            """
            SELECT id, owner_id, shop_name, gstin,
                   business_type, city, area, sync_status
            FROM shops
            WHERE owner_id = ?
            """.trimIndent(),
            arrayOf(userId)
        ).use { c ->
            if (!c.moveToFirst()) null
            else ShopProfile(
                id = c.getString(0),
                ownerId = c.getString(1),
                shopName = c.getString(2),
                gstin = if (c.isNull(3)) null else c.getString(3),
                businessType = c.getString(4),
                city = if (c.isNull(5)) null else c.getString(5),
                area = if (c.isNull(6)) null else c.getString(6),
                syncStatus = c.getString(7)
            )
        }

    fun isMerchantSetupComplete(userId: String): Boolean {
        val profile = getMerchantProfile(userId) ?: return false
        val shop = getShopForOwner(userId) ?: return false

        return profile.ownerName.isNotBlank() &&
            shop.shopName.isNotBlank() &&
            shop.businessType.isNotBlank()
    }

    fun markMerchantSetupSynced(userId: String) {
        val values = ContentValues().apply { put("sync_status", SYNC_SYNCED) }

        writableDatabase.update(
            "merchant_profiles",
            values,
            "user_id = ?",
            arrayOf(userId)
        )

        writableDatabase.update(
            "shops",
            values,
            "owner_id = ?",
            arrayOf(userId)
        )
    }

    /**
     * Compatibility API used by the current screens.
     * Each new product gets one default variant.
     */
    fun addProduct(
        name: String,
        unit: String,
        openingQuantity: Double,
        minimumStock: Double
    ): Long {
        require(name.isNotBlank())
        require(unit.isNotBlank())
        require(openingQuantity >= 0)
        require(minimumStock >= 0)

        val db = writableDatabase
        db.beginTransaction()

        try {
            val now = System.currentTimeMillis()

            val productId =
                db.insertOrThrow(
                    "products",
                    null,
                    ContentValues().apply {
                        put("name", name.trim())
                        put("unit", unit.trim().lowercase())
                        put("quantity", openingQuantity)
                        put("minimum_stock", minimumStock)
                        put("updated_at", now)
                        put("sync_status", SYNC_PENDING)
                    }
                )

            val variantId =
                db.insertOrThrow(
                    "product_variants",
                    null,
                    ContentValues().apply {
                        put("product_id", productId)
                        put("sku", "LOCAL-$productId-DEFAULT")
                        put("variant_name", "Default")
                        put("selling_unit", unit.trim().lowercase())
                        put("active", 1)
                        put("created_at", now)
                        put("updated_at", now)
                    }
                )

            db.insertOrThrow(
                "inventory",
                null,
                ContentValues().apply {
                    put("variant_id", variantId)
                    put("quantity", openingQuantity)
                    put("reorder_level", minimumStock)
                    put("updated_at", now)
                    put("sync_status", SYNC_PENDING)
                }
            )

            AliasRepository(this).add(
                productId = productId,
                alias = name.trim(),
                source = "CATALOG"
            )

            db.setTransactionSuccessful()
            return productId
        } finally {
            db.endTransaction()
        }
    }

    fun getProducts(): List<Product> {
        val result = mutableListOf<Product>()

        readableDatabase.rawQuery(
            """
            SELECT id, name, unit, quantity,
                   minimum_stock, sync_status
            FROM products
            ORDER BY name COLLATE NOCASE
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                result += Product(
                    id = c.getLong(0),
                    name = c.getString(1),
                    unit = c.getString(2),
                    quantity = c.getDouble(3),
                    minimumStock = c.getDouble(4),
                    syncStatus = c.getString(5)
                )
            }
        }

        return result
    }

    fun getProductById(id: Long): Product? =
        readableDatabase.rawQuery(
            """
            SELECT id, name, unit, quantity,
                   minimum_stock, sync_status
            FROM products
            WHERE id = ?
            """.trimIndent(),
            arrayOf(id.toString())
        ).use { c ->
            if (!c.moveToFirst()) null
            else Product(
                id = c.getLong(0),
                name = c.getString(1),
                unit = c.getString(2),
                quantity = c.getDouble(3),
                minimumStock = c.getDouble(4),
                syncStatus = c.getString(5)
            )
        }

    fun defaultVariantId(productId: Long): Long? =
        readableDatabase.rawQuery(
            """
            SELECT id
            FROM product_variants
            WHERE product_id = ? AND active = 1
            ORDER BY CASE WHEN variant_name = 'Default' THEN 0 ELSE 1 END, id
            LIMIT 1
            """.trimIndent(),
            arrayOf(productId.toString())
        ).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }

    /**
     * Compatibility stock update.
     * New production voice code should use ActionPlan + InventoryCommandExecutor.
     */
    fun adjustStock(
        productId: Long,
        type: String,
        quantity: Double,
        source: String,
        transcript: String? = null
    ): Pair<Boolean, String> {
        if (quantity <= 0) {
            return false to "Quantity must be greater than zero."
        }

        val product =
            getProductById(productId)
                ?: return false to "Product not found."

        val variantId =
            defaultVariantId(productId)
                ?: return false to "Product variant was not found."

        val inventory = InventoryRepository(this)
        val current =
            inventory.get(variantId)
                ?: return false to "Inventory record was not found."

        val newQuantity =
            current.quantity +
                if (type == "STOCK_OUT") -quantity else quantity

        if (newQuantity < 0) {
            return false to "Not enough ${product.name} in stock."
        }

        val db = writableDatabase
        db.beginTransaction()

        try {
            check(inventory.setQuantity(variantId, newQuantity) == 1)

            db.update(
                "products",
                ContentValues().apply {
                    put("quantity", newQuantity)
                    put("updated_at", System.currentTimeMillis())
                    put("sync_status", SYNC_PENDING)
                },
                "id = ?",
                arrayOf(productId.toString())
            )

            TransactionRepository(this).insert(
                productId = productId,
                variantId = variantId,
                commandId = null,
                type = type,
                quantity = quantity,
                unit = product.unit,
                source = source,
                transcript = transcript
            )

            db.setTransactionSuccessful()

            return true to
                "${product.name} updated to ${formatQuantity(newQuantity)} ${product.unit}."
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
            SELECT t.id, t.product_id, p.name, t.type, t.quantity, t.unit,
                   t.source, t.transcript, t.created_at, t.sync_status,
                   t.variant_id, t.command_id,
                   t.reverses_transaction_id, t.reversed_by_transaction_id
            FROM stock_transactions t
            JOIN products p ON p.id = t.product_id
            ORDER BY t.created_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result += StockTransaction(
                    id = c.getString(0),
                    productId = c.getLong(1),
                    productName = c.getString(2),
                    type = c.getString(3),
                    quantity = c.getDouble(4),
                    unit = c.getString(5),
                    source = c.getString(6),
                    transcript = if (c.isNull(7)) null else c.getString(7),
                    createdAt = c.getLong(8),
                    syncStatus = c.getString(9),
                    variantId = if (c.isNull(10)) null else c.getLong(10),
                    commandId = if (c.isNull(11)) null else c.getString(11),
                    reversesTransactionId =
                        if (c.isNull(12)) null else c.getString(12),
                    reversedByTransactionId =
                        if (c.isNull(13)) null else c.getString(13)
                )
            }
        }

        return result
    }

    fun getStats(): DashboardStats {
        val products = getProducts()

        val pendingTransactions =
            count(
                "SELECT COUNT(*) FROM stock_transactions WHERE sync_status = ?",
                arrayOf(SYNC_PENDING)
            )

        val pendingProducts =
            count(
                "SELECT COUNT(*) FROM products WHERE sync_status = ?",
                arrayOf(SYNC_PENDING)
            )

        val pendingInventory =
            count(
                "SELECT COUNT(*) FROM inventory WHERE sync_status = ?",
                arrayOf(SYNC_PENDING)
            )

        return DashboardStats(
            totalProducts = products.size,
            lowStock = products.count { it.quantity <= it.minimumStock },
            outOfStock = products.count { it.quantity <= 0.0 },
            pendingSync =
                pendingTransactions +
                    pendingProducts +
                    pendingInventory
        )
    }

    fun markEverythingSynced(): Int {
        val db = writableDatabase
        val values = ContentValues().apply { put("sync_status", SYNC_SYNCED) }

        db.beginTransaction()
        try {
            var total = 0

            listOf(
                "products",
                "inventory",
                "stock_transactions",
                "voice_commands",
                "audit_events"
            ).forEach { table ->
                total += db.update(
                    table,
                    values,
                    "sync_status = ?",
                    arrayOf(SYNC_PENDING)
                )
            }

            db.setTransactionSuccessful()
            return total
        } finally {
            db.endTransaction()
        }
    }

    private fun count(sql: String, args: Array<String>): Int =
        readableDatabase.rawQuery(sql, args).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    private fun formatQuantity(value: Double): String =
        if (value % 1.0 == 0.0)
            value.toLong().toString()
        else
            "%.2f".format(value)
}
