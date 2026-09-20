package com.example.gigpoint.actions

import com.example.gigpoint.voice.InventoryAction

import com.example.gigpoint.AppPreferences

/**
 * Security / privilege boundary for voice commands.
 *
 * Whisper itself has ZERO database privileges.
 *
 * Flow:
 *
 * Whisper
 *   -> transcript
 *   -> understanding
 *   -> conversation
 *   -> policy
 *   -> confirmation
 *   -> executor
 *   -> database
 */
object VoiceActionPolicy {

    /**
     * Actions that only read inventory information.
     */
    fun isReadOnly(
        action: InventoryAction
    ): Boolean {

        return action in setOf(
            InventoryAction.CHECK_STOCK,
            InventoryAction.LOW_STOCK
        )
    }

    /**
     * Actions which can actually be passed to the executor.
     *
     * UNKNOWN and OUTBOUND_UNSPECIFIED are incomplete intents
     * and must never directly modify inventory.
     */
    fun canExecute(
        action: InventoryAction
    ): Boolean {

        return action !in setOf(
            InventoryAction.UNKNOWN,
            InventoryAction.OUTBOUND_UNSPECIFIED
        )
    }

    /**
     * Inventory-changing commands ALWAYS require confirmation.
     *
     * This remains true even if the merchant turns off the
     * general confirmation preference.
     */
    fun requiresMandatoryConfirmation(
        action: InventoryAction
    ): Boolean {

        return action in setOf(

            InventoryAction.STOCK_IN,

            InventoryAction.SALE,

            InventoryAction.DAMAGE,

            InventoryAction.EXPIRED,

            InventoryAction.CUSTOMER_RETURN,

            InventoryAction.SUPPLIER_RETURN,

            InventoryAction.ADJUSTMENT_IN,

            InventoryAction.ADJUSTMENT_OUT,

            InventoryAction.SET_STOCK,

            InventoryAction.SET_REORDER_LEVEL,

            InventoryAction.ARCHIVE_PRODUCT
        )
    }

    /**
     * Final decision used by VoiceConversationManager.
     */
    fun requiresConfirmation(
        action: InventoryAction,
        preferences: AppPreferences
    ): Boolean {

        if (
            requiresMandatoryConfirmation(
                action
            )
        ) {
            return true
        }

        if (
            isReadOnly(
                action
            )
        ) {
            return false
        }

        return preferences
            .confirmVoiceActions()
    }

    /**
     * Voice must NEVER be allowed to perform these operations:
     *
     * - account deletion
     * - arbitrary SQL
     * - delete transaction history
     * - another merchant's data
     * - Supabase secret/service-role access
     *
     * Those operations are intentionally not represented by
     * InventoryAction.
     */
}