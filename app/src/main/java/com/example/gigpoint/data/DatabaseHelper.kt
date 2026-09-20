package com.example.gigpoint.data

import com.example.gigpoint.DashboardStats
import com.example.gigpoint.MerchantProfile
import com.example.gigpoint.ShopProfile
import com.example.gigpoint.domain.Product
import com.example.gigpoint.domain.StockTransaction

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "gigpoint_inventory.db"
        private const val DATABASE_VERSION = 2

        const val SYNC_PENDING = "PENDING"
        const val SYNC_SYNCED = "SYNCED"
    }

    override fun onCreate(db: SQLiteDatabase) {
        createInventoryTables(db)
        createMerchantTables(db)
        seedDemoData(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            createMerchantTables(db)
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
            "CREATE INDEX IF NOT EXISTS idx_transactions_created_at " +
                "ON stock_transactions(created_at DESC)"
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_transactions_sync_status " +
                "ON stock_transactions(sync_status)"
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
                if (it.moveToFirst())
                    it.getInt(0)
                else
                    0
            }

        if (existing > 0) return

        val now =
            System.currentTimeMillis()

        val demoProducts =
            listOf(
                arrayOf(
                    "Rice",
                    "bag",
                    20.0,
                    5.0
                ),
                arrayOf(
                    "Sugar",
                    "kg",
                    12.0,
                    10.0
                ),
                arrayOf(
                    "Sunflower Oil",
                    "carton",
                    4.0,
                    5.0
                ),
                arrayOf(
                    "Coca-Cola",
                    "carton",
                    8.0,
                    4.0
                ),
                arrayOf(
                    "Parle-G",
                    "box",
                    0.0,
                    3.0
                )
            )

        demoProducts.forEach { row ->
            val values =
                ContentValues().apply {
                    put(
                        "name",
                        row[0] as String
                    )
                    put(
                        "unit",
                        row[1] as String
                    )
                    put(
                        "quantity",
                        row[2] as Double
                    )
                    put(
                        "minimum_stock",
                        row[3] as Double
                    )
                    put(
                        "updated_at",
                        now
                    )
                    put(
                        "sync_status",
                        SYNC_SYNCED
                    )
                }

            db.insert(
                "products",
                null,
                values
            )
        }
    }

    fun saveMerchantProfile(
        profile: MerchantProfile
    ) {
        val values =
            ContentValues().apply {
                put(
                    "user_id",
                    profile.userId
                )
                put(
                    "owner_name",
                    profile.ownerName
                )
                put(
                    "phone",
                    profile.phone
                )
                put(
                    "preferred_language",
                    profile.preferredLanguage
                )
                put(
                    "theme",
                    profile.theme
                )
                put(
                    "updated_at",
                    System.currentTimeMillis()
                )
                put(
                    "sync_status",
                    profile.syncStatus
                )
            }

        writableDatabase
            .insertWithOnConflict(
                "merchant_profiles",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
    }

    fun getMerchantProfile(
        userId: String
    ): MerchantProfile? {
        readableDatabase.rawQuery(
            """
            SELECT user_id, owner_name, phone,
                   preferred_language, theme,
                   sync_status
            FROM merchant_profiles
            WHERE user_id = ?
            """.trimIndent(),
            arrayOf(userId)
        ).use { cursor ->
            if (!cursor.moveToFirst())
                return null

            return MerchantProfile(
                userId = cursor.getString(0),
                ownerName = cursor.getString(1),
                phone =
                    if (cursor.isNull(2))
                        null
                    else
                        cursor.getString(2),
                preferredLanguage =
                    cursor.getString(3),
                theme =
                    cursor.getString(4),
                syncStatus =
                    cursor.getString(5)
            )
        }
    }

    fun saveShop(
        shop: ShopProfile
    ) {
        val values =
            ContentValues().apply {
                put("id", shop.id)
                put(
                    "owner_id",
                    shop.ownerId
                )
                put(
                    "shop_name",
                    shop.shopName
                )
                put(
                    "gstin",
                    shop.gstin
                )
                put(
                    "business_type",
                    shop.businessType
                )
                put(
                    "city",
                    shop.city
                )
                put(
                    "area",
                    shop.area
                )
                put(
                    "updated_at",
                    System.currentTimeMillis()
                )
                put(
                    "sync_status",
                    shop.syncStatus
                )
            }

        writableDatabase
            .insertWithOnConflict(
                "shops",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
    }

    fun getShopForOwner(
        userId: String
    ): ShopProfile? {
        readableDatabase.rawQuery(
            """
            SELECT id, owner_id, shop_name, gstin,
                   business_type, city, area,
                   sync_status
            FROM shops
            WHERE owner_id = ?
            """.trimIndent(),
            arrayOf(userId)
        ).use { cursor ->
            if (!cursor.moveToFirst())
                return null

            return ShopProfile(
                id = cursor.getString(0),
                ownerId = cursor.getString(1),
                shopName = cursor.getString(2),
                gstin =
                    if (cursor.isNull(3))
                        null
                    else
                        cursor.getString(3),
                businessType =
                    cursor.getString(4),
                city =
                    if (cursor.isNull(5))
                        null
                    else
                        cursor.getString(5),
                area =
                    if (cursor.isNull(6))
                        null
                    else
                        cursor.getString(6),
                syncStatus =
                    cursor.getString(7)
            )
        }
    }

    fun isMerchantSetupComplete(
        userId: String
    ): Boolean {
        val profile =
            getMerchantProfile(userId)
                ?: return false

        val shop =
            getShopForOwner(userId)
                ?: return false

        return profile.ownerName.isNotBlank() &&
            shop.shopName.isNotBlank() &&
            shop.businessType.isNotBlank()
    }

    fun markMerchantSetupSynced(
        userId: String
    ) {
        val values =
            ContentValues().apply {
                put(
                    "sync_status",
                    SYNC_SYNCED
                )
            }

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

    // ---------------- Existing inventory API ----------------

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

        return writableDatabase.insert(
            "products",
            null,
            values
        )
    }

    fun getProducts(): List<Product> {
        val result =
            mutableListOf<Product>()

        readableDatabase.rawQuery(
            """
            SELECT id, name, unit, quantity,
                   minimum_stock, sync_status
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
                    quantity =
                        cursor.getDouble(3),
                    minimumStock =
                        cursor.getDouble(4),
                    syncStatus =
                        cursor.getString(5)
                )
            }
        }

        return result
    }

    fun getProductById(
        id: Long
    ): Product? {
        readableDatabase.rawQuery(
            """
            SELECT id, name, unit, quantity,
                   minimum_stock, sync_status
            FROM products
            WHERE id = ?
            """.trimIndent(),
            arrayOf(id.toString())
        ).use { cursor ->
            return if (
                cursor.moveToFirst()
            ) {
                Product(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    unit = cursor.getString(2),
                    quantity =
                        cursor.getDouble(3),
                    minimumStock =
                        cursor.getDouble(4),
                    syncStatus =
                        cursor.getString(5)
                )
            } else {
                null
            }
        }
    }

    fun adjustStock(
        productId: Long,
        type: String,
        quantity: Double,
        source: String,
        transcript: String? = null
    ): Pair<Boolean, String> {
        if (quantity <= 0) {
            return false to
                "Quantity must be greater than zero."
        }

        val db =
            writableDatabase

        db.beginTransaction()

        try {
            val product =
                getProductById(productId)
                    ?: return false to
                        "Product not found."

            val change =
                if (type == "STOCK_OUT")
                    -quantity
                else
                    quantity

            val newQuantity =
                product.quantity + change

            if (newQuantity < 0) {
                return false to
                    "Not enough ${product.name} in stock."
            }

            db.update(
                "products",
                ContentValues().apply {
                    put(
                        "quantity",
                        newQuantity
                    )
                    put(
                        "updated_at",
                        System.currentTimeMillis()
                    )
                    put(
                        "sync_status",
                        SYNC_PENDING
                    )
                },
                "id = ?",
                arrayOf(
                    productId.toString()
                )
            )

            db.insertOrThrow(
                "stock_transactions",
                null,
                ContentValues().apply {
                    put(
                        "id",
                        UUID.randomUUID()
                            .toString()
                    )
                    put(
                        "product_id",
                        productId
                    )
                    put("type", type)
                    put(
                        "quantity",
                        quantity
                    )
                    put(
                        "unit",
                        product.unit
                    )
                    put(
                        "source",
                        source
                    )
                    put(
                        "transcript",
                        transcript
                    )
                    put(
                        "created_at",
                        System.currentTimeMillis()
                    )
                    put(
                        "sync_status",
                        SYNC_PENDING
                    )
                }
            )

            db.setTransactionSuccessful()

            return true to
                "${product.name} updated to ${formatQuantity(newQuantity)} ${product.unit}."
        } finally {
            db.endTransaction()
        }
    }

    fun getLowStockProducts():
        List<Product> =
        getProducts().filter {
            it.quantity <= it.minimumStock
        }

    fun getTransactions(
        limit: Int = 30
    ): List<StockTransaction> {
        val result =
            mutableListOf<StockTransaction>()

        readableDatabase.rawQuery(
            """
            SELECT t.id, t.product_id, p.name,
                   t.type, t.quantity, t.unit,
                   t.source, t.transcript,
                   t.created_at, t.sync_status
            FROM stock_transactions t
            JOIN products p
              ON p.id = t.product_id
            ORDER BY t.created_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += StockTransaction(
                    id = cursor.getString(0),
                    productId =
                        cursor.getLong(1),
                    productName =
                        cursor.getString(2),
                    type =
                        cursor.getString(3),
                    quantity =
                        cursor.getDouble(4),
                    unit =
                        cursor.getString(5),
                    source =
                        cursor.getString(6),
                    transcript =
                        if (cursor.isNull(7))
                            null
                        else
                            cursor.getString(7),
                    createdAt =
                        cursor.getLong(8),
                    syncStatus =
                        cursor.getString(9)
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

        return DashboardStats(
            totalProducts =
                products.size,
            lowStock =
                products.count {
                    it.quantity <=
                        it.minimumStock
                },
            outOfStock =
                products.count {
                    it.quantity <= 0.0
                },
            pendingSync =
                pendingTransactions +
                pendingProducts
        )
    }

    fun markEverythingSynced():
        Int {
        val db =
            writableDatabase

        db.beginTransaction()

        try {
            val values =
                ContentValues().apply {
                    put(
                        "sync_status",
                        SYNC_SYNCED
                    )
                }

            val productsUpdated =
                db.update(
                    "products",
                    values,
                    "sync_status = ?",
                    arrayOf(SYNC_PENDING)
                )

            val transactionsUpdated =
                db.update(
                    "stock_transactions",
                    values,
                    "sync_status = ?",
                    arrayOf(SYNC_PENDING)
                )

            db.setTransactionSuccessful()

            return productsUpdated +
                transactionsUpdated
        } finally {
            db.endTransaction()
        }
    }

    private fun count(
        sql: String,
        args: Array<String>
    ): Int {
        readableDatabase
            .rawQuery(
                sql,
                args
            ).use { cursor ->
                return if (
                    cursor.moveToFirst()
                )
                    cursor.getInt(0)
                else
                    0
            }
    }

    private fun formatQuantity(
        value: Double
    ): String =
        if (value % 1.0 == 0.0)
            value.toLong()
                .toString()
        else
            "%.2f".format(value)
}
