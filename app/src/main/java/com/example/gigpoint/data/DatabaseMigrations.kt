package com.example.gigpoint.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.Locale

object DatabaseMigrations {
    const val PRODUCTION_CORE_VERSION = 3

    fun migrateToProductionCore(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            createProductionTables(db)
            extendLegacyTransactions(db)
            migrateLegacyProducts(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun createProductionTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS product_variants (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                sku TEXT NOT NULL UNIQUE,
                variant_name TEXT,
                package_quantity REAL,
                package_unit TEXT,
                container_type TEXT,
                selling_unit TEXT NOT NULL,
                purchase_price REAL,
                selling_price REAL,
                barcode TEXT,
                active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(product_id) REFERENCES products(id)
            )
            """.trimIndent()
        )

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_variants_product
            ON product_variants(product_id, active)
        """.trimIndent())

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory (
                variant_id INTEGER PRIMARY KEY,
                quantity REAL NOT NULL DEFAULT 0,
                reorder_level REAL NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                sync_status TEXT NOT NULL DEFAULT 'PENDING',
                FOREIGN KEY(variant_id) REFERENCES product_variants(id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS product_aliases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                variant_id INTEGER,
                alias TEXT NOT NULL,
                normalized_alias TEXT NOT NULL,
                language TEXT,
                source TEXT NOT NULL DEFAULT 'MERCHANT',
                confidence REAL NOT NULL DEFAULT 1.0,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(product_id) REFERENCES products(id),
                FOREIGN KEY(variant_id) REFERENCES product_variants(id)
            )
            """.trimIndent()
        )

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_alias_normalized
            ON product_aliases(normalized_alias)
        """.trimIndent())

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stock_batches (
                id TEXT PRIMARY KEY,
                variant_id INTEGER NOT NULL,
                quantity_received REAL NOT NULL,
                quantity_remaining REAL NOT NULL,
                purchase_price REAL,
                supplier TEXT,
                received_at INTEGER NOT NULL,
                manufactured_at INTEGER,
                expires_at INTEGER,
                sync_status TEXT NOT NULL DEFAULT 'PENDING',
                FOREIGN KEY(variant_id) REFERENCES product_variants(id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS voice_commands (
                id TEXT PRIMARY KEY,
                merchant_id TEXT,
                transcript TEXT NOT NULL,
                intent TEXT NOT NULL,
                status TEXT NOT NULL,
                language_tag TEXT,
                speaker_id TEXT,
                speaker_confidence REAL,
                created_at INTEGER NOT NULL,
                executed_at INTEGER,
                sync_status TEXT NOT NULL DEFAULT 'PENDING'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS voice_command_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                command_id TEXT NOT NULL,
                ordinal INTEGER NOT NULL,
                product_mention TEXT NOT NULL,
                product_id INTEGER,
                variant_id INTEGER,
                operation TEXT NOT NULL,
                quantity REAL,
                quantity_unit TEXT,
                package_quantity REAL,
                package_unit TEXT,
                packaging TEXT,
                price REAL,
                confidence REAL NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'PENDING',
                clarification TEXT,
                FOREIGN KEY(command_id) REFERENCES voice_commands(id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS audit_events (
                id TEXT PRIMARY KEY,
                command_id TEXT,
                event_type TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id TEXT,
                payload TEXT,
                created_at INTEGER NOT NULL,
                sync_status TEXT NOT NULL DEFAULT 'PENDING'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS idempotency_keys (
                command_id TEXT PRIMARY KEY,
                result_message TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun extendLegacyTransactions(db: SQLiteDatabase) {
        addColumnIfMissing(db, "stock_transactions", "variant_id", "INTEGER")
        addColumnIfMissing(db, "stock_transactions", "command_id", "TEXT")
        addColumnIfMissing(db, "stock_transactions", "reverses_transaction_id", "TEXT")
        addColumnIfMissing(db, "stock_transactions", "reversed_by_transaction_id", "TEXT")

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_transactions_command
            ON stock_transactions(command_id)
        """.trimIndent())
    }

    private fun migrateLegacyProducts(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()

        db.rawQuery(
            "SELECT id, name, unit, quantity, minimum_stock FROM products",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val productId = cursor.getLong(0)
                val name = cursor.getString(1)
                val unit = cursor.getString(2)
                val quantity = cursor.getDouble(3)
                val minimum = cursor.getDouble(4)
                val sku = "LEGACY-$productId-${slug(name)}"

                val variantId =
                    db.rawQuery(
                        "SELECT id FROM product_variants WHERE sku = ? LIMIT 1",
                        arrayOf(sku)
                    ).use { c ->
                        if (c.moveToFirst()) c.getLong(0) else null
                    }
                    ?: db.insertOrThrow(
                        "product_variants",
                        null,
                        ContentValues().apply {
                            put("product_id", productId)
                            put("sku", sku)
                            put("variant_name", "Default")
                            put("selling_unit", unit)
                            put("active", 1)
                            put("created_at", now)
                            put("updated_at", now)
                        }
                    )

                db.insertWithOnConflict(
                    "inventory",
                    null,
                    ContentValues().apply {
                        put("variant_id", variantId)
                        put("quantity", quantity)
                        put("reorder_level", minimum)
                        put("updated_at", now)
                        put("sync_status", DatabaseHelper.SYNC_PENDING)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE
                )

                val normalized = normalizeAlias(name)
                val aliasExists =
                    db.rawQuery(
                        """
                        SELECT 1 FROM product_aliases
                        WHERE product_id = ? AND normalized_alias = ?
                        LIMIT 1
                        """.trimIndent(),
                        arrayOf(productId.toString(), normalized)
                    ).use { it.moveToFirst() }

                if (!aliasExists) {
                    db.insert(
                        "product_aliases",
                        null,
                        ContentValues().apply {
                            put("product_id", productId)
                            putNull("variant_id")
                            put("alias", name)
                            put("normalized_alias", normalized)
                            put("language", "und")
                            put("source", "CATALOG")
                            put("confidence", 1.0)
                            put("created_at", now)
                        }
                    )
                }
            }
        }
    }

    private fun addColumnIfMissing(
        db: SQLiteDatabase,
        table: String,
        column: String,
        definition: String
    ) {
        if (!columnExists(db, table, column)) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }

    private fun columnExists(
        db: SQLiteDatabase,
        table: String,
        column: String
    ): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex).equals(column, true)) {
                    found = true
                    break
                }
            }
            found
        }

    private fun slug(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .take(32)
            .ifBlank { "product" }

    fun normalizeAlias(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{N}.₹]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
