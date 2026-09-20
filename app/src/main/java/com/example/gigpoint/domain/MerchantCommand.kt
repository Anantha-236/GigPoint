package com.example.gigpoint.domain

import java.util.UUID

data class MerchantCommand(
    val id: String = UUID.randomUUID().toString(),
    val intent: InventoryOperation,
    val items: List<CommandItem>,
    val sourceTranscript: String,
    val languageTag: String? = null,
    val speakerId: String? = null,
    val speakerConfidence: Double? = null,
    val merchantId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
