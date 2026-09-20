package com.example.gigpoint.data

import android.content.ContentValues
import com.example.gigpoint.domain.MerchantCommand
import java.util.UUID

class CommandRepository(
    private val db: DatabaseHelper
) {
    fun getIdempotentResult(commandId: String): String? =
        db.readableDatabase.rawQuery(
            """
            SELECT result_message
            FROM idempotency_keys
            WHERE command_id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(commandId)
        ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun savePlanned(command: MerchantCommand) {
        db.writableDatabase.insertWithOnConflict(
            "voice_commands",
            null,
            ContentValues().apply {
                put("id", command.id)
                put("merchant_id", command.merchantId)
                put("transcript", command.sourceTranscript)
                put("intent", command.intent.name)
                put("status", "PLANNED")
                put("language_tag", command.languageTag)
                put("speaker_id", command.speakerId)
                put("speaker_confidence", command.speakerConfidence)
                put("created_at", command.createdAt)
                put("sync_status", DatabaseHelper.SYNC_PENDING)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
        )

        command.items.forEach { item ->
            db.writableDatabase.insert(
                "voice_command_items",
                null,
                ContentValues().apply {
                    put("command_id", command.id)
                    put("ordinal", item.ordinal)
                    put("product_mention", item.productMention)
                    put("product_id", item.resolvedProductId)
                    put("variant_id", item.resolvedVariantId)
                    put("operation", item.operation.name)
                    put("quantity", item.quantity)
                    put("quantity_unit", item.quantityUnit)
                    put("package_quantity", item.packageQuantity)
                    put("package_unit", item.packageUnit)
                    put("packaging", item.packaging)
                    put("price", item.price)
                    put("confidence", item.confidence.score)
                    put("clarification", item.clarification)
                }
            )
        }
    }

    fun markExecuted(commandId: String, message: String) {
        val now = System.currentTimeMillis()

        db.writableDatabase.update(
            "voice_commands",
            ContentValues().apply {
                put("status", "EXECUTED")
                put("executed_at", now)
                put("sync_status", DatabaseHelper.SYNC_PENDING)
            },
            "id = ?",
            arrayOf(commandId)
        )

        db.writableDatabase.insertWithOnConflict(
            "idempotency_keys",
            null,
            ContentValues().apply {
                put("command_id", commandId)
                put("result_message", message)
                put("created_at", now)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun markFailed(commandId: String, message: String) {
        db.writableDatabase.update(
            "voice_commands",
            ContentValues().apply { put("status", "FAILED") },
            "id = ?",
            arrayOf(commandId)
        )
        appendAudit(
            commandId, "COMMAND_FAILED", "VOICE_COMMAND", commandId, message
        )
    }

    fun appendAudit(
        commandId: String?,
        eventType: String,
        entityType: String,
        entityId: String?,
        payload: String?
    ) {
        db.writableDatabase.insert(
            "audit_events",
            null,
            ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("command_id", commandId)
                put("event_type", eventType)
                put("entity_type", entityType)
                put("entity_id", entityId)
                put("payload", payload)
                put("created_at", System.currentTimeMillis())
                put("sync_status", DatabaseHelper.SYNC_PENDING)
            }
        )
    }
}
